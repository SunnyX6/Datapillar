"""
上下文压缩机制端到端测试

真正调用 LLM 执行压缩，验证完整流程。
"""

import pytest

from datapillar_oneagentic.config import datapillar_configure, reset_config
from datapillar_oneagentic.memory.session_memory import SessionMemory
from datapillar_oneagentic.memory.compact_policy import CompactPolicy
from datapillar_oneagentic.memory.compactor import Compactor, clear_compactor_cache, get_compactor
from datapillar_oneagentic.providers.llm import call_llm
from datapillar_oneagentic.todo.todo_list import AgentTodoList


# GLM 配置
GLM_API_KEY = "da90d1098b0d4126848881f56ee2197c.B77DUfAuh4To29o7"
GLM_BASE_URL = "https://open.bigmodel.cn/api/paas/v4"
GLM_MODEL = "glm-4.7"


class TestCompactE2E:
    """端到端压缩测试 - 真实调用 LLM"""

    @pytest.fixture(autouse=True)
    def setup(self):
        """测试前配置 LLM 和小阈值"""
        reset_config()
        clear_compactor_cache()
        # 配置 LLM 和小阈值
        datapillar_configure(
            llm={
                "api_key": GLM_API_KEY,
                "base_url": GLM_BASE_URL,
                "model": GLM_MODEL,
            },
            context={
                "window_size": 500,                # 500 tokens 窗口
                "compact_trigger_threshold": 0.5,  # 50% = 250 tokens 触发
                "compact_target_ratio": 0.3,       # 压缩到 30%
                "compact_min_keep_entries": 2,     # 保留 2 条
                "compact_max_summary_tokens": 200, # 摘要最大 200 tokens
            },
            resilience={
                "max_retries": 2,
                "llm_timeout_seconds": 60,
            },
        )
        yield
        reset_config()
        clear_compactor_cache()

    @pytest.mark.asyncio
    async def test_compact_e2e_with_real_llm(self):
        """端到端测试：真实 LLM 压缩"""
        memory = SessionMemory(
            session_id="e2e_test_session",
            user_id="e2e_test_user",
        )

        # 模拟真实对话场景 - 添加足够多的内容触发压缩
        long_conversations = [
            ("user", "我想创建一个用户画像宽表，包含用户基本信息和行为数据，需要整合多个数据源"),
            ("analyst", "好的，我来分析您的需求。用户画像宽表需要整合多个数据源，包括用户注册信息、订单数据、浏览行为等。这是一个典型的数据仓库建模场景。"),
            ("user", "数据源有哪些？请详细列出"),
            ("analyst", "根据分析，数据源包括：1) user_info 用户基础表包含姓名电话邮箱 2) order_detail 订单明细表包含订单金额时间 3) page_view 页面浏览表包含访问路径时长 4) user_label 用户标签表包含用户分群标签"),
            ("user", "使用什么存储格式？有什么推荐吗？"),
            ("architect", "建议使用 Apache Iceberg 格式存储，支持 ACID 事务和时间旅行功能，适合数据湖场景。Iceberg 还支持 Schema Evolution 和分区演进。"),
            ("user", "好的，开始开发吧，我需要完整的 SQL"),
            ("developer", "我来编写 SQL 代码。首先创建目标表结构，然后编写 ETL 逻辑将数据整合到宽表中。需要处理数据清洗、类型转换、聚合计算等。"),
            ("user", "进度如何？完成了多少？"),
            ("developer", "SQL 开发完成，已经创建了 user_profile_wide 宽表，包含 50 个字段，覆盖用户基础信息、订单统计、行为特征等维度。代码已通过测试。"),
        ]

        # 添加对话
        for speaker, content in long_conversations:
            if speaker == "user":
                memory.add_user_message(content)
            else:
                memory.add_agent_response(speaker, content)

        # 添加工具调用结果增加 token 数
        memory.add_tool_result("analyst", "search_tables", "找到 15 张相关表: user_info, order_detail, page_view, user_label, order_header, payment_log...")
        memory.add_tool_result("architect", "get_table_schema", "user_info 表结构: id BIGINT, name STRING, phone STRING, email STRING, created_at TIMESTAMP, updated_at TIMESTAMP...")
        memory.add_tool_result("developer", "execute_sql", "SQL 执行成功，影响行数: 1000000，耗时 45 秒，扫描数据量 2.5GB")

        # 检查状态
        tokens_before = memory.estimate_tokens()
        entries_before = len(memory.conversation.entries)
        trigger_threshold = memory.policy.get_trigger_tokens()

        print(f"\n压缩前状态:")
        print(f"  - 对话条目数: {entries_before}")
        print(f"  - Token 数: {tokens_before}")
        print(f"  - 触发阈值: {trigger_threshold}")
        print(f"  - 需要压缩: {memory.needs_compact()}")

        # 验证需要压缩
        assert memory.needs_compact() is True, f"Token 数 {tokens_before} 应该超过阈值 {trigger_threshold}"

        # 创建真实 LLM 压缩器
        llm = call_llm(temperature=0.0)
        policy = CompactPolicy(
            context_window=1000,
            trigger_threshold=0.5,
            target_ratio=0.3,
            min_keep_entries=2,
            max_summary_tokens=300,
        )
        compactor = Compactor(llm=llm, policy=policy)

        # 执行压缩
        result = await memory.compact(compactor=compactor)

        # 输出结果
        print(f"\n压缩结果:")
        print(f"  - 成功: {result.success}")
        print(f"  - 压缩前 tokens: {result.tokens_before}")
        print(f"  - 压缩后 tokens: {result.tokens_after}")
        print(f"  - 节省 tokens: {result.tokens_saved}")
        print(f"  - 移除条目: {result.removed_count}")
        print(f"  - 保留条目: {result.kept_count}")
        print(f"\n生成的摘要:")
        print(f"{memory.compressed_summary}")

        # 验证压缩成功
        assert result.success is True
        assert result.tokens_saved > 0
        assert result.removed_count > 0
        assert memory.compressed_summary != ""
        assert memory.total_compactions == 1

        # 验证摘要质量（应包含关键信息）
        summary = memory.compressed_summary.lower()
        # 摘要应该包含一些关键词
        assert any(keyword in summary for keyword in ["用户", "宽表", "iceberg", "sql", "数据"]), \
            f"摘要缺少关键信息: {memory.compressed_summary}"

        # 验证对话条目减少
        entries_after = len(memory.conversation.entries)
        assert entries_after < entries_before

        print(f"\n压缩后状态:")
        print(f"  - 对话条目数: {entries_after}")
        print(f"  - Token 数: {memory.estimate_tokens()}")
        print(f"  - 压缩次数: {memory.total_compactions}")
        print(f"  - 累计节省: {memory.total_tokens_saved}")

    @pytest.mark.asyncio
    async def test_compact_preserves_recent_and_user_messages(self):
        """验证压缩保留最近消息和用户消息"""
        memory = SessionMemory(
            session_id="preserve_test",
            user_id="test_user",
        )

        # 添加大量对话 - 确保超过阈值
        for i in range(15):
            memory.add_user_message(f"用户问题 {i}: 这是一个关于数据处理的详细问题，需要仔细分析和回答，包含多个技术要点。")
            memory.add_agent_response("analyst", f"分析回复 {i}: 这是一个详细的分析回复，包含多个要点和建议，涉及数据建模和 ETL 流程设计。")

        # 记录最后两条消息
        last_entries = memory.conversation.entries[-2:]
        last_contents = [e.content for e in last_entries]

        print(f"\n压缩前 token 数: {memory.estimate_tokens()}")
        print(f"触发阈值: {memory.policy.get_trigger_tokens()}")

        # 执行压缩
        llm = call_llm(temperature=0.0)
        policy = CompactPolicy(
            context_window=500,
            trigger_threshold=0.5,
            min_keep_entries=2,
        )
        compactor = Compactor(llm=llm, policy=policy)

        result = await memory.compact(compactor=compactor)

        print(f"\n压缩结果: 保留 {result.kept_count} 条，移除 {result.removed_count} 条")

        # 验证最近的消息被保留
        current_contents = [e.content for e in memory.conversation.entries]
        for content in last_contents:
            assert content in current_contents, f"最近消息应该被保留: {content[:50]}..."

    @pytest.mark.asyncio
    async def test_multiple_compactions(self):
        """测试多次压缩"""
        memory = SessionMemory(
            session_id="multi_compact_test",
            user_id="test_user",
        )

        llm = call_llm(temperature=0.0)
        policy = CompactPolicy(
            context_window=500,
            trigger_threshold=0.5,
            min_keep_entries=2,
            max_summary_tokens=150,
        )
        compactor = Compactor(llm=llm, policy=policy)

        # 第一轮对话 - 添加足够多触发压缩
        for i in range(10):
            memory.add_user_message(f"第一轮问题 {i}: 关于数据仓库建设的问题，需要详细的技术方案和实施建议。")
            memory.add_agent_response("analyst", f"第一轮回复 {i}: 数据仓库建设需要考虑多个方面，包括数据建模、ETL流程、存储选型等。")

        # 第一次压缩
        result1 = await memory.compact(compactor=compactor)
        print(f"\n第一次压缩: 节省 {result1.tokens_saved} tokens")

        # 第二轮对话 - 添加足够多触发第二次压缩
        for i in range(10):
            memory.add_user_message(f"第二轮问题 {i}: 关于 ETL 开发的问题，需要详细的代码实现和性能优化建议。")
            memory.add_agent_response("developer", f"第二轮回复 {i}: ETL 开发需要注意性能优化，包括增量处理、并行计算、数据分区等策略。")

        # 第二次压缩
        result2 = await memory.compact(compactor=compactor)
        print(f"第二次压缩: 节省 {result2.tokens_saved} tokens")

        # 验证累计压缩
        assert memory.total_compactions == 2
        assert memory.total_tokens_saved == result1.tokens_saved + result2.tokens_saved

        print(f"\n最终状态:")
        print(f"  - 总压缩次数: {memory.total_compactions}")
        print(f"  - 累计节省: {memory.total_tokens_saved} tokens")
        print(f"  - 摘要长度: {len(memory.compressed_summary)} 字符")

    @pytest.mark.asyncio
    async def test_todo_preserved_after_compact(self):
        """测试压缩前后 TODO 保留情况 - TODO 存储在 PinnedContext，不参与压缩"""
        memory = SessionMemory(
            session_id="todo_compact_test",
            user_id="test_user",
        )

        # 1. 创建 TODO 并添加工作步骤
        todo_list = AgentTodoList(
            agent_id="analyst",
            session_id="todo_compact_test",
        )
        todo_list.set_task("创建用户画像宽表")
        step1 = todo_list.add_step("分析数据源")
        step1.mark_completed("找到 4 个数据源")
        step2 = todo_list.add_step("设计表结构")
        step2.mark_in_progress()
        step3 = todo_list.add_step("编写 ETL SQL")
        step4 = todo_list.add_step("测试验证")

        # 2. 将 TODO 设置到 SessionMemory
        memory.set_todos(todo_list)

        # 3. 同时添加一些决策和约束（也在 PinnedContext 中）
        memory.pin_decision("使用 Iceberg 格式存储", "architect")
        memory.pin_decision("采用增量更新策略", "developer")
        memory.pin_constraint("必须支持 Hive 兼容")
        memory.pin_constraint("数据保留 7 天")

        # 4. 添加足够多的对话触发压缩
        for i in range(15):
            memory.add_user_message(f"问题 {i}: 这是一个关于数据仓库建设的详细问题，包含多个技术要点和实施细节。")
            memory.add_agent_response("analyst", f"回复 {i}: 这是一个详细的分析回复，涉及数据建模、ETL 流程、存储选型等多个方面。")

        # 5. 记录压缩前状态
        tokens_before = memory.estimate_tokens()
        entries_before = len(memory.conversation.entries)
        todos_before = memory.get_todos()
        decisions_before = len(memory.pinned.decisions)
        constraints_before = len(memory.pinned.constraints)

        print(f"\n===== 压缩前状态 =====")
        print(f"对话条目数: {entries_before}")
        print(f"Token 数: {tokens_before}")
        print(f"触发阈值: {memory.policy.get_trigger_tokens()}")
        print(f"需要压缩: {memory.needs_compact()}")
        print(f"\nTODO 清单:")
        print(f"  - 当前任务: {todos_before.current_task}")
        print(f"  - 步骤数: {len(todos_before.steps)}")
        for step in todos_before.steps:
            print(f"    - [{step.status}] {step.id}: {step.description}")
        print(f"\n决策数: {decisions_before}")
        for d in memory.pinned.decisions:
            print(f"  - {d.to_display()}")
        print(f"约束数: {constraints_before}")
        for c in memory.pinned.constraints:
            print(f"  - {c}")

        # 6. 执行压缩
        llm = call_llm(temperature=0.0)
        policy = CompactPolicy(
            context_window=500,
            trigger_threshold=0.5,
            min_keep_entries=2,
            max_summary_tokens=150,
        )
        compactor = Compactor(llm=llm, policy=policy)
        result = await memory.compact(compactor=compactor)

        # 7. 验证压缩成功
        assert result.success is True
        assert result.tokens_saved > 0
        print(f"\n===== 压缩结果 =====")
        print(f"成功: {result.success}")
        print(f"节省 tokens: {result.tokens_saved}")
        print(f"移除条目: {result.removed_count}")
        print(f"保留条目: {result.kept_count}")

        # 8. 验证压缩后状态
        entries_after = len(memory.conversation.entries)
        todos_after = memory.get_todos()
        decisions_after = len(memory.pinned.decisions)
        constraints_after = len(memory.pinned.constraints)

        print(f"\n===== 压缩后状态 =====")
        print(f"对话条目数: {entries_before} -> {entries_after}")
        print(f"\nTODO 清单（应完全保留）:")
        print(f"  - 当前任务: {todos_after.current_task}")
        print(f"  - 步骤数: {len(todos_after.steps)}")
        for step in todos_after.steps:
            print(f"    - [{step.status}] {step.id}: {step.description}")
            if step.result:
                print(f"      结果: {step.result}")
        print(f"\n决策数: {decisions_after}")
        print(f"约束数: {constraints_after}")

        # 9. 核心断言：TODO、决策、约束必须完全保留
        assert todos_after is not None, "TODO 不应该被压缩丢失"
        assert todos_after.current_task == todos_before.current_task, "任务描述应保留"
        assert len(todos_after.steps) == len(todos_before.steps), "步骤数应保留"

        # 验证每个步骤状态保留
        for i, step in enumerate(todos_after.steps):
            assert step.id == todos_before.steps[i].id
            assert step.description == todos_before.steps[i].description
            assert step.status == todos_before.steps[i].status
            assert step.result == todos_before.steps[i].result

        # 验证决策和约束保留
        assert decisions_after == decisions_before, "决策数应保留"
        assert constraints_after == constraints_before, "约束数应保留"

        # 验证对话确实被压缩了
        assert entries_after < entries_before, "对话应该被压缩减少"

        print(f"\n===== 验证通过 =====")
        print(f"✓ TODO 完全保留 ({len(todos_after.steps)} 个步骤)")
        print(f"✓ 决策完全保留 ({decisions_after} 个)")
        print(f"✓ 约束完全保留 ({constraints_after} 个)")
        print(f"✓ 对话成功压缩 ({entries_before} -> {entries_after})")

    @pytest.mark.asyncio
    async def test_memory_storage_and_restore(self):
        """测试记忆的存储和恢复 - 验证底层存储正确性"""
        # 1. 创建原始 SessionMemory 并填充数据
        original_memory = SessionMemory()

        # 添加对话
        original_memory.add_user_message("帮我创建用户画像宽表")
        original_memory.add_agent_response("analyst", "好的，我来分析需求")
        original_memory.add_agent_handover("analyst", "architect", "需求分析完成")
        original_memory.add_tool_result("architect", "search_tables", "找到 10 张相关表")

        # 添加固定上下文
        original_memory.pin_decision("使用 Iceberg 格式存储", "architect")
        original_memory.pin_decision("采用增量更新策略", "developer")
        original_memory.pin_constraint("必须支持 Hive 兼容")
        original_memory.pin_artifact("sql_001", "sql", "用户宽表创建 SQL")

        # 添加 TODO
        todo_list = AgentTodoList(
            agent_id="developer",
            session_id="storage_test_session",
        )
        todo_list.set_task("开发用户画像宽表")
        step1 = todo_list.add_step("编写建表 SQL")
        step1.mark_completed("建表语句已完成")
        step2 = todo_list.add_step("编写 ETL 逻辑")
        step2.mark_in_progress()
        step3 = todo_list.add_step("测试验证")
        original_memory.set_todos(todo_list)

        # 设置压缩摘要（模拟已压缩过）
        original_memory.compressed_summary = "历史摘要：用户想创建画像宽表，已完成需求分析"
        original_memory.total_compactions = 1
        original_memory.total_tokens_saved = 500

        print("\n===== 原始记忆状态 =====")
        print(f"对话条目数: {len(original_memory.conversation.entries)}")
        print(f"决策数: {len(original_memory.pinned.decisions)}")
        print(f"约束数: {len(original_memory.pinned.constraints)}")
        print(f"工件数: {len(original_memory.pinned.artifacts)}")
        print(f"TODO 步骤数: {len(original_memory.get_todos().steps)}")
        print(f"压缩摘要: {original_memory.compressed_summary[:50]}...")
        print(f"压缩次数: {original_memory.total_compactions}")
        print(f"累计节省: {original_memory.total_tokens_saved}")

        # 2. 序列化为 dict（模拟存储到 Checkpointer）
        serialized_data = original_memory.model_dump(mode="json")

        print("\n===== 序列化数据 =====")
        print(f"数据类型: {type(serialized_data)}")
        print(f"顶层键: {list(serialized_data.keys())}")
        print(f"对话条目数: {len(serialized_data['conversation']['entries'])}")
        print(f"决策数: {len(serialized_data['pinned']['decisions'])}")
        print(f"约束数: {len(serialized_data['pinned']['constraints'])}")
        print(f"工件数: {len(serialized_data['pinned']['artifacts'])}")
        print(f"TODO 数据存在: {serialized_data['pinned']['todos_data'] is not None}")

        # 3. 从 dict 恢复（模拟从 Checkpointer 加载）
        restored_memory = SessionMemory.model_validate(serialized_data)

        print("\n===== 恢复后记忆状态 =====")
        print(f"对话条目数: {len(restored_memory.conversation.entries)}")
        print(f"决策数: {len(restored_memory.pinned.decisions)}")
        print(f"约束数: {len(restored_memory.pinned.constraints)}")
        print(f"工件数: {len(restored_memory.pinned.artifacts)}")
        print(f"TODO 步骤数: {len(restored_memory.get_todos().steps)}")
        print(f"压缩摘要: {restored_memory.compressed_summary[:50]}...")
        print(f"压缩次数: {restored_memory.total_compactions}")
        print(f"累计节省: {restored_memory.total_tokens_saved}")

        # 4. 验证所有字段正确恢复
        # 对话历史
        assert len(restored_memory.conversation.entries) == len(original_memory.conversation.entries)
        for i, entry in enumerate(restored_memory.conversation.entries):
            orig_entry = original_memory.conversation.entries[i]
            assert entry.seq == orig_entry.seq
            assert entry.speaker == orig_entry.speaker
            assert entry.listener == orig_entry.listener
            assert entry.entry_type == orig_entry.entry_type
            assert entry.content == orig_entry.content

        # 决策
        assert len(restored_memory.pinned.decisions) == len(original_memory.pinned.decisions)
        for i, decision in enumerate(restored_memory.pinned.decisions):
            orig_decision = original_memory.pinned.decisions[i]
            assert decision.content == orig_decision.content
            assert decision.agent_id == orig_decision.agent_id

        # 约束
        assert restored_memory.pinned.constraints == original_memory.pinned.constraints

        # 工件
        assert len(restored_memory.pinned.artifacts) == len(original_memory.pinned.artifacts)
        for i, artifact in enumerate(restored_memory.pinned.artifacts):
            orig_artifact = original_memory.pinned.artifacts[i]
            assert artifact.ref_id == orig_artifact.ref_id
            assert artifact.dtype == orig_artifact.dtype
            assert artifact.summary == orig_artifact.summary

        # TODO
        restored_todos = restored_memory.get_todos()
        original_todos = original_memory.get_todos()
        assert restored_todos.agent_id == original_todos.agent_id
        assert restored_todos.current_task == original_todos.current_task
        assert len(restored_todos.steps) == len(original_todos.steps)
        for i, step in enumerate(restored_todos.steps):
            orig_step = original_todos.steps[i]
            assert step.id == orig_step.id
            assert step.description == orig_step.description
            assert step.status == orig_step.status
            assert step.result == orig_step.result

        # 压缩状态
        assert restored_memory.compressed_summary == original_memory.compressed_summary
        assert restored_memory.total_compactions == original_memory.total_compactions
        assert restored_memory.total_tokens_saved == original_memory.total_tokens_saved

        print("\n===== 验证通过 =====")
        print("✓ 对话历史正确恢复")
        print("✓ 决策正确恢复")
        print("✓ 约束正确恢复")
        print("✓ 工件正确恢复")
        print("✓ TODO 正确恢复（包括步骤状态和结果）")
        print("✓ 压缩状态正确恢复")

    @pytest.mark.asyncio
    async def test_memory_storage_after_compact(self):
        """测试压缩后的记忆存储和恢复"""
        # 1. 创建并填充记忆
        memory = SessionMemory(
            session_id="compact_storage_test",
            user_id="test_user",
        )

        # 添加足够多的对话触发压缩
        for i in range(15):
            memory.add_user_message(f"问题 {i}: 关于数据仓库建设的详细问题，包含技术要点。")
            memory.add_agent_response("analyst", f"回复 {i}: 详细的分析回复，涉及多个技术方面。")

        # 添加固定上下文
        memory.pin_decision("使用 Iceberg 格式", "architect")
        todo_list = AgentTodoList(agent_id="analyst", session_id="compact_storage_test")
        todo_list.set_task("分析需求")
        todo_list.add_step("收集数据源").mark_completed("完成")
        memory.set_todos(todo_list)

        print("\n===== 压缩前状态 =====")
        print(f"对话条目数: {len(memory.conversation.entries)}")
        print(f"Token 数: {memory.estimate_tokens()}")

        # 2. 执行压缩
        llm = call_llm(temperature=0.0)
        policy = CompactPolicy(
            context_window=500,
            trigger_threshold=0.5,
            min_keep_entries=2,
            max_summary_tokens=150,
        )
        compactor = Compactor(llm=llm, policy=policy)
        result = await memory.compact(compactor=compactor)

        print(f"\n===== 压缩结果 =====")
        print(f"节省 tokens: {result.tokens_saved}")
        print(f"压缩摘要长度: {len(memory.compressed_summary)}")

        # 3. 序列化（存储）
        serialized = memory.model_dump(mode="json")

        print(f"\n===== 序列化数据 =====")
        print(f"压缩摘要存在: {bool(serialized.get('compressed_summary'))}")
        print(f"压缩次数: {serialized.get('total_compactions')}")
        print(f"对话条目数: {len(serialized['conversation']['entries'])}")

        # 4. 反序列化（恢复）
        restored = SessionMemory.model_validate(serialized)

        # 5. 验证压缩状态正确恢复
        assert restored.compressed_summary == memory.compressed_summary
        assert restored.total_compactions == memory.total_compactions
        assert restored.total_tokens_saved == memory.total_tokens_saved
        assert len(restored.conversation.entries) == len(memory.conversation.entries)

        # 验证固定上下文保留
        assert len(restored.pinned.decisions) == 1
        assert restored.get_todos() is not None

        print(f"\n===== 恢复后验证 =====")
        print(f"✓ 压缩摘要正确恢复 ({len(restored.compressed_summary)} 字符)")
        print(f"✓ 压缩次数正确恢复 ({restored.total_compactions})")
        print(f"✓ 对话条目正确恢复 ({len(restored.conversation.entries)} 条)")
        print(f"✓ 决策正确恢复")
        print(f"✓ TODO 正确恢复")
