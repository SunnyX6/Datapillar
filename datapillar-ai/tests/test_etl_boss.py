"""
EtlBoss 单元测试

测试场景：
1. 前置拦截：human 请求
2. 前置拦截：delegate 请求
3. LLM 决策：route 到员工
4. LLM 决策：complete 任务完成
5. LLM 决策：ask_human 需要澄清
6. LLM 返回无效 action
7. LLM 返回无效 target_agent
8. LLM 未配置
9. 状态描述完整性
"""

from unittest.mock import AsyncMock, MagicMock

import pytest

from src.modules.etl.boss import (
    AGENT_IDS,
    AGENT_IDS_SET,
    BossDecision,
    EtlBoss,
)
from src.modules.etl.schemas.requests import BlackboardRequest
from src.modules.etl.state.blackboard import AgentReport, Blackboard


class TestBossDecision:
    """测试 BossDecision 数据结构"""

    def test_route_decision(self):
        """route 决策需要 target_agent"""
        decision = BossDecision(
            action="route",
            target_agent="analyst_agent",
            reason="用户提出新需求",
        )
        assert decision.action == "route"
        assert decision.target_agent == "analyst_agent"

    def test_complete_decision(self):
        """complete 决策不需要 target_agent"""
        decision = BossDecision(
            action="complete",
            reason="所有员工已完成",
        )
        assert decision.action == "complete"
        assert decision.target_agent is None

    def test_ask_human_decision(self):
        """ask_human 决策不需要 target_agent"""
        decision = BossDecision(
            action="ask_human",
            reason="需求不明确",
        )
        assert decision.action == "ask_human"
        assert decision.target_agent is None


class TestAgentIds:
    """测试 Agent ID 常量"""

    def test_agent_ids_order(self):
        """AGENT_IDS 是有序元组"""
        assert AGENT_IDS == (
            "analyst_agent",
            "architect_agent",
            "developer_agent",
            "tester_agent",
        )

    def test_agent_ids_set(self):
        """AGENT_IDS_SET 包含所有员工"""
        assert "analyst_agent" in AGENT_IDS_SET
        assert "architect_agent" in AGENT_IDS_SET
        assert "developer_agent" in AGENT_IDS_SET
        assert "tester_agent" in AGENT_IDS_SET
        # knowledge_agent 不是员工
        assert "knowledge_agent" not in AGENT_IDS_SET


class TestBossDecide:
    """测试 Boss.decide 方法"""

    @pytest.fixture
    def boss(self):
        """创建 Boss 实例（无 LLM）"""
        return EtlBoss()

    @pytest.fixture
    def boss_with_llm(self):
        """创建带 Mock LLM 的 Boss 实例"""
        mock_llm = AsyncMock()
        return EtlBoss(llm=mock_llm), mock_llm

    @pytest.fixture
    def empty_blackboard(self):
        """空白 Blackboard"""
        return Blackboard(session_id="test-session", task="帮我做一个订单汇总表")

    # ==================== 前置拦截测试 ====================

    @pytest.mark.asyncio
    async def test_intercept_human_request(self, boss_with_llm, empty_blackboard):
        """前置拦截：有 human 请求时，直接返回 human_in_the_loop"""
        boss, mock_llm = boss_with_llm
        blackboard = empty_blackboard

        # 添加 human 请求
        blackboard.pending_requests = [
            BlackboardRequest(
                request_id="req-1",
                kind="human",
                status="pending",
                created_by="analyst_agent",
                payload={"message": "请确认数据源"},
            )
        ]

        result = await boss.decide(blackboard)

        assert result == {"current_agent": "human_in_the_loop"}
        # LLM 不应该被调用
        mock_llm.ainvoke.assert_not_called()

    @pytest.mark.asyncio
    async def test_intercept_delegate_request(self, boss_with_llm, empty_blackboard):
        """前置拦截：有 delegate 请求时，直接路由到目标员工"""
        boss, mock_llm = boss_with_llm
        blackboard = empty_blackboard

        # 添加 delegate 请求
        blackboard.pending_requests = [
            BlackboardRequest(
                request_id="req-1",
                kind="delegate",
                status="pending",
                created_by="analyst_agent",
                target_agent="architect_agent",
                payload={},
            )
        ]

        result = await boss.decide(blackboard)

        assert result == {"current_agent": "architect_agent"}
        # LLM 不应该被调用
        mock_llm.ainvoke.assert_not_called()

    @pytest.mark.asyncio
    async def test_intercept_invalid_delegate_target(self, boss_with_llm, empty_blackboard):
        """前置拦截：delegate 目标无效时，走 LLM 决策"""
        boss, mock_llm = boss_with_llm
        blackboard = empty_blackboard

        # 添加无效目标的 delegate 请求
        blackboard.pending_requests = [
            BlackboardRequest(
                request_id="req-1",
                kind="delegate",
                status="pending",
                created_by="analyst_agent",
                target_agent="invalid_agent",  # 无效目标
                payload={},
            )
        ]

        # Mock LLM 返回
        mock_response = MagicMock()
        mock_response.content = (
            '{"action": "route", "target_agent": "analyst_agent", "reason": "test"}'
        )
        mock_llm.ainvoke.return_value = mock_response

        result = await boss.decide(blackboard)

        # 应该走 LLM 决策
        assert result == {"current_agent": "analyst_agent"}
        mock_llm.ainvoke.assert_called_once()

    # ==================== LLM 决策测试 ====================

    @pytest.mark.asyncio
    async def test_llm_route_decision(self, boss_with_llm, empty_blackboard):
        """LLM 决策：route 到员工"""
        boss, mock_llm = boss_with_llm

        mock_response = MagicMock()
        mock_response.content = (
            '{"action": "route", "target_agent": "developer_agent", "reason": "需要生成SQL"}'
        )
        mock_llm.ainvoke.return_value = mock_response

        result = await boss.decide(empty_blackboard)

        assert result == {"current_agent": "developer_agent"}

    @pytest.mark.asyncio
    async def test_llm_complete_decision(self, boss_with_llm, empty_blackboard):
        """LLM 决策：complete 任务完成"""
        boss, mock_llm = boss_with_llm

        mock_response = MagicMock()
        mock_response.content = '{"action": "complete", "reason": "所有员工已完成工作"}'
        mock_llm.ainvoke.return_value = mock_response

        result = await boss.decide(empty_blackboard)

        assert result == {"current_agent": "finalize"}

    @pytest.mark.asyncio
    async def test_llm_ask_human_decision(self, boss_with_llm, empty_blackboard):
        """LLM 决策：ask_human 需要澄清"""
        boss, mock_llm = boss_with_llm

        mock_response = MagicMock()
        mock_response.content = '{"action": "ask_human", "reason": "需求不明确，请用户补充"}'
        mock_llm.ainvoke.return_value = mock_response

        result = await boss.decide(empty_blackboard)

        assert result == {"current_agent": "human_in_the_loop"}

    # ==================== 异常处理测试 ====================

    @pytest.mark.asyncio
    async def test_llm_invalid_action(self, boss_with_llm, empty_blackboard):
        """LLM 返回无效 action 时，fallback 到 ask_human"""
        boss, mock_llm = boss_with_llm

        mock_response = MagicMock()
        mock_response.content = '{"action": "invalid_action", "reason": "test"}'
        mock_llm.ainvoke.return_value = mock_response

        result = await boss.decide(empty_blackboard)

        assert result == {"current_agent": "human_in_the_loop"}

    @pytest.mark.asyncio
    async def test_llm_invalid_target_agent(self, boss_with_llm, empty_blackboard):
        """LLM 返回无效 target_agent 时，fallback 到 ask_human"""
        boss, mock_llm = boss_with_llm

        mock_response = MagicMock()
        mock_response.content = (
            '{"action": "route", "target_agent": "unknown_agent", "reason": "test"}'
        )
        mock_llm.ainvoke.return_value = mock_response

        result = await boss.decide(empty_blackboard)

        assert result == {"current_agent": "human_in_the_loop"}

    @pytest.mark.asyncio
    async def test_llm_not_configured(self, boss, empty_blackboard):
        """LLM 未配置时，返回 ask_human"""
        result = await boss.decide(empty_blackboard)

        assert result == {"current_agent": "human_in_the_loop"}

    @pytest.mark.asyncio
    async def test_empty_task(self, boss_with_llm):
        """任务为空时，返回 ask_human"""
        boss, mock_llm = boss_with_llm
        blackboard = Blackboard(session_id="test-session", task="")

        result = await boss.decide(blackboard)

        assert result == {"current_agent": "human_in_the_loop"}
        # LLM 不应该被调用
        mock_llm.ainvoke.assert_not_called()

    @pytest.mark.asyncio
    async def test_llm_exception(self, boss_with_llm, empty_blackboard):
        """LLM 调用异常时，返回 ask_human"""
        boss, mock_llm = boss_with_llm

        mock_llm.ainvoke.side_effect = Exception("LLM 服务不可用")

        result = await boss.decide(empty_blackboard)

        assert result == {"current_agent": "human_in_the_loop"}

    @pytest.mark.asyncio
    async def test_llm_invalid_json(self, boss_with_llm, empty_blackboard):
        """LLM 返回无效 JSON 时，返回 ask_human"""
        boss, mock_llm = boss_with_llm

        mock_response = MagicMock()
        mock_response.content = "这不是JSON"
        mock_llm.ainvoke.return_value = mock_response

        result = await boss.decide(empty_blackboard)

        assert result == {"current_agent": "human_in_the_loop"}


class TestBuildStateDescription:
    """测试状态描述构建"""

    @pytest.fixture
    def boss(self):
        return EtlBoss()

    def test_empty_state(self, boss):
        """空白状态描述"""
        blackboard = Blackboard(session_id="test", task="测试任务")

        desc = boss._build_state_description(blackboard)

        assert "任务状态: 进行中" in desc
        assert "analyst_agent: 未开始" in desc
        assert "architect_agent: 未开始" in desc
        assert "developer_agent: 未开始" in desc
        assert "tester_agent: 未开始" in desc

    def test_completed_state(self, boss):
        """已完成状态"""
        blackboard = Blackboard(session_id="test", task="测试任务", is_completed=True)

        desc = boss._build_state_description(blackboard)

        assert "任务状态: 已完成" in desc

    def test_error_state(self, boss):
        """错误状态"""
        blackboard = Blackboard(
            session_id="test",
            task="测试任务",
            error="SQL 语法错误",
        )

        desc = boss._build_state_description(blackboard)

        assert "任务状态: 错误" in desc
        assert "SQL 语法错误" in desc

    def test_agent_reports(self, boss):
        """员工汇报状态"""
        blackboard = Blackboard(session_id="test", task="测试任务")
        blackboard.reports["analyst_agent"] = AgentReport(
            status="completed",
            summary="识别出3个业务步骤",
        )
        blackboard.reports["architect_agent"] = AgentReport(
            status="in_progress",
            summary="正在设计工作流",
        )

        desc = boss._build_state_description(blackboard)

        assert "analyst_agent: completed - 识别出3个业务步骤" in desc
        assert "architect_agent: in_progress - 正在设计工作流" in desc
        assert "developer_agent: 未开始" in desc

    def test_agent_ids_order_in_description(self, boss):
        """状态描述中员工顺序固定"""
        blackboard = Blackboard(session_id="test", task="测试任务")

        desc = boss._build_state_description(blackboard)

        # 检查顺序
        analyst_pos = desc.find("analyst_agent")
        architect_pos = desc.find("architect_agent")
        developer_pos = desc.find("developer_agent")
        tester_pos = desc.find("tester_agent")

        assert analyst_pos < architect_pos < developer_pos < tester_pos


class TestRecordReport:
    """测试记录员工汇报"""

    def test_record_report_with_full_agent_id(self):
        """使用完整 agent ID 记录汇报"""
        boss = EtlBoss()
        blackboard = Blackboard(session_id="test")

        boss.record_report(
            blackboard,
            agent_id="analyst_agent",
            status="completed",
            summary="分析完成",
            deliverable_ref="analysis:abc123",
        )

        assert "analyst_agent" in blackboard.reports
        report = blackboard.reports["analyst_agent"]
        assert report.status == "completed"
        assert report.summary == "分析完成"
        assert report.deliverable_ref == "analysis:abc123"


class TestPopCompletedRequest:
    """测试弹出已完成请求"""

    def test_pop_delegate_request(self):
        """弹出 delegate 请求"""
        boss = EtlBoss()
        blackboard = Blackboard(session_id="test")
        blackboard.pending_requests = [
            BlackboardRequest(
                request_id="req-1",
                kind="delegate",
                status="pending",
                created_by="analyst_agent",
                target_agent="architect_agent",
                payload={},
            )
        ]

        updated, req = boss.pop_completed_request(blackboard, "architect_agent")

        assert req is not None
        assert req.request_id == "req-1"
        assert len(updated.pending_requests) == 0
        assert "req-1" in updated.request_results

    def test_pop_wrong_target(self):
        """目标不匹配时不弹出"""
        boss = EtlBoss()
        blackboard = Blackboard(session_id="test")
        blackboard.pending_requests = [
            BlackboardRequest(
                request_id="req-1",
                kind="delegate",
                status="pending",
                created_by="analyst_agent",
                target_agent="architect_agent",
                payload={},
            )
        ]

        updated, req = boss.pop_completed_request(blackboard, "developer_agent")

        assert req is None
        assert len(updated.pending_requests) == 1
