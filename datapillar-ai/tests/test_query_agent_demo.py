"""
测试 Query Agent Demo

运行方式：
    python -m pytest tests/test_query_agent_demo.py -v
    或
    python tests/test_query_agent_demo.py
"""

import asyncio
import sys
from pathlib import Path

# 添加项目根目录到 Python 路径
project_root = Path(__file__).parent.parent
sys.path.insert(0, str(project_root))


async def test_query_agent():
    """测试 Query Agent"""
    print("=" * 60)
    print("测试 OneAgentic 框架 - Query Agent Demo")
    print("=" * 60)

    # 1. 导入并初始化 demo
    print("\n[1] 导入 Demo 模块...")
    from src.modules.oneagentic.examples.query_agent_demo import (
        init_query_agent,
    )

    # 注册工具和知识
    init_query_agent()
    print("    ✅ 工具和知识已注册")

    # 2. 检查 Agent 是否已注册
    print("\n[2] 检查 Agent 注册...")
    from src.modules.oneagentic.core.agent import AgentRegistry

    agent_spec = AgentRegistry.get("query_agent")
    if agent_spec:
        print(f"    ✅ Agent 已注册: {agent_spec.name} ({agent_spec.id})")
        print(f"       - 角色: {agent_spec.role}")
        print(f"       - 入口: {agent_spec.is_entry}")
        print(f"       - 工具: {agent_spec.tools}")
        print(f"       - 知识: {agent_spec.knowledge_domains}")
    else:
        print("    ❌ Agent 未注册")
        return

    # 3. 创建 Orchestrator（禁用自动发现，只用我们注册的 Agent）
    print("\n[3] 创建 Orchestrator...")
    from src.modules.oneagentic import Orchestrator

    orchestrator = Orchestrator(auto_discover=False)
    print("    ✅ Orchestrator 已创建")

    # 4. 测试查询
    print("\n[4] 测试查询: '有哪些用户相关的表？'")
    print("-" * 60)

    events = []
    async for event in orchestrator.stream(
        query="有哪些用户相关的表？",
        session_id="test_session_001",
        user_id="test_user",
    ):
        events.append(event)
        print(f"    📨 事件: {event.get('type', 'unknown')}")

        # 打印详细信息
        if event.get("type") == "agent_start":
            print(f"       Agent: {event.get('agent_id')}")
        elif event.get("type") == "agent_end":
            print(f"       状态: {event.get('status')}")
            if event.get("summary"):
                print(f"       摘要: {event.get('summary')[:100]}...")
        elif event.get("type") == "tool_call":
            print(f"       工具: {event.get('tool_name')}")
        elif event.get("type") == "error":
            print(f"       错误: {event.get('error')}")

    print("-" * 60)
    print(f"    共收到 {len(events)} 个事件")

    # 5. 检查最终结果
    print("\n[5] 检查最终结果...")
    final_event = events[-1] if events else None
    if final_event and final_event.get("type") == "agent_end":
        deliverable = final_event.get("deliverable")
        if deliverable:
            print("    ✅ 获得交付物:")
            print(f"       - answer: {deliverable.get('answer', 'N/A')[:100]}...")
            print(f"       - confidence: {deliverable.get('confidence', 'N/A')}")
            print(f"       - sources: {deliverable.get('sources', [])}")
        else:
            print(f"    ⚠️ 无交付物，摘要: {final_event.get('summary', 'N/A')}")
    else:
        print("    ❌ 未获得最终结果")

    print("\n" + "=" * 60)
    print("测试完成")
    print("=" * 60)


async def test_framework_encapsulation():
    """测试框架封装性"""
    print("\n" + "=" * 60)
    print("测试框架封装性")
    print("=" * 60)

    # 1. 测试 __all__ 导出
    print("\n[1] 测试 oneagentic.__all__...")
    from src.modules import oneagentic

    expected_exports = [
        "agent",
        "AgentContext",
        "AgentRole",
        "Clarification",
        "ToolRegistry",
        "KnowledgeDomain",
        "KnowledgeLevel",
        "KnowledgeStore",
        "Orchestrator",
    ]

    for name in expected_exports:
        if hasattr(oneagentic, name):
            print(f"    ✅ {name} 可用")
        else:
            print(f"    ❌ {name} 不可用")

    # 2. 测试不应该导出的
    print("\n[2] 测试不应该导出的...")
    should_not_export = [
        "AgentSpec",
        "AgentRegistry",
        "AgentExecutor",
        "Blackboard",
        "AgentResult",
    ]

    for name in should_not_export:
        if name in oneagentic.__all__:
            print(f"    ❌ {name} 不应该在 __all__ 中")
        else:
            print(f"    ✅ {name} 不在 __all__ 中")

    # 3. 测试 AgentContext 私有字段
    print("\n[3] 测试 AgentContext 私有字段...")
    from src.modules.oneagentic import AgentContext

    # 创建一个空的 context（仅用于检查字段）
    ctx = AgentContext(session_id="test", query="test")

    # 检查公开字段
    public_fields = ["session_id", "query"]
    for field in public_fields:
        if hasattr(ctx, field):
            print(f"    ✅ 公开字段 {field} 可访问")

    # 检查私有字段（应该以 _ 开头）
    private_fields = ["_llm", "_tools", "_memory", "_state", "_spec"]
    for field in private_fields:
        if hasattr(ctx, field):
            print(f"    ✅ 私有字段 {field} 存在（按约定不应使用）")

    # 检查原来的公开字段是否已被移除
    old_public_fields = ["llm", "tools", "memory", "state", "spec"]
    for field in old_public_fields:
        if hasattr(ctx, field):
            print(f"    ❌ 旧字段 {field} 仍然存在（应该已被私有化）")
        else:
            print(f"    ✅ 旧字段 {field} 已被私有化")

    print("\n" + "=" * 60)
    print("封装性测试完成")
    print("=" * 60)


async def main():
    """主函数"""
    # 先测试封装性
    await test_framework_encapsulation()

    # 再测试 Agent
    await test_query_agent()


if __name__ == "__main__":
    asyncio.run(main())
