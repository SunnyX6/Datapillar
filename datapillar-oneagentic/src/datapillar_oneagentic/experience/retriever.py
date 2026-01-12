"""
经验检索器

提供高级检索功能：
- 相似任务检索
- 成功经验优先
- 失败教训检索
- 综合建议生成

使用示例：
```python
from datapillar_oneagentic.experience import ExperienceRetriever

retriever = ExperienceRetriever(store=store)

# 获取相似经验
experiences = await retriever.get_similar_experiences(
    goal="分析销售数据",
    k=5,
    prefer_success=True,
)

# 获取执行建议
advice = await retriever.get_advice_for_task(goal="分析销售数据")
```
"""

from __future__ import annotations

import logging
from dataclasses import dataclass
from typing import Any

from datapillar_oneagentic.experience.episode import Episode, Outcome
from datapillar_oneagentic.storage.learning_stores import VectorStore, VectorSearchResult

logger = logging.getLogger(__name__)


@dataclass
class TaskAdvice:
    """任务建议"""

    goal: str
    """任务目标"""

    similar_experiences: list[Episode]
    """相似经验"""

    recommended_agents: list[str]
    """推荐使用的 Agent"""

    common_tools: list[str]
    """常用工具"""

    success_tips: list[str]
    """成功经验"""

    pitfalls_to_avoid: list[str]
    """需要避免的坑"""

    estimated_success_rate: float
    """预估成功率"""

    def to_prompt(self) -> str:
        """生成可注入 prompt 的格式"""
        lines = [
            "## 基于历史经验的建议",
            "",
            f"### 预估成功率: {self.estimated_success_rate:.0%}",
            "",
        ]

        if self.recommended_agents:
            lines.append(f"### 推荐 Agent: {', '.join(self.recommended_agents)}")
            lines.append("")

        if self.success_tips:
            lines.append("### 成功经验")
            for tip in self.success_tips[:5]:
                lines.append(f"- {tip}")
            lines.append("")

        if self.pitfalls_to_avoid:
            lines.append("### 需要注意")
            for pitfall in self.pitfalls_to_avoid[:5]:
                lines.append(f"- {pitfall}")
            lines.append("")

        if self.similar_experiences:
            lines.append("### 相似案例")
            for exp in self.similar_experiences[:3]:
                status = "成功" if exp.outcome == Outcome.SUCCESS else "失败"
                lines.append(f"- [{status}] {exp.goal[:50]}...")
            lines.append("")

        return "\n".join(lines)


class ExperienceRetriever:
    """
    经验检索器

    提供高级检索和建议功能。
    """

    def __init__(
        self,
        store: VectorStore,
        embedding_func: Any | None = None,
    ):
        """
        初始化检索器

        Args:
            store: 向量存储
            embedding_func: 向量化函数（可选）
        """
        self._store = store
        self._embedding_func = embedding_func

    def _result_to_episode(self, result: VectorSearchResult) -> Episode:
        """将 VectorSearchResult 转换为 Episode"""
        return Episode.model_validate_json(result.metadata.get("data", "{}"))

    async def get_similar_experiences(
        self,
        goal: str,
        k: int = 5,
        prefer_success: bool = True,
        task_type: str | None = None,
    ) -> list[Episode]:
        """
        获取相似经验

        Args:
            goal: 任务目标
            k: 返回数量
            prefer_success: 是否优先返回成功案例
            task_type: 任务类型过滤

        Returns:
            相似经验列表
        """
        if prefer_success:
            # 先搜索成功案例
            filter_dict: dict[str, Any] = {"outcome": "success"}
            if task_type:
                filter_dict["task_type"] = task_type
            results = await self._store.search_by_text(goal, k=k, filter=filter_dict)

            # 如果不够，补充失败案例
            if len(results) < k:
                remaining = k - len(results)
                filter_fail: dict[str, Any] = {"outcome": "failure"}
                if task_type:
                    filter_fail["task_type"] = task_type
                fail_results = await self._store.search_by_text(goal, k=remaining, filter=filter_fail)
                results.extend(fail_results)
        else:
            filter_dict = {"task_type": task_type} if task_type else None
            results = await self._store.search_by_text(goal, k=k, filter=filter_dict)

        return [self._result_to_episode(r) for r in results]

    async def get_success_patterns(
        self,
        goal: str,
        k: int = 10,
    ) -> dict[str, Any]:
        """
        获取成功模式

        分析相似成功案例，提取共同模式。

        Args:
            goal: 任务目标
            k: 分析的案例数量

        Returns:
            {
                "common_agents": [...],
                "common_tools": [...],
                "success_factors": [...],
                "avg_duration_ms": ...,
            }
        """
        filter_dict = {"outcome": "success"}
        results = await self._store.search_by_text(goal, k=k, filter=filter_dict)

        if not results:
            return {
                "common_agents": [],
                "common_tools": [],
                "success_factors": [],
                "avg_duration_ms": None,
            }

        # 统计 Agent 使用频率
        agent_counts: dict[str, int] = {}
        tool_counts: dict[str, int] = {}
        factor_counts: dict[str, int] = {}
        total_duration = 0
        duration_count = 0

        for result in results:
            ep = self._result_to_episode(result)

            for agent in ep.agents_involved:
                agent_counts[agent] = agent_counts.get(agent, 0) + 1

            for tool in ep.tools_used:
                tool_counts[tool] = tool_counts.get(tool, 0) + 1

            for factor in ep.success_factors:
                factor_counts[factor] = factor_counts.get(factor, 0) + 1

            if ep.duration_ms:
                total_duration += ep.duration_ms
                duration_count += 1

        # 排序并返回
        common_agents = sorted(agent_counts.items(), key=lambda x: x[1], reverse=True)
        common_tools = sorted(tool_counts.items(), key=lambda x: x[1], reverse=True)
        success_factors = sorted(factor_counts.items(), key=lambda x: x[1], reverse=True)

        return {
            "common_agents": [a for a, _ in common_agents[:5]],
            "common_tools": [t for t, _ in common_tools[:10]],
            "success_factors": [f for f, _ in success_factors[:5]],
            "avg_duration_ms": total_duration // duration_count if duration_count > 0 else None,
        }

    async def get_failure_lessons(
        self,
        goal: str,
        k: int = 10,
    ) -> list[str]:
        """
        获取失败教训

        分析相似失败案例，提取教训。

        Args:
            goal: 任务目标
            k: 分析的案例数量

        Returns:
            教训列表
        """
        filter_dict = {"outcome": "failure"}
        results = await self._store.search_by_text(goal, k=k, filter=filter_dict)

        # 收集所有失败原因和教训
        lesson_counts: dict[str, int] = {}

        for result in results:
            ep = self._result_to_episode(result)

            for reason in ep.failure_reasons:
                lesson_counts[reason] = lesson_counts.get(reason, 0) + 1

            for lesson in ep.lessons_learned:
                lesson_counts[lesson] = lesson_counts.get(lesson, 0) + 1

        # 排序并返回
        sorted_lessons = sorted(lesson_counts.items(), key=lambda x: x[1], reverse=True)
        return [lesson for lesson, _ in sorted_lessons[:10]]

    async def get_advice_for_task(
        self,
        goal: str,
        task_type: str | None = None,
    ) -> TaskAdvice:
        """
        获取任务执行建议

        综合分析相似经验，生成执行建议。

        Args:
            goal: 任务目标
            task_type: 任务类型

        Returns:
            TaskAdvice
        """
        # 获取相似经验
        experiences = await self.get_similar_experiences(
            goal=goal,
            k=10,
            prefer_success=True,
            task_type=task_type,
        )

        # 获取成功模式
        patterns = await self.get_success_patterns(goal, k=10)

        # 获取失败教训
        pitfalls = await self.get_failure_lessons(goal, k=10)

        # 计算成功率
        success_rate = await self._get_success_rate(task_type=task_type)

        return TaskAdvice(
            goal=goal,
            similar_experiences=experiences[:5],
            recommended_agents=patterns.get("common_agents", []),
            common_tools=patterns.get("common_tools", []),
            success_tips=patterns.get("success_factors", []),
            pitfalls_to_avoid=pitfalls[:5],
            estimated_success_rate=success_rate,
        )

    async def _get_success_rate(self, task_type: str | None = None) -> float:
        """获取成功率"""
        filter_base = {"task_type": task_type} if task_type else None
        filter_success = {"outcome": "success"}
        if task_type:
            filter_success["task_type"] = task_type

        success_count = await self._store.count(filter=filter_success)
        total_count = await self._store.count(filter=filter_base)

        if total_count == 0:
            return 0.0
        return success_count / total_count

    async def should_use_experience(
        self,
        goal: str,
        min_similar_count: int = 3,
        min_success_rate: float = 0.5,
    ) -> bool:
        """
        判断是否应该使用历史经验

        如果有足够多的相似经验且成功率可接受，则建议使用。

        Args:
            goal: 任务目标
            min_similar_count: 最少相似经验数量
            min_success_rate: 最低成功率

        Returns:
            是否建议使用历史经验
        """
        # 检索相似经验
        results = await self._store.search_by_text(goal, k=min_similar_count)

        if len(results) < min_similar_count:
            return False

        # 计算这些相似经验的成功率
        success_count = sum(
            1 for r in results if self._result_to_episode(r).outcome == Outcome.SUCCESS
        )
        success_rate = success_count / len(results)

        return success_rate >= min_success_rate
