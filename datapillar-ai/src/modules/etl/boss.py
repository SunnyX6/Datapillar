"""
BossAgent - ETL 多智能体系统的老板

Boss 是独立于员工图的 Agent，负责：
- 理解用户意图（通过 LLM）
- 主动查看 Blackboard（直接访问属性）
- 调用员工子图执行任务（通过 worker_graph.invoke()）
- 与用户对话（返回消息）

设计原则：
- Boss 不需要工具，直接在代码中执行逻辑
- Boss 持有 Blackboard 引用，可以主动查看状态
- Boss 通过 worker_graph.invoke() 调用员工子图
- Boss 返回 AgentResult，统一接口
"""

from __future__ import annotations

import logging
import uuid
from typing import TYPE_CHECKING, Any, Literal

from pydantic import BaseModel, Field

from src.infrastructure.llm.client import call_llm
from src.modules.etl.schemas.requests import BlackboardRequest
from src.modules.etl.state import Blackboard

if TYPE_CHECKING:
    from src.modules.etl.worker_graph import WorkerGraph

logger = logging.getLogger(__name__)


class BossDecision(BaseModel):
    """Boss 的决策结果（用于 structured output）"""

    action: Literal["dispatch", "complete", "ask_human"] = Field(
        ...,
        description="决策动作：dispatch=派活给员工，complete=任务已完成，ask_human=需要与用户对话",
    )
    target_agent: (
        Literal["analyst_agent", "architect_agent", "developer_agent", "reviewer_agent"] | None
    ) = Field(
        default=None,
        description="目标员工ID，仅当 action=dispatch 时必填",
    )
    reason: str = Field(..., description="决策理由（内部记录）")
    message: str | None = Field(
        default=None,
        description="对用户说的话，当 action=ask_human 时必填",
    )


# 有效的员工 ID
AGENT_IDS: tuple[str, ...] = (
    "analyst_agent",
    "architect_agent",
    "developer_agent",
    "reviewer_agent",
)
AGENT_IDS_SET: set[str] = set(AGENT_IDS)


_BOSS_SYSTEM_PROMPT = """你是 Datapillar 的老板（Sunny）。

## 你的身份

你是一位幽默善良、专业的数仓团队负责人。你可以与用户自然对话，理解他们的诉求意图，并协调团队完成任务。


## 团队能力
目前你的团队只能处理元数据相关内容以及ETL任务生成工作流，其他的需求暂时无法处理，比如“BI需求”，“问有哪些数据”这些需求都处理不了。

## 你的员工

### analyst_agent
- 职责：需求分析师
- 能力：理解用户需求，收敛业务口径
- 何时找他：用户提出明确的 ETL/数据开发需求

### architect_agent
- 职责：数据架构师
- 依赖：analyst_agent 的产物
- 能力：设计 ETL 工作流、选择技术组件
- 何时找他：需要设计/修改架构

### developer_agent
- 职责：数据开发
- 依赖：architect_agent 的产物
- 能力：生成 SQL 代码
- 何时找他：需要生成/修改 SQL

### reviewer_agent
- 职责：方案和代码评审
- 依赖：analyst_agent 和 developer_agent 的产物
- 能力：review 设计和代码
- 何时找他：需要 review 设计或代码

## 你的职责

1. 与用户自然对话，理解他们的意图
2. 查看当前状态（已有的产物、各员工的进度）
3. 决定下一步：
   - dispatch: 用户提出了明确的 ETL 需求，派活给对应员工
   - complete: 任务已完成
   - ask_human: 需要与用户继续对话（闲聊、引导、澄清）

## 输出格式（JSON）

```json
{
  "action": "ask_human",
  "target_agent": null,
  "reason": "用户只是打招呼，需要引导提供具体需求",
  "message": "你好！我是 Sunny，ETL 团队的负责人。我的团队可以帮你完成数据开发任务，比如数据清洗、ETL 流程设计、SQL 开发等。请告诉我你想做什么？"
}
```

## 字段说明

- action: 决策动作
  - "dispatch": 派活给员工（必须填 target_agent）
  - "complete": 任务已完成
  - "ask_human": 与用户对话（必须填 message）
- target_agent: 目标员工ID（仅当 action=dispatch 时必填）
- reason: 决策理由（内部记录，必填）
- message: 对用户说的话（当 action=ask_human 时必填，要友善、专业）

## 重要提示
- 当用户只是打招呼、闲聊、询问团队能力时，使用 ask_human 并友善回复，可以适当带上emoji给用户更好体验
- 当用户提出明确的数据开发需求时，使用 dispatch 交给 analyst_agent
- message 是你直接对用户说的话，要像正常对话一样自然

## 禁止
- 禁止回答用户超出自己团队能力的事情
- 禁止胡乱编造
"""


class BossAgent:
    """
    BossAgent - ETL 多智能体系统的老板

    Boss 是独立于员工图的 Agent，负责：
    - 理解用户意图（通过 LLM）
    - 主动查看 Blackboard（直接访问属性）
    - 调用员工子图执行任务
    - 与用户对话
    """

    def __init__(self):
        # Boss 的决策使用 temperature=0.0 保证确定性
        self._llm = call_llm(temperature=0.0)

    async def run(
        self,
        *,
        user_input: str,
        blackboard: Blackboard,
        worker_graph: WorkerGraph,
    ) -> dict[str, Any]:
        """
        Boss 的主运行方法

        流程：
        1. 前置拦截：处理必须优先处理的请求（human/delegate）
        2. 确定性推进：当状态可推导时不调用 LLM
        3. LLM 决策：其余业务决策由 LLM 做
        4. 执行决策：调用员工子图或返回消息

        参数：
        - user_input: 用户输入
        - blackboard: 共享状态（Boss 主动查看）
        - worker_graph: 员工子图（Boss 调用）

        返回：
        - blackboard: 更新后的共享状态
        - boss_message: Boss 的回复消息（如果有）
        - is_completed: 任务是否完成
        """
        # 更新任务（对话历史在 LLM 决策后记录，避免重复）
        if user_input:
            blackboard.task = user_input

        # 前置拦截：human 请求是"中断"，必须优先处理
        if blackboard.has_human_request():
            return {
                "blackboard": blackboard,
                "need_human_input": True,
            }

        # 前置拦截：delegate 请求是员工发起的委派
        pending = blackboard.pending_requests
        if pending:
            req = pending[0]
            if req.kind == "delegate" and req.target_agent in AGENT_IDS_SET:
                # 移除已处理的请求，避免死循环
                blackboard.pending_requests = [
                    r for r in blackboard.pending_requests if r.request_id != req.request_id
                ]
                # 调用员工子图执行
                result = await self._dispatch_worker(
                    blackboard=blackboard,
                    worker_graph=worker_graph,
                    target_agent=req.target_agent,
                )
                return result

        # 确定性推进：已有进度时，优先走可推导规则
        next_agent = self._decide_by_progress(blackboard)
        if next_agent == "finalize":
            blackboard.is_completed = True
            return {
                "blackboard": blackboard,
                "is_completed": True,
            }
        if next_agent:
            # 调用员工子图执行
            result = await self._dispatch_worker(
                blackboard=blackboard,
                worker_graph=worker_graph,
                target_agent=next_agent,
            )
            return result

        # LLM 决策
        decision = await self._decide_by_llm(blackboard)

        # 应用决策
        if decision.action == "complete":
            blackboard.is_completed = True
            return {
                "blackboard": blackboard,
                "is_completed": True,
            }

        elif decision.action == "ask_human":
            # 记录 Boss 的回复到对话历史（使用 JSON 格式，保持与 LLM 输出一致）
            if decision.message:
                decision_json = decision.model_dump_json(ensure_ascii=False)
                blackboard.add_agent_turn("boss", "assistant", decision_json)

            # 创建 human 请求
            human_request = BlackboardRequest(
                request_id=f"req_{uuid.uuid4().hex}",
                kind="human",
                created_by="boss",
                resume_to="boss",
                payload={
                    "type": "boss_conversation",
                    "message": decision.message,
                },
            )
            blackboard.pending_requests.append(human_request)

            return {
                "blackboard": blackboard,
                "boss_message": decision.message,
                "need_human_input": True,
            }

        else:  # dispatch
            # 调用员工子图执行
            result = await self._dispatch_worker(
                blackboard=blackboard,
                worker_graph=worker_graph,
                target_agent=decision.target_agent,
            )
            return result

    async def _dispatch_worker(
        self,
        *,
        blackboard: Blackboard,
        worker_graph: WorkerGraph,
        target_agent: str,
    ) -> dict[str, Any]:
        """
        调用员工子图执行任务

        这是 Boss 独立于员工图的关键：
        - Boss 不在员工图内
        - Boss 通过 worker_graph.invoke() 调用员工子图
        - 员工子图执行完成后，Boss 获取更新后的 Blackboard
        """
        logger.info(f"👔 Boss 派活给 {target_agent}")

        # 编译并调用员工子图
        compiled_graph = worker_graph.compile()

        # 准备子图输入（状态转换：父图 → 子图）
        worker_input = {
            "blackboard": blackboard,
            "target_agent": target_agent,
            "handover": None,  # 子图会自己初始化
        }

        # 调用子图
        result = await compiled_graph.ainvoke(worker_input)

        # 获取更新后的 Blackboard（状态转换：子图 → 父图）
        updated_blackboard = result.get("blackboard", blackboard)

        # 检查是否完成
        if updated_blackboard.is_completed:
            return {
                "blackboard": updated_blackboard,
                "is_completed": True,
            }

        # 检查是否需要人机交互
        if updated_blackboard.has_human_request():
            return {
                "blackboard": updated_blackboard,
                "need_human_input": True,
            }

        return {
            "blackboard": updated_blackboard,
        }

    def _decide_by_progress(self, blackboard: Blackboard) -> str | None:
        """
        基于黑板状态的确定性路由（不调用 LLM）

        返回：
        - 员工 ID：需要调用的下一个员工
        - "finalize"：任务已完成
        - None：需要 LLM 决策
        """
        if blackboard.is_completed:
            return "finalize"

        reports = blackboard.reports or {}

        def is_completed(agent_id: str) -> bool:
            report = reports.get(agent_id)
            return bool(report and report.status == "completed")

        # 无任何进度时，交给 LLM 做语义决策
        if not reports:
            return None

        analyst_done = is_completed("analyst_agent")
        architect_done = is_completed("architect_agent")
        developer_done = is_completed("developer_agent")
        design_review_passed = bool(blackboard.design_review_passed)
        development_review_passed = bool(blackboard.development_review_passed)

        # 按依赖顺序推进
        if analyst_done and not architect_done:
            return "architect_agent"

        if analyst_done and architect_done and not design_review_passed:
            return "reviewer_agent"

        if analyst_done and architect_done and design_review_passed and not developer_done:
            return "developer_agent"

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
        """通过 LLM 决策下一步"""
        from langchain_core.messages import AIMessage, HumanMessage, SystemMessage

        from src.infrastructure.llm.structured_output import parse_structured_output

        user_input = blackboard.task or ""

        # 构建当前状态描述
        current_state = self._build_state_description(blackboard)

        # SystemMessage: 系统提示 + 当前状态
        system_content = f"{_BOSS_SYSTEM_PROMPT}\n\n## 当前状态\n\n{current_state}"
        messages: list = [SystemMessage(content=system_content)]

        # 历史对话（不包含当前用户输入，避免重复）
        memory = blackboard.ensure_memory()
        boss_conv = memory.get_agent_conversation("boss")
        for turn in boss_conv.recent_turns:
            role = turn.get("role", "")
            content = turn.get("content", "")
            if role == "user":
                messages.append(HumanMessage(content=content))
            elif role == "assistant":
                messages.append(AIMessage(content=content))

        # HumanMessage: 用户当前输入
        messages.append(HumanMessage(content=user_input))

        # 记录当前用户输入到对话历史（在构建消息后、调用 LLM 前记录）
        if user_input:
            blackboard.add_agent_turn("boss", "user", user_input)

        try:
            # 使用 json_mode（依赖 client 层正确传递 response_format 参数）
            llm_structured = self._llm.with_structured_output(
                BossDecision,
                method="json_mode",
                include_raw=True,
            )
            result = await llm_structured.ainvoke(messages)

            # 解析结果
            if isinstance(result, BossDecision):
                logger.info(
                    f"👔 Boss 决策: {result.action} -> {result.target_agent}, 理由: {result.reason}"
                )
                return result

            if isinstance(result, dict):
                parsed = result.get("parsed")
                if isinstance(parsed, BossDecision):
                    logger.info(
                        f"👔 Boss 决策: {parsed.action} -> {parsed.target_agent}, 理由: {parsed.reason}"
                    )
                    return parsed

                # 解析失败，尝试从 raw 中恢复
                raw = result.get("raw")
                if raw:
                    raw_text = getattr(raw, "content", None)
                    if raw_text:
                        logger.warning("with_structured_output 解析失败，尝试兜底解析")
                        try:
                            decision = parse_structured_output(raw_text, BossDecision)
                            logger.info(
                                f"👔 Boss 决策(兜底): {decision.action} -> {decision.target_agent}"
                            )
                            return decision
                        except ValueError as e:
                            logger.error(f"兜底解析也失败: {e}")

            # 所有解析都失败
            return BossDecision(
                action="ask_human",
                reason="LLM 输出格式异常，需要用户介入",
                message="抱歉，我没能理解你的意思。能再说一遍吗？",
            )

        except Exception as e:
            logger.error(f"LLM 决策失败: {e}")
            return BossDecision(
                action="ask_human",
                reason=f"LLM 决策异常: {e}",
                message="抱歉，系统出现了一些问题。请稍后再试或换个方式描述你的需求。",
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

        # 员工状态
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
