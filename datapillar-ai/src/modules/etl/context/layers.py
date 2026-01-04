"""
上下文分层定义

4层上下文，按优先级从高到低：
1. TaskContext: 任务上下文（必须保留）
2. ArtifactContext: 产物上下文（可压缩）
3. ConversationContext: 对话上下文（可裁剪）
4. KnowledgeContext: 知识上下文（可aggressive裁剪）

ContextBuilder 按预算分配，组装各层上下文给 Agent 使用。
"""

from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field


class TaskContext(BaseModel):
    """
    Layer 1: 任务上下文

    优先级：最高（必须保留，不可裁剪）
    来源：Blackboard.task
    内容：当前任务描述、全局约束
    """

    task: str = Field(..., description="当前任务描述")
    constraints: list[str] = Field(default_factory=list, description="全局约束")

    def to_prompt(self) -> str:
        """转换为 prompt 文本"""
        lines = [f"## 任务\n{self.task}"]
        if self.constraints:
            lines.append("\n## 约束")
            for c in self.constraints:
                lines.append(f"- {c}")
        return "\n".join(lines)


class ArtifactContext(BaseModel):
    """
    Layer 2: 产物上下文

    优先级：高（可压缩，保留摘要）
    来源：Handover
    内容：上游 Agent 的产物摘要
    """

    analysis_summary: str | None = Field(None, description="需求分析摘要")
    plan_summary: str | None = Field(None, description="架构方案摘要")
    test_summary: str | None = Field(None, description="测试结果摘要")

    def to_prompt(self) -> str:
        """转换为 prompt 文本"""
        lines = []
        if self.analysis_summary:
            lines.append(f"## 需求分析\n{self.analysis_summary}")
        if self.plan_summary:
            lines.append(f"## 架构方案\n{self.plan_summary}")
        if self.test_summary:
            lines.append(f"## 测试结果\n{self.test_summary}")
        return "\n\n".join(lines) if lines else ""


class ConversationContext(BaseModel):
    """
    Layer 3: 对话上下文

    优先级：中（可按轮次裁剪）
    来源：AgentPrivate
    内容：用户和这个 Agent 的对话历史
    """

    turns: list[dict[str, str]] = Field(
        default_factory=list,
        description="对话轮次，每轮包含 role 和 content",
    )

    def to_prompt(self) -> str:
        """转换为 prompt 文本"""
        if not self.turns:
            return ""
        lines = ["## 对话历史"]
        for turn in self.turns:
            role = "用户" if turn.get("role") == "user" else "助手"
            lines.append(f"**{role}**: {turn.get('content', '')}")
        return "\n".join(lines)


class KnowledgeContext(BaseModel):
    """
    Layer 4: 知识上下文

    优先级：低（可 aggressive 裁剪）
    来源：Handover (knowledge)
    内容：工具调用结果、元数据指针
    """

    tables: list[dict[str, Any]] = Field(
        default_factory=list,
        description="可用的表信息",
    )
    tools: list[str] = Field(
        default_factory=list,
        description="可用的工具列表",
    )
    tool_results: list[dict[str, Any]] = Field(
        default_factory=list,
        description="工具调用结果",
    )

    def to_prompt(self) -> str:
        """转换为 prompt 文本"""
        lines = []
        if self.tables:
            lines.append("## 可用表")
            for t in self.tables:
                lines.append(f"- {t.get('name', '')}: {t.get('description', '')}")
        if self.tools:
            lines.append("\n## 可用工具")
            for tool in self.tools:
                lines.append(f"- {tool}")
        return "\n".join(lines) if lines else ""


class AgentContext(BaseModel):
    """
    Agent 执行时的完整上下文

    由 ContextBuilder 组装各层上下文而成。
    """

    agent_id: str = Field(..., description="Agent ID")
    task: TaskContext = Field(..., description="任务上下文")
    artifacts: ArtifactContext = Field(default_factory=ArtifactContext, description="产物上下文")
    conversation: ConversationContext = Field(
        default_factory=ConversationContext, description="对话上下文"
    )
    knowledge: KnowledgeContext = Field(default_factory=KnowledgeContext, description="知识上下文")

    def to_prompt(self) -> str:
        """转换为完整的 prompt 文本"""
        sections = [
            self.task.to_prompt(),
            self.artifacts.to_prompt(),
            self.conversation.to_prompt(),
            self.knowledge.to_prompt(),
        ]
        return "\n\n".join(s for s in sections if s)

    def estimate_tokens(self) -> int:
        """估算 token 数量（粗略估计：4字符=1token）"""
        return len(self.to_prompt()) // 4
