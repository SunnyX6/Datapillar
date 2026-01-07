"""
Worker 节点 interrupt 机制和记忆测试

测试场景：
1. 员工需要澄清时能正确 interrupt
2. 用户回复后员工能继续处理
3. 员工有独立的对话记忆
4. 员工搞不定时能正确汇报给 Boss
"""

import json
import logging
import sys
from pathlib import Path
from unittest.mock import AsyncMock, patch

import pytest

project_root = Path(__file__).parent.parent
sys.path.insert(0, str(project_root))

from src.modules.etl.context.handover import Handover
from src.modules.etl.schemas.agent_result import AgentResult
from src.modules.etl.schemas.analyst import AnalysisResult, Step
from src.modules.etl.state.blackboard import Blackboard

logger = logging.getLogger(__name__)


# ==================== Fixtures ====================


@pytest.fixture
def blackboard():
    """创建测试用 Blackboard"""
    bb = Blackboard(
        session_id="test_session_001",
        user_id="test_user",
        task="帮我把订单表同步到目标表",
    )
    return bb


@pytest.fixture
def handover():
    """创建测试用 Handover"""
    return Handover(session_id="test_session_001")


# ==================== 测试用例 ====================


class TestWorkerInterrupt:
    """Worker 节点 interrupt 机制测试"""

    @pytest.mark.asyncio
    async def test_analyst_needs_clarification_triggers_interrupt(self, blackboard, handover):
        """测试：分析师需要澄清时触发 interrupt"""
        from src.modules.etl.worker_graph import WorkerGraph, WorkerState

        # 创建 mock agent，第一次返回 needs_clarification，第二次返回 completed
        mock_agent = AsyncMock()

        # 第一次调用：需要澄清
        first_result = AgentResult.needs_clarification(
            summary="需求不够明确",
            message="我有几个问题需要确认",
            questions=["目标表是哪个？", "需要同步哪些字段？"],
        )

        # 第二次调用：完成
        second_result = AgentResult.completed(
            summary="需求分析完成",
            deliverable=AnalysisResult(
                user_query="帮我把订单表同步到目标表，目标表是 order_dim，同步所有字段",
                summary="订单表同步到 order_dim",
                steps=[
                    Step(
                        step_id="s1",
                        step_name="数据同步",
                        description="同步订单表到 order_dim",
                    )
                ],
                confidence=0.9,
            ),
            deliverable_type="analysis",
        )

        mock_agent.run = AsyncMock(side_effect=[first_result, second_result])

        # 创建 WorkerGraph 并替换 analyst_agent
        worker_graph = WorkerGraph()
        worker_graph.analyst_agent = mock_agent

        # 创建 state
        state = WorkerState(blackboard=blackboard, handover=handover)

        # Mock interrupt 函数
        with patch("src.modules.etl.worker_graph.interrupt") as mock_interrupt:
            mock_interrupt.return_value = "目标表是 order_dim，同步所有字段"

            # 调用 _analyst_node
            await worker_graph._analyst_node(state)

            # 验证 interrupt 被调用
            mock_interrupt.assert_called_once()
            interrupt_payload = mock_interrupt.call_args[0][0]
            assert interrupt_payload["type"] == "clarification"
            assert interrupt_payload["agent_id"] == "analyst_agent"
            assert "目标表是哪个？" in interrupt_payload["questions"]

            # 验证 agent.run 被调用两次
            assert mock_agent.run.call_count == 2

            # 验证第二次调用使用了用户回复
            second_call_args = mock_agent.run.call_args_list[1]
            assert second_call_args.kwargs["user_query"] == "目标表是 order_dim，同步所有字段"

        print("\n✅ 测试通过：分析师需要澄清时正确触发 interrupt")

    @pytest.mark.asyncio
    async def test_worker_memory_records_conversation(self, blackboard, handover):
        """测试：员工对话历史被正确记录"""
        from src.modules.etl.worker_graph import WorkerGraph, WorkerState

        # 创建 mock agent
        mock_agent = AsyncMock()
        mock_agent.run = AsyncMock(
            return_value=AgentResult.completed(
                summary="需求分析完成",
                deliverable=AnalysisResult(
                    user_query="同步订单表",
                    summary="订单表同步",
                    steps=[],
                    confidence=0.9,
                ),
                deliverable_type="analysis",
            )
        )

        # 创建 WorkerGraph 并替换 analyst_agent
        worker_graph = WorkerGraph()
        worker_graph.analyst_agent = mock_agent

        # 创建 state
        state = WorkerState(blackboard=blackboard, handover=handover)

        # 调用 _analyst_node
        await worker_graph._analyst_node(state)

        # 验证对话历史被记录
        agent_context = blackboard.get_agent_context("analyst_agent")
        conversation = agent_context.get("conversation", {})
        recent_turns = conversation.get("recent_turns", [])

        print(f"\n对话历史: {json.dumps(recent_turns, ensure_ascii=False, indent=2)}")

        # 应该有用户输入和助手回复
        assert len(recent_turns) >= 1, "应该记录了对话历史"

        # 检查用户输入
        user_turns = [t for t in recent_turns if t.get("role") == "user"]
        assert len(user_turns) >= 1, "应该记录了用户输入"

        print("\n✅ 测试通过：员工对话历史被正确记录")

    @pytest.mark.asyncio
    async def test_worker_memory_persists_after_interrupt(self, blackboard, handover):
        """测试：interrupt 后员工记忆持续存在"""
        from src.modules.etl.worker_graph import WorkerGraph, WorkerState

        # 创建 mock agent
        mock_agent = AsyncMock()

        first_result = AgentResult.needs_clarification(
            summary="需要澄清",
            message="请确认目标表",
            questions=["目标表是哪个？"],
        )

        second_result = AgentResult.completed(
            summary="分析完成",
            deliverable=AnalysisResult(
                user_query="同步到 order_dim",
                summary="订单表同步到 order_dim",
                steps=[],
                confidence=0.9,
            ),
            deliverable_type="analysis",
        )

        mock_agent.run = AsyncMock(side_effect=[first_result, second_result])

        # 创建 WorkerGraph
        worker_graph = WorkerGraph()
        worker_graph.analyst_agent = mock_agent

        state = WorkerState(blackboard=blackboard, handover=handover)

        with patch("src.modules.etl.worker_graph.interrupt") as mock_interrupt:
            mock_interrupt.return_value = "目标表是 order_dim"
            await worker_graph._analyst_node(state)

        # 验证对话历史包含多轮对话
        agent_context = blackboard.get_agent_context("analyst_agent")
        conversation = agent_context.get("conversation", {})
        recent_turns = conversation.get("recent_turns", [])

        print(
            f"\n对话历史（interrupt 后）: {json.dumps(recent_turns, ensure_ascii=False, indent=2)}"
        )

        # 应该记录了：
        # 1. 第一次用户输入
        # 2. 第一次助手回复（needs_clarification）
        # 3. 第二次用户输入（interrupt 返回值）
        # 4. 第二次助手回复（completed）
        user_turns = [t for t in recent_turns if t.get("role") == "user"]
        assistant_turns = [t for t in recent_turns if t.get("role") == "assistant"]

        assert len(user_turns) >= 2, f"应该记录至少 2 次用户输入，实际 {len(user_turns)}"
        assert len(assistant_turns) >= 2, f"应该记录至少 2 次助手回复，实际 {len(assistant_turns)}"

        print("\n✅ 测试通过：interrupt 后员工记忆持续存在")

    @pytest.mark.asyncio
    async def test_different_workers_have_isolated_memory(self, blackboard, handover):
        """测试：不同员工有独立的记忆"""
        # 给 analyst 添加对话
        blackboard.add_agent_turn("analyst_agent", "user", "分析订单需求")
        blackboard.add_agent_turn("analyst_agent", "assistant", "好的，我来分析")

        # 给 architect 添加对话
        blackboard.add_agent_turn("architect_agent", "user", "设计架构方案")
        blackboard.add_agent_turn("architect_agent", "assistant", "好的，我来设计")

        # 验证各自独立
        analyst_context = blackboard.get_agent_context("analyst_agent")
        architect_context = blackboard.get_agent_context("architect_agent")

        analyst_turns = analyst_context["conversation"]["recent_turns"]
        architect_turns = architect_context["conversation"]["recent_turns"]

        print(f"\nAnalyst 对话: {json.dumps(analyst_turns, ensure_ascii=False, indent=2)}")
        print(f"Architect 对话: {json.dumps(architect_turns, ensure_ascii=False, indent=2)}")

        # 验证内容不同
        analyst_content = "".join([t.get("content", "") for t in analyst_turns])
        architect_content = "".join([t.get("content", "") for t in architect_turns])

        assert "分析订单需求" in analyst_content
        assert "设计架构方案" not in analyst_content
        assert "设计架构方案" in architect_content
        assert "分析订单需求" not in architect_content

        print("\n✅ 测试通过：不同员工有独立的记忆")


class TestWorkerReportToBoss:
    """员工汇报机制测试 - 员工搞不定时回到 Boss"""

    @pytest.fixture
    def blackboard(self):
        return Blackboard(
            session_id="test_session_002",
            user_id="test_user",
            task="帮我分析一个模糊的需求",
        )

    @pytest.fixture
    def handover(self):
        return Handover(session_id="test_session_002")

    @pytest.mark.asyncio
    async def test_worker_still_needs_clarification_after_interrupt_reports_to_boss(
        self, blackboard, handover
    ):
        """测试：员工 interrupt 后还是搞不定，正确汇报给 Boss"""
        from src.modules.etl.worker_graph import WorkerGraph, WorkerState

        mock_agent = AsyncMock()

        # 第一次：需要澄清
        first_result = AgentResult.needs_clarification(
            summary="需求太模糊",
            message="你说的订单表是哪个？",
            questions=["源表是哪个？", "目标表是哪个？"],
        )

        # 第二次：用户回复后还是不行
        second_result = AgentResult.needs_clarification(
            summary="还是不明确",
            message="你说的字段我找不到",
            questions=["amount 字段在哪个表？"],
        )

        mock_agent.run = AsyncMock(side_effect=[first_result, second_result])

        worker_graph = WorkerGraph()
        worker_graph.analyst_agent = mock_agent

        state = WorkerState(blackboard=blackboard, handover=handover)

        with patch("src.modules.etl.worker_graph.interrupt") as mock_interrupt:
            mock_interrupt.return_value = "源表是 t_order，目标表是 order_dim"
            await worker_graph._analyst_node(state)

        # 验证 AgentReport 被创建，status = "needs_clarification"
        report = blackboard.reports.get("analyst_agent")
        assert report is not None, "应该创建了 AgentReport"
        assert (
            report.status == "needs_clarification"
        ), f"状态应该是 needs_clarification，实际是 {report.status}"
        assert "不明确" in report.summary, f"摘要应该包含问题描述，实际是 {report.summary}"

        print(f"\n员工汇报: status={report.status}, summary={report.summary}")
        print("✅ 测试通过：员工搞不定时正确汇报给 Boss")

    @pytest.mark.asyncio
    async def test_boss_sees_worker_report_in_state_description(self, blackboard, handover):
        """测试：Boss 能看到员工的汇报"""
        from src.modules.etl.boss import BossAgent

        # 模拟员工汇报
        from src.modules.etl.state.blackboard import AgentReport

        blackboard.reports["analyst_agent"] = AgentReport(
            status="needs_clarification",
            summary="需求太模糊，需要用户澄清源表和目标表",
            updated_at_ms=1234567890,
        )

        boss = BossAgent()
        state_desc = boss._build_state_description(blackboard)

        print(f"\nBoss 看到的状态描述:\n{state_desc}")

        # 验证 Boss 能看到员工的汇报
        assert "analyst_agent" in state_desc
        assert "needs_clarification" in state_desc
        assert "需求太模糊" in state_desc

        print("✅ 测试通过：Boss 能看到员工的汇报")

    @pytest.mark.asyncio
    async def test_worker_graph_routes_to_end_when_still_needs_clarification(
        self, blackboard, handover
    ):
        """测试：员工还是 needs_clarification 时，路由到 end（返回 Boss）"""
        from src.modules.etl.state.blackboard import AgentReport
        from src.modules.etl.worker_graph import WorkerGraph, WorkerState

        # 模拟员工汇报 needs_clarification
        blackboard.reports["analyst_agent"] = AgentReport(
            status="needs_clarification",
            summary="需求太模糊",
            updated_at_ms=1234567890,
        )

        worker_graph = WorkerGraph()
        state = WorkerState(blackboard=blackboard, handover=handover)

        # 调用路由函数
        next_node = worker_graph._route_after_worker(state)

        print(f"\n路由结果: {next_node}")

        # needs_clarification 不是 completed，所以不会推进到下一个员工
        # 应该返回 end，让控制权回到 Boss
        assert next_node == "end", f"应该路由到 end，实际是 {next_node}"

        print("✅ 测试通过：员工搞不定时路由到 end（返回 Boss）")

    @pytest.mark.asyncio
    async def test_boss_decide_by_progress_returns_none_for_needs_clarification(
        self, blackboard, handover
    ):
        """测试：Boss 的确定性路由对 needs_clarification 返回 None（需要 LLM 决策）"""
        from src.modules.etl.boss import BossAgent
        from src.modules.etl.state.blackboard import AgentReport

        # 模拟员工汇报 needs_clarification
        blackboard.reports["analyst_agent"] = AgentReport(
            status="needs_clarification",
            summary="需求太模糊",
            updated_at_ms=1234567890,
        )

        boss = BossAgent()
        next_agent = boss._decide_by_progress(blackboard)

        print(f"\nBoss 确定性路由结果: {next_agent}")

        # needs_clarification 不是 completed，所以返回 None（需要 LLM 决策）
        assert next_agent is None, f"应该返回 None，实际是 {next_agent}"

        print("✅ 测试通过：Boss 对 needs_clarification 返回 None（需要 LLM 决策）")


if __name__ == "__main__":
    pytest.main([__file__, "-v", "-s", "--tb=short"])
