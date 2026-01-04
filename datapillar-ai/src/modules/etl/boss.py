"""
ETL Boss（老板）

老板的职责：
- 理解用户说的话
- 结合团队能力和当前状态，决定找谁
- 协调员工，追踪问题

设计原则：
- Boss 通过 LLM 理解用户意图
- Boss 根据当前状态动态决策（不是固定流水线）
- 通过 Blackboard 与员工通信
"""

from __future__ import annotations

import json
import logging
import time
from collections.abc import Callable
from typing import TYPE_CHECKING, Any, Literal

from pydantic import BaseModel, Field

from src.modules.etl.schemas.requests import BlackboardRequest
from src.modules.etl.state.blackboard import AgentReport, Blackboard

if TYPE_CHECKING:
    from langchain_core.language_models import BaseChatModel

logger = logging.getLogger(__name__)


class BossDecision(BaseModel):
    """Boss 的决策结果"""

    action: Literal["route", "complete", "ask_human"] = Field(
        ...,
        description="决策动作：route=找员工，complete=任务完成，ask_human=需要用户澄清",
    )
    target_agent: str | None = Field(
        default=None,
        description="目标员工 ID（仅 action=route 时有效）",
    )
    reason: str = Field(default="", description="决策理由")


# 有效的员工 ID（knowledge_agent 是共享服务，不在此列）
# 使用元组保证顺序，便于状态描述时按固定顺序展示
AGENT_IDS: tuple[str, ...] = ("analyst_agent", "architect_agent", "developer_agent", "tester_agent")
AGENT_IDS_SET: set[str] = set(AGENT_IDS)

_BOSS_SYSTEM_PROMPT = """你是 Datapillar ETL 多智能体系统的老板（Boss）。

## 你的员工

### analyst_agent
- 职责：需求分析师
- 能力：理解用户需求，收敛业务口径
- 输出：AnalysisResult
- 何时找他：用户提出新需求、需要澄清/修改需求

### architect_agent
- 职责：数据架构师
- 依赖：analyst_agent 的产物
- 能力：设计 ETL 工作流、选择技术组件
- 输出：Workflow
- 何时找他：需要设计/修改架构

### developer_agent
- 职责：数据开发
- 依赖：architect_agent 的产物
- 能力：生成 SQL 代码
- 输出：SQL 脚本
- 何时找他：需要生成/修改 SQL

### tester_agent
- 职责：测试验证
- 依赖：analyst_agent 和 developer_agent 的产物
- 能力：验证 SQL 是否符合需求
- 输出：测试报告
- 何时找他：需要测试/验证

## 你的职责

1. 理解用户说的话
2. 结合当前状态（已有的产物、各员工的进度）
3. 决定下一步：找员工、任务完成、或请用户澄清

## 输出格式

输出纯 JSON（不要 markdown 代码块）：

{
  "action": "route/complete/ask_human",
  "target_agent": "员工ID（仅 action=route 时填写）",
  "reason": "简短理由"
}

字段说明：
- action: 必填，route=找员工 | complete=任务完成 | ask_human=需要用户澄清
- target_agent: 仅 action=route 时必填，值为 analyst_agent/architect_agent/developer_agent/tester_agent
- reason: 必填，简短说明理由
"""

_BOSS_HUMAN_TEMPLATE = """## 当前状态

{current_state}

## 用户说

{user_input}

请决定下一步找谁。"""


class EtlBoss:
    """
    ETL 老板

    职责：
    - 理解用户说的话（通过 LLM）
    - 结合当前状态动态决策
    - 协调员工，追踪问题
    """

    def __init__(
        self,
        *,
        llm: BaseChatModel | None = None,
        now_ms: Callable[[], int] | None = None,
    ):
        self._llm = llm
        self._now_ms_fn: Callable[[], int] = now_ms or (lambda: int(time.time() * 1000))

    def _now_ms(self) -> int:
        return int(self._now_ms_fn())

    async def decide(self, blackboard: Blackboard) -> dict[str, Any]:
        """
        决策：下一步做什么

        决策流程：
        1. 前置拦截：处理必须优先处理的中断请求（human/delegate）
        2. LLM 决策：所有业务决策由 LLM 做
        """
        # 前置拦截：human 请求是"中断"，必须优先处理
        if blackboard.has_pending_human_request():
            return {"current_agent": "human_in_the_loop"}

        # 前置拦截：delegate 请求是员工发起的委派，必须处理
        pending = blackboard.pending_requests
        if pending:
            req = pending[0]
            if req.kind == "delegate" and req.target_agent in AGENT_IDS_SET:
                return {"current_agent": req.target_agent}

        # LLM 决策：所有业务决策由 LLM 做（包括判断是否完成）
        decision = await self._decide_by_llm(blackboard)

        # 应用决策
        if decision.action == "complete":
            return {"current_agent": "finalize"}
        elif decision.action == "ask_human":
            return {"current_agent": "human_in_the_loop"}
        else:  # route
            return {"current_agent": decision.target_agent}

    async def _decide_by_llm(self, blackboard: Blackboard) -> BossDecision:
        """通过 LLM 决策下一步"""
        if not self._llm:
            logger.error("Boss 没有配置 LLM，无法决策")
            return BossDecision(
                action="ask_human",
                reason="系统未配置 LLM，需要用户指定下一步",
            )

        user_input = blackboard.task or ""
        if not user_input.strip():
            return BossDecision(
                action="ask_human",
                reason="用户输入为空，需要用户提供需求",
            )

        # 构建当前状态描述
        current_state = self._build_state_description(blackboard)

        # 构建消息：SystemMessage + HumanMessage
        human_content = _BOSS_HUMAN_TEMPLATE.format(
            current_state=current_state,
            user_input=user_input,
        )

        try:
            from langchain_core.messages import HumanMessage, SystemMessage

            messages = [
                SystemMessage(content=_BOSS_SYSTEM_PROMPT),
                HumanMessage(content=human_content),
            ]
            response = await self._llm.ainvoke(messages)
            content = getattr(response, "content", "") or ""
            return self._parse_decision(content)
        except Exception as e:
            logger.error(f"LLM 决策失败: {e}")
            return BossDecision(
                action="ask_human",
                reason=f"LLM 决策异常，需要用户介入: {e}",
            )

    def _build_state_description(self, blackboard: Blackboard) -> str:
        """构建当前状态描述（告诉 LLM 完整状态）"""
        lines = []

        # 任务状态
        if blackboard.is_completed:
            lines.append("任务状态: 已完成")
        elif blackboard.error:
            lines.append(f"任务状态: 错误 - {blackboard.error}")
            if not blackboard.can_iterate():
                lines.append("（已达最大迭代次数，无法继续）")
        else:
            lines.append("任务状态: 进行中")

        # 员工状态（使用完整 ID）
        lines.append("\n员工进度:")
        for agent_id in AGENT_IDS:
            report = blackboard.get_report(agent_id)
            if report:
                lines.append(f"- {agent_id}: {report.status} - {report.summary or '无摘要'}")
            else:
                lines.append(f"- {agent_id}: 未开始")

        # 交付物
        if blackboard.deliverable:
            lines.append(f"\n已有交付物: {len(blackboard.deliverable.jobs)} 个 Job")

        return "\n".join(lines)

    def _parse_decision(self, content: str) -> BossDecision:
        """解析 LLM 输出"""
        try:
            raw = content.strip()
            if "```" in raw:
                raw = raw.replace("```json", "").replace("```", "").strip()
            data = json.loads(raw)

            action = data.get("action", "")
            target_agent = data.get("target_agent")
            reason = data.get("reason", "")

            # 验证 action
            if action not in ("route", "complete", "ask_human"):
                logger.warning(f"LLM 返回无效的 action: {action}")
                return BossDecision(
                    action="ask_human",
                    reason=f"LLM 返回无效动作 '{action}'，需要用户指定",
                )

            # 如果是 route，验证 target_agent
            if action == "route" and target_agent not in AGENT_IDS_SET:
                logger.warning(f"LLM 返回无效的 target_agent: {target_agent}")
                return BossDecision(
                    action="ask_human",
                    reason=f"LLM 返回无效员工 '{target_agent}'，需要用户指定",
                )

            return BossDecision(action=action, target_agent=target_agent, reason=reason)
        except Exception as e:
            logger.warning(f"解析决策结果失败: {e}, content={content[:200]}")
            return BossDecision(
                action="ask_human",
                reason="LLM 输出解析失败，需要用户指定下一步",
            )

    def record_report(
        self,
        blackboard: Blackboard,
        agent_id: str,
        status: str,
        summary: str,
        deliverable_ref: str | None = None,
        blocked_reason: str | None = None,
        next_suggestion: str | None = None,
    ) -> Blackboard:
        """记录员工汇报"""
        report = AgentReport(
            status=status,
            summary=summary,
            deliverable_ref=deliverable_ref,
            blocked_reason=blocked_reason,
            next_suggestion=next_suggestion,
            updated_at_ms=self._now_ms(),
        )
        blackboard.reports[agent_id] = report
        return blackboard

    def pop_completed_request(
        self,
        blackboard: Blackboard,
        completed_by: str,
    ) -> tuple[Blackboard, BlackboardRequest | None]:
        """
        弹出已完成的请求

        当目标 Agent 执行完毕后调用
        """
        if not blackboard.pending_requests:
            return blackboard, None

        req = blackboard.pending_requests[0]
        if req.kind != "delegate":
            return blackboard, None

        # 直接比较 target_agent（统一使用完整 ID 如 analyst_agent）
        if req.target_agent != completed_by:
            return blackboard, None

        # 弹出请求
        blackboard.pending_requests = blackboard.pending_requests[1:]

        # 记录结果
        blackboard.request_results[req.request_id] = {
            "status": "completed",
            "completed_by": completed_by,
            "completed_at_ms": self._now_ms(),
        }

        return blackboard, req
