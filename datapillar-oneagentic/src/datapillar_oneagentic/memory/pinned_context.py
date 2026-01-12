"""
固定上下文 - 不参与压缩的结构化信息

类似 Claude Code 的 CLAUDE.md + TODO 机制。
这些信息在整个会话中保持完整，不会被压缩丢失。

包含：
- decisions: 关键决策（用户确认的重要决定）
- constraints: 用户约束（明确提出的限制条件）
- todos: 工作清单（AgentTodoList）
- artifacts: 工件引用（重要的中间产物）
"""

from __future__ import annotations

import time
from typing import TYPE_CHECKING

from pydantic import BaseModel, Field

if TYPE_CHECKING:
    from datapillar_oneagentic.todo.todo_list import AgentTodoList


def _now_ms() -> int:
    return int(time.time() * 1000)


class Decision(BaseModel):
    """关键决策"""

    content: str = Field(..., description="决策内容")
    agent_id: str = Field(..., description="做出决策的 Agent")
    timestamp_ms: int = Field(default_factory=_now_ms)

    def to_display(self) -> str:
        return f"[{self.agent_id}] {self.content}"


class ArtifactRef(BaseModel):
    """工件引用"""

    ref_id: str = Field(..., description="引用 ID")
    dtype: str = Field(..., description="类型（analysis/plan/workflow 等）")
    summary: str = Field(..., description="摘要描述")
    timestamp_ms: int = Field(default_factory=_now_ms)

    def to_display(self) -> str:
        return f"[{self.dtype}] {self.summary} (ref: {self.ref_id})"


class PinnedContext(BaseModel):
    """
    固定上下文 - 不参与压缩的结构化信息

    这些信息在会话中保持完整，不会被压缩。
    """

    # 关键决策
    decisions: list[Decision] = Field(default_factory=list)

    # 用户约束
    constraints: list[str] = Field(default_factory=list)

    # 工作清单（引用，由外部管理）
    todos_data: dict | None = Field(default=None, description="AgentTodoList 序列化数据")

    # 工件引用
    artifacts: list[ArtifactRef] = Field(default_factory=list)

    # 更新时间
    updated_at_ms: int = Field(default_factory=_now_ms)

    def pin_decision(self, content: str, agent_id: str) -> Decision:
        """固定一个关键决策"""
        decision = Decision(content=content[:500], agent_id=agent_id)
        self.decisions.append(decision)
        self.updated_at_ms = _now_ms()
        return decision

    def pin_constraint(self, constraint: str) -> None:
        """固定用户约束"""
        if constraint and constraint not in self.constraints:
            self.constraints.append(constraint[:300])
            self.updated_at_ms = _now_ms()

    def pin_artifact(self, ref_id: str, dtype: str, summary: str) -> ArtifactRef:
        """固定工件引用"""
        artifact = ArtifactRef(ref_id=ref_id, dtype=dtype, summary=summary[:200])
        self.artifacts.append(artifact)
        self.updated_at_ms = _now_ms()
        return artifact

    def set_todos(self, todos: AgentTodoList) -> None:
        """设置工作清单"""
        self.todos_data = todos.model_dump(mode="json")
        self.updated_at_ms = _now_ms()

    def get_todos(self) -> AgentTodoList | None:
        """获取工作清单"""
        if not self.todos_data:
            return None
        from datapillar_oneagentic.todo.todo_list import AgentTodoList

        return AgentTodoList.model_validate(self.todos_data)

    def clear_decisions(self) -> int:
        """清空决策，返回清除的数量"""
        count = len(self.decisions)
        self.decisions.clear()
        self.updated_at_ms = _now_ms()
        return count

    def clear_constraints(self) -> int:
        """清空约束，返回清除的数量"""
        count = len(self.constraints)
        self.constraints.clear()
        self.updated_at_ms = _now_ms()
        return count

    def is_empty(self) -> bool:
        """判断是否为空"""
        return (
            not self.decisions
            and not self.constraints
            and not self.todos_data
            and not self.artifacts
        )

    def to_prompt(self) -> str:
        """转换为 prompt 文本"""
        if self.is_empty():
            return ""

        lines = ["## 固定上下文"]

        # 关键决策
        if self.decisions:
            lines.append("")
            lines.append("### 关键决策")
            for d in self.decisions[-10:]:
                lines.append(f"- {d.to_display()}")

        # 用户约束
        if self.constraints:
            lines.append("")
            lines.append("### 用户约束")
            for c in self.constraints[-5:]:
                lines.append(f"- {c}")

        # 工作清单
        todos = self.get_todos()
        if todos:
            todos_prompt = todos.to_prompt()
            if todos_prompt:
                lines.append("")
                lines.append("### 当前工作")
                lines.append(todos_prompt)

        # 工件引用
        if self.artifacts:
            lines.append("")
            lines.append("### 已有产物")
            for a in self.artifacts[-5:]:
                lines.append(f"- {a.to_display()}")

        return "\n".join(lines)
