"""
Blackboard - Boss（老板）的工作台

这是多智能体系统的全局共享状态，遵循黑板架构模式。

设计原则：
- 只放老板关心的信息：任务、员工汇报、协作请求、最终交付物
- 不放员工内部逻辑：对话历史、压缩策略、中间计算
- 员工通过汇报中的 deliverable_ref 引用交接物，老板需要时去 Handover 查看
- SessionMemory（短期记忆）通过这里持久化（~3-4KB，很小）

老板（Boss）的职责：
- 理解客户（用户）需求，分配任务
- 查看员工汇报，协调下一步
- 追踪交接物引用，出错时能定位问题
- 收集最终交付物，交给客户
"""

from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, Field

from src.modules.etl.memory.session_memory import SessionMemory
from src.modules.etl.schemas.requests import BlackboardRequest
from src.modules.etl.schemas.workflow import Workflow

ReportStatus = Literal[
    "completed",
    "in_progress",
    "blocked",
    "failed",
    "needs_clarification",
    "needs_delegation",
    "waiting",
]


class AgentReport(BaseModel):
    """
    员工向老板汇报

    老板通过汇报了解：
    - 员工当前状态
    - 工作摘要
    - 交接物引用（老板需要时可去 SessionStore 查看具体内容）
    - 遇到的问题
    - 建议的下一步
    """

    status: ReportStatus = Field(
        ...,
        description=(
            "状态：completed/in_progress/blocked/failed/"
            "needs_clarification/needs_delegation/waiting"
        ),
    )
    summary: str = Field(..., description="一句话总结（如：需求分析完成，识别出3个业务步骤）")
    deliverable_ref: str | None = Field(None, description="交接物引用（如：analysis:abc123）")
    blocked_reason: str | None = Field(None, description="如果 blocked，原因是什么")
    next_suggestion: str | None = Field(None, description="建议下一步（如：可以交给架构师了）")
    updated_at_ms: int | None = Field(None, description="汇报时间戳")


class Blackboard(BaseModel):
    """
    Blackboard - Boss（老板）的工作台

    这是多智能体系统的全局共享状态，只放老板关心的信息。

    字段分类：
    - 会话标识：session_id, user_id
    - 任务：task（老板理解后的问题描述）
    - 员工汇报：reports（带交接物引用）
    - 协作请求：pending_requests（委派、人机交互）
    - 最终交付物：deliverable（给客户的答案）
    - 执行控制：current_agent, is_completed, error
    """

    # ==================== 会话标识 ====================
    session_id: str = Field(default="", description="会话ID")
    user_id: str = Field(default="", description="用户ID")

    # ==================== 任务 ====================
    task: str | None = Field(None, description="当前任务描述（Boss 理解后的问题）")

    # ==================== 员工汇报 ====================
    reports: dict[str, AgentReport] = Field(
        default_factory=dict,
        description="员工汇报，key 为完整 agent_id（如 analyst_agent, architect_agent）",
    )

    # ==================== 协作请求 ====================
    pending_requests: list[BlackboardRequest] = Field(
        default_factory=list,
        description="待处理的协作请求（委派、人机交互）",
    )
    request_results: dict[str, Any] = Field(
        default_factory=dict,
        description="已完成的请求结果（按 request_id）",
    )

    # ==================== 最终交付物 ====================
    deliverable: Workflow | None = Field(None, description="最终交付物（给客户的答案）")

    # ==================== 执行控制 ====================
    current_agent: str | None = Field(None, description="当前执行的员工")
    review_retry_threshold: int = Field(
        default=3, description="Review 打回阈值（连续失败几次后暂停请求用户介入）"
    )
    design_review_iteration_count: int = Field(
        default=0, description="设计阶段 review 迭代计数（独立计数）"
    )
    development_review_iteration_count: int = Field(
        default=0, description="开发阶段 review 迭代计数（独立计数）"
    )
    design_review_passed: bool = Field(default=False, description="设计阶段 review 是否通过")
    development_review_passed: bool = Field(default=False, description="开发阶段 review 是否通过")
    is_completed: bool = Field(default=False, description="是否完成")
    error: str | None = Field(None, description="错误信息")

    # ==================== 短期记忆（通过 Checkpointer 持久化） ====================
    memory: SessionMemory | None = Field(
        default=None,
        description="短期记忆（需求TODO、产物状态、对话摘要），~3-4KB",
    )

    model_config = {"arbitrary_types_allowed": True}

    # ==================== 辅助方法 ====================

    def get_report(self, agent_id: str) -> AgentReport | None:
        """获取指定员工的汇报"""
        return self.reports.get(agent_id)

    def is_agent_completed(self, agent_id: str) -> bool:
        """检查指定员工是否已完成"""
        report = self.get_report(agent_id)
        return report is not None and report.status == "completed"

    def is_agent_blocked(self, agent_id: str) -> bool:
        """检查指定员工是否被阻塞"""
        report = self.get_report(agent_id)
        return report is not None and report.status == "blocked"

    def get_deliverable_ref(self, agent_id: str) -> str | None:
        """获取指定员工的交接物引用"""
        report = self.get_report(agent_id)
        return report.deliverable_ref if report else None

    def has_human_request(self) -> bool:
        """是否有待处理的人机交互请求"""
        return any(req.kind == "human" and req.status == "pending" for req in self.pending_requests)

    def has_delegate_request(self) -> bool:
        """是否有待处理的委派请求"""
        return any(
            req.kind == "delegate" and req.status == "pending" for req in self.pending_requests
        )

    # ==================== 短期记忆操作 ====================

    def ensure_memory(self) -> SessionMemory:
        """确保短期记忆已初始化"""
        if self.memory is None:
            self.memory = SessionMemory(session_id=self.session_id)
        return self.memory

    def update_agent_status(
        self,
        agent_id: str,
        status: str,
        deliverable_type: str | None = None,
        summary: str = "",
    ) -> None:
        """更新短期记忆中的 Agent 状态"""
        memory = self.ensure_memory()
        memory.update_agent_status(agent_id, status, deliverable_type, summary)

    def add_agent_turn(self, agent_id: str, role: str, content: str) -> None:
        """添加一轮对话到指定 Agent 的记忆"""
        memory = self.ensure_memory()
        memory.add_agent_turn(agent_id, role, content)

    def get_agent_context(self, agent_id: str) -> dict:
        """获取指定 Agent 的记忆上下文（用于注入 prompt）"""
        memory = self.ensure_memory()
        return memory.get_agent_context(agent_id)

    def apply_agent_compression(self, agent_id: str, summary: str) -> None:
        """应用压缩结果到指定 Agent"""
        memory = self.ensure_memory()
        memory.apply_agent_compression(agent_id, summary)
