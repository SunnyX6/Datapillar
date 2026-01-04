"""
上下文构建器

从各个数据源读取信息，按预算分配，组装成 AgentContext 给 Agent 使用。

数据源：
- Blackboard: 任务上下文
- Handover: 产物上下文、知识上下文
- AgentPrivate: 对话上下文
"""

from __future__ import annotations

from typing import TYPE_CHECKING

from pydantic import BaseModel

from src.modules.etl.context.agent_private import AgentPrivate
from src.modules.etl.context.handover import Handover
from src.modules.etl.context.layers import (
    AgentContext,
    ArtifactContext,
    ConversationContext,
    KnowledgeContext,
    TaskContext,
)

if TYPE_CHECKING:
    from src.modules.etl.state.blackboard import Blackboard


class ContextBuildConfig(BaseModel):
    """上下文构建配置"""

    total_tokens: int = 8000
    task_ratio: float = 0.15
    artifact_ratio: float = 0.35
    conversation_ratio: float = 0.30
    knowledge_ratio: float = 0.20
    max_conversation_turns: int = 10


class ContextBuilder:
    """
    上下文构建器

    职责：
    - 从 Blackboard 读取任务
    - 从 Handover 读取产物（压缩后）
    - 从 AgentPrivate 读取对话历史
    - 按预算裁剪，组装成 AgentContext
    """

    def __init__(self, config: ContextBuildConfig | None = None):
        self.config = config or ContextBuildConfig()

    def build(
        self,
        agent_id: str,
        blackboard: Blackboard,
        handover: Handover,
        agent_private: AgentPrivate | None = None,
    ) -> AgentContext:
        """构建 Agent 上下文"""
        task_ctx = self._build_task_context(blackboard)
        artifact_ctx = self._build_artifact_context(agent_id, handover)
        conversation_ctx = self._build_conversation_context(agent_private)
        knowledge_ctx = self._build_knowledge_context(agent_id, handover)

        return AgentContext(
            agent_id=agent_id,
            task=task_ctx,
            artifacts=artifact_ctx,
            conversation=conversation_ctx,
            knowledge=knowledge_ctx,
        )

    def _build_task_context(self, blackboard: Blackboard) -> TaskContext:
        """构建任务上下文"""
        return TaskContext(
            task=blackboard.task or "暂无任务",
            constraints=[],
        )

    def _build_artifact_context(self, agent_id: str, handover: Handover) -> ArtifactContext:
        """构建产物上下文（压缩上游产物）"""
        analysis = handover.get_analysis()
        analysis_summary = None
        if analysis:
            analysis_summary = f"目标: {analysis.summary}\n步骤数: {len(analysis.steps)}"

        # 根据 agent_id 决定需要哪些上游产物
        # Analyst 不需要上游产物
        # Architect 需要 analysis
        # Developer 需要 analysis + plan
        # Tester 需要 analysis + plan

        return ArtifactContext(
            analysis_summary=analysis_summary if agent_id != "analyst" else None,
            plan_summary=None,  # 从 Blackboard.deliverable 或 Handover 获取
            test_summary=None,
        )

    def _build_conversation_context(
        self,
        agent_private: AgentPrivate | None,
    ) -> ConversationContext:
        """构建对话上下文"""
        if not agent_private:
            return ConversationContext()

        turns = agent_private.get_session_turns()
        recent_turns = turns[-self.config.max_conversation_turns :]

        return ConversationContext(
            turns=[{"role": t.role, "content": t.content} for t in recent_turns]
        )

    def _build_knowledge_context(self, agent_id: str, handover: Handover) -> KnowledgeContext:
        """构建知识上下文"""
        knowledge = handover.get_knowledge(agent_id)
        if not knowledge:
            return KnowledgeContext()

        tables = []
        for pointer in knowledge.table_index.values():
            tables.append(
                {
                    "name": pointer.qualified_name,
                    "description": pointer.description or "",
                }
            )

        return KnowledgeContext(
            tables=tables,
            tools=list(knowledge.tools or []),
        )
