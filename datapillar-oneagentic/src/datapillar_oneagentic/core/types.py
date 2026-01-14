"""
核心类型定义

对外暴露：
- SessionKey: 会话标识（namespace + session_id）

框架内部：
- AgentResult: Agent 执行结果
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Literal, Self

from langchain_core.messages import BaseMessage
from pydantic import BaseModel, Field


@dataclass(frozen=True, slots=True)
class SessionKey:
    """
    会话标识（不可变值对象）

    统一 namespace + session_id 的组合，确保全系统一致性。
    所有子系统（Checkpoint、Timeline、SSE、Store）必须使用此类型作为 key。

    使用示例：
    ```python
    key = SessionKey(namespace="etl_team", session_id="abc123")

    # 作为存储 key
    buffer[str(key)]  # "etl_team:abc123"

    # 解析
    key = SessionKey.parse("etl_team:abc123")
    key.namespace  # "etl_team"
    key.session_id  # "abc123"
    ```
    """

    namespace: str
    session_id: str

    def __post_init__(self) -> None:
        if not self.namespace or not self.session_id:
            raise ValueError("namespace 和 session_id 不能为空")

    def __str__(self) -> str:
        """用于存储 key（Redis/Dict/Checkpoint）"""
        return f"{self.namespace}:{self.session_id}"

    @classmethod
    def parse(cls, key: str) -> Self:
        """从字符串解析"""
        if ":" not in key:
            raise ValueError(f"无效的 SessionKey 格式: {key}")
        namespace, session_id = key.split(":", 1)
        return cls(namespace=namespace, session_id=session_id)


# ==================== 框架内部类型 ====================

AgentResultStatus = Literal["completed", "failed", "error"]


class AgentResult(BaseModel):
    """
    Agent 执行结果（框架内部使用）

    业务侧不需要直接构建此类型，框架会自动处理。

    状态语义：
    - completed: Agent 正确完成任务
    - failed: 业务失败（逻辑错误，非技术故障）
    - error: 系统异常（技术故障）
    """

    model_config = {"arbitrary_types_allowed": True}

    status: AgentResultStatus = Field(..., description="执行状态")
    deliverable: Any | None = Field(None, description="交付物")
    deliverable_type: str | None = Field(None, description="交付物类型")
    error: str | None = Field(None, description="错误信息")
    messages: list[BaseMessage] = Field(default_factory=list, description="Agent 执行过程中的消息")

    @classmethod
    def completed(
        cls,
        deliverable: Any,
        deliverable_type: str,
        messages: list[BaseMessage] | None = None,
    ) -> AgentResult:
        """创建成功结果"""
        return cls(
            status="completed",
            deliverable=deliverable,
            deliverable_type=deliverable_type,
            messages=messages or [],
        )

    @classmethod
    def failed(
        cls,
        error: str,
        messages: list[BaseMessage] | None = None,
    ) -> AgentResult:
        """创建业务失败结果"""
        return cls(status="failed", error=error, messages=messages or [])

    @classmethod
    def system_error(
        cls,
        error: str,
        messages: list[BaseMessage] | None = None,
    ) -> AgentResult:
        """创建系统异常结果"""
        return cls(status="error", error=error, messages=messages or [])
