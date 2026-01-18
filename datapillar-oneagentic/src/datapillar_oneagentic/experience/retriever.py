"""
经验检索器

职责：
1. 检索相似经验
2. 自动拼接成上下文（调用 ExperienceRecord.to_context()）

使用示例：
```python
from datapillar_oneagentic.experience import ExperienceRetriever

retriever = ExperienceRetriever(store=store, embedding_provider=embedding_provider)

# 检索相似经验
records = await retriever.search(goal="分析销售数据", k=5)

# 自动生成上下文（框架内部调用）
context = await retriever.build_context(goal="分析销售数据")
```
"""

from __future__ import annotations

import logging
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from datapillar_oneagentic.experience.learner import ExperienceRecord
    from datapillar_oneagentic.storage.learning_stores.base import ExperienceStore
    from datapillar_oneagentic.providers.llm.embedding import EmbeddingProviderClient

logger = logging.getLogger(__name__)


class ExperienceRetriever:
    """
    经验检索器

    职责：
    1. 从向量库检索相似经验
    2. 自动拼接成可注入 prompt 的上下文
    """

    def __init__(self, store: ExperienceStore, embedding_provider: "EmbeddingProviderClient"):
        """
        初始化检索器

        Args:
            store: 经验存储（ExperienceStore 抽象接口）
            embedding_provider: Embedding 提供者（用于向量化）
        """
        self._store = store
        self._embedding_provider = embedding_provider

    async def search(
        self,
        goal: str,
        k: int = 5,
        outcome: str | None = None,
    ) -> list[ExperienceRecord]:
        """
        检索相似经验

        Args:
            goal: 当前任务目标
            k: 返回数量
            outcome: 结果过滤（success / failure / None=全部）

        Returns:
            经验记录列表
        """
        # 向量化查询文本
        try:
            query_vector = await self._embedding_provider.embed_text(goal)
        except Exception as e:
            logger.warning(f"向量化查询失败: {e}")
            return []

        # 直接调用 store.search，返回 ExperienceRecord 列表
        return await self._store.search(query_vector, k=k, outcome=outcome)

    async def build_context(
        self,
        goal: str,
        k: int = 3,
        prefer_success: bool = True,
    ) -> str:
        """
        构建经验上下文

        框架自动调用此方法，将相似经验拼接成可注入 prompt 的上下文。

        Args:
            goal: 当前任务目标
            k: 参考经验数量
            prefer_success: 是否优先成功案例

        Returns:
            上下文字符串，无经验时返回空字符串
        """
        if prefer_success:
            # 先找成功案例
            records = await self.search(goal, k=k, outcome="success")
            # 不够则补充
            if len(records) < k:
                more = await self.search(goal, k=k - len(records), outcome=None)
                # 去重
                existing_ids = {r.id for r in records}
                for r in more:
                    if r.id not in existing_ids:
                        records.append(r)
        else:
            records = await self.search(goal, k=k)

        if not records:
            return ""

        # 拼接上下文
        lines = [
            "## 相似经验参考",
            "",
        ]

        for i, record in enumerate(records[:k], 1):
            lines.append(f"### 经验 {i}")
            lines.append(record.to_context())
            lines.append("")

        return "\n".join(lines)

    async def get_common_tools(self, goal: str, k: int = 10) -> list[str]:
        """
        获取常用工具

        Args:
            goal: 任务目标
            k: 参考经验数量

        Returns:
            工具列表（按使用频率排序）
        """
        records = await self.search(goal, k=k, outcome="success")

        tool_counts: dict[str, int] = {}
        for record in records:
            for tool in record.tools_used:
                tool_counts[tool] = tool_counts.get(tool, 0) + 1

        sorted_tools = sorted(tool_counts.keys(), key=lambda t: tool_counts[t], reverse=True)
        return sorted_tools
