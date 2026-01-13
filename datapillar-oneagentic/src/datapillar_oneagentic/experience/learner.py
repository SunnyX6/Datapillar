"""
经验学习器

职责：
1. 自动记录执行过程
2. 使用者调用 save_experience 保存（包含 feedback）
3. 框架自动处理，不需要策略

使用示例：
```python
from datapillar_oneagentic import Datapillar

team = Datapillar(
    agents=[...],
    enable_learning=True,
)

# 执行任务（框架自动记录）
async for event in team.stream(query="分析销售数据", session_id="s001"):
    ...

# 保存经验（包含用户反馈）
await team.save_experience(
    session_id="s001",
    feedback={"stars": 5, "comment": "很好用"},
)
```
"""

from __future__ import annotations

import logging
import time
from dataclasses import dataclass, field
from typing import Any, TYPE_CHECKING

if TYPE_CHECKING:
    from datapillar_oneagentic.storage.learning_stores.base import ExperienceStore

logger = logging.getLogger(__name__)


def _now_ms() -> int:
    return int(time.time() * 1000)


@dataclass
class ExperienceRecord:
    """
    经验记录

    这是存入向量库的数据结构，所有字段都是独立列。
    vector 字段存储 embedding 向量，用于相似度搜索。
    """

    id: str
    """记录 ID（通常用 session_id）"""

    namespace: str
    """命名空间（用于隔离不同团队的经验）"""

    session_id: str
    """会话 ID"""

    goal: str
    """用户目标"""

    outcome: str = "pending"
    """执行结果: pending / success / failure / partial"""

    result_summary: str = ""
    """结果摘要"""

    tools_used: list[str] = field(default_factory=list)
    """使用的工具"""

    agents_involved: list[str] = field(default_factory=list)
    """参与的 Agent"""

    duration_ms: int = 0
    """执行时长（毫秒）"""

    feedback: dict[str, Any] = field(default_factory=dict)
    """用户反馈（结构由使用者定义）"""

    created_at: int = field(default_factory=_now_ms)
    """创建时间"""

    vector: list[float] = field(default_factory=list)
    """embedding 向量（用于相似度搜索）"""

    def to_embed_text(self) -> str:
        """
        生成用于向量化的完整文本

        包含所有关键信息，让相似度搜索能匹配到完整的经验语义。
        """
        parts = [f"目标: {self.goal}"]

        if self.outcome and self.outcome != "pending":
            parts.append(f"结果: {self.outcome}")

        if self.result_summary:
            parts.append(f"摘要: {self.result_summary}")

        if self.tools_used:
            parts.append(f"使用工具: {', '.join(self.tools_used)}")

        if self.agents_involved:
            parts.append(f"参与Agent: {', '.join(self.agents_involved)}")

        return "\n".join(parts)

    def to_context(self) -> str:
        """
        生成可注入 prompt 的上下文

        框架自动调用此方法，将经验拼接成上下文。
        """
        lines = [
            f"- 目标: {self.goal}",
            f"- 结果: {self.outcome}",
        ]

        if self.result_summary:
            lines.append(f"- 摘要: {self.result_summary[:100]}")

        if self.tools_used:
            lines.append(f"- 工具: {', '.join(self.tools_used)}")

        if self.agents_involved:
            lines.append(f"- Agent: {', '.join(self.agents_involved)}")

        if self.feedback:
            feedback_str = ", ".join(f"{k}={v}" for k, v in self.feedback.items())
            lines.append(f"- 反馈: {feedback_str}")

        return "\n".join(lines)

    def to_dict(self) -> dict[str, Any]:
        """序列化为字典（用于存储）"""
        return {
            "id": self.id,
            "namespace": self.namespace,
            "session_id": self.session_id,
            "goal": self.goal,
            "outcome": self.outcome,
            "result_summary": self.result_summary,
            "tools_used": self.tools_used,
            "agents_involved": self.agents_involved,
            "duration_ms": self.duration_ms,
            "feedback": self.feedback,
            "created_at": self.created_at,
            "vector": self.vector,
        }

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "ExperienceRecord":
        """从字典反序列化"""
        return cls(
            id=data.get("id", ""),
            namespace=data.get("namespace", ""),
            session_id=data.get("session_id", ""),
            goal=data.get("goal", ""),
            outcome=data.get("outcome", "pending"),
            result_summary=data.get("result_summary", ""),
            tools_used=data.get("tools_used", []),
            agents_involved=data.get("agents_involved", []),
            duration_ms=data.get("duration_ms", 0),
            feedback=data.get("feedback", {}),
            created_at=data.get("created_at", 0),
            vector=data.get("vector", []),
        )


class ExperienceLearner:
    """
    经验学习器

    职责：
    1. 自动记录执行过程（内存临时存储）
    2. 使用者调用 save_experience 保存到向量库
    """

    def __init__(
        self,
        store: "ExperienceStore",
        namespace: str,
    ):
        """
        初始化学习器

        Args:
            store: 经验存储（ExperienceStore 抽象接口）
            namespace: 命名空间（用于隔离不同团队的经验）
        """
        self._store = store
        self._namespace = namespace
        self._pending: dict[str, ExperienceRecord] = {}  # 临时记录

    # ==================== 框架内部调用 ====================

    def start_recording(self, session_id: str, goal: str) -> None:
        """开始记录（框架内部调用）"""
        self._pending[session_id] = ExperienceRecord(
            id=session_id,
            namespace=self._namespace,
            session_id=session_id,
            goal=goal,
        )

    def record_tool(self, session_id: str, tool_name: str) -> None:
        """记录工具使用（框架内部调用）"""
        record = self._pending.get(session_id)
        if record and tool_name not in record.tools_used:
            record.tools_used.append(tool_name)

    def record_agent(self, session_id: str, agent_id: str) -> None:
        """记录 Agent 参与（框架内部调用）"""
        record = self._pending.get(session_id)
        if record and agent_id not in record.agents_involved:
            record.agents_involved.append(agent_id)

    def complete_recording(
        self,
        session_id: str,
        outcome: str,
        result_summary: str = "",
    ) -> None:
        """完成记录（框架内部调用）"""
        record = self._pending.get(session_id)
        if not record:
            return

        record.outcome = outcome
        record.result_summary = result_summary
        record.duration_ms = _now_ms() - record.created_at

    # ==================== 使用者调用 ====================

    async def save_experience(
        self,
        session_id: str,
        feedback: dict[str, Any] | None = None,
    ) -> bool:
        """
        保存经验到向量库

        使用者调用此方法保存经验，feedback 作为记录的一部分存储。

        Args:
            session_id: 会话 ID
            feedback: 用户反馈（可选）

        Returns:
            是否保存成功
        """
        record = self._pending.pop(session_id, None)
        if not record:
            logger.warning(f"未找到待保存的记录: {session_id}")
            return False

        # 设置 feedback
        if feedback:
            record.feedback = feedback

        # 生成 embedding（向量化完整经验信息）
        from datapillar_oneagentic.providers.llm.embedding import embed_text

        embed_content = record.to_embed_text()
        try:
            record.vector = await embed_text(embed_content)
        except Exception as e:
            logger.error(f"生成 embedding 失败，无法保存经验: {e}")
            self._pending[session_id] = record
            return False

        # 直接保存 ExperienceRecord
        await self._store.add(record)
        logger.info(f"经验已保存: {session_id}")
        return True

    def has_pending(self, session_id: str) -> bool:
        """是否有待保存的记录"""
        return session_id in self._pending

    def discard(self, session_id: str) -> None:
        """丢弃临时记录"""
        if session_id in self._pending:
            del self._pending[session_id]
