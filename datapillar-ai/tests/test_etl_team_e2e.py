"""
ETL 团队端到端测试

测试真实 LLM 调用的路由逻辑：
1. 元数据查询场景：Analyst → Catalog → 结束
2. ETL 生成场景：Analyst → Architect → Developer → Reviewer → 结束
"""

import asyncio

import pytest

from datapillar_oneagentic.sse import SseEventType

pytestmark = pytest.mark.skip(reason="需要真实 LLM API Key，手动运行")


async def test_catalog_query():
    """测试元数据查询路由：应该走 Analyst → Catalog"""
    from src.modules.etl.agents import create_etl_team

    team = create_etl_team()

    print("\n" + "=" * 60)
    print("测试场景：元数据查询")
    print("输入：有哪些表？")
    print("期望路由：Analyst → Catalog → 结束")
    print("=" * 60)

    agent_trace = []

    async for event in team.stream(
        query="有哪些表？",
        session_id="test_catalog_001",
    ):
        event_type = event.get("event")
        data = event.get("data", {})

        if event_type == SseEventType.AGENT_START:
            agent = event.get("agent", {})
            agent_name = agent.get("name", "")
            agent_id = agent.get("id", "")
            if agent_id:  # 只记录有 ID 的
                agent_trace.append(agent_id)
            print(f"🚀 Agent 开始: {agent_name} ({agent_id})")

        elif event_type == SseEventType.AGENT_END:
            agent = event.get("agent", {})
            agent_name = agent.get("name", "")
            print(f"✅ Agent 结束: {agent_name}")

        elif event_type == SseEventType.TOOL_START:
            tool = event.get("tool", {})
            tool_name = tool.get("name", "")
            print(f"  🔧 工具调用: {tool_name}")

        elif event_type == SseEventType.TOOL_END:
            tool = event.get("tool", {})
            tool_name = tool.get("name", "")
            print(f"  ✅ 工具完成: {tool_name}")

        elif event_type == SseEventType.RESULT:
            result = event.get("result", {})
            deliverable = result.get("deliverable", {}) if isinstance(result, dict) else {}
            summary = deliverable.get("summary", "")
            print(f"\n📋 最终结果: {summary[:200]}...")

        elif event_type == SseEventType.ERROR:
            error = event.get("error", {})
            message = error.get("message", "")
            detail = error.get("detail", "")
            print(f"❌ 错误: {message} {detail}")

    print(f"\n路由轨迹: {' → '.join(agent_trace)}")
    print("=" * 60)

    return agent_trace


async def main():
    """运行所有端到端测试"""
    print("\n🧪 ETL 团队端到端测试")
    print("=" * 60)

    # 测试 2: ETL 生成（完整流水线）
    trace2 = await test_etl_generation()

    # 验证路由
    if "analyst" in trace2 and "architect" in trace2:
        print("✅ ETL 生成路由正确")
    else:
        print("❌ ETL 生成路由错误")


async def test_etl_generation():
    """测试 ETL 生成路由：Analyst → Architect → Developer → Reviewer"""
    from src.modules.etl.agents import create_etl_team

    team = create_etl_team()

    print("\n" + "=" * 60)
    print("测试场景：ETL 生成")
    print("输入：帮我创建一个用户宽表，汇总 hive_catalog.lineage_db.ods_user 的数据")
    print("期望路由：Analyst → Architect → Developer → Reviewer")
    print("=" * 60)

    agent_trace = []
    seen_agents = set()

    async for event in team.stream(
        query="帮我设计一个 ETL 流程：从 hive_catalog.datapillar.t_order 读取订单数据，按用户汇总订单金额，写入 hive_catalog.datapillar.dws_user_order_summary，使用 overwrite 模式",
        session_id="test_etl_006",
    ):
        event_type = event.get("event")

        if event_type == SseEventType.AGENT_START:
            agent = event.get("agent", {})
            agent_name = agent.get("name", "")
            agent_id = agent.get("id", "")
            if agent_id and agent_id not in seen_agents:
                seen_agents.add(agent_id)
                agent_trace.append(agent_id)
                print(f"🚀 Agent 开始: {agent_name} ({agent_id})")

        elif event_type == SseEventType.AGENT_END:
            agent = event.get("agent", {})
            agent_name = agent.get("name", "")
            agent_id = agent.get("id", "")
            if agent_id in seen_agents:
                print(f"✅ Agent 结束: {agent_name}")

        elif event_type == SseEventType.TOOL_START:
            tool = event.get("tool", {})
            tool_name = tool.get("name", "")
            if tool_name:
                print(f"  🔧 工具调用: {tool_name}")

        elif event_type == SseEventType.RESULT:
            result = event.get("result", {})
            deliverable = result.get("deliverable", {}) if isinstance(result, dict) else {}
            summary = deliverable.get("summary", "")
            print(f"\n📋 最终结果: {summary[:100]}...")

        elif event_type == SseEventType.ERROR:
            error = event.get("error", {})
            message = error.get("message", "")
            detail = error.get("detail", "")
            print(f"❌ 错误: {message} {detail}")

    print(f"\n路由轨迹: {' → '.join(agent_trace)}")
    print("=" * 60)

    return agent_trace


if __name__ == "__main__":
    asyncio.run(main())
