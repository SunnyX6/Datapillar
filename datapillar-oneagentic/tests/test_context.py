"""
Context 模块测试

测试：
- SessionMemory 基本操作
- Timeline 事件记录
- ContextBuilder 从 Blackboard 恢复和序列化
"""

import pytest

from datapillar_oneagentic.context import (
    ContextBuilder,
    SessionMemory,
    Timeline,
    TimelineEntry,
    EventType,
    CheckpointType,
)
from datapillar_oneagentic.state.blackboard import Blackboard, create_blackboard


class TestSessionMemory:
    """SessionMemory 测试"""

    def test_create_empty(self):
        """创建空的 SessionMemory"""
        memory = SessionMemory()
        assert memory.conversation is not None
        assert memory.pinned is not None
        assert len(memory.conversation.entries) == 0

    def test_add_messages(self):
        """添加消息"""
        memory = SessionMemory()

        entry1 = memory.add_user_message("你好")
        assert entry1.content == "你好"
        assert entry1.entry_type == "user_message"

        entry2 = memory.add_agent_response("analyst", "你好，有什么可以帮助你的？")
        assert entry2.content == "你好，有什么可以帮助你的？"
        assert entry2.entry_type == "agent_response"

        assert len(memory.conversation.entries) == 2

    def test_pin_decision(self):
        """固定决策"""
        memory = SessionMemory()
        decision = memory.pin_decision("使用 Iceberg 格式存储", "architect")

        assert decision.content == "使用 Iceberg 格式存储"
        assert decision.agent_id == "architect"
        assert len(memory.pinned.decisions) == 1

    def test_pin_constraint(self):
        """固定约束"""
        memory = SessionMemory()
        memory.pin_constraint("必须兼容现有 Hive 表")

        assert len(memory.pinned.constraints) == 1
        assert "必须兼容现有 Hive 表" in memory.pinned.constraints

    def test_serialization(self):
        """序列化和反序列化"""
        memory = SessionMemory()
        memory.add_user_message("测试消息")
        memory.pin_decision("测试决策", "test_agent")

        # 序列化
        data = memory.to_dict()

        # 反序列化
        restored = SessionMemory.from_dict(data)

        assert len(restored.conversation.entries) == 1
        assert len(restored.pinned.decisions) == 1


class TestTimeline:
    """Timeline 测试"""

    def test_create_empty(self):
        """创建空的 Timeline"""
        timeline = Timeline()
        assert len(timeline.entries) == 0
        assert timeline.next_seq == 1

    def test_add_entry(self):
        """添加事件"""
        timeline = Timeline()

        entry = timeline.add_entry(
            event_type=EventType.USER_MESSAGE,
            content="用户发送消息",
        )

        assert entry.event_type == EventType.USER_MESSAGE
        assert entry.content == "用户发送消息"
        assert entry.seq == 1
        assert len(timeline.entries) == 1
        assert timeline.next_seq == 2

    def test_add_checkpoint(self):
        """添加检查点"""
        timeline = Timeline()

        entry = timeline.add_checkpoint(
            checkpoint_id="cp_test_001",
            content="测试检查点",
            checkpoint_type=CheckpointType.AUTO,
        )

        assert entry.is_checkpoint
        assert entry.checkpoint_id == "cp_test_001"
        assert "cp_test_001" in timeline.checkpoint_ids
        assert timeline.current_checkpoint_id == "cp_test_001"

    def test_get_checkpoint_entries(self):
        """获取检查点事件"""
        timeline = Timeline()

        timeline.add_entry(EventType.USER_MESSAGE, "消息1")
        timeline.add_checkpoint("cp_001", "检查点1")
        timeline.add_entry(EventType.AGENT_END, "消息2")
        timeline.add_checkpoint("cp_002", "检查点2")

        checkpoints = timeline.get_checkpoint_entries()
        assert len(checkpoints) == 2
        assert checkpoints[0].checkpoint_id == "cp_001"
        assert checkpoints[1].checkpoint_id == "cp_002"

    def test_truncate_to_checkpoint(self):
        """截断到检查点"""
        timeline = Timeline()

        timeline.add_entry(EventType.USER_MESSAGE, "消息1")
        timeline.add_checkpoint("cp_001", "检查点1")
        timeline.add_entry(EventType.AGENT_END, "消息2")
        timeline.add_entry(EventType.AGENT_END, "消息3")

        removed = timeline.truncate_to_checkpoint("cp_001")

        assert removed == 2
        assert len(timeline.entries) == 2
        assert timeline.current_checkpoint_id == "cp_001"

    def test_serialization(self):
        """序列化和反序列化"""
        timeline = Timeline()
        timeline.add_entry(EventType.USER_MESSAGE, "测试消息")
        timeline.add_checkpoint("cp_001", "测试检查点")

        # 序列化
        data = timeline.to_dict()

        # 反序列化
        restored = Timeline.from_dict(data)

        assert len(restored.entries) == 2
        assert restored.checkpoint_ids == ["cp_001"]


class TestContextBuilder:
    """ContextBuilder 测试"""

    def test_from_empty_state(self):
        """从空状态创建"""
        state = create_blackboard(
            session_id="test_session",
            team_id="test_team",
            user_id="test_user",
        )

        builder = ContextBuilder.from_state(state)

        assert builder.session_id == "test_session"
        assert builder.team_id == "test_team"
        assert builder.user_id == "test_user"
        assert builder.memory is not None
        assert builder.timeline is not None

    def test_from_existing_state(self):
        """从现有状态恢复"""
        # 准备有数据的状态
        memory = SessionMemory()
        memory.add_user_message("历史消息")
        memory.pin_decision("历史决策", "analyst")

        timeline = Timeline()
        timeline.add_entry(EventType.USER_MESSAGE, "历史事件")

        state = create_blackboard(
            session_id="test_session",
            team_id="test_team",
            user_id="test_user",
        )
        state["memory"] = memory.to_dict()
        state["timeline"] = timeline.to_dict()

        # 恢复
        builder = ContextBuilder.from_state(state)

        assert len(builder.memory.conversation.entries) == 1
        assert len(builder.memory.pinned.decisions) == 1
        assert len(builder.timeline.entries) == 1

    def test_add_operations(self):
        """测试添加操作"""
        state = create_blackboard(
            session_id="test_session",
            team_id="test_team",
            user_id="test_user",
        )
        builder = ContextBuilder.from_state(state)

        # 添加用户消息
        builder.add_user_message("用户输入")
        assert len(builder.memory.conversation.entries) == 1
        assert len(builder.timeline.entries) == 1  # 同时记录到 timeline

        # 添加 Agent 响应
        builder.add_agent_response("analyst", "Agent 响应")
        assert len(builder.memory.conversation.entries) == 2
        assert len(builder.timeline.entries) == 2

        # 固定决策
        builder.pin_decision("重要决策", "architect")
        assert len(builder.memory.pinned.decisions) == 1
        assert len(builder.timeline.entries) == 3

    def test_to_state_update(self):
        """测试生成状态更新"""
        state = create_blackboard(
            session_id="test_session",
            team_id="test_team",
            user_id="test_user",
        )
        builder = ContextBuilder.from_state(state)
        builder.add_user_message("测试消息")
        builder.pin_decision("测试决策", "test_agent")

        # 生成更新
        update = builder.to_state_update()

        assert "memory" in update
        assert "timeline" in update

        # 验证可以用于恢复
        state["memory"] = update["memory"]
        state["timeline"] = update["timeline"]

        restored = ContextBuilder.from_state(state)
        assert len(restored.memory.conversation.entries) == 1
        assert len(restored.memory.pinned.decisions) == 1

    def test_checkpoint_operations(self):
        """测试检查点操作"""
        state = create_blackboard(
            session_id="test_session",
            team_id="test_team",
            user_id="test_user",
        )
        builder = ContextBuilder.from_state(state)

        # 创建检查点
        cp_id = builder.create_checkpoint("测试检查点", checkpoint_type=CheckpointType.AUTO)

        assert cp_id.startswith("cp_aut_")
        assert builder.get_latest_checkpoint_id() == cp_id
        assert len(builder.get_checkpoints()) == 1

    def test_to_prompt(self):
        """测试生成 prompt"""
        state = create_blackboard(
            session_id="test_session",
            team_id="test_team",
            user_id="test_user",
        )
        builder = ContextBuilder.from_state(state)
        builder.add_user_message("用户问题")
        builder.add_agent_response("analyst", "Agent 回答")
        builder.pin_decision("关键决策", "architect")

        # 生成 prompt
        prompt = builder.to_prompt()

        assert "用户问题" in prompt or "Agent 回答" in prompt
        assert "关键决策" in prompt


class TestTimelineEntry:
    """TimelineEntry 测试"""

    def test_create_entry(self):
        """创建条目"""
        entry = TimelineEntry(
            seq=1,
            event_type=EventType.USER_MESSAGE,
            content="测试内容",
        )

        assert entry.seq == 1
        assert entry.event_type == EventType.USER_MESSAGE
        assert entry.content == "测试内容"
        assert entry.id is not None  # 自动生成
        assert entry.timestamp_ms > 0  # 自动生成

    def test_to_display(self):
        """转换为显示格式"""
        entry = TimelineEntry(
            seq=1,
            event_type=EventType.USER_MESSAGE,
            agent_id="analyst",
            content="测试内容",
        )

        display = entry.to_display()
        assert "analyst" in display
        assert "user.message" in display
        assert "测试内容" in display


class TestIntegration:
    """集成测试"""

    def test_full_workflow(self):
        """完整工作流程测试"""
        # 1. 创建 Blackboard
        state = create_blackboard(
            session_id="workflow_test",
            team_id="test_team",
            user_id="test_user",
        )

        # 2. 创建 ContextBuilder
        builder = ContextBuilder.from_state(state)

        # 3. 模拟工作流
        builder.add_user_message("帮我创建用户表")
        builder.add_agent_response("analyst", "好的，我来分析需求")
        builder.pin_decision("使用 Iceberg 格式", "architect")
        builder.create_checkpoint("分析完成")
        builder.add_agent_handover("analyst", "developer", "需求已分析，请开发")
        builder.add_agent_response("developer", "开发完成")
        builder.create_checkpoint("开发完成")

        # 4. 验证状态
        assert len(builder.memory.conversation.entries) == 4
        assert len(builder.memory.pinned.decisions) == 1
        assert len(builder.timeline.entries) == 7  # 4 memory + 2 checkpoint + 1 decision
        assert len(builder.get_checkpoints()) == 2

        # 5. 序列化并恢复
        update = builder.to_state_update()
        state["memory"] = update["memory"]
        state["timeline"] = update["timeline"]

        restored = ContextBuilder.from_state(state)

        # 6. 验证恢复后的状态
        assert len(restored.memory.conversation.entries) == 4
        assert len(restored.memory.pinned.decisions) == 1
        assert len(restored.timeline.entries) == 7
        assert len(restored.get_checkpoints()) == 2
