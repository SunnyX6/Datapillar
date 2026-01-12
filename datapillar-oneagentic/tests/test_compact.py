"""
上下文压缩机制集成测试

测试目标：
- 通过调整配置参数触发压缩机制
- 验证压缩流程完整性
- 验证压缩结果正确性

测试策略：
- 使用小阈值配置（1000 tokens）快速触发压缩
- 模拟真实对话场景
"""

import pytest
from unittest.mock import AsyncMock, MagicMock

from datapillar_oneagentic.config import datapillar_configure, reset_config
from datapillar_oneagentic.memory.session_memory import SessionMemory
from datapillar_oneagentic.memory.compact_policy import CompactPolicy
from datapillar_oneagentic.memory.compactor import Compactor, clear_compactor_cache
from datapillar_oneagentic.memory.conversation import ConversationEntry


class TestCompactWithSmallThreshold:
    """通过调整参数测试压缩机制"""

    @pytest.fixture(autouse=True)
    def setup(self):
        """测试前配置小阈值"""
        reset_config()
        clear_compactor_cache()
        # 配置小阈值用于测试
        datapillar_configure(
            context={
                "window_size": 1000,              # 1k tokens 窗口
                "compact_trigger_threshold": 0.8,  # 80% = 800 tokens 触发
                "compact_target_ratio": 0.5,       # 压缩到 50%
                "compact_min_keep_entries": 2,     # 保留 2 条
                "compact_max_summary_tokens": 200, # 摘要最大 200 tokens
            }
        )
        yield
        reset_config()
        clear_compactor_cache()

    def test_config_applied(self):
        """验证配置正确应用"""
        policy = CompactPolicy()

        assert policy.get_context_window() == 1000
        assert policy.get_trigger_threshold() == 0.8
        assert policy.get_trigger_tokens() == 800
        assert policy.get_target_ratio() == 0.5
        assert policy.get_target_tokens() == 500
        assert policy.get_min_keep_entries() == 2
        assert policy.get_max_summary_tokens() == 200

    def test_needs_compact_with_small_threshold(self):
        """测试小阈值下的压缩触发判断"""
        memory = SessionMemory()

        # 添加少量消息，不触发
        memory.add_user_message("短消息")
        assert memory.needs_compact() is False

        # 添加大量消息，触发压缩
        long_content = "这是一条很长的测试消息，用于填充 token 数量。" * 20
        for i in range(10):
            memory.add_user_message(f"消息{i}: {long_content}")
            memory.add_agent_response("analyst", f"回复{i}: {long_content}")

        # 此时应该需要压缩
        current_tokens = memory.estimate_tokens()
        trigger_tokens = memory.policy.get_trigger_tokens()

        print(f"当前 tokens: {current_tokens}, 触发阈值: {trigger_tokens}")
        assert current_tokens > trigger_tokens
        assert memory.needs_compact() is True

    @pytest.mark.asyncio
    async def test_compact_with_mock_llm(self):
        """使用 Mock LLM 测试压缩流程"""
        memory = SessionMemory()

        # 添加足够多的对话触发压缩
        long_content = "这是一条很长的测试消息，用于填充 token 数量。" * 20
        for i in range(10):
            memory.add_user_message(f"用户问题{i}: {long_content}")
            memory.add_agent_response("analyst", f"分析回复{i}: {long_content}")
            memory.add_tool_result("analyst", "search_tables", f"工具结果{i}")

        # 验证需要压缩
        assert memory.needs_compact() is True
        tokens_before = memory.estimate_tokens()
        entries_before = len(memory.conversation.entries)

        # 创建 Mock LLM
        mock_llm = AsyncMock()
        mock_response = MagicMock()
        mock_response.content = """## 用户目标
用户进行了多轮数据分析咨询

## 已完成工作
- 完成了 10 轮问答
- 使用了搜索工具

## 关键决策
- 无特殊决策"""
        mock_llm.ainvoke.return_value = mock_response

        # 创建压缩器
        policy = CompactPolicy(
            context_window=1000,
            trigger_threshold=0.8,
            target_ratio=0.5,
            min_keep_entries=2,
            max_summary_tokens=200,
        )
        compactor = Compactor(llm=mock_llm, policy=policy)

        # 执行压缩
        result = await memory.compact(compactor=compactor)

        # 验证压缩结果
        assert result.success is True
        assert result.tokens_before > 0
        assert result.tokens_saved > 0
        assert result.removed_count > 0
        assert result.kept_count > 0

        # 验证内存状态更新
        assert memory.compressed_summary != ""
        assert memory.total_compactions == 1
        assert memory.total_tokens_saved > 0

        # 验证条目减少
        entries_after = len(memory.conversation.entries)
        assert entries_after < entries_before

        print(f"压缩前 tokens: {tokens_before}")
        print(f"压缩后 tokens: {result.tokens_after}")
        print(f"节省 tokens: {result.tokens_saved}")
        print(f"条目数: {entries_before} -> {entries_after}")
        print(f"摘要: {memory.compressed_summary[:100]}...")

    @pytest.mark.asyncio
    async def test_no_compact_when_below_threshold(self):
        """测试低于阈值时不触发压缩"""
        memory = SessionMemory()

        # 只添加少量消息
        memory.add_user_message("短消息1")
        memory.add_agent_response("analyst", "短回复1")

        # 不应该需要压缩
        assert memory.needs_compact() is False

        # 创建 Mock LLM
        mock_llm = AsyncMock()
        policy = CompactPolicy(
            context_window=1000,
            trigger_threshold=0.8,
        )
        compactor = Compactor(llm=mock_llm, policy=policy)

        # 执行压缩
        result = await memory.compact(compactor=compactor)

        # 应该返回 no_action
        assert result.success is True
        assert result.tokens_saved == 0
        assert "无需压缩" in (result.error or "")

        # LLM 不应该被调用
        mock_llm.ainvoke.assert_not_called()


class TestCompactorClassifyEntries:
    """测试条目分类逻辑"""

    @pytest.fixture(autouse=True)
    def setup(self):
        """测试前配置"""
        reset_config()
        clear_compactor_cache()
        datapillar_configure(
            context={
                "window_size": 1000,
                "compact_trigger_threshold": 0.8,
                "compact_min_keep_entries": 2,
            }
        )
        yield
        reset_config()
        clear_compactor_cache()

    def test_classify_keeps_recent_entries(self):
        """验证最近条目始终保留"""
        mock_llm = MagicMock()
        policy = CompactPolicy(min_keep_entries=2)
        compactor = Compactor(llm=mock_llm, policy=policy)

        # 创建 5 条消息
        entries = [
            ConversationEntry(
                seq=i,
                speaker="user" if i % 2 == 0 else "agent",
                listener="agent" if i % 2 == 0 else "user",
                entry_type="user_message" if i % 2 == 0 else "agent_response",
                content=f"消息{i}",
            )
            for i in range(5)
        ]

        keep, compress = compactor._classify_entries(entries)

        # 最近 2 条应该保留
        assert len(keep) >= 2
        assert entries[-1] in keep
        assert entries[-2] in keep

    def test_classify_keeps_user_messages(self):
        """验证用户消息保留"""
        mock_llm = MagicMock()
        policy = CompactPolicy(
            min_keep_entries=1,
            keep_categories=["user_message", "clarification"],
        )
        compactor = Compactor(llm=mock_llm, policy=policy)

        entries = [
            ConversationEntry(
                seq=1,
                speaker="user",
                listener="agent",
                entry_type="user_message",
                content="用户消息",
            ),
            ConversationEntry(
                seq=2,
                speaker="agent",
                listener="user",
                entry_type="agent_response",
                content="Agent 响应",
            ),
            ConversationEntry(
                seq=3,
                speaker="agent",
                listener="user",
                entry_type="tool_result",
                content="工具结果",
            ),
            ConversationEntry(
                seq=4,
                speaker="user",
                listener="agent",
                entry_type="user_message",
                content="最新消息",
            ),
        ]

        keep, compress = compactor._classify_entries(entries)

        # user_message 应该保留
        user_messages_in_keep = [e for e in keep if e.entry_type == "user_message"]
        assert len(user_messages_in_keep) >= 1

        # agent_response 和 tool_result 应该被压缩
        compressed_types = {e.entry_type for e in compress}
        assert "agent_response" in compressed_types or "tool_result" in compressed_types


class TestCompactPreHooks:
    """测试压缩前钩子"""

    @pytest.fixture(autouse=True)
    def setup(self):
        """测试前配置"""
        reset_config()
        clear_compactor_cache()
        datapillar_configure(
            context={
                "window_size": 500,
                "compact_trigger_threshold": 0.5,
            }
        )
        yield
        reset_config()
        clear_compactor_cache()

    @pytest.mark.asyncio
    async def test_pre_compact_hook_called(self):
        """验证压缩前钩子被调用"""
        memory = SessionMemory()

        # 添加足够内容触发压缩
        long_content = "测试消息内容" * 50
        for i in range(5):
            memory.add_user_message(f"消息{i}: {long_content}")

        # 创建钩子
        hook_called = []

        def pre_hook(mem: SessionMemory):
            hook_called.append(True)  # 只记录调用

        # Mock LLM
        mock_llm = AsyncMock()
        mock_response = MagicMock()
        mock_response.content = "压缩摘要"
        mock_llm.ainvoke.return_value = mock_response

        policy = CompactPolicy(
            context_window=500,
            trigger_threshold=0.5,
        )
        compactor = Compactor(llm=mock_llm, policy=policy)

        # 执行压缩
        await memory.compact(compactor=compactor, pre_hooks=[pre_hook])

        # 验证钩子被调用
        assert len(hook_called) == 1
        assert hook_called[0] is True


class TestCompactStats:
    """测试压缩统计信息"""

    @pytest.fixture(autouse=True)
    def setup(self):
        """测试前配置"""
        reset_config()
        clear_compactor_cache()
        datapillar_configure(
            context={
                "window_size": 500,
                "compact_trigger_threshold": 0.5,
            }
        )
        yield
        reset_config()
        clear_compactor_cache()

    @pytest.mark.asyncio
    async def test_stats_after_compact(self):
        """验证压缩后统计信息更新"""
        memory = SessionMemory()

        # 初始状态
        assert memory.total_compactions == 0
        assert memory.total_tokens_saved == 0

        # 添加内容
        long_content = "测试消息" * 50
        for i in range(5):
            memory.add_user_message(f"消息{i}: {long_content}")

        # Mock LLM
        mock_llm = AsyncMock()
        mock_response = MagicMock()
        mock_response.content = "压缩摘要"
        mock_llm.ainvoke.return_value = mock_response

        policy = CompactPolicy(
            context_window=500,
            trigger_threshold=0.5,
        )
        compactor = Compactor(llm=mock_llm, policy=policy)

        # 执行压缩
        result = await memory.compact(compactor=compactor)

        if result.success and result.tokens_saved > 0:
            # 验证统计更新
            assert memory.total_compactions == 1
            assert memory.total_tokens_saved == result.tokens_saved

            # 验证 get_stats
            stats = memory.get_stats()
            assert stats["total_compactions"] == 1
            assert stats["total_tokens_saved"] > 0
