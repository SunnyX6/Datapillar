"""
经验学习器

从执行结果中提取和总结经验：
- 记录执行过程
- 生成经验总结
- 提取教训和模式
- 基于策略判断是否沉淀

使用示例：
```python
from src.modules.oneagentic.experience import ExperienceLearner, DefaultSedimentationPolicy

# 使用默认策略
learner = ExperienceLearner(
    store=store,
    llm=llm,
    policy=DefaultSedimentationPolicy(store=store),
)

# 开始记录
episode = learner.start_episode(
    session_id="...",
    user_id="...",
    goal="创建用户宽表",
)

# 记录步骤
learner.record_step(episode, step)

# 完成并学习（会自动判断是否值得保存）
result = await learner.complete_and_learn(episode, outcome=Outcome.SUCCESS)
print(f"是否保存: {result.saved}, 原因: {result.reason}")
```
"""

from __future__ import annotations

import logging
from dataclasses import dataclass
from typing import Any

from langchain_core.messages import HumanMessage, SystemMessage
from pydantic import BaseModel, Field

from src.modules.oneagentic.experience.episode import Episode, EpisodeStep, Outcome
from src.modules.oneagentic.experience.policy import (
    AlwaysSavePolicy,
    SedimentationPolicy,
)
from src.modules.oneagentic.experience.store import ExperienceStore

logger = logging.getLogger(__name__)


class LearningOutput(BaseModel):
    """LLM 学习输出"""

    reflection: str = Field(..., description="对这次执行的反思总结")
    lessons_learned: list[str] = Field(
        default_factory=list, description="从这次执行中学到的经验教训"
    )
    success_factors: list[str] = Field(
        default_factory=list, description="成功的关键因素（如果成功）"
    )
    failure_reasons: list[str] = Field(default_factory=list, description="失败的原因（如果失败）")
    improvement_suggestions: list[str] = Field(
        default_factory=list, description="下次可以改进的地方"
    )


LEARNER_SYSTEM_PROMPT = """你是一个经验学习器，负责从任务执行结果中提取有价值的经验教训。

## 你的职责
1. 分析执行过程和结果
2. 总结成功或失败的关键因素
3. 提取可复用的经验教训
4. 给出改进建议

## 分析原则
1. 客观分析：基于事实，不主观臆断
2. 可操作：提取的经验要具体、可执行
3. 有价值：避免泛泛而谈，要有针对性
4. 简洁：每条经验控制在一句话内

## 输出格式
- reflection: 对这次执行的整体反思（2-3句话）
- lessons_learned: 学到的经验教训（3-5条）
- success_factors: 成功的关键因素（如果成功，2-3条）
- failure_reasons: 失败的原因（如果失败，2-3条）
- improvement_suggestions: 改进建议（2-3条）
"""


@dataclass
class LearningResult:
    """学习结果"""

    episode: Episode
    """经验片段"""

    saved: bool
    """是否已保存到数据库"""

    reason: str
    """决策理由"""

    quality_score: float
    """质量评分 0-1"""


class ExperienceLearner:
    """
    经验学习器

    从执行结果中学习，提取经验教训。
    """

    def __init__(
        self,
        store: ExperienceStore,
        llm: Any | None = None,
        embedding_func: Any | None = None,
        policy: SedimentationPolicy | None = None,
    ):
        """
        初始化学习器

        Args:
            store: 经验存储
            llm: LLM 实例（用于生成总结）
            embedding_func: 向量化函数（用于生成 embedding）
            policy: 沉淀策略（决定哪些经验值得保存）
        """
        self._store = store
        self._llm = llm
        self._embedding_func = embedding_func
        self._policy = policy or AlwaysSavePolicy()

    def start_episode(
        self,
        *,
        session_id: str,
        user_id: str,
        goal: str,
        team_id: str = "default",
        task_type: str = "general",
        tags: list[str] | None = None,
    ) -> Episode:
        """
        开始记录一次执行经历

        Args:
            session_id: 会话 ID
            user_id: 用户 ID
            goal: 用户目标
            team_id: 团队 ID（用于隔离）
            task_type: 任务类型
            tags: 标签

        Returns:
            Episode 实例
        """
        episode = Episode(
            session_id=session_id,
            user_id=user_id,
            goal=goal,
            team_id=team_id,
            task_type=task_type,
            tags=tags or [],
        )

        logger.info(f"开始记录经验: {episode.episode_id}, 团队: {team_id}, 目标: {goal[:50]}...")
        return episode

    def record_step(
        self,
        episode: Episode,
        step: EpisodeStep,
    ) -> None:
        """
        记录执行步骤

        Args:
            episode: 经验实例
            step: 执行步骤
        """
        episode.add_step(step)
        logger.debug(f"记录步骤: {step.step_id}, Agent: {step.agent_id}")

    def record_plan(
        self,
        episode: Episode,
        plan_summary: str,
        planned_steps: int,
    ) -> None:
        """
        记录执行计划

        Args:
            episode: 经验实例
            plan_summary: 计划摘要
            planned_steps: 计划步骤数
        """
        episode.plan_summary = plan_summary
        episode.planned_steps = planned_steps

    async def complete_and_learn(
        self,
        episode: Episode,
        outcome: Outcome,
        result_summary: str = "",
        deliverable_type: str | None = None,
        deliverable: Any | None = None,
    ) -> LearningResult:
        """
        完成执行并学习

        Args:
            episode: 经验实例
            outcome: 执行结果
            result_summary: 结果摘要
            deliverable_type: 交付物类型
            deliverable: 交付物内容（SQL/工作流等）

        Returns:
            LearningResult 学习结果（包含是否保存的决策）
        """
        episode.deliverable_type = deliverable_type
        episode.deliverable = deliverable

        # 使用 LLM 生成学习总结
        if self._llm:
            learning = await self._generate_learning(episode, outcome, result_summary)
            episode.complete(
                outcome=outcome,
                result_summary=result_summary,
                reflection=learning.reflection,
                lessons=learning.lessons_learned,
            )
            episode.success_factors = learning.success_factors
            episode.failure_reasons = learning.failure_reasons
        else:
            # 没有 LLM，使用基本总结
            episode.complete(
                outcome=outcome,
                result_summary=result_summary,
            )

        # 生成 embedding
        if self._embedding_func:
            try:
                search_text = episode.to_search_text()
                episode.goal_embedding = await self._embedding_func(search_text)
            except Exception as e:
                logger.warning(f"生成 embedding 失败: {e}")

        # 调用策略判断是否值得保存
        decision = await self._policy.evaluate(episode)

        saved = False
        if decision.should_save:
            await self._store.add(episode)
            saved = True
            logger.info(f"经验已保存: {episode.episode_id}, 原因: {decision.reason}")
        else:
            logger.info(f"经验未保存: {episode.episode_id}, 原因: {decision.reason}")

        return LearningResult(
            episode=episode,
            saved=saved,
            reason=decision.reason,
            quality_score=decision.quality_score,
        )

    async def _generate_learning(
        self,
        episode: Episode,
        outcome: Outcome,
        result_summary: str,
    ) -> LearningOutput:
        """使用 LLM 生成学习总结"""
        # 构建上下文
        context_parts = [
            f"## 任务目标\n{episode.goal}",
            f"\n## 执行结果: {outcome.value}",
        ]

        if result_summary:
            context_parts.append(f"\n## 结果摘要\n{result_summary}")

        if episode.plan_summary:
            context_parts.append(f"\n## 执行计划\n{episode.plan_summary}")

        # 添加步骤信息
        if episode.steps:
            steps_info = []
            for step in episode.steps:
                step_line = (
                    f"- [{step.outcome.value}] {step.agent_name}: {step.task_description[:50]}"
                )
                if step.error_message:
                    step_line += f"\n  错误: {step.error_message}"
                steps_info.append(step_line)

            context_parts.append("\n## 执行步骤\n" + "\n".join(steps_info))

        # 添加统计信息
        stats = [
            f"- 参与 Agent: {', '.join(episode.agents_involved)}",
            f"- 使用工具: {', '.join(episode.tools_used) if episode.tools_used else '无'}",
            f"- 重试次数: {episode.retry_count}",
            f"- 重规划次数: {episode.replan_count}",
        ]
        context_parts.append("\n## 执行统计\n" + "\n".join(stats))

        context = "\n".join(context_parts)

        messages = [
            SystemMessage(content=LEARNER_SYSTEM_PROMPT),
            HumanMessage(content=context),
        ]

        structured_llm = self._llm.with_structured_output(LearningOutput)
        return await structured_llm.ainvoke(messages)

    async def learn_from_feedback(
        self,
        episode_id: str,
        satisfaction: float,
        feedback_text: str | None = None,
        *,
        auto_verify: bool = True,
        verify_threshold: float = 0.7,
        reject_threshold: float = 0.3,
        delete_on_reject: bool = False,
    ) -> Episode | None:
        """
        从用户反馈中学习

        根据用户满意度自动更新经验的验证状态：
        - 满意度 >= verify_threshold → verified（确认有价值）
        - 满意度 <= reject_threshold → rejected（标记无价值）
        - 其他 → 保持 pending

        Args:
            episode_id: 经验 ID
            satisfaction: 用户满意度 0-1
            feedback_text: 用户反馈文本
            auto_verify: 是否根据满意度自动设置验证状态
            verify_threshold: 满意度超过此值自动 verified
            reject_threshold: 满意度低于此值自动 rejected
            delete_on_reject: 是否在 rejected 时删除经验

        Returns:
            更新后的 Episode，如果不存在或已删除返回 None
        """
        episode = await self._store.get(episode_id)
        if not episode:
            logger.warning(f"经验不存在: {episode_id}")
            return None

        # 应用反馈
        episode.apply_feedback(
            satisfaction=satisfaction,
            feedback_text=feedback_text,
            auto_verify=auto_verify,
            verify_threshold=verify_threshold,
            reject_threshold=reject_threshold,
        )

        # 如果有反馈文本且有 LLM，提取额外教训
        if feedback_text and self._llm:
            try:
                additional_lesson = await self._extract_lesson_from_feedback(episode, feedback_text)
                if additional_lesson:
                    episode.lessons_learned.append(additional_lesson)
            except Exception as e:
                logger.warning(f"从反馈提取教训失败: {e}")

        # 处理 rejected 经验
        if episode.is_rejected and delete_on_reject:
            await self._store.delete(episode_id)
            logger.info(f"经验已删除: {episode_id}, 原因: 用户标记为无价值")
            return None

        await self._store.update(episode)
        logger.info(
            f"经验反馈已更新: {episode_id}, "
            f"满意度: {satisfaction:.0%}, "
            f"状态: {episode.validation_status.value}"
        )
        return episode

    async def _extract_lesson_from_feedback(
        self,
        episode: Episode,
        feedback_text: str,
    ) -> str | None:
        """使用 LLM 从反馈中提取教训"""
        from langchain_core.messages import HumanMessage, SystemMessage

        prompt = f"""根据用户对以下任务的反馈，提取一条简短的经验教训（一句话）。

任务目标: {episode.goal}
执行结果: {episode.outcome.value}
用户反馈: {feedback_text}

如果反馈中没有有价值的教训，返回空字符串。"""

        messages = [
            SystemMessage(content="你是一个经验提取器，从用户反馈中提取简短、可操作的经验教训。"),
            HumanMessage(content=prompt),
        ]

        response = await self._llm.ainvoke(messages)
        lesson = response.content.strip()

        # 过滤无效响应
        if not lesson or lesson == '""' or len(lesson) < 5:
            return None

        return f"用户反馈教训: {lesson[:100]}"

    async def verify_episode(self, episode_id: str) -> Episode | None:
        """手动验证经验"""
        episode = await self._store.get(episode_id)
        if not episode:
            return None

        episode.verify()
        await self._store.update(episode)
        logger.info(f"经验已验证: {episode_id}")
        return episode

    async def reject_episode(
        self,
        episode_id: str,
        reason: str | None = None,
        delete: bool = False,
    ) -> bool:
        """手动拒绝经验"""
        episode = await self._store.get(episode_id)
        if not episode:
            return False

        episode.reject(reason)

        if delete:
            await self._store.delete(episode_id)
            logger.info(f"经验已删除: {episode_id}, 原因: {reason or '手动拒绝'}")
        else:
            await self._store.update(episode)
            logger.info(f"经验已拒绝: {episode_id}, 原因: {reason or '未指定'}")

        return True

    async def summarize_experiences(
        self,
        task_type: str | None = None,
        limit: int = 50,
    ) -> str:
        """
        汇总经验（定期运行）

        分析一段时间内的经验，生成汇总报告。

        Args:
            task_type: 任务类型
            limit: 分析的经验数量

        Returns:
            汇总报告文本
        """

        # 获取经验统计
        total = await self._store.count()
        success_rate = await self._store.get_success_rate(task_type=task_type)
        failure_reasons = await self._store.get_common_failure_reasons(
            task_type=task_type,
            limit=5,
        )
        task_types = await self._store.list_task_types()

        # 生成报告
        lines = [
            "# 经验汇总报告",
            "",
            "## 概览",
            f"- 总经验数: {total}",
            f"- 成功率: {success_rate:.1%}",
            f"- 任务类型: {', '.join(task_types) if task_types else '无'}",
            "",
        ]

        if failure_reasons:
            lines.append("## 常见失败原因")
            for reason, count in failure_reasons:
                lines.append(f"- {reason} ({count} 次)")
            lines.append("")

        return "\n".join(lines)
