"""
Handover - 员工交接物存储（运行时）

存储员工之间的交接物（AnalysisResult、Workflow、SQL 等）。

设计原则：
- 只存运行时交接物，用完即弃
- 不持久化（会话结束即清）
- SQL 原文、Workflow JSON 在这里传递，不存入记忆

交接物引用格式：
- analysis:{uuid}  -> AnalysisResult
- workflow:{uuid}  -> Workflow
- review:{uuid}    -> ReviewResult
"""

from __future__ import annotations

import uuid
from typing import Any

from pydantic import BaseModel, Field

from src.modules.etl.schemas.analyst import AnalysisResult
from src.modules.etl.schemas.review import ReviewResult


class DeliverableRef(BaseModel):
    """交接物引用"""

    ref: str = Field(..., description="引用ID（如 analysis:abc123）")
    type: str = Field(..., description="类型（analysis/test/knowledge）")
    producer: str = Field(..., description="生产者（agent_id）")
    created_at_ms: int = Field(..., description="创建时间戳")

    @classmethod
    def create(cls, dtype: str, producer: str, created_at_ms: int) -> DeliverableRef:
        """创建新的引用"""
        ref_id = f"{dtype}:{uuid.uuid4().hex[:8]}"
        return cls(ref=ref_id, type=dtype, producer=producer, created_at_ms=created_at_ms)


class Handover(BaseModel):
    """
    Handover - 员工交接物存储（运行时）

    存储员工之间的工作成果，用完即弃。
    不持久化，会话结束即清。
    """

    session_id: str = Field(default="", description="会话ID")

    # ==================== 交接物存储（运行时） ====================
    deliverables: dict[str, Any] = Field(
        default_factory=dict,
        description="交接物存储，key 为 ref（如 analysis:abc123）",
    )

    # ==================== 快捷访问 ====================
    latest_analysis_ref: str | None = Field(None, description="最新的需求分析结果引用")
    latest_workflow_ref: str | None = Field(None, description="最新的工作流引用")
    latest_review_ref: str | None = Field(None, description="最新的 review 结果引用")
    latest_knowledge_refs: dict[str, str] = Field(
        default_factory=dict,
        description="各 Agent 最新的知识上下文引用",
    )

    model_config = {"arbitrary_types_allowed": True}

    # ==================== 存储操作 ====================

    def store(self, ref: DeliverableRef, content: Any) -> str:
        """存储交接物，返回引用ID"""
        self.deliverables[ref.ref] = content

        if ref.type == "analysis":
            self.latest_analysis_ref = ref.ref
        elif ref.type == "workflow" or ref.type == "plan":
            self.latest_workflow_ref = ref.ref
        elif ref.type == "test" or ref.type == "review":
            self.latest_review_ref = ref.ref
        elif ref.type == "knowledge" or ref.type == "context":
            self.latest_knowledge_refs[ref.producer] = ref.ref

        return ref.ref

    def get(self, ref: str) -> Any | None:
        """根据引用获取交接物"""
        return self.deliverables.get(ref)

    def get_analysis(self) -> AnalysisResult | None:
        """获取最新的需求分析结果"""
        if not self.latest_analysis_ref:
            return None
        content = self.deliverables.get(self.latest_analysis_ref)
        if isinstance(content, AnalysisResult):
            return content
        if isinstance(content, dict):
            return AnalysisResult(**content)
        return None

    def get_workflow(self) -> Any | None:
        """获取最新的工作流"""
        if not self.latest_workflow_ref:
            return None
        return self.deliverables.get(self.latest_workflow_ref)

    def get_review_result(self) -> ReviewResult | None:
        """获取最新的 review 结果"""
        if not self.latest_review_ref:
            return None
        content = self.deliverables.get(self.latest_review_ref)
        if isinstance(content, ReviewResult):
            return content
        if isinstance(content, dict):
            return ReviewResult(**content)
        return None

    # ==================== 简化接口（供 Orchestrator 使用） ====================

    def store_deliverable(self, dtype: str, content: Any) -> str:
        """存储交付物（简化接口）"""
        import time

        ref = DeliverableRef.create(dtype, "orchestrator", int(time.time() * 1000))
        return self.store(ref, content)

    def get_deliverable(self, dtype: str) -> Any | None:
        """获取最新的交付物（按类型）"""
        if dtype == "analysis":
            return self.get_analysis()
        if dtype == "review":
            return self.get_review_result()
        if dtype == "plan" or dtype == "workflow":
            return self.get_workflow()
        # 通用查找
        for key in reversed(list(self.deliverables.keys())):
            if key.startswith(f"{dtype}:"):
                return self.deliverables.get(key)
        return None

    def store_context(self, agent_type: str, context: Any) -> None:
        """存储 Agent 上下文"""
        import time

        ref = DeliverableRef.create("context", agent_type, int(time.time() * 1000))
        self.deliverables[ref.ref] = context
        self.latest_knowledge_refs[agent_type] = ref.ref

    def get_context(self, agent_type: str) -> Any | None:
        """获取 Agent 上下文"""
        ref = self.latest_knowledge_refs.get(agent_type)
        if ref:
            return self.deliverables.get(ref)
        return None

    def clear(self) -> None:
        """清空所有交接物"""
        self.deliverables.clear()
        self.latest_analysis_ref = None
        self.latest_workflow_ref = None
        self.latest_review_ref = None
        self.latest_knowledge_refs.clear()
