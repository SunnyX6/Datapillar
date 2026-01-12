"""
经验沉淀策略

判断一条经验是否值得保存到数据库。

设计原则：
- 框架提供默认策略（基于规则）
- 业务可自定义策略（基于业务逻辑）
- 避免垃圾经验污染数据库

使用示例：
```python
# 使用默认策略
learner = ExperienceLearner(
    store=store,
    policy=DefaultSedimentationPolicy(store=store),
)

# 自定义策略
class MyPolicy(SedimentationPolicy):
    async def should_save(self, episode: Episode) -> bool:
        # 只保存特定类型任务
        return episode.task_type in ["analysis", "report"]

learner = ExperienceLearner(store=store, policy=MyPolicy())
```
"""

from __future__ import annotations

import logging
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import TYPE_CHECKING

from datapillar_oneagentic.experience.episode import Episode, Outcome

if TYPE_CHECKING:
    from datapillar_oneagentic.storage.learning_stores import VectorStore

logger = logging.getLogger(__name__)


@dataclass
class SedimentationDecision:
    """沉淀决策结果"""

    should_save: bool
    """是否应该保存"""

    reason: str
    """决策理由"""

    quality_score: float = 0.0
    """质量评分 0-1（可选）"""


class SedimentationPolicy(ABC):
    """
    经验沉淀策略抽象接口

    决定哪些经验值得保存到数据库。
    """

    @abstractmethod
    async def evaluate(self, episode: Episode) -> SedimentationDecision:
        """
        评估经验是否值得保存

        Args:
            episode: 经验片段

        Returns:
            SedimentationDecision 决策结果
        """
        pass

    async def should_save(self, episode: Episode) -> bool:
        """便捷方法：直接返回是否保存"""
        decision = await self.evaluate(episode)
        return decision.should_save


class AlwaysSavePolicy(SedimentationPolicy):
    """
    总是保存策略

    适用于：
    - 测试环境
    - 需要完整记录的场景
    """

    async def evaluate(self, episode: Episode) -> SedimentationDecision:
        return SedimentationDecision(
            should_save=True,
            reason="AlwaysSavePolicy: 保存所有经验",
            quality_score=0.5,
        )


class NeverSavePolicy(SedimentationPolicy):
    """
    从不保存策略

    适用于：
    - 禁用经验学习
    - 隐私敏感场景
    """

    async def evaluate(self, episode: Episode) -> SedimentationDecision:
        return SedimentationDecision(
            should_save=False,
            reason="NeverSavePolicy: 禁用经验保存",
            quality_score=0.0,
        )


class DefaultSedimentationPolicy(SedimentationPolicy):
    """
    默认沉淀策略

    基于多维度规则判断：
    1. 执行时长（太短的可能是简单查询）
    2. 工具使用（没用工具可能是纯对话）
    3. 重复检测（与已有经验高度相似）
    4. 失败质量（失败经验需要有明确教训）
    5. 步骤完整性（至少完成了一些步骤）
    """

    def __init__(
        self,
        store: VectorStore | None = None,
        *,
        min_duration_ms: int = 3000,
        min_steps: int = 1,
        require_tools: bool = False,
        dedup_threshold: float = 0.95,
        require_lessons_for_failure: bool = True,
    ):
        """
        初始化默认策略

        Args:
            store: 经验存储（用于去重检测）
            min_duration_ms: 最小执行时长（毫秒）
            min_steps: 最少步骤数
            require_tools: 是否要求使用了工具
            dedup_threshold: 去重阈值（相似度超过此值视为重复）
            require_lessons_for_failure: 失败经验是否需要有教训
        """
        self._store = store
        self._min_duration_ms = min_duration_ms
        self._min_steps = min_steps
        self._require_tools = require_tools
        self._dedup_threshold = dedup_threshold
        self._require_lessons_for_failure = require_lessons_for_failure

    async def evaluate(self, episode: Episode) -> SedimentationDecision:
        """评估经验"""
        quality_score = 0.0
        reasons = []

        # 1. 检查执行时长
        if episode.duration_ms is not None and episode.duration_ms < self._min_duration_ms:
            return SedimentationDecision(
                should_save=False,
                reason=f"执行时间过短: {episode.duration_ms}ms < {self._min_duration_ms}ms",
                quality_score=0.1,
            )
        if episode.duration_ms is not None:
            # 时长越长，质量分越高（上限 0.2）
            duration_score = min(episode.duration_ms / 60000, 1.0) * 0.2
            quality_score += duration_score

        # 2. 检查步骤数
        if len(episode.steps) < self._min_steps:
            return SedimentationDecision(
                should_save=False,
                reason=f"步骤数不足: {len(episode.steps)} < {self._min_steps}",
                quality_score=0.1,
            )
        # 步骤越多，质量分越高（上限 0.2）
        steps_score = min(len(episode.steps) / 5, 1.0) * 0.2
        quality_score += steps_score

        # 3. 检查工具使用
        if self._require_tools and not episode.tools_used:
            return SedimentationDecision(
                should_save=False,
                reason="未使用任何工具",
                quality_score=0.2,
            )
        if episode.tools_used:
            tools_score = min(len(episode.tools_used) / 3, 1.0) * 0.2
            quality_score += tools_score

        # 4. 检查失败经验的教训
        if episode.outcome == Outcome.FAILURE:
            if self._require_lessons_for_failure:
                has_lessons = bool(episode.failure_reasons or episode.lessons_learned)
                if not has_lessons:
                    return SedimentationDecision(
                        should_save=False,
                        reason="失败经验缺少明确教训",
                        quality_score=0.2,
                    )
            # 有教训的失败经验有价值
            if episode.failure_reasons or episode.lessons_learned:
                quality_score += 0.2
                reasons.append("有失败教训")

        # 5. 成功经验加分
        if episode.outcome == Outcome.SUCCESS:
            quality_score += 0.2
            reasons.append("执行成功")

            # 有成功因素的更有价值
            if episode.success_factors:
                quality_score += 0.1
                reasons.append("有成功因素分析")

        # 6. 去重检测（如果有 store）
        if self._store and self._dedup_threshold < 1.0:
            try:
                similar = await self._store.search(episode.goal, k=1)
                if similar and similar[0].score > self._dedup_threshold:
                    return SedimentationDecision(
                        should_save=False,
                        reason=f"与已有经验高度相似: {similar[0].score:.2%}",
                        quality_score=quality_score,
                    )
            except Exception as e:
                logger.warning(f"去重检测失败: {e}")

        # 7. 计算最终质量分
        quality_score = min(quality_score, 1.0)

        return SedimentationDecision(
            should_save=True,
            reason=f"符合沉淀条件: {'; '.join(reasons) if reasons else '基本条件满足'}",
            quality_score=quality_score,
        )


class CompositePolicy(SedimentationPolicy):
    """
    组合策略

    支持多个策略的组合：
    - ALL: 所有策略都同意才保存
    - ANY: 任一策略同意就保存
    """

    def __init__(
        self,
        policies: list[SedimentationPolicy],
        mode: str = "ALL",
    ):
        """
        初始化组合策略

        Args:
            policies: 策略列表
            mode: 组合模式 "ALL" 或 "ANY"
        """
        self._policies = policies
        self._mode = mode.upper()

    async def evaluate(self, episode: Episode) -> SedimentationDecision:
        """评估所有策略"""
        decisions = []
        for policy in self._policies:
            decision = await policy.evaluate(episode)
            decisions.append(decision)

        if self._mode == "ALL":
            # 所有策略都同意才保存
            should_save = all(d.should_save for d in decisions)
            reasons = [d.reason for d in decisions if not d.should_save]
            reason = reasons[0] if reasons else "所有策略通过"
        else:  # ANY
            # 任一策略同意就保存
            should_save = any(d.should_save for d in decisions)
            reasons = [d.reason for d in decisions if d.should_save]
            reason = reasons[0] if reasons else "所有策略拒绝"

        # 质量分取平均
        avg_score = sum(d.quality_score for d in decisions) / len(decisions)

        return SedimentationDecision(
            should_save=should_save,
            reason=f"[{self._mode}] {reason}",
            quality_score=avg_score,
        )


class TaskTypePolicy(SedimentationPolicy):
    """
    任务类型策略

    只保存特定类型的任务经验。
    """

    def __init__(
        self,
        allowed_types: list[str],
        blocked_types: list[str] | None = None,
    ):
        """
        初始化任务类型策略

        Args:
            allowed_types: 允许的任务类型（白名单）
            blocked_types: 禁止的任务类型（黑名单）
        """
        self._allowed_types = set(allowed_types)
        self._blocked_types = set(blocked_types) if blocked_types else set()

    async def evaluate(self, episode: Episode) -> SedimentationDecision:
        """检查任务类型"""
        task_type = episode.task_type

        # 黑名单优先
        if task_type in self._blocked_types:
            return SedimentationDecision(
                should_save=False,
                reason=f"任务类型 '{task_type}' 在黑名单中",
                quality_score=0.0,
            )

        # 检查白名单
        if self._allowed_types and task_type not in self._allowed_types:
            return SedimentationDecision(
                should_save=False,
                reason=f"任务类型 '{task_type}' 不在白名单中",
                quality_score=0.0,
            )

        return SedimentationDecision(
            should_save=True,
            reason=f"任务类型 '{task_type}' 允许保存",
            quality_score=0.5,
        )


class QualityThresholdPolicy(SedimentationPolicy):
    """
    质量阈值策略

    基于质量评分决定是否保存。
    """

    def __init__(
        self,
        base_policy: SedimentationPolicy,
        min_quality: float = 0.5,
    ):
        """
        初始化质量阈值策略

        Args:
            base_policy: 基础策略（用于计算质量分）
            min_quality: 最低质量分
        """
        self._base_policy = base_policy
        self._min_quality = min_quality

    async def evaluate(self, episode: Episode) -> SedimentationDecision:
        """检查质量阈值"""
        decision = await self._base_policy.evaluate(episode)

        if decision.quality_score < self._min_quality:
            return SedimentationDecision(
                should_save=False,
                reason=f"质量分不足: {decision.quality_score:.2f} < {self._min_quality:.2f}",
                quality_score=decision.quality_score,
            )

        return decision


class FeedbackAwareSedimentationPolicy(SedimentationPolicy):
    """
    反馈感知沉淀策略

    基于历史反馈数据来评估当前经验：
    1. 同类任务历史上被频繁 rejected → 降低保存意愿
    2. 用户历史反馈积极 → 提高保存意愿
    3. 结合基础策略的规则判断

    使用示例：
    ```python
    policy = FeedbackAwareSedimentationPolicy(
        store=store,
        base_policy=DefaultSedimentationPolicy(store=store),
        rejection_threshold=0.5,  # 同类任务 rejected 率超 50% 则不保存
    )
    ```
    """

    def __init__(
        self,
        store: VectorStore,
        base_policy: SedimentationPolicy | None = None,
        *,
        rejection_threshold: float = 0.5,
        min_history_count: int = 3,
        user_boost: float = 0.1,
    ):
        """
        初始化反馈感知策略

        Args:
            store: 向量存储（用于查询历史反馈）
            base_policy: 基础策略（默认使用 DefaultSedimentationPolicy）
            rejection_threshold: 拒绝率阈值，超过则不保存
            min_history_count: 最少历史记录数才生效
            user_boost: 用户历史积极时的加分
        """
        self._store = store
        self._base_policy = base_policy or DefaultSedimentationPolicy(store=store)
        self._rejection_threshold = rejection_threshold
        self._min_history_count = min_history_count
        self._user_boost = user_boost

    async def evaluate(self, episode: Episode) -> SedimentationDecision:
        """评估经验，考虑历史反馈"""
        # 先用基础策略评估
        base_decision = await self._base_policy.evaluate(episode)
        if not base_decision.should_save:
            return base_decision

        quality_score = base_decision.quality_score
        reasons = [base_decision.reason]

        # 查询同类任务的历史反馈
        try:
            task_rejection_rate = await self._get_task_rejection_rate(episode.task_type)
            if task_rejection_rate is not None:
                if task_rejection_rate > self._rejection_threshold:
                    return SedimentationDecision(
                        should_save=False,
                        reason=f"同类任务 '{episode.task_type}' 历史拒绝率过高: {task_rejection_rate:.0%}",
                        quality_score=quality_score * (1 - task_rejection_rate),
                    )
                # 低拒绝率加分
                quality_score += (1 - task_rejection_rate) * 0.1
                reasons.append(f"同类任务历史表现良好")

            # 查询用户历史反馈
            user_satisfaction = await self._get_user_avg_satisfaction(episode.user_id)
            if user_satisfaction is not None and user_satisfaction > 0.7:
                quality_score += self._user_boost
                reasons.append(f"用户历史反馈积极")

        except Exception as e:
            logger.warning(f"查询历史反馈失败: {e}")

        return SedimentationDecision(
            should_save=True,
            reason="; ".join(reasons),
            quality_score=min(quality_score, 1.0),
        )

    async def _get_task_rejection_rate(self, task_type: str) -> float | None:
        """获取任务类型的历史拒绝率"""
        from datapillar_oneagentic.experience.episode import ValidationStatus

        # 查询该任务类型的历史经验
        filter_dict = {"task_type": task_type}
        results = await self._store.search_by_text("", k=20, filter=filter_dict)

        if len(results) < self._min_history_count:
            return None

        # 反序列化 Episode
        episodes = [
            Episode.model_validate_json(r.metadata.get("data", "{}"))
            for r in results
        ]

        # 只统计有反馈的经验
        with_feedback = [e for e in episodes if e.validation_status != ValidationStatus.PENDING]
        if not with_feedback:
            return None

        rejected_count = sum(1 for e in with_feedback if e.validation_status == ValidationStatus.REJECTED)
        return rejected_count / len(with_feedback)

    async def _get_user_avg_satisfaction(self, user_id: str) -> float | None:
        """获取用户的平均满意度"""
        filter_dict = {"user_id": user_id}
        results = await self._store.search_by_text("", k=20, filter=filter_dict)

        # 反序列化 Episode
        episodes = [
            Episode.model_validate_json(r.metadata.get("data", "{}"))
            for r in results
        ]

        # 只统计有满意度的经验
        with_satisfaction = [e for e in episodes if e.user_satisfaction is not None]
        if len(with_satisfaction) < self._min_history_count:
            return None

        return sum(e.user_satisfaction for e in with_satisfaction) / len(with_satisfaction)
