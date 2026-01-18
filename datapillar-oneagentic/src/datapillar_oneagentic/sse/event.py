"""
SSE 事件协议

设计目标：
1. 前端不需要从 content 猜状态：通过 event/state/agent/tool/llm 字段直接渲染
2. role 只表示"消息角色"（system/user/assistant/tool），不再混用 agent 概念
3. agent 是执行主体，独立字段；event 是事件类型，独立字段
4. 不输出无意义的"开始/完成"文案，关键步骤用结构化字段表达

使用示例：
```python
from datapillar_oneagentic.sse import SseEvent

# Agent 开始工作
event = SseEvent.agent_start(agent_id="analyst", agent_name="需求分析师")

# 工具调用
event = SseEvent.tool_start(
    agent_id="analyst",
    agent_name="需求分析师",
    tool_name="search_tables",
    tool_input={"keyword": "orders"},
)

# 需要用户输入
event = SseEvent.agent_interrupt(
    agent_id="analyst",
    agent_name="需求分析师",
    payload={"prompt": "请问您想查询哪个数据库？"},
)
```
"""

from __future__ import annotations

from enum import Enum
from typing import Any

from pydantic import BaseModel, Field

from datapillar_oneagentic.core.status import ExecutionStatus, is_failed

from datapillar_oneagentic.utils.time import now_ms

class SseEventType(str, Enum):
    """事件类型（前端据此做稳定状态机）"""

    AGENT_START = "agent.start"
    AGENT_END = "agent.end"
    AGENT_THINKING = "agent.thinking"
    LLM_START = "llm.start"
    LLM_END = "llm.end"
    TOOL_START = "tool.start"
    TOOL_END = "tool.end"
    AGENT_INTERRUPT = "agent.interrupt"
    TODO_UPDATE = "todo.update"
    RESULT = "result"
    ERROR = "error"
    ABORTED = "aborted"


class SseState(str, Enum):
    """事件状态（前端据此渲染 icon/颜色）"""

    THINKING = "thinking"
    INVOKING = "invoking"
    WAITING = "waiting"
    DONE = "done"
    ERROR = "error"
    ABORTED = "aborted"


class SseLevel(str, Enum):
    """严重级别（前端可直接映射颜色）"""

    INFO = "info"
    SUCCESS = "success"
    WARNING = "warning"
    ERROR = "error"


class SseAgent(BaseModel):
    """Agent 信息"""

    id: str = Field(..., description="内部节点ID")
    name: str = Field(..., description="展示名")


class SseSpan(BaseModel):
    """追踪信息"""

    run_id: str | None = Field(default=None, description="LangChain/LangGraph run_id")
    parent_run_id: str | None = Field(default=None, description="父级 run_id")


class SseMessage(BaseModel):
    """消息内容"""

    role: str = Field(..., description="system/user/assistant/tool")
    content: str = Field(..., description="用户可见文本")


class SseTool(BaseModel):
    """工具调用信息"""

    name: str = Field(..., description="工具名")
    input: Any | None = Field(default=None, description="工具入参")
    output: Any | None = Field(default=None, description="工具输出")


class SseLlm(BaseModel):
    """LLM 调用信息"""

    name: str | None = Field(default=None, description="模型名")
    usage: dict[str, Any] | None = Field(default=None, description="token 使用量")
    cost_usd: dict[str, Any] | None = Field(default=None, description="费用预估")


class SseInterrupt(BaseModel):
    """中断信息"""

    payload: Any | None = Field(default=None, description="中断 payload（可选）")


class SseResult(BaseModel):
    """结果信息"""

    deliverable: Any | None = None
    deliverable_type: str | None = None
    episode_id: str | None = Field(default=None, description="经验 ID（用于反馈）")


class SseError(BaseModel):
    """错误信息"""

    message: str = Field(..., description="错误摘要")
    detail: str | None = Field(default=None, description="错误详情")


class SseEvent(BaseModel):
    """
    SSE 事件统一结构

    说明：
    - event + state + level：前端无需猜测，直接渲染
    - agent：执行主体，前端可按 agent 分组显示
    - message/tool/llm/interrupt/result/error：按需出现，避免噪声
    """

    v: int = Field(default=1, description="协议版本")
    ts: int = Field(default_factory=now_ms, description="毫秒时间戳")
    event: SseEventType = Field(..., description="事件类型")
    state: SseState = Field(..., description="事件状态")
    level: SseLevel = Field(..., description="严重级别")
    namespace: str | None = Field(default=None, description="命名空间")
    session_id: str | None = Field(default=None, description="会话 ID")
    duration_ms: int | None = Field(default=None, description="耗时（毫秒）")
    timeline: dict[str, Any] | None = Field(default=None, description="时间线数据")

    agent: SseAgent | None = None
    span: SseSpan | None = None

    message: SseMessage | None = None
    tool: SseTool | None = None
    llm: SseLlm | None = None
    interrupt: SseInterrupt | None = None
    result: SseResult | None = None
    todo: dict[str, Any] | None = None
    error: SseError | None = None

    def to_dict(self) -> dict[str, Any]:
        """转换为 dict（剔除 None 字段）"""
        return self.model_dump(exclude_none=True)

    def with_session(self, *, namespace: str, session_id: str) -> SseEvent:
        """补充会话信息"""
        return self.model_copy(update={"namespace": namespace, "session_id": session_id})

    # ==================== 工厂方法 ====================

    @classmethod
    def agent_start(
        cls,
        *,
        agent_id: str,
        agent_name: str,
        run_id: str | None = None,
        parent_run_id: str | None = None,
    ) -> SseEvent:
        """Agent 开始工作"""
        return cls(
            event=SseEventType.AGENT_START,
            state=SseState.THINKING,
            level=SseLevel.INFO,
            agent=SseAgent(id=agent_id, name=agent_name),
            span=SseSpan(run_id=run_id, parent_run_id=parent_run_id),
        )

    @classmethod
    def agent_end(
        cls,
        *,
        agent_id: str,
        agent_name: str,
        summary: str | None = None,
        run_id: str | None = None,
        parent_run_id: str | None = None,
    ) -> SseEvent:
        """Agent 完成工作"""
        return cls(
            event=SseEventType.AGENT_END,
            state=SseState.DONE,
            level=SseLevel.SUCCESS,
            agent=SseAgent(id=agent_id, name=agent_name),
            span=SseSpan(run_id=run_id, parent_run_id=parent_run_id),
            message=SseMessage(role="assistant", content=summary) if summary else None,
        )

    @classmethod
    def agent_thinking(
        cls,
        *,
        agent_id: str,
        agent_name: str,
        content: str,
        run_id: str | None = None,
        parent_run_id: str | None = None,
    ) -> SseEvent:
        """Agent 思考内容"""
        return cls(
            event=SseEventType.AGENT_THINKING,
            state=SseState.THINKING,
            level=SseLevel.INFO,
            agent=SseAgent(id=agent_id, name=agent_name),
            span=SseSpan(run_id=run_id, parent_run_id=parent_run_id),
            message=SseMessage(role="assistant", content=content),
        )

    @classmethod
    def llm_start(
        cls,
        *,
        agent_id: str,
        agent_name: str,
        model_name: str | None = None,
        run_id: str | None = None,
        parent_run_id: str | None = None,
    ) -> SseEvent:
        """LLM 调用开始"""
        return cls(
            event=SseEventType.LLM_START,
            state=SseState.THINKING,
            level=SseLevel.INFO,
            agent=SseAgent(id=agent_id, name=agent_name),
            span=SseSpan(run_id=run_id, parent_run_id=parent_run_id),
            llm=SseLlm(name=model_name),
        )

    @classmethod
    def llm_end(
        cls,
        *,
        agent_id: str,
        agent_name: str,
        model_name: str | None = None,
        usage: dict[str, Any] | None = None,
        cost_usd: dict[str, Any] | None = None,
        run_id: str | None = None,
        parent_run_id: str | None = None,
    ) -> SseEvent:
        """LLM 调用结束"""
        return cls(
            event=SseEventType.LLM_END,
            state=SseState.THINKING,
            level=SseLevel.INFO,
            agent=SseAgent(id=agent_id, name=agent_name),
            span=SseSpan(run_id=run_id, parent_run_id=parent_run_id),
            llm=SseLlm(name=model_name, usage=usage, cost_usd=cost_usd),
        )

    @classmethod
    def tool_start(
        cls,
        *,
        agent_id: str,
        agent_name: str,
        tool_name: str,
        tool_input: Any | None = None,
        run_id: str | None = None,
        parent_run_id: str | None = None,
    ) -> SseEvent:
        """工具调用开始"""
        return cls(
            event=SseEventType.TOOL_START,
            state=SseState.INVOKING,
            level=SseLevel.INFO,
            agent=SseAgent(id=agent_id, name=agent_name),
            span=SseSpan(run_id=run_id, parent_run_id=parent_run_id),
            tool=SseTool(name=tool_name, input=tool_input),
        )

    @classmethod
    def tool_end(
        cls,
        *,
        agent_id: str,
        agent_name: str,
        tool_name: str,
        tool_output: Any | None = None,
        run_id: str | None = None,
        parent_run_id: str | None = None,
    ) -> SseEvent:
        """工具调用结束"""
        return cls(
            event=SseEventType.TOOL_END,
            state=SseState.DONE,
            level=SseLevel.INFO,
            agent=SseAgent(id=agent_id, name=agent_name),
            span=SseSpan(run_id=run_id, parent_run_id=parent_run_id),
            tool=SseTool(name=tool_name, output=tool_output),
        )

    @classmethod
    def agent_interrupt(
        cls,
        *,
        agent_id: str,
        agent_name: str,
        payload: Any | None = None,
        run_id: str | None = None,
        parent_run_id: str | None = None,
    ) -> SseEvent:
        """需要用户输入"""
        return cls(
            event=SseEventType.AGENT_INTERRUPT,
            state=SseState.WAITING,
            level=SseLevel.WARNING,
            agent=SseAgent(id=agent_id, name=agent_name),
            span=SseSpan(run_id=run_id, parent_run_id=parent_run_id),
            interrupt=SseInterrupt(payload=payload),
        )

    @classmethod
    def todo_update(cls, *, todo: dict[str, Any]) -> SseEvent:
        """Todo 更新"""
        return cls(
            event=SseEventType.TODO_UPDATE,
            state=SseState.DONE,
            level=SseLevel.INFO,
            todo=todo,
        )

    @classmethod
    def result_event(
        cls,
        *,
        message: str = "完成",
        deliverable: Any | None = None,
        deliverable_type: str | None = None,
        episode_id: str | None = None,
    ) -> SseEvent:
        """任务完成"""
        return cls(
            event=SseEventType.RESULT,
            state=SseState.DONE,
            level=SseLevel.SUCCESS,
            message=SseMessage(role="assistant", content=message),
            result=SseResult(
                deliverable=deliverable,
                deliverable_type=deliverable_type,
                episode_id=episode_id,
            ),
        )

    @classmethod
    def error_event(
        cls,
        *,
        message: str,
        detail: str | None = None,
        agent_id: str | None = None,
        agent_name: str | None = None,
    ) -> SseEvent:
        """错误事件"""
        return cls(
            event=SseEventType.ERROR,
            state=SseState.ERROR,
            level=SseLevel.ERROR,
            agent=SseAgent(id=agent_id, name=agent_name) if agent_id and agent_name else None,
            error=SseError(message=message, detail=detail),
        )

    @classmethod
    def aborted_event(
        cls,
        *,
        message: str = "已停止",
        agent_id: str | None = None,
        agent_name: str | None = None,
    ) -> SseEvent:
        """用户主动打断事件"""
        return cls(
            event=SseEventType.ABORTED,
            state=SseState.ABORTED,
            level=SseLevel.WARNING,
            agent=SseAgent(id=agent_id, name=agent_name) if agent_id and agent_name else None,
            message=SseMessage(role="system", content=message),
        )


def map_execution_status_to_sse(
    status: ExecutionStatus | str | None,
) -> tuple[SseState, SseLevel]:
    """
    执行状态 -> SSE 状态/级别 映射
    - FAILED -> ERROR/ERROR
    - 其他 -> DONE/SUCCESS
    """
    if is_failed(status):
        return (SseState.ERROR, SseLevel.ERROR)
    return (SseState.DONE, SseLevel.SUCCESS)
