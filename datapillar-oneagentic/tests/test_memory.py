"""
记忆系统单元测试

测试模块：
- datapillar_oneagentic.memory.conversation
- datapillar_oneagentic.memory.session_memory
- datapillar_oneagentic.memory.pinned_context
- datapillar_oneagentic.memory.compact_policy
"""

import pytest

from datapillar_oneagentic.config import datapillar_configure, reset_config
from datapillar_oneagentic.memory.conversation import ConversationEntry, ConversationMemory
from datapillar_oneagentic.memory.session_memory import SessionMemory
from datapillar_oneagentic.memory.compact_policy import CompactPolicy, CompactResult


class TestConversationEntry:
    """ConversationEntry 测试"""

    def test_create_entry(self):
        """测试创建对话条目"""
        entry = ConversationEntry(
            seq=1,
            speaker="user",
            listener="agent",
            entry_type="user_message",
            content="你好",
        )

        assert entry.seq == 1
        assert entry.speaker == "user"
        assert entry.listener == "agent"
        assert entry.entry_type == "user_message"
        assert entry.content == "你好"

    def test_entry_to_display(self):
        """测试条目显示格式"""
        entry = ConversationEntry(
            seq=1,
            speaker="user",
            listener="system",
            entry_type="user_message",
            content="创建用户表",
        )

        display = entry.to_display()

        assert "[1]" in display
        assert "👤" in display
        assert "user" in display
        assert "创建用户表" in display

    def test_entry_types(self):
        """测试不同条目类型"""
        types_icons = {
            "user_message": "👤",
            "agent_response": "🤖",
            "agent_handover": "🔄",
            "clarification": "❓",
            "system_event": "⚙️",
            "tool_result": "🔧",
        }

        for entry_type, icon in types_icons.items():
            entry = ConversationEntry(
                seq=1,
                speaker="test",
                listener="test",
                entry_type=entry_type,
                content="test",
            )
            display = entry.to_display()
            assert icon in display


class TestConversationMemory:
    """ConversationMemory 测试"""

    def test_create_memory(self):
        """测试创建对话记忆"""
        memory = ConversationMemory()

        assert memory.entries == []
        assert memory.next_seq == 1

    def test_append_entry(self):
        """测试添加对话条目"""
        memory = ConversationMemory()

        entry = memory.append(
            speaker="user",
            listener="system",
            entry_type="user_message",
            content="你好",
        )

        assert len(memory.entries) == 1
        assert entry.seq == 1
        assert memory.next_seq == 2

    def test_append_multiple(self):
        """测试添加多条记录"""
        memory = ConversationMemory()

        memory.append("user", "system", "user_message", "问题1")
        memory.append("analyst", "user", "agent_response", "回答1")
        memory.append("user", "system", "user_message", "问题2")

        assert len(memory.entries) == 3
        assert memory.entries[0].seq == 1
        assert memory.entries[1].seq == 2
        assert memory.entries[2].seq == 3

    def test_get_recent(self):
        """测试获取最近记录"""
        memory = ConversationMemory()

        for i in range(10):
            memory.append("user", "system", "user_message", f"消息{i}")

        recent = memory.get_recent(limit=5)

        assert len(recent) == 5
        assert recent[0].content == "消息5"
        assert recent[4].content == "消息9"

    def test_update_agent_summary(self):
        """测试更新 Agent 摘要"""
        memory = ConversationMemory()

        memory.update_agent_summary("analyst", "分析完成")

        assert memory.agent_summaries["analyst"] == "分析完成"

    def test_estimate_tokens(self):
        """测试估算 token 数"""
        memory = ConversationMemory()

        memory.append("user", "system", "user_message", "这是一条测试消息")
        memory.append("analyst", "user", "agent_response", "这是回复消息")

        tokens = memory.estimate_tokens()

        assert tokens > 0

    def test_to_prompt(self):
        """测试生成 prompt"""
        memory = ConversationMemory()

        memory.append("user", "system", "user_message", "创建表")
        memory.append("analyst", "user", "agent_response", "好的")

        prompt = memory.to_prompt()

        assert "对话历史" in prompt
        assert "创建表" in prompt
        assert "好的" in prompt

    def test_clear(self):
        """测试清空记录"""
        memory = ConversationMemory()

        memory.append("user", "system", "user_message", "消息1")
        memory.append("user", "system", "user_message", "消息2")
        memory.update_agent_summary("analyst", "摘要")

        count = memory.clear()

        assert count == 2
        assert len(memory.entries) == 0
        assert len(memory.agent_summaries) == 0
        assert memory.next_seq == 1


class TestSessionMemory:
    """SessionMemory 测试"""

    @pytest.fixture(autouse=True)
    def setup(self):
        """测试前配置"""
        reset_config()
        datapillar_configure(
            context={
                "window_size": 100000,
                "compact_trigger_threshold": 0.95,
            }
        )
        yield
        reset_config()

    def test_create_session_memory(self):
        """测试创建会话记忆"""
        memory = SessionMemory()

        assert memory.conversation is not None
        assert memory.pinned is not None

    def test_add_user_message(self):
        """测试添加用户消息"""
        memory = SessionMemory()

        entry = memory.add_user_message("创建用户表")

        assert entry.speaker == "user"
        assert entry.entry_type == "user_message"
        assert entry.content == "创建用户表"

    def test_add_agent_response(self):
        """测试添加 Agent 响应"""
        memory = SessionMemory()

        entry = memory.add_agent_response("analyst", "好的，我来分析")

        assert entry.speaker == "analyst"
        assert entry.entry_type == "agent_response"

    def test_add_agent_handover(self):
        """测试添加 Agent 交接"""
        memory = SessionMemory()

        entry = memory.add_agent_handover("analyst", "developer", "需求已分析")

        assert entry.speaker == "analyst"
        assert entry.listener == "developer"
        assert entry.entry_type == "agent_handover"

    def test_add_clarification(self):
        """测试添加澄清问题"""
        memory = SessionMemory()

        entry = memory.add_clarification("analyst", "请确认数据源？")

        assert entry.entry_type == "clarification"

    def test_add_tool_result(self):
        """测试添加工具结果"""
        memory = SessionMemory()

        entry = memory.add_tool_result("analyst", "search_tables", "找到 5 张表")

        assert entry.entry_type == "tool_result"
        assert "search_tables" in entry.speaker

    def test_pin_decision(self):
        """测试固定决策"""
        memory = SessionMemory()

        decision = memory.pin_decision("使用 Iceberg 格式", "architect")

        assert decision.content == "使用 Iceberg 格式"
        assert decision.agent_id == "architect"
        assert len(memory.pinned.decisions) == 1

    def test_pin_constraint(self):
        """测试固定约束"""
        memory = SessionMemory()

        memory.pin_constraint("必须兼容 Hive")

        assert "必须兼容 Hive" in memory.pinned.constraints

    def test_pin_artifact(self):
        """测试固定工件引用"""
        memory = SessionMemory()

        artifact = memory.pin_artifact("sql_001", "sql", "用户宽表 SQL")

        assert artifact.ref_id == "sql_001"
        assert artifact.dtype == "sql"
        assert len(memory.pinned.artifacts) == 1

    def test_estimate_tokens(self):
        """测试估算 token 数"""
        memory = SessionMemory()

        memory.add_user_message("创建用户表")
        memory.add_agent_response("analyst", "好的")
        memory.pin_decision("使用 Iceberg", "architect")

        tokens = memory.estimate_tokens()

        assert tokens > 0

    def test_needs_compact(self):
        """测试判断是否需要压缩"""
        memory = SessionMemory()

        memory.add_user_message("短消息")

        assert memory.needs_compact() is False

    def test_to_prompt(self):
        """测试生成完整 prompt"""
        memory = SessionMemory()

        memory.add_user_message("创建用户表")
        memory.add_agent_response("analyst", "好的")
        memory.pin_decision("使用 Iceberg", "architect")

        prompt = memory.to_prompt()

        assert "创建用户表" in prompt
        assert "好的" in prompt

    def test_get_stats(self):
        """测试获取统计信息"""
        memory = SessionMemory()

        memory.add_user_message("消息1")
        memory.add_user_message("消息2")
        memory.pin_decision("决策1", "agent")
        memory.pin_constraint("约束1")

        stats = memory.get_stats()

        assert stats["total_entries"] == 2
        assert stats["total_decisions"] == 1
        assert stats["total_constraints"] == 1

    def test_to_dict_and_from_dict(self):
        """测试序列化和反序列化"""
        memory = SessionMemory()

        memory.add_user_message("测试消息")
        memory.pin_decision("测试决策", "agent")

        data = memory.to_dict()
        restored = SessionMemory.from_dict(data)

        assert len(restored.conversation.entries) == 1
        assert len(restored.pinned.decisions) == 1


class TestCompactPolicy:
    """CompactPolicy 测试"""

    @pytest.fixture(autouse=True)
    def setup(self):
        """测试前配置"""
        reset_config()
        datapillar_configure(
            context={
                "window_size": 100000,
                "compact_trigger_threshold": 0.95,
                "compact_target_ratio": 0.60,
                "compact_min_keep_entries": 5,
                "compact_max_summary_tokens": 2000,
            }
        )
        yield
        reset_config()

    def test_default_policy(self):
        """测试默认策略"""
        policy = CompactPolicy()

        assert policy.get_trigger_threshold() == 0.95
        assert policy.get_target_ratio() == 0.60
        assert policy.get_min_keep_entries() == 5
        assert policy.get_max_summary_tokens() == 2000

    def test_override_trigger_threshold(self):
        """测试覆盖触发阈值"""
        policy = CompactPolicy(trigger_threshold=0.8)

        assert policy.get_trigger_threshold() == 0.8

    def test_override_context_window(self):
        """测试覆盖上下文窗口"""
        policy = CompactPolicy(context_window=50000)

        assert policy.get_context_window() == 50000

    def test_get_trigger_tokens(self):
        """测试获取触发 token 数"""
        policy = CompactPolicy(
            context_window=100000,
            trigger_threshold=0.9,
        )

        assert policy.get_trigger_tokens() == 90000

    def test_get_target_tokens(self):
        """测试获取目标 token 数"""
        policy = CompactPolicy(
            context_window=100000,
            target_ratio=0.5,
        )

        assert policy.get_target_tokens() == 50000

    def test_should_keep_entry(self):
        """测试判断是否保留条目"""
        policy = CompactPolicy()

        assert policy.should_keep_entry("user_message") is True
        assert policy.should_keep_entry("clarification") is True
        assert policy.should_keep_entry("agent_response") is False
        assert policy.should_keep_entry("tool_result") is False

    def test_should_compress_entry(self):
        """测试判断是否压缩条目"""
        policy = CompactPolicy()

        assert policy.should_compress_entry("agent_response") is True
        assert policy.should_compress_entry("tool_result") is True
        assert policy.should_compress_entry("user_message") is False

    def test_custom_keep_categories(self):
        """测试自定义保留类别"""
        policy = CompactPolicy(
            keep_categories=["user_message", "agent_response"],
        )

        assert policy.should_keep_entry("user_message") is True
        assert policy.should_keep_entry("agent_response") is True
        assert policy.should_keep_entry("tool_result") is False


class TestCompactResult:
    """CompactResult 测试"""

    def test_create_success_result(self):
        """测试创建成功结果"""
        result = CompactResult(
            success=True,
            summary="压缩摘要",
            kept_count=5,
            removed_count=10,
            tokens_before=5000,
            tokens_after=2000,
            tokens_saved=3000,
        )

        assert result.success is True
        assert result.summary == "压缩摘要"
        assert result.tokens_saved == 3000

    def test_create_failed_result(self):
        """测试创建失败结果"""
        result = CompactResult.failed("压缩失败")

        assert result.success is False
        assert result.error == "压缩失败"

    def test_create_no_action_result(self):
        """测试创建无操作结果"""
        result = CompactResult.no_action("无需压缩")

        assert result.success is True
        assert result.error == "无需压缩"
