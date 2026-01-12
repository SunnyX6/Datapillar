"""
经验学习模块测试
"""

import pytest

from datapillar_oneagentic.experience import (
    Episode,
    EpisodeStep,
    Outcome,
    ValidationStatus,
    TaskAdvice,
    SedimentationPolicy,
    SedimentationDecision,
    AlwaysSavePolicy,
    NeverSavePolicy,
    DefaultSedimentationPolicy,
    CompositePolicy,
    TaskTypePolicy,
    QualityThresholdPolicy,
)


class TestEpisode:
    """Episode 模型测试"""

    def test_create_episode(self):
        """测试创建 Episode"""
        episode = Episode(
            goal="创建用户宽表",
            task_type="etl",
            team_id="team_001",
            session_id="session_001",
            user_id="user_001",
        )

        assert episode.goal == "创建用户宽表"
        assert episode.task_type == "etl"
        assert episode.team_id == "team_001"
        assert episode.outcome == Outcome.SUCCESS
        assert episode.steps == []
        assert episode.validation_status == ValidationStatus.PENDING

    def test_add_step(self):
        """测试添加步骤"""
        episode = Episode(
            goal="创建用户宽表",
            task_type="etl",
            team_id="team_001",
            session_id="session_001",
            user_id="user_001",
        )

        step = EpisodeStep(
            agent_id="analyst",
            agent_name="分析师",
            task_description="分析需求",
            input_summary="用户需求",
            output_summary="需求分析完成",
        )
        episode.add_step(step)

        assert len(episode.steps) == 1
        assert episode.steps[0].agent_id == "analyst"
        assert "analyst" in episode.agents_involved

    def test_set_outcome(self):
        """测试设置结果"""
        episode = Episode(
            goal="创建用户宽表",
            task_type="etl",
            team_id="team_001",
            session_id="session_001",
            user_id="user_001",
        )

        episode.complete(outcome=Outcome.SUCCESS, result_summary="任务完成")

        assert episode.outcome == Outcome.SUCCESS
        assert episode.result_summary == "任务完成"
        assert episode.completed_at_ms is not None

    def test_episode_with_tools(self):
        """测试带工具的 Episode"""
        episode = Episode(
            goal="查询数据",
            session_id="session_001",
            user_id="user_001",
        )

        step = EpisodeStep(
            agent_id="analyst",
            agent_name="分析师",
            task_description="执行查询",
            tools_used=["search_tables", "get_table_schema"],
            tool_calls_count=2,
        )
        episode.add_step(step)

        assert "search_tables" in episode.tools_used
        assert "get_table_schema" in episode.tools_used

    def test_episode_apply_feedback(self):
        """测试应用反馈"""
        episode = Episode(
            goal="测试任务",
            session_id="session_001",
            user_id="user_001",
        )

        episode.apply_feedback(satisfaction=0.9, feedback_text="很好")

        assert episode.user_satisfaction == 0.9
        assert episode.feedback_text == "很好"
        assert episode.validation_status == ValidationStatus.VERIFIED

    def test_episode_reject_low_satisfaction(self):
        """测试低满意度自动拒绝"""
        episode = Episode(
            goal="测试任务",
            session_id="session_001",
            user_id="user_001",
        )

        episode.apply_feedback(satisfaction=0.2)

        assert episode.validation_status == ValidationStatus.REJECTED

    def test_episode_verify_and_reject(self):
        """测试手动验证和拒绝"""
        episode = Episode(
            goal="测试任务",
            session_id="session_001",
            user_id="user_001",
        )

        episode.verify()
        assert episode.is_verified

        episode.reject(reason="无价值")
        assert episode.is_rejected
        assert episode.feedback_text == "无价值"

    def test_episode_to_search_text(self):
        """测试生成搜索文本"""
        episode = Episode(
            goal="创建用户宽表",
            task_type="etl",
            session_id="session_001",
            user_id="user_001",
            result_summary="成功创建",
            reflection="流程顺利",
        )

        search_text = episode.to_search_text()

        assert "创建用户宽表" in search_text
        assert "etl" in search_text
        assert "成功创建" in search_text


class TestEpisodeStep:
    """EpisodeStep 测试"""

    def test_create_step(self):
        """测试创建步骤"""
        step = EpisodeStep(
            agent_id="analyst",
            agent_name="分析师",
            task_description="分析用户需求",
        )

        assert step.agent_id == "analyst"
        assert step.agent_name == "分析师"
        assert step.task_description == "分析用户需求"
        assert step.outcome == Outcome.SUCCESS

    def test_step_complete(self):
        """测试完成步骤"""
        step = EpisodeStep(
            agent_id="analyst",
            agent_name="分析师",
            task_description="分析用户需求",
        )

        step.complete(
            outcome=Outcome.SUCCESS,
            output_summary="分析完成",
        )

        assert step.outcome == Outcome.SUCCESS
        assert step.output_summary == "分析完成"
        assert step.ended_at_ms is not None
        assert step.duration_ms is not None

    def test_step_complete_with_error(self):
        """测试失败步骤"""
        step = EpisodeStep(
            agent_id="analyst",
            agent_name="分析师",
            task_description="分析用户需求",
        )

        step.complete(
            outcome=Outcome.FAILURE,
            error="连接超时",
        )

        assert step.outcome == Outcome.FAILURE
        assert step.error_message == "连接超时"


class TestSedimentationPolicy:
    """沉淀策略测试"""

    @pytest.mark.asyncio
    async def test_always_policy(self):
        """测试 AlwaysSavePolicy"""
        policy = AlwaysSavePolicy()
        episode = Episode(
            goal="测试任务",
            task_type="etl",
            team_id="team_001",
            session_id="session_001",
            user_id="user_001",
        )

        decision = await policy.evaluate(episode)

        assert decision.should_save is True

    @pytest.mark.asyncio
    async def test_never_policy(self):
        """测试 NeverSavePolicy"""
        policy = NeverSavePolicy()
        episode = Episode(
            goal="测试任务",
            task_type="etl",
            team_id="team_001",
            session_id="session_001",
            user_id="user_001",
        )

        decision = await policy.evaluate(episode)

        assert decision.should_save is False

    @pytest.mark.asyncio
    async def test_default_policy_with_steps(self):
        """测试 DefaultSedimentationPolicy - 有步骤的任务"""
        policy = DefaultSedimentationPolicy(
            min_duration_ms=0,
            min_steps=1,
            require_tools=False,
        )

        episode = Episode(
            goal="测试任务",
            task_type="etl",
            session_id="session_001",
            user_id="user_001",
            outcome=Outcome.SUCCESS,
        )

        step = EpisodeStep(
            agent_id="analyst",
            agent_name="分析师",
            task_description="分析",
        )
        episode.add_step(step)
        episode.complete(outcome=Outcome.SUCCESS)

        decision = await policy.evaluate(episode)

        assert decision.should_save is True

    @pytest.mark.asyncio
    async def test_default_policy_no_steps(self):
        """测试 DefaultSedimentationPolicy - 无步骤"""
        policy = DefaultSedimentationPolicy(
            min_duration_ms=0,
            min_steps=1,
        )

        episode = Episode(
            goal="测试任务",
            task_type="etl",
            session_id="session_001",
            user_id="user_001",
        )

        decision = await policy.evaluate(episode)

        assert decision.should_save is False
        assert "步骤数不足" in decision.reason

    @pytest.mark.asyncio
    async def test_task_type_policy_allowed(self):
        """测试 TaskTypePolicy - 允许的类型"""
        policy = TaskTypePolicy(allowed_types=["etl", "query"])

        episode = Episode(
            goal="测试任务",
            task_type="etl",
            session_id="session_001",
            user_id="user_001",
        )

        decision = await policy.evaluate(episode)

        assert decision.should_save is True

    @pytest.mark.asyncio
    async def test_task_type_policy_blocked(self):
        """测试 TaskTypePolicy - 不允许的类型"""
        policy = TaskTypePolicy(allowed_types=["etl", "query"])

        episode = Episode(
            goal="测试任务",
            task_type="other",
            session_id="session_001",
            user_id="user_001",
        )

        decision = await policy.evaluate(episode)

        assert decision.should_save is False
        assert "不在白名单" in decision.reason

    @pytest.mark.asyncio
    async def test_task_type_policy_blacklist(self):
        """测试 TaskTypePolicy - 黑名单"""
        policy = TaskTypePolicy(allowed_types=[], blocked_types=["sensitive"])

        episode = Episode(
            goal="测试任务",
            task_type="sensitive",
            session_id="session_001",
            user_id="user_001",
        )

        decision = await policy.evaluate(episode)

        assert decision.should_save is False
        assert "黑名单" in decision.reason

    @pytest.mark.asyncio
    async def test_quality_threshold_policy_high(self):
        """测试 QualityThresholdPolicy - 高质量"""
        base_policy = AlwaysSavePolicy()
        policy = QualityThresholdPolicy(base_policy=base_policy, min_quality=0.3)

        episode = Episode(
            goal="测试任务",
            task_type="etl",
            session_id="session_001",
            user_id="user_001",
        )

        decision = await policy.evaluate(episode)

        assert decision.should_save is True

    @pytest.mark.asyncio
    async def test_quality_threshold_policy_low(self):
        """测试 QualityThresholdPolicy - 低质量"""
        base_policy = AlwaysSavePolicy()
        policy = QualityThresholdPolicy(base_policy=base_policy, min_quality=0.9)

        episode = Episode(
            goal="测试任务",
            task_type="etl",
            session_id="session_001",
            user_id="user_001",
        )

        decision = await policy.evaluate(episode)

        assert decision.should_save is False
        assert "质量分不足" in decision.reason

    @pytest.mark.asyncio
    async def test_composite_policy_all(self):
        """测试 CompositePolicy - ALL 模式"""
        policy = CompositePolicy(
            policies=[AlwaysSavePolicy(), NeverSavePolicy()],
            mode="ALL",
        )

        episode = Episode(
            goal="测试任务",
            session_id="session_001",
            user_id="user_001",
        )

        decision = await policy.evaluate(episode)

        assert decision.should_save is False

    @pytest.mark.asyncio
    async def test_composite_policy_any(self):
        """测试 CompositePolicy - ANY 模式"""
        policy = CompositePolicy(
            policies=[AlwaysSavePolicy(), NeverSavePolicy()],
            mode="ANY",
        )

        episode = Episode(
            goal="测试任务",
            session_id="session_001",
            user_id="user_001",
        )

        decision = await policy.evaluate(episode)

        assert decision.should_save is True


class TestTaskAdvice:
    """TaskAdvice 测试"""

    def test_create_task_advice(self):
        """测试创建 TaskAdvice"""
        advice = TaskAdvice(
            goal="创建用户宽表",
            similar_experiences=[],
            recommended_agents=["analyst", "developer"],
            common_tools=["search_tables"],
            success_tips=["先分析需求"],
            pitfalls_to_avoid=["数据倾斜"],
            estimated_success_rate=0.85,
        )

        assert advice.goal == "创建用户宽表"
        assert advice.recommended_agents == ["analyst", "developer"]
        assert advice.estimated_success_rate == 0.85

    def test_task_advice_to_prompt(self):
        """测试 TaskAdvice 生成 prompt"""
        advice = TaskAdvice(
            goal="创建用户宽表",
            similar_experiences=[],
            recommended_agents=["analyst"],
            common_tools=["search_tables"],
            success_tips=["先分析需求"],
            pitfalls_to_avoid=["数据倾斜"],
            estimated_success_rate=0.85,
        )

        prompt = advice.to_prompt()

        assert "85%" in prompt
        assert "analyst" in prompt
        assert "先分析需求" in prompt
        assert "数据倾斜" in prompt


class TestSedimentationDecision:
    """SedimentationDecision 测试"""

    def test_create_decision(self):
        """测试创建决策"""
        decision = SedimentationDecision(
            should_save=True,
            reason="符合条件",
            quality_score=0.8,
        )

        assert decision.should_save is True
        assert decision.reason == "符合条件"
        assert decision.quality_score == 0.8

    def test_decision_defaults(self):
        """测试决策默认值"""
        decision = SedimentationDecision(
            should_save=False,
            reason="不符合条件",
        )

        assert decision.quality_score == 0.0
