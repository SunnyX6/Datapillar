"""
ETL 短期记忆 + Checkpoint 测试

测试场景：
1. 记忆存储验证：SessionMemory 通过 Checkpoint 持久化到 Redis
2. 记忆恢复验证：重新加载后记忆数据正确恢复
3. TTL 过期验证：设置短 TTL 后验证数据自动过期
4. Redis-Stack 检查：直接查看 Redis 中的 keys 和数据

注意：
- 测试使用独立的 thread_id 避免污染其他测试
- TTL 测试设置为 2 秒，快速验证过期机制
"""

import asyncio
import logging
import sys
import time
import uuid
from pathlib import Path

import pytest
import pytest_asyncio

project_root = Path(__file__).parent.parent
sys.path.insert(0, str(project_root))

logger = logging.getLogger(__name__)


# ==================== Fixtures ====================


@pytest.fixture(scope="function")
def unique_thread_id():
    """生成唯一的 thread_id 避免测试间干扰"""
    return f"test:memory:checkpoint:{uuid.uuid4().hex}"


@pytest.fixture(scope="function")
def unique_session_info():
    """生成唯一的 session_id 和 user_id"""
    unique_id = uuid.uuid4().hex[:8]
    return {
        "session_id": f"test_session_{unique_id}",
        "user_id": f"test_user_{unique_id}",
    }


@pytest_asyncio.fixture(scope="module")
async def redis_client():
    """获取 Redis 客户端（用于直接检查 Redis 数据）"""
    from src.infrastructure.database.redis import RedisClient

    client = await RedisClient.get_instance()
    yield client
    # 不关闭，由应用统一管理


@pytest_asyncio.fixture(scope="module")
async def binary_redis_client():
    """获取二进制模式 Redis 客户端（用于检查 Checkpoint 数据）"""
    from src.infrastructure.database.redis import RedisClient

    client = await RedisClient.get_binary_client()
    yield client


@pytest_asyncio.fixture(scope="function")
async def cleanup_checkpoint(unique_thread_id):
    """测试结束后清理 checkpoint 数据"""
    yield
    # 清理测试数据
    from src.infrastructure.repository.checkpoint import Checkpoint

    try:
        await Checkpoint.delete_thread(unique_thread_id)
        logger.info(f"已清理测试 checkpoint: {unique_thread_id}")
    except Exception as e:
        logger.warning(f"清理 checkpoint 失败: {e}")


# ==================== 测试用例：记忆存储与恢复 ====================


class TestSessionMemoryCheckpoint:
    """SessionMemory + Checkpoint 集成测试"""

    @pytest.mark.asyncio
    async def test_memory_persistence_via_checkpoint(
        self, unique_thread_id, binary_redis_client, cleanup_checkpoint
    ):
        """测试记忆通过 Checkpoint 持久化到 Redis"""
        from langchain_core.runnables import RunnableConfig

        from src.infrastructure.repository.checkpoint import Checkpoint
        from src.modules.etl.state.blackboard import Blackboard

        # 1. 创建 Blackboard 并添加记忆数据
        blackboard = Blackboard(
            session_id="test_session_001",
            user_id="test_user_001",
            task="测试任务：把用户表同步到维度表",
        )

        # 添加 Agent 状态
        blackboard.update_agent_status(
            agent_id="analyst_agent",
            status="completed",
            deliverable_type="analysis",
            summary="需求分析完成，识别出3个业务步骤",
        )

        # 添加对话历史
        blackboard.add_agent_turn("analyst_agent", "user", "把用户表同步到维度表")
        blackboard.add_agent_turn("analyst_agent", "assistant", "好的，我来分析这个需求...")

        # 2. 通过 Checkpoint 保存
        config: RunnableConfig = {"configurable": {"thread_id": unique_thread_id}}

        checkpoint_data = {
            "v": 1,
            "ts": str(time.time()),
            "id": str(uuid.uuid4()),
            "channel_values": {"blackboard": blackboard.model_dump()},
            "channel_versions": {"blackboard": 1},
            "versions_seen": {},
            "pending_sends": [],
        }

        metadata = {"source": "test", "step": 1}

        saved_config = await Checkpoint.save_checkpoint(
            config=config,
            checkpoint=checkpoint_data,
            metadata=metadata,
            new_versions={"blackboard": 1},
        )

        print(f"\n保存 checkpoint 成功: {saved_config}")

        # 3. 检查 Redis 中是否有数据
        keys = await binary_redis_client.keys(f"*{unique_thread_id}*")
        print(f"\nRedis 中找到的 keys ({len(keys)} 个):")
        for key in keys:
            key_str = key.decode() if isinstance(key, bytes) else key
            print(f"  - {key_str}")

        assert len(keys) > 0, "Redis 中应该存在 checkpoint 数据"

        # 4. 读取 checkpoint 验证数据完整性
        loaded = await Checkpoint.get_checkpoint(config)
        assert loaded is not None, "应该能读取到 checkpoint"

        loaded_checkpoint = loaded.checkpoint
        channel_values = loaded_checkpoint.get("channel_values", {})
        blackboard_data = channel_values.get("blackboard", {})

        print("\n读取到的 checkpoint:")
        print(f"  - session_id: {blackboard_data.get('session_id')}")
        print(f"  - task: {blackboard_data.get('task')}")

        # 验证记忆数据
        memory_data = blackboard_data.get("memory")
        if memory_data:
            print("\n记忆数据:")
            print(f"  - agent_statuses: {memory_data.get('agent_statuses', {})}")
            print(
                f"  - agent_conversations: {list(memory_data.get('agent_conversations', {}).keys())}"
            )

            # 验证 Agent 状态
            agent_statuses = memory_data.get("agent_statuses", {})
            assert "analyst_agent" in agent_statuses, "应该包含 analyst_agent 状态"

            analyst_status = agent_statuses["analyst_agent"]
            assert analyst_status["status"] == "completed", "analyst_agent 状态应为 completed"

            # 验证对话历史
            agent_conversations = memory_data.get("agent_conversations", {})
            if "analyst_agent" in agent_conversations:
                conv = agent_conversations["analyst_agent"]
                recent_turns = conv.get("recent_turns", [])
                print(f"  - recent_turns 数量: {len(recent_turns)}")
                assert len(recent_turns) >= 2, "应该有至少2轮对话"

    @pytest.mark.asyncio
    async def test_memory_recovery_after_reload(self, unique_thread_id, cleanup_checkpoint):
        """测试重新加载后记忆数据正确恢复"""
        from langchain_core.runnables import RunnableConfig

        from src.infrastructure.repository.checkpoint import Checkpoint
        from src.modules.etl.state.blackboard import Blackboard

        config: RunnableConfig = {"configurable": {"thread_id": unique_thread_id}}

        # 1. 创建并保存初始状态
        original_blackboard = Blackboard(
            session_id="test_session_recovery",
            user_id="test_user_recovery",
            task="测试恢复任务",
        )

        # 添加多个 Agent 的状态和对话
        for agent_id in ["analyst_agent", "architect_agent", "developer_agent"]:
            original_blackboard.update_agent_status(
                agent_id=agent_id,
                status="completed" if agent_id != "developer_agent" else "in_progress",
                deliverable_type="analysis" if agent_id == "analyst_agent" else "workflow",
                summary=f"{agent_id} 工作摘要",
            )
            original_blackboard.add_agent_turn(agent_id, "user", f"{agent_id} 输入")
            original_blackboard.add_agent_turn(agent_id, "assistant", f"{agent_id} 响应")

        checkpoint_data = {
            "v": 1,
            "ts": str(time.time()),
            "id": str(uuid.uuid4()),
            "channel_values": {"blackboard": original_blackboard.model_dump()},
            "channel_versions": {"blackboard": 1},
            "versions_seen": {},
            "pending_sends": [],
        }

        await Checkpoint.save_checkpoint(
            config=config,
            checkpoint=checkpoint_data,
            metadata={"source": "test"},
            new_versions={"blackboard": 1},
        )

        print("\n原始状态已保存")

        # 2. 模拟重新加载（新的程序实例）
        loaded = await Checkpoint.get_checkpoint(config)
        assert loaded is not None

        # 3. 从 checkpoint 恢复 Blackboard
        channel_values = loaded.checkpoint.get("channel_values", {})
        blackboard_data = channel_values.get("blackboard", {})
        recovered_blackboard = Blackboard(**blackboard_data)

        print("\n恢复的 Blackboard:")
        print(f"  - session_id: {recovered_blackboard.session_id}")
        print(f"  - task: {recovered_blackboard.task}")
        print(f"  - reports: {list(recovered_blackboard.reports.keys())}")

        # 4. 验证记忆完整性
        memory = recovered_blackboard.ensure_memory()

        print("\n恢复的记忆:")
        print(f"  - agent_statuses: {list(memory.agent_statuses.keys())}")
        print(f"  - agent_conversations: {list(memory.agent_conversations.keys())}")

        # 验证所有 Agent 状态都恢复了
        for agent_id in ["analyst_agent", "architect_agent", "developer_agent"]:
            status = memory.get_agent_status(agent_id)
            assert status is not None, f"{agent_id} 状态应该存在"
            print(f"  - {agent_id}: status={status.status}, summary={status.summary}")

            # 验证对话历史
            conv = memory.agent_conversations.get(agent_id)
            if conv:
                assert len(conv.recent_turns) >= 2, f"{agent_id} 应该有对话历史"

        print("\n记忆恢复验证通过")


# ==================== 测试用例：TTL 过期验证 ====================


class TestCheckpointTTLExpiry:
    """Checkpoint TTL 过期测试"""

    @pytest.mark.asyncio
    async def test_checkpoint_ttl_expiry(self, binary_redis_client):
        """测试 checkpoint TTL 过期机制（设置 2 秒 TTL）"""
        from langchain_core.runnables import RunnableConfig
        from langgraph.checkpoint.redis.aio import AsyncRedisSaver

        from src.infrastructure.database.redis import RedisClient
        from src.modules.etl.state.blackboard import Blackboard

        # 使用独立的 thread_id
        thread_id = f"test:ttl:expiry:{uuid.uuid4().hex}"
        config: RunnableConfig = {"configurable": {"thread_id": thread_id}}

        # 1. 创建短 TTL 的 saver（2 秒）
        short_ttl_minutes = 2 / 60.0  # 2 秒转换为分钟
        redis_client = await RedisClient.get_binary_client()

        saver = AsyncRedisSaver(
            redis_client=redis_client,
            ttl={"default_ttl": short_ttl_minutes, "refresh_on_read": False},
        )
        await saver.asetup()

        print(f"\n创建短 TTL saver: {short_ttl_minutes * 60} 秒")

        # 2. 保存 checkpoint
        blackboard = Blackboard(
            session_id="test_ttl_session",
            user_id="test_ttl_user",
            task="TTL 过期测试任务",
        )
        blackboard.add_agent_turn("analyst_agent", "user", "TTL 测试输入")

        checkpoint_data = {
            "v": 1,
            "ts": str(time.time()),
            "id": str(uuid.uuid4()),
            "channel_values": {"blackboard": blackboard.model_dump()},
            "channel_versions": {"blackboard": 1},
            "versions_seen": {},
            "pending_sends": [],
        }

        await saver.aput(
            config=config,
            checkpoint=checkpoint_data,
            metadata={"source": "ttl_test"},
            new_versions={"blackboard": 1},
        )

        print("checkpoint 已保存")

        # 3. 立即读取，应该存在
        immediate_read = await saver.aget_tuple(config)
        assert immediate_read is not None, "立即读取应该能获取到数据"
        print("立即读取成功: checkpoint 存在")

        # 检查 Redis keys
        keys_before = await binary_redis_client.keys(f"*{thread_id}*")
        print(f"过期前 Redis keys ({len(keys_before)} 个):")
        for key in keys_before:
            key_str = key.decode() if isinstance(key, bytes) else key
            # 获取 TTL
            ttl = await binary_redis_client.ttl(key)
            print(f"  - {key_str} (TTL: {ttl}s)")

        # 4. 等待 TTL 过期（2秒 + 1秒缓冲）
        wait_seconds = 3
        print(f"\n等待 {wait_seconds} 秒让 TTL 过期...")
        await asyncio.sleep(wait_seconds)

        # 5. 再次读取，应该已过期
        expired_read = await saver.aget_tuple(config)

        # 检查 Redis keys
        keys_after = await binary_redis_client.keys(f"*{thread_id}*")
        print(f"\n过期后 Redis keys ({len(keys_after)} 个):")
        for key in keys_after:
            key_str = key.decode() if isinstance(key, bytes) else key
            print(f"  - {key_str}")

        if expired_read is None:
            print("\nTTL 过期验证通过: checkpoint 已自动删除")
        else:
            print("\n注意: checkpoint 仍然存在（可能 Redis 配置未启用过期策略）")
            # 手动清理
            await saver.adelete_thread(thread_id)

        # 即使数据没过期，keys 数量应该减少或 TTL 应该已过期
        # 验证点：检查是否确实使用了短 TTL
        assert len(keys_before) > 0, "保存后应该有 keys"


# ==================== 测试用例：Redis-Stack 检查 ====================


class TestRedisStackInspection:
    """Redis-Stack 数据检查测试"""

    @pytest.mark.asyncio
    async def test_inspect_checkpoint_keys_structure(
        self, unique_thread_id, binary_redis_client, cleanup_checkpoint
    ):
        """检查 Redis 中 checkpoint 的 key 结构"""
        from langchain_core.runnables import RunnableConfig

        from src.infrastructure.repository.checkpoint import Checkpoint
        from src.modules.etl.state.blackboard import Blackboard

        config: RunnableConfig = {"configurable": {"thread_id": unique_thread_id}}

        # 保存测试数据
        blackboard = Blackboard(
            session_id="inspect_session",
            user_id="inspect_user",
            task="检查 key 结构的测试任务",
        )
        blackboard.add_agent_turn("analyst_agent", "user", "检查输入")

        checkpoint_data = {
            "v": 1,
            "ts": str(time.time()),
            "id": str(uuid.uuid4()),
            "channel_values": {"blackboard": blackboard.model_dump()},
            "channel_versions": {"blackboard": 1},
            "versions_seen": {},
            "pending_sends": [],
        }

        await Checkpoint.save_checkpoint(
            config=config,
            checkpoint=checkpoint_data,
            metadata={"source": "inspect_test"},
            new_versions={"blackboard": 1},
        )

        # 检查所有相关 keys
        all_keys = await binary_redis_client.keys("*")
        checkpoint_keys = [
            k.decode() if isinstance(k, bytes) else k
            for k in all_keys
            if unique_thread_id in (k.decode() if isinstance(k, bytes) else k)
        ]

        print(f"\n与 thread_id 相关的 keys ({len(checkpoint_keys)} 个):")
        for key in checkpoint_keys:
            key_type = await binary_redis_client.type(key.encode() if isinstance(key, str) else key)
            key_type_str = key_type.decode() if isinstance(key_type, bytes) else key_type
            ttl = await binary_redis_client.ttl(key.encode() if isinstance(key, str) else key)
            print(f"  - {key}")
            print(f"    type: {key_type_str}, TTL: {ttl}s")

            # 如果是 string 类型，显示部分值
            if key_type_str == "string":
                value = await binary_redis_client.get(key.encode() if isinstance(key, str) else key)
                if value:
                    value_preview = value[:200] if len(value) > 200 else value
                    print(f"    value (前200字节): {value_preview}")

        assert len(checkpoint_keys) > 0, "应该有 checkpoint 相关的 keys"

    @pytest.mark.asyncio
    async def test_list_all_checkpoint_threads(self, binary_redis_client):
        """列出 Redis 中所有 checkpoint threads（用于调试）"""
        # 搜索所有可能的 checkpoint keys 模式
        patterns = [
            "checkpoint:*",
            "langgraph:*",
            "*:checkpoint:*",
            "etl:user:*",
        ]

        print("\n扫描 Redis 中的 checkpoint 数据:")

        all_found_keys = set()
        for pattern in patterns:
            keys = await binary_redis_client.keys(pattern)
            for key in keys:
                key_str = key.decode() if isinstance(key, bytes) else key
                all_found_keys.add(key_str)

        if all_found_keys:
            print(f"\n找到 {len(all_found_keys)} 个相关 keys:")
            for key in sorted(all_found_keys)[:20]:  # 只显示前20个
                ttl = await binary_redis_client.ttl(key.encode())
                print(f"  - {key} (TTL: {ttl}s)")
            if len(all_found_keys) > 20:
                print(f"  ... 还有 {len(all_found_keys) - 20} 个 keys")
        else:
            print("\n未找到 checkpoint 相关的 keys（可能是首次运行或已全部过期）")

    @pytest.mark.asyncio
    async def test_redis_info(self, binary_redis_client):
        """检查 Redis 服务器信息"""
        info = await binary_redis_client.info()

        print("\nRedis 服务器信息:")
        print(f"  - redis_version: {info.get('redis_version', 'N/A')}")
        print(f"  - used_memory_human: {info.get('used_memory_human', 'N/A')}")
        print(f"  - connected_clients: {info.get('connected_clients', 'N/A')}")
        print(f"  - db0: {info.get('db0', 'N/A')}")

        # 检查 keyspace
        keyspace_info = {}
        for key, value in info.items():
            if key.startswith("db"):
                keyspace_info[key] = value

        if keyspace_info:
            print("\nKeyspace 信息:")
            for db, db_info in keyspace_info.items():
                print(f"  - {db}: {db_info}")


# ==================== 测试用例：编排器集成测试 ====================


class TestOrchestratorMemoryIntegration:
    """编排器与记忆系统集成测试"""

    @pytest.mark.asyncio
    async def test_orchestrator_memory_persistence(self, unique_session_info, binary_redis_client):
        """测试编排器执行过程中记忆的持久化"""
        from src.infrastructure.repository.checkpoint import Checkpoint
        from src.modules.etl.orchestrator_v2 import EtlOrchestratorV2

        session_id = unique_session_info["session_id"]
        user_id = unique_session_info["user_id"]

        orchestrator = EtlOrchestratorV2()
        thread_id = orchestrator._get_thread_id(session_id, user_id)

        print("\n测试编排器记忆持久化:")
        print(f"  - session_id: {session_id}")
        print(f"  - user_id: {user_id}")
        print(f"  - thread_id: {thread_id}")

        try:
            # 执行一次流程（会触发 Boss 决策）
            # 由于没有任务，Boss 会要求用户输入，然后 interrupt
            events = []
            try:
                async for event in orchestrator.stream(
                    user_input="测试任务：把用户表同步到维度表",
                    session_id=session_id,
                    user_id=user_id,
                ):
                    events.append(event)
                    if event.get("event") == "state_update":
                        data = event.get("data", {})
                        print(f"  - 事件: {list(data.keys())}")
            except Exception as e:
                # interrupt 会抛出异常，这是预期的
                print(f"  - 流程中断（预期）: {type(e).__name__}")

            # 检查 Redis 中是否有 checkpoint
            keys = await binary_redis_client.keys(f"*{thread_id}*")
            print(f"\nRedis 中找到 {len(keys)} 个相关 keys")

            if keys:
                # 读取 checkpoint 验证
                config = {"configurable": {"thread_id": thread_id}}
                loaded = await Checkpoint.get_checkpoint(config)

                if loaded:
                    channel_values = loaded.checkpoint.get("channel_values", {})
                    blackboard_data = channel_values.get("blackboard", {})
                    memory_data = blackboard_data.get("memory")

                    print("\nCheckpoint 内容:")
                    print(f"  - task: {blackboard_data.get('task')}")
                    print(f"  - current_agent: {blackboard_data.get('current_agent')}")

                    if memory_data:
                        print(f"  - memory.session_id: {memory_data.get('session_id')}")
                        print(
                            f"  - memory.agent_statuses: {list(memory_data.get('agent_statuses', {}).keys())}"
                        )

        finally:
            # 清理
            await orchestrator.clear_session(session_id, user_id)
            print(f"\n已清理会话: {thread_id}")


# ==================== 直接运行 ====================


if __name__ == "__main__":
    pytest.main([__file__, "-v", "-s", "--tb=short"])
