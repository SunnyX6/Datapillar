"""
经验学习流程集成测试

验证：
1. 会话级别的经验记录
2. 多 Agent 协作时的步骤记录
3. "完成"时机的正确性
4. 反馈收集和经验更新
"""

import pytest

from src.modules.oneagentic.experience import (
    AlwaysSavePolicy,
    DefaultSedimentationPolicy,
    Episode,
    EpisodeStep,
    ExperienceLearner,
    ExperienceStore,
    LearningResult,
    Outcome,
    SearchFilter,
    SearchResult,
    ValidationStatus,
)


class InMemoryExperienceStore(ExperienceStore):
    """内存版 ExperienceStore，用于测试"""

    def __init__(self):
        self._episodes: dict[str, Episode] = {}

    async def initialize(self) -> None:
        pass

    async def close(self) -> None:
        pass

    async def add(self, episode: Episode) -> str:
        self._episodes[episode.episode_id] = episode
        return episode.episode_id

    async def add_batch(self, episodes: list[Episode]) -> list[str]:
        ids = []
        for ep in episodes:
            self._episodes[ep.episode_id] = ep
            ids.append(ep.episode_id)
        return ids

    async def update(self, episode: Episode) -> bool:
        if episode.episode_id not in self._episodes:
            return False
        self._episodes[episode.episode_id] = episode
        return True

    async def delete(self, episode_id: str) -> bool:
        if episode_id not in self._episodes:
            return False
        del self._episodes[episode_id]
        return True

    async def get(self, episode_id: str) -> Episode | None:
        return self._episodes.get(episode_id)

    async def search(
        self,
        query: str,
        k: int = 5,
        filter: SearchFilter | None = None,
    ) -> list[SearchResult]:
        results = []
        for ep in self._episodes.values():
            if filter:
                if filter.task_type and ep.task_type != filter.task_type:
                    continue
                if filter.outcome and ep.outcome != filter.outcome:
                    continue
                if filter.user_id and ep.user_id != filter.user_id:
                    continue
            results.append(SearchResult(episode=ep, score=0.8))
        return results[:k]

    async def search_by_embedding(
        self,
        embedding: list[float],
        k: int = 5,
        filter: SearchFilter | None = None,
    ) -> list[SearchResult]:
        return await self.search("", k, filter)

    async def count(self, filter: SearchFilter | None = None) -> int:
        if not filter:
            return len(self._episodes)
        count = 0
        for ep in self._episodes.values():
            if filter.task_type and ep.task_type != filter.task_type:
                continue
            if filter.outcome and ep.outcome != filter.outcome:
                continue
            count += 1
        return count

    async def list_task_types(self) -> list[str]:
        return list(set(ep.task_type for ep in self._episodes.values()))

    async def list_tags(self) -> list[str]:
        tags = set()
        for ep in self._episodes.values():
            tags.update(ep.tags)
        return list(tags)

    async def list(
        self,
        filter: SearchFilter | None = None,
        limit: int = 100,
    ) -> list[Episode]:
        """列出经验（用于策略评估）"""
        results = []
        for ep in self._episodes.values():
            if filter:
                if filter.task_type and ep.task_type != filter.task_type:
                    continue
                if filter.user_id and ep.user_id != filter.user_id:
                    continue
            results.append(ep)
        return results[:limit]


class TestExperienceFlow:
    """经验学习流程测试"""

    @pytest.fixture
    def store(self) -> InMemoryExperienceStore:
        return InMemoryExperienceStore()

    @pytest.fixture
    def learner(self, store: InMemoryExperienceStore) -> ExperienceLearner:
        return ExperienceLearner(
            store=store,
            llm=None,  # 不使用 LLM
            policy=AlwaysSavePolicy(),
        )

    @pytest.mark.asyncio
    async def test_single_agent_complete_flow(
        self,
        store: InMemoryExperienceStore,
        learner: ExperienceLearner,
    ):
        """测试单 Agent 完整流程"""
        # 1. 开始记录
        episode = learner.start_episode(
            session_id="session_001",
            user_id="user_001",
            goal="创建用户宽表",
            task_type="etl",
        )

        # 2. 记录执行步骤
        step = EpisodeStep(
            agent_id="analyst",
            agent_name="需求分析师",
            task_description="分析用户需求",
            tools_used=["search_tables"],
            tool_calls_count=2,
        )
        step.complete(
            outcome=Outcome.SUCCESS,
            output_summary="找到 3 个相关表",
        )
        learner.record_step(episode, step)

        # 3. 完成并学习
        result = await learner.complete_and_learn(
            episode,
            outcome=Outcome.SUCCESS,
            result_summary="成功创建用户宽表",
        )

        # 验证
        assert isinstance(result, LearningResult)
        assert result.saved is True
        assert result.episode.outcome == Outcome.SUCCESS
        assert len(result.episode.steps) == 1
        assert result.episode.agents_involved == ["analyst"]
        assert "search_tables" in result.episode.tools_used

        # 验证已保存到 store
        saved = await store.get(episode.episode_id)
        assert saved is not None
        assert saved.outcome == Outcome.SUCCESS

    @pytest.mark.asyncio
    async def test_multi_agent_complete_flow(
        self,
        store: InMemoryExperienceStore,
        learner: ExperienceLearner,
    ):
        """测试多 Agent 协作流程"""
        # 1. 开始记录（会话级别）
        episode = learner.start_episode(
            session_id="session_002",
            user_id="user_001",
            goal="创建订单分析报表",
            task_type="etl",
        )

        # 2. Agent 1: Analyst
        step1 = EpisodeStep(
            agent_id="analyst",
            agent_name="需求分析师",
            task_description="分析报表需求",
        )
        step1.complete(Outcome.SUCCESS, "需求分析完成")
        learner.record_step(episode, step1)

        # 3. Agent 2: Architect
        step2 = EpisodeStep(
            agent_id="architect",
            agent_name="架构师",
            task_description="设计数据模型",
            tools_used=["search_tables", "get_table_schema"],
        )
        step2.complete(Outcome.SUCCESS, "数据模型设计完成")
        learner.record_step(episode, step2)

        # 4. Agent 3: Developer
        step3 = EpisodeStep(
            agent_id="developer",
            agent_name="开发者",
            task_description="生成 SQL 代码",
            tools_used=["write_sql"],
        )
        step3.complete(Outcome.SUCCESS, "SQL 代码生成完成")
        learner.record_step(episode, step3)

        # 5. 会话结束，完成学习（这是"完成"的时机）
        result = await learner.complete_and_learn(
            episode,
            outcome=Outcome.SUCCESS,
            result_summary="报表创建成功",
            deliverable_type="sql",
        )

        # 验证
        assert result.saved is True
        assert len(result.episode.steps) == 3
        assert result.episode.agents_involved == ["analyst", "architect", "developer"]
        assert "search_tables" in result.episode.tools_used
        assert "write_sql" in result.episode.tools_used

    @pytest.mark.asyncio
    async def test_failure_with_lessons(
        self,
        store: InMemoryExperienceStore,
        learner: ExperienceLearner,
    ):
        """测试失败场景"""
        episode = learner.start_episode(
            session_id="session_003",
            user_id="user_001",
            goal="创建复杂报表",
            task_type="etl",
        )

        # 执行失败
        step = EpisodeStep(
            agent_id="developer",
            agent_name="开发者",
            task_description="生成 SQL",
        )
        step.complete(
            Outcome.FAILURE,
            error="表不存在: orders_detail",
        )
        learner.record_step(episode, step)

        result = await learner.complete_and_learn(
            episode,
            outcome=Outcome.FAILURE,
            result_summary="任务失败：表不存在",
        )

        assert result.saved is True
        assert result.episode.outcome == Outcome.FAILURE
        assert len(result.episode.failure_reasons) > 0

    @pytest.mark.asyncio
    async def test_feedback_updates_validation_status(
        self,
        store: InMemoryExperienceStore,
        learner: ExperienceLearner,
    ):
        """测试反馈更新验证状态"""
        # 创建并保存经验
        episode = learner.start_episode(
            session_id="session_004",
            user_id="user_001",
            goal="测试反馈",
            task_type="test",
        )
        result = await learner.complete_and_learn(
            episode,
            outcome=Outcome.SUCCESS,
        )

        # 验证初始状态是 PENDING
        saved = await store.get(episode.episode_id)
        assert saved.validation_status == ValidationStatus.PENDING

        # 用户给正面反馈
        updated = await learner.learn_from_feedback(
            episode_id=episode.episode_id,
            satisfaction=0.9,
            feedback_text="非常有帮助！",
        )

        # 验证状态变为 VERIFIED
        assert updated is not None
        assert updated.validation_status == ValidationStatus.VERIFIED
        assert updated.user_satisfaction == 0.9

    @pytest.mark.asyncio
    async def test_feedback_negative_rejects_episode(
        self,
        store: InMemoryExperienceStore,
        learner: ExperienceLearner,
    ):
        """测试负面反馈导致拒绝"""
        episode = learner.start_episode(
            session_id="session_005",
            user_id="user_001",
            goal="测试负面反馈",
            task_type="test",
        )
        await learner.complete_and_learn(episode, outcome=Outcome.SUCCESS)

        # 用户给负面反馈
        updated = await learner.learn_from_feedback(
            episode_id=episode.episode_id,
            satisfaction=0.2,
            feedback_text="结果不对",
        )

        assert updated.validation_status == ValidationStatus.REJECTED

    @pytest.mark.asyncio
    async def test_feedback_delete_on_reject(
        self,
        store: InMemoryExperienceStore,
        learner: ExperienceLearner,
    ):
        """测试负面反馈可选删除"""
        episode = learner.start_episode(
            session_id="session_006",
            user_id="user_001",
            goal="测试删除",
            task_type="test",
        )
        await learner.complete_and_learn(episode, outcome=Outcome.SUCCESS)

        # 负面反馈 + 删除
        result = await learner.learn_from_feedback(
            episode_id=episode.episode_id,
            satisfaction=0.1,
            delete_on_reject=True,
        )

        # 验证已删除
        assert result is None
        assert await store.get(episode.episode_id) is None


class TestSedimentationPolicy:
    """沉淀策略测试"""

    @pytest.fixture
    def store(self) -> InMemoryExperienceStore:
        return InMemoryExperienceStore()

    @pytest.mark.asyncio
    async def test_default_policy_rejects_short_duration(
        self,
        store: InMemoryExperienceStore,
    ):
        """测试默认策略拒绝执行时间过短的经验"""
        policy = DefaultSedimentationPolicy(
            store=store,
            min_duration_ms=3000,
            min_steps=1,
        )
        learner = ExperienceLearner(store=store, policy=policy)

        episode = learner.start_episode(
            session_id="session_007",
            user_id="user_001",
            goal="快速任务",
            task_type="test",
        )

        # 添加步骤以通过步骤数检查
        step = EpisodeStep(
            agent_id="agent1",
            agent_name="Agent 1",
            task_description="Quick step",
        )
        step.complete(Outcome.SUCCESS)
        learner.record_step(episode, step)

        # 模拟执行时间过短
        episode.duration_ms = 1000  # 只用了 1 秒

        result = await learner.complete_and_learn(episode, outcome=Outcome.SUCCESS)

        # 应该被拒绝保存
        assert result.saved is False
        assert "执行时间过短" in result.reason

    @pytest.mark.asyncio
    async def test_default_policy_rejects_no_steps(
        self,
        store: InMemoryExperienceStore,
    ):
        """测试默认策略拒绝无步骤的经验"""
        policy = DefaultSedimentationPolicy(
            store=store,
            min_duration_ms=0,  # 不检查时长
            min_steps=1,
        )
        learner = ExperienceLearner(store=store, policy=policy)

        episode = learner.start_episode(
            session_id="session_008",
            user_id="user_001",
            goal="无步骤任务",
            task_type="test",
        )
        # 没有记录任何步骤

        result = await learner.complete_and_learn(episode, outcome=Outcome.SUCCESS)

        assert result.saved is False
        assert "步骤数不足" in result.reason


class TestCompletionTiming:
    """「完成」时机测试"""

    @pytest.fixture
    def store(self) -> InMemoryExperienceStore:
        return InMemoryExperienceStore()

    @pytest.fixture
    def learner(self, store: InMemoryExperienceStore) -> ExperienceLearner:
        return ExperienceLearner(store=store, policy=AlwaysSavePolicy())

    @pytest.mark.asyncio
    async def test_episode_not_saved_until_complete(
        self,
        store: InMemoryExperienceStore,
        learner: ExperienceLearner,
    ):
        """测试经验在完成前不会保存"""
        episode = learner.start_episode(
            session_id="session_009",
            user_id="user_001",
            goal="测试完成时机",
            task_type="test",
        )

        # 记录步骤
        step = EpisodeStep(
            agent_id="agent1",
            agent_name="Agent 1",
            task_description="Step 1",
        )
        learner.record_step(episode, step)

        # 还没调用 complete_and_learn，store 中应该没有
        assert await store.count() == 0

        # 完成后才保存
        await learner.complete_and_learn(episode, outcome=Outcome.SUCCESS)
        assert await store.count() == 1

    @pytest.mark.asyncio
    async def test_completion_captures_all_steps(
        self,
        store: InMemoryExperienceStore,
        learner: ExperienceLearner,
    ):
        """测试完成时包含所有步骤"""
        episode = learner.start_episode(
            session_id="session_010",
            user_id="user_001",
            goal="多步骤任务",
            task_type="test",
        )

        # 记录多个步骤（模拟多 agent 协作）
        for i in range(3):
            step = EpisodeStep(
                agent_id=f"agent_{i}",
                agent_name=f"Agent {i}",
                task_description=f"Step {i}",
            )
            step.complete(Outcome.SUCCESS)
            learner.record_step(episode, step)

        result = await learner.complete_and_learn(episode, outcome=Outcome.SUCCESS)

        # 验证所有步骤都被记录
        assert len(result.episode.steps) == 3
        assert result.episode.agents_involved == ["agent_0", "agent_1", "agent_2"]


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
