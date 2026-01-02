"""
黑板协作请求（Blackboard Requests）

目标：
- 用统一的数据结构表达“人机交互请求”和“委派请求”
- 编排器只做路由：看到 pending_requests 就抢占处理
- 任意 Agent 都可以创建请求并写入 state.pending_requests

说明：
- 这里不引入复杂枚举/状态机，先保证协议清晰、可扩展、可调试
- 所有字段与 payload 约定必须使用简体中文
"""

from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, Field


RequestKind = Literal[
    "human",          # 需要用户输入/确认
    "delegate",       # 委派给另一个 Agent 执行子任务
]


RequestStatus = Literal[
    "pending",
    "completed",
    "canceled",
]


class BlackboardRequest(BaseModel):
    """
    黑板请求（统一协议）

    设计要点：
    - kind=human：payload 直接用于 LangGraph interrupt（包含 type/message/questions/options 等）
    - kind=delegate：target_agent 指定要运行的 Agent，payload 作为子任务输入（由目标 Agent 自行解释）
    - resume_to：请求完成后恢复到哪个节点（通常是发起请求的 Agent 或 blackboard_router）
    """

    request_id: str = Field(..., description="请求ID（全局唯一）")
    kind: RequestKind = Field(..., description="请求类型：human/delegate")
    status: RequestStatus = Field(default="pending", description="请求状态：pending/completed/canceled")

    created_by: str = Field(..., description="发起请求的节点ID（例如 analyst_agent）")
    target_agent: str | None = Field(default=None, description="委派目标节点ID（仅 kind=delegate 使用）")
    resume_to: str | None = Field(default=None, description="请求完成后恢复到的节点ID")

    payload: dict[str, Any] = Field(default_factory=dict, description="请求载荷（human: interrupt payload；delegate: 子任务输入）")
    response: Any | None = Field(default=None, description="请求的响应（human: 用户输入；delegate: 目标Agent产物摘要）")

    model_config = {"extra": "ignore"}

