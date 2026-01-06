"""
ETL Boss 决策测试

测试场景：
1. 空任务 -> 要求用户输入
2. 新任务 -> LLM 决策路由到分析师
3. 分析完成 -> 确定性推进到架构师
4. 全部完成 -> finalize
5. LLM 结构化输出验证
"""

import logging
import sys
from pathlib import Path

import pytest

project_root = Path(__file__).parent.parent
sys.path.insert(0, str(project_root))

logger = logging.getLogger(__name__)


# ==================== Fixtures ====================


@pytest.fixture
def boss():
    """创建 Boss 实例"""
    from src.modules.etl.boss import EtlBoss

    return EtlBoss()


@pytest.fixture
def empty_blackboard():
    """空黑板（无任务）"""
    from src.modules.etl.state.blackboard import Blackboard

    return Blackboard(session_id="test_session", user_id="test_user")


@pytest.fixture
def blackboard_with_task():
    """带任务的黑板（无进度）"""
    from src.modules.etl.state.blackboard import Blackboard

    return Blackboard(
        session_id="test_session",
        user_id="test_user",
        task="把用户表同步到用户维度表",
    )


@pytest.fixture
def blackboard_analyst_done():
    """分析完成的黑板"""
    from src.modules.etl.state.blackboard import AgentReport, Blackboard

    return Blackboard(
        session_id="test_session",
        user_id="test_user",
        task="把用户表同步到用户维度表",
        reports={
            "analyst_agent": AgentReport(
                status="completed",
                summary="需求分析完成",
                deliverable_ref="analysis:123",
            ),
        },
    )


@pytest.fixture
def blackboard_architect_done():
    """架构设计完成的黑板（待设计 review）"""
    from src.modules.etl.state.blackboard import AgentReport, Blackboard

    return Blackboard(
        session_id="test_session",
        user_id="test_user",
        task="把用户表同步到用户维度表",
        reports={
            "analyst_agent": AgentReport(status="completed", summary="需求分析完成"),
            "architect_agent": AgentReport(status="completed", summary="架构设计完成"),
        },
        design_review_passed=False,
    )


@pytest.fixture
def blackboard_design_review_passed():
    """设计 review 通过的黑板（待开发）"""
    from src.modules.etl.state.blackboard import AgentReport, Blackboard

    return Blackboard(
        session_id="test_session",
        user_id="test_user",
        task="把用户表同步到用户维度表",
        reports={
            "analyst_agent": AgentReport(status="completed", summary="需求分析完成"),
            "architect_agent": AgentReport(status="completed", summary="架构设计完成"),
        },
        design_review_passed=True,
    )


@pytest.fixture
def blackboard_all_done():
    """全部完成的黑板"""
    from src.modules.etl.state.blackboard import AgentReport, Blackboard

    return Blackboard(
        session_id="test_session",
        user_id="test_user",
        task="把用户表同步到用户维度表",
        reports={
            "analyst_agent": AgentReport(status="completed", summary="需求分析完成"),
            "architect_agent": AgentReport(status="completed", summary="架构设计完成"),
            "developer_agent": AgentReport(status="completed", summary="SQL 生成完成"),
            "reviewer_agent": AgentReport(status="completed", summary="Review 通过"),
        },
        design_review_passed=True,
        development_review_passed=True,
    )


# ==================== 确定性决策测试（不依赖 LLM）====================


class TestBossDecisionDeterministic:
    """Boss 确定性决策测试（基于状态推导，不调用 LLM）"""

    @pytest.mark.asyncio
    async def test_empty_task_ask_human(self, boss, empty_blackboard):
        """测试空任务时要求用户输入"""
        result = await boss.decide(empty_blackboard)

        print(f"\n决策结果: {result}")
        assert result["current_agent"] == "human_in_the_loop"

    @pytest.mark.asyncio
    async def test_analyst_done_route_to_architect(self, boss, blackboard_analyst_done):
        """测试分析完成后确定性推进到架构师"""
        result = await boss.decide(blackboard_analyst_done)

        print(f"\n决策结果: {result}")
        assert result["current_agent"] == "architect_agent"

    @pytest.mark.asyncio
    async def test_architect_done_route_to_reviewer(self, boss, blackboard_architect_done):
        """测试架构设计完成后推进到 reviewer（设计 review）"""
        result = await boss.decide(blackboard_architect_done)

        print(f"\n决策结果: {result}")
        assert result["current_agent"] == "reviewer_agent"

    @pytest.mark.asyncio
    async def test_design_review_passed_route_to_developer(
        self, boss, blackboard_design_review_passed
    ):
        """测试设计 review 通过后推进到开发"""
        result = await boss.decide(blackboard_design_review_passed)

        print(f"\n决策结果: {result}")
        assert result["current_agent"] == "developer_agent"

    @pytest.mark.asyncio
    async def test_all_done_finalize(self, boss, blackboard_all_done):
        """测试全部完成后结束"""
        result = await boss.decide(blackboard_all_done)

        print(f"\n决策结果: {result}")
        assert result["current_agent"] == "finalize"


# ==================== LLM 决策测试 ====================


class TestBossDecisionLLM:
    """Boss LLM 决策测试（需要调用 LLM）"""

    @pytest.mark.asyncio
    async def test_new_task_llm_decision(self, boss, blackboard_with_task):
        """测试新任务时 LLM 决策"""
        from src.modules.etl.boss import BossDecision

        # 直接调用 _decide_by_llm 测试结构化输出
        decision = await boss._decide_by_llm(blackboard_with_task)

        print(f"\n决策类型: {type(decision)}")
        print(
            f"决策内容: action={decision.action}, target={decision.target_agent}, reason={decision.reason}"
        )

        # 验证返回的是 BossDecision 实例
        assert isinstance(decision, BossDecision)
        assert decision.action in ["route", "complete", "ask_human"]
        assert decision.reason  # 必须有理由

        # 新任务应该路由到 analyst_agent
        if decision.action == "route":
            print(f"✅ LLM 决策路由到: {decision.target_agent}")
            assert (
                decision.target_agent == "analyst_agent"
            ), f"新任务应该路由到 analyst_agent，而不是 {decision.target_agent}"

    @pytest.mark.asyncio
    async def test_new_task_full_decide(self, boss, blackboard_with_task):
        """测试新任务完整决策流程"""
        result = await boss.decide(blackboard_with_task)

        print(f"\n决策结果: {result}")
        # 新任务应该路由到 analyst_agent
        assert (
            result["current_agent"] == "analyst_agent"
        ), f"新任务应该路由到 analyst_agent，而不是 {result['current_agent']}"


# ==================== 直接运行 ====================


if __name__ == "__main__":
    pytest.main([__file__, "-v", "-s", "--tb=short"])
