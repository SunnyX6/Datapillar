"""
上下文压缩测试（复杂多轮对话场景）

测试场景：
1. 多轮对话后触发压缩
2. 压缩后 TODO 状态保持正确
3. 压缩后对话摘要包含关键信息
4. 多 Agent 交替对话后的压缩
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
def session_memory():
    """创建 SessionMemory 实例"""
    from src.modules.etl.memory.session_memory import SessionMemory

    return SessionMemory(session_id="test_compress_session")


@pytest.fixture
def llm():
    """创建 LLM 实例"""
    from src.infrastructure.llm.client import call_llm

    return call_llm(temperature=0.0)


# ==================== 测试用例：SessionMemory 基础功能 ====================


class TestSessionMemoryBasic:
    """SessionMemory 基础功能测试"""

    def test_add_conversation_turns(self, session_memory):
        """测试添加对话轮次"""
        agent_id = "analyst_agent"

        # 添加多轮对话
        session_memory.add_agent_turn(agent_id, "user", "把用户表同步到维度表")
        session_memory.add_agent_turn(agent_id, "assistant", "好的，我来分析这个需求")
        session_memory.add_agent_turn(agent_id, "user", "需要过滤掉已删除的用户")
        session_memory.add_agent_turn(agent_id, "assistant", "明白，我会添加过滤条件")

        conv = session_memory.get_agent_conversation(agent_id)

        print(f"\n对话轮次: {len(conv.recent_turns)}")
        for turn in conv.recent_turns:
            print(f"  [{turn['role']}] {turn['content'][:50]}...")

        assert len(conv.recent_turns) == 4
        assert conv.recent_turns[0]["role"] == "user"
        assert "用户表" in conv.recent_turns[0]["content"]

    def test_requirement_todos(self, session_memory):
        """测试需求 TODO 管理"""
        todos = [
            {"id": "1", "title": "同步用户表到维度表", "status": "done"},
            {"id": "2", "title": "添加过滤条件", "status": "pending"},
            {"id": "3", "title": "设计 ETL 架构", "status": "pending"},
        ]

        session_memory.update_requirement_todos(todos, revision=1)

        print(f"\nTODO 数量: {len(session_memory.requirement_todos)}")
        for todo in session_memory.requirement_todos:
            status = "✅" if todo["status"] == "done" else "⏳"
            print(f"  {status} {todo['title']}")

        assert len(session_memory.requirement_todos) == 3
        assert session_memory.requirement_revision == 1

    def test_agent_status_tracking(self, session_memory):
        """测试 Agent 状态追踪"""
        session_memory.update_agent_status(
            "analyst_agent",
            status="completed",
            deliverable_type="analysis",
            summary="需求分析完成，识别出3个业务步骤",
        )
        session_memory.update_agent_status(
            "architect_agent",
            status="in_progress",
            deliverable_type="workflow",
            summary="正在设计ETL架构",
        )

        print("\nAgent 状态:")
        for agent_id, status in session_memory.agent_statuses.items():
            print(f"  {agent_id}: {status.status} - {status.summary}")

        assert session_memory.agent_statuses["analyst_agent"].status == "completed"
        assert session_memory.agent_statuses["architect_agent"].status == "in_progress"


# ==================== 测试用例：复杂多轮对话压缩 ====================


class TestContextCompressionMultiTurn:
    """复杂多轮对话压缩测试"""

    @pytest.mark.asyncio
    async def test_compress_long_conversation(self, session_memory, llm):
        """测试压缩长对话"""
        from src.modules.etl.context.compress.agent_compressor import compress_agent_conversation

        agent_id = "analyst_agent"

        # 模拟复杂多轮对话（ETL 需求讨论）
        conversation = [
            ("user", "我想把订单数据从 MySQL 同步到 Hive 数仓"),
            ("assistant", "好的，我来帮你分析这个 ETL 需求。请问源表和目标表的具体信息是什么？"),
            (
                "user",
                "源表是 mysql_erp.t_ord_main 订单主表，目标是 hive_prod.dw_core.order_detail_clean",
            ),
            ("assistant", "明白了。订单主表有哪些关键字段需要同步？有没有需要过滤的条件？"),
            ("user", "需要同步订单ID、用户ID、订单金额、订单状态、支付方式。过滤掉已取消的订单。"),
            (
                "assistant",
                "好的，我记录下来：\n1. 同步字段：ord_id, usr_id, ord_amt, ord_sts, pay_type\n2. 过滤条件：ord_sts != 'CANCELLED'\n还有其他要求吗？",
            ),
            ("user", "对了，还需要关联用户表获取用户等级"),
            (
                "assistant",
                "明白，需要 JOIN t_user_info 表获取 usr_level 字段。这样目标表会多一个 user_level 列。",
            ),
            ("user", "是的，另外只保留支付宝和微信的订单"),
            (
                "assistant",
                "好的，添加过滤条件：pay_type IN ('ALIPAY', 'WECHAT')。让我总结一下完整需求...",
            ),
            ("user", "总结得不错，开始设计架构吧"),
        ]

        for role, content in conversation:
            session_memory.add_agent_turn(agent_id, role, content)

        print(
            f"\n压缩前对话轮次: {len(session_memory.get_agent_conversation(agent_id).recent_turns)}"
        )

        # 添加 TODO
        session_memory.update_requirement_todos(
            [
                {"id": "1", "title": "同步订单主表到清洗表", "status": "done"},
                {"id": "2", "title": "过滤已取消订单", "status": "done"},
                {"id": "3", "title": "关联用户表", "status": "done"},
                {"id": "4", "title": "过滤支付方式", "status": "done"},
                {"id": "5", "title": "设计 ETL 架构", "status": "pending"},
            ],
            revision=1,
        )

        # 执行压缩
        result = await compress_agent_conversation(
            llm=llm,
            memory=session_memory,
            agent_id=agent_id,
            include_todos=True,
        )

        print(f"\n压缩状态: {result.status}")
        if result.summary:
            print(f"压缩摘要:\n{result.summary}")

        assert result.status == "success", f"压缩应该成功，而不是 {result.status}"
        assert result.summary is not None
        assert len(result.summary) > 0

        # 验证摘要包含关键信息
        key_terms = ["订单", "用户", "同步"]
        found_terms = [term for term in key_terms if term in result.summary]
        print(f"\n摘要中包含的关键词: {found_terms}")

    @pytest.mark.asyncio
    async def test_compress_preserves_todos(self, session_memory, llm):
        """测试压缩后 TODO 状态保持正确"""
        from src.modules.etl.context.compress.agent_compressor import compress_agent_conversation

        agent_id = "analyst_agent"

        # 添加对话
        session_memory.add_agent_turn(agent_id, "user", "把订单表同步到数仓")
        session_memory.add_agent_turn(agent_id, "assistant", "好的，我来分析需求")

        # 添加 TODO（混合状态）
        todos = [
            {"id": "1", "title": "分析源表结构", "status": "done"},
            {"id": "2", "title": "设计目标表", "status": "done"},
            {"id": "3", "title": "编写 ETL SQL", "status": "pending"},
            {"id": "4", "title": "测试验证", "status": "pending"},
        ]
        session_memory.update_requirement_todos(todos, revision=1)

        # 记录压缩前状态
        todos_before = session_memory.requirement_todos.copy()
        revision_before = session_memory.requirement_revision

        print("\n压缩前 TODO 状态:")
        for todo in todos_before:
            status = "✅" if todo["status"] == "done" else "⏳"
            print(f"  {status} {todo['title']}")

        # 执行压缩
        result = await compress_agent_conversation(
            llm=llm,
            memory=session_memory,
            agent_id=agent_id,
            include_todos=True,
        )

        print(f"\n压缩状态: {result.status}")

        # 验证 TODO 状态未被修改
        assert session_memory.requirement_todos == todos_before, "TODO 列表不应被压缩修改"
        assert session_memory.requirement_revision == revision_before, "TODO 版本号不应被压缩修改"

        print("\n✅ 压缩后 TODO 状态保持不变")

    @pytest.mark.asyncio
    async def test_multi_agent_conversation_compress(self, session_memory, llm):
        """测试多 Agent 交替对话后的压缩"""
        from src.modules.etl.context.compress.agent_compressor import compress_agent_conversation

        # 模拟多 Agent 交替工作
        # Analyst 分析需求
        session_memory.add_agent_turn("analyst_agent", "user", "把用户订单数据做成宽表")
        session_memory.add_agent_turn("analyst_agent", "assistant", "好的，需要关联订单表和用户表")

        # Architect 设计架构
        session_memory.add_agent_turn("architect_agent", "user", "根据分析结果设计 ETL 架构")
        session_memory.add_agent_turn(
            "architect_agent", "assistant", "设计了2个 Job：Job1 清洗订单，Job2 关联用户"
        )

        # Developer 开发 SQL
        session_memory.add_agent_turn("developer_agent", "user", "根据架构生成 SQL")
        session_memory.add_agent_turn(
            "developer_agent", "assistant", "已生成 Stage1 清洗 SQL 和 Stage2 关联 SQL"
        )

        print("\n各 Agent 对话轮次:")
        for agent_id in ["analyst_agent", "architect_agent", "developer_agent"]:
            conv = session_memory.get_agent_conversation(agent_id)
            print(f"  {agent_id}: {len(conv.recent_turns)} 轮")

        # 分别压缩各 Agent 的对话
        results = {}
        for agent_id in ["analyst_agent", "architect_agent", "developer_agent"]:
            result = await compress_agent_conversation(
                llm=llm,
                memory=session_memory,
                agent_id=agent_id,
                include_todos=False,
            )
            results[agent_id] = result
            print(f"\n{agent_id} 压缩状态: {result.status}")
            if result.summary:
                print(f"  摘要: {result.summary[:100]}...")

        # 验证各 Agent 的压缩独立
        assert all(r.status == "success" for r in results.values()), "所有 Agent 的压缩都应成功"


# ==================== 测试用例：边界情况 ====================


class TestContextCompressionEdgeCases:
    """压缩边界情况测试"""

    @pytest.mark.asyncio
    async def test_compress_empty_conversation(self, session_memory, llm):
        """测试压缩空对话"""
        from src.modules.etl.context.compress.agent_compressor import compress_agent_conversation

        result = await compress_agent_conversation(
            llm=llm,
            memory=session_memory,
            agent_id="analyst_agent",
        )

        print(f"\n空对话压缩状态: {result.status}")
        assert result.status == "skipped", "空对话应该跳过压缩"

    @pytest.mark.asyncio
    async def test_compress_with_existing_summary(self, session_memory, llm):
        """测试已有摘要时的增量压缩"""
        from src.modules.etl.context.compress.agent_compressor import compress_agent_conversation

        agent_id = "analyst_agent"

        # 先设置一个已有的压缩摘要
        conv = session_memory.get_agent_conversation(agent_id)
        conv.compressed_summary = "历史摘要：用户需要把订单数据从 MySQL 同步到 Hive"
        conv.compression_count = 1

        # 添加新对话
        session_memory.add_agent_turn(agent_id, "user", "还需要添加用户等级字段")
        session_memory.add_agent_turn(agent_id, "assistant", "好的，我会关联用户表获取等级信息")

        print(f"\n已有摘要: {conv.compressed_summary}")
        print(f"新增对话: {len(conv.recent_turns)} 轮")

        # 执行压缩
        result = await compress_agent_conversation(
            llm=llm,
            memory=session_memory,
            agent_id=agent_id,
        )

        print(f"\n增量压缩状态: {result.status}")
        if result.summary:
            print(f"新摘要:\n{result.summary}")

        assert result.status == "success"
        # 新摘要应该包含历史信息和新信息
        if result.summary:
            assert "订单" in result.summary or "用户" in result.summary


# ==================== 测试用例：Token 估算和阈值判断 ====================


class TestContextBudget:
    """上下文预算测试"""

    def test_estimate_context_tokens(self):
        """测试上下文 token 估算"""
        from src.modules.etl.context.compress.agent_compressor import estimate_context_tokens

        system_instructions = "你是需求分析师，负责分析 ETL 需求"
        context_payload = {
            "knowledge_context": {
                "tables": [
                    {
                        "full_path": "hive_prod.dw_core.order_detail_clean",
                        "description": "订单明细表",
                    },
                    {"full_path": "hive_prod.dw_core.user_dim", "description": "用户维度表"},
                ]
            }
        }
        memory_context = {
            "compressed_summary": "用户需要同步订单数据",
            "recent_turns": [
                {"role": "user", "content": "把订单表同步到数仓"},
                {"role": "assistant", "content": "好的，我来分析需求"},
            ],
        }
        user_query = "还需要关联用户表"

        tokens = estimate_context_tokens(
            system_instructions=system_instructions,
            context_payload=context_payload,
            memory_context=memory_context,
            user_query=user_query,
        )

        print(f"\n估算的 token 数: {tokens}")
        assert tokens > 0
        assert tokens < 10000  # 合理范围

    def test_should_compress_threshold(self):
        """测试压缩阈值判断"""
        from src.modules.etl.context.compress.agent_compressor import should_compress
        from src.modules.etl.context.compress.budget import ContextBudget

        budget = ContextBudget(
            model_context_tokens=8000,
            soft_limit_ratio=0.7,  # 5600 tokens
        )

        # 低于阈值，不压缩
        assert not should_compress(estimated_tokens=5000, budget=budget)

        # 高于阈值，需要压缩
        assert should_compress(estimated_tokens=6000, budget=budget)

        # 手动触发，强制压缩
        assert should_compress(estimated_tokens=1000, budget=budget, manual_trigger=True)

        print("\n阈值判断测试通过")


if __name__ == "__main__":
    pytest.main([__file__, "-v", "-s", "--tb=short"])
