"""
ETL Boss（老板）

老板的职责：
- 理解用户说的话
- 结合团队能力和当前状态，决定找谁
- 协调员工，追踪问题

设计原则：
- Boss 通过 LLM 理解用户意图（使用 structured output 确保输出格式）
- Boss 根据当前状态动态决策（不是固定流水线）
- 通过 Blackboard 与员工通信
"""

from __future__ import annotations

import logging
import time
from collections.abc import Callable
from typing import TYPE_CHECKING, Any, Literal

from pydantic import BaseModel, Field

from src.modules.etl.schemas.requests import BlackboardRequest
from src.modules.etl.state.blackboard import AgentReport, Blackboard

if TYPE_CHECKING:
    pass

logger = logging.getLogger(__name__)


class BossDecision(BaseModel):
    """Boss 的决策结果（用于 structured output）"""

    action: Literal["route", "complete", "ask_human"] = Field(
        ...,
        description="决策动作：route=找员工执行任务，complete=任务已完成，ask_human=需要用户澄清",
    )
    target_agent: (
        Literal["analyst_agent", "architect_agent", "developer_agent", "reviewer_agent"] | None
    ) = Field(
        default=None,
        description="目标员工ID，仅当 action=route 时必填",
    )
    reason: str = Field(..., description="决策理由")


# 有效的员工 ID（knowledge_agent 是共享服务，不在此列）
# 使用元组保证顺序，便于状态描述时按固定顺序展示
AGENT_IDS: tuple[str, ...] = (
    "analyst_agent",
    "architect_agent",
    "developer_agent",
    "reviewer_agent",
)
AGENT_IDS_SET: set[str] = set(AGENT_IDS)

_BOSS_SYSTEM_PROMPT = """你是 Datapillar ETL 多智能体系统的老板（Sunny）。

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

### reviewer_agent
- 职责：方案和代码评审
- 依赖：analyst_agent 和 developer_agent 的产物
- 能力：review 设计和代码
- 输出：ReviewResult
- 何时找他：需要 review 设计或代码

## 你的职责

1. 理解用户说的话
2. 结合当前状态（已有的产物、各员工的进度）
3. 决定下一步：找员工（route）、任务完成（complete）、或请用户澄清（ask_human）

## 输出格式（JSON）

决策完成后，直接输出以下 JSON 格式：
```json
{
  "action": "route",
  "target_agent": "analyst_agent",
  "reason": "用户提出了新需求，需要先进行需求分析"
}
```

## 字段说明
- action: 决策动作
  - "route": 找员工执行任务（必须填 target_agent）
  - "complete": 任务已完成
  - "ask_human": 需要用户澄清
- target_agent: 目标员工ID（仅当 action=route 时必填）
  - "analyst_agent": 需求分析师
  - "architect_agent": 数据架构师
  - "developer_agent": 数据开发
  - "reviewer_agent": 方案评审
- reason: 决策理由（必填）
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
    - 理解用户说的话（通过 LLM + structured output）
    - 结合当前状态动态决策
    - 协调员工，追踪问题
    """

    def __init__(
        self,
        *,
        now_ms: Callable[[], int] | None = None,
    ):
        from src.infrastructure.llm.client import call_llm

        # Boss 的路由属于"控制面"决策，必须可复现；使用 temperature=0.0 保证确定性。
        # 不传 output_schema，改用 with_structured_output(json_mode) + 兜底机制
        self._llm = call_llm(temperature=0.0)
        self._now_ms_fn: Callable[[], int] = now_ms or (lambda: int(time.time() * 1000))

    def _now_ms(self) -> int:
        return int(self._now_ms_fn())

    async def decide(self, blackboard: Blackboard) -> dict[str, Any]:
        """
        决策：下一步做什么

        决策流程：
        1. 前置拦截：处理必须优先处理的中断请求（human/delegate）
        2. 确定性推进：当状态可推导时不依赖 LLM（保证可复现）
        3. LLM 决策：其余业务决策由 LLM 做
        """
        # 前置拦截：human 请求是"中断"，必须优先处理
        if blackboard.has_human_request():
            return {"current_agent": "human_in_the_loop"}

        # 前置拦截：delegate 请求是员工发起的委派，必须处理
        pending = blackboard.pending_requests
        if pending:
            req = pending[0]
            if req.kind == "delegate" and req.target_agent in AGENT_IDS_SET:
                return {"current_agent": req.target_agent}

        # 确定性推进：已有进度（或任务已完成）时，优先走可推导规则
        progressed_decision = self._decide_by_progress(blackboard)
        if progressed_decision is not None:
            return {"current_agent": progressed_decision}

        # LLM 决策：所有业务决策由 LLM 做（包括判断是否完成）
        decision = await self._decide_by_llm(blackboard)

        # 应用决策
        if decision.action == "complete":
            return {"current_agent": "finalize"}
        elif decision.action == "ask_human":
            return {"current_agent": "human_in_the_loop"}
        else:  # route
            return {"current_agent": decision.target_agent}

    def _decide_by_progress(self, blackboard: Blackboard) -> str | None:
        """
        基于黑板状态的确定性路由（不调用 LLM）。

        规则意图：
        - 任务已完成 / 全员已完成：直接 finalize
        - 已存在阶段性产物：按依赖顺序推进到下一位员工，并执行“分阶段 review”
          1) analyst -> architect -> reviewer(设计review) -> developer -> reviewer(开发review) -> finalize
        - 设计/开发 review 通过与否由 Blackboard 维护（design_review_passed/development_review_passed）
        - 状态冲突或信息不足：返回 None，交给 LLM 决策
        """
        if blackboard.is_completed:
            return "finalize"

        reports = blackboard.reports or {}

        def is_completed(agent_id: str) -> bool:
            report = reports.get(agent_id)
            return bool(report and report.status == "completed")

        # 无任何进度时，交给 LLM 做语义决策（避免退化为固定流水线）
        if not reports:
            return None

        analyst_done = is_completed("analyst_agent")
        architect_done = is_completed("architect_agent")
        developer_done = is_completed("developer_agent")
        design_review_passed = bool(blackboard.design_review_passed)
        development_review_passed = bool(blackboard.development_review_passed)

        # 只有在依赖链前置已完成时，才推进到下一位；否则交给 LLM 处理异常状态
        if analyst_done and not architect_done:
            return "architect_agent"

        # 设计阶段：架构完成后，必须先跑一次 reviewer review（不依赖 SQL）
        if analyst_done and architect_done and not design_review_passed:
            return "reviewer_agent"

        if analyst_done and architect_done and design_review_passed and not developer_done:
            return "developer_agent"

        # 开发阶段：开发完成后，必须再跑一次 reviewer review（包含 SQL）
        if (
            analyst_done
            and architect_done
            and design_review_passed
            and developer_done
            and not development_review_passed
        ):
            return "reviewer_agent"

        if (
            analyst_done
            and architect_done
            and design_review_passed
            and developer_done
            and development_review_passed
        ):
            return "finalize"

        return None

    async def _decide_by_llm(self, blackboard: Blackboard) -> BossDecision:
        """
        通过 LLM 决策下一步

        使用 with_structured_output(json_mode) + parse_structured_output 兜底
        """
        from src.infrastructure.llm.structured_output import parse_structured_output

        user_input = blackboard.task or ""
        if not user_input.strip():
            return BossDecision(
                action="ask_human",
                reason="用户输入为空，需要用户提供需求",
            )

        # 构建当前状态描述
        current_state = self._build_state_description(blackboard)

        # 构建消息
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

            # 使用 json_mode + include_raw（和 Agent 保持一致）
            llm_structured = self._llm.with_structured_output(
                BossDecision,
                method="json_mode",
                include_raw=True,
            )
            result = await llm_structured.ainvoke(messages)

            # 情况 1：直接解析成功
            if isinstance(result, BossDecision):
                logger.info(
                    f"Boss 决策: {result.action} -> {result.target_agent}, 理由: {result.reason}"
                )
                return result

            # 情况 2：dict 格式（include_raw=True 的返回）
            if isinstance(result, dict):
                parsed = result.get("parsed")
                if isinstance(parsed, BossDecision):
                    logger.info(
                        f"Boss 决策: {parsed.action} -> {parsed.target_agent}, 理由: {parsed.reason}"
                    )
                    return parsed

                # 解析失败，尝试从 raw 中恢复
                raw = result.get("raw")
                if raw:
                    raw_text = getattr(raw, "content", None)
                    if raw_text:
                        logger.warning(
                            "with_structured_output 解析失败，尝试 parse_structured_output 兜底"
                        )
                        try:
                            decision = parse_structured_output(raw_text, BossDecision)
                            logger.info(
                                f"Boss 决策(兜底): {decision.action} -> {decision.target_agent}, 理由: {decision.reason}"
                            )
                            return decision
                        except ValueError as e:
                            logger.error(f"parse_structured_output 兜底也失败: {e}")

            # 所有解析都失败，让用户介入
            return BossDecision(
                action="ask_human",
                reason="LLM 输出格式异常，需要用户介入",
            )

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

        lines.append("\nReview 阶段:")
        lines.append(
            f"- 设计阶段: {'已通过' if blackboard.design_review_passed else '未通过/未执行'}"
        )
        lines.append(
            f"- 开发阶段: {'已通过' if blackboard.development_review_passed else '未通过/未执行'}"
        )

        # 交付物
        if blackboard.deliverable:
            lines.append(f"\n已有交付物: {len(blackboard.deliverable.jobs)} 个 Job")

        return "\n".join(lines)

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
