"""
ETL Team end-to-end testing

test true LLM Called routing logic：
1. Metadata query scenario：Analyst → Catalog → end
2. ETL Generate scene：Analyst → Architect → Developer → Reviewer → end
"""

import asyncio

import pytest
from datapillar_oneagentic.sse import SseEventType

pytestmark = pytest.mark.skip(reason="need to be real LLM API Key，Run manually")


async def test_catalog_query():
    """Test metadata query routing：should go Analyst → Catalog"""
    from src.modules.etl.agents import create_etl_team

    team = create_etl_team()

    print("\n" + "=" * 60)
    print("test scenario：Metadata query")
    print("input：What tables are there?？")
    print("expected route：Analyst → Catalog → end")
    print("=" * 60)

    agent_trace = []

    async for event in team.stream(
        query="What tables are there?？",
        session_id="test_catalog_001",
    ):
        event_type = event.get("event")
        if event_type == SseEventType.AGENT_START:
            agent = event.get("agent", {})
            agent_name = agent.get("name", "")
            agent_id = agent.get("id", "")
            if agent_id:  # Only records have ID of
                agent_trace.append(agent_id)
            print(f"🚀 Agent start: {agent_name} ({agent_id})")

        elif event_type == SseEventType.AGENT_END:
            agent = event.get("agent", {})
            agent_name = agent.get("name", "")
            print(f"✅ Agent end: {agent_name}")

        elif event_type == SseEventType.TOOL_START:
            tool = event.get("tool", {})
            tool_name = tool.get("name", "")
            print(f"  🔧 Tool call: {tool_name}")

        elif event_type == SseEventType.TOOL_END:
            tool = event.get("tool", {})
            tool_name = tool.get("name", "")
            print(f"  ✅ Tool complete: {tool_name}")

        elif event_type == SseEventType.RESULT:
            result = event.get("result", {})
            deliverable = result.get("deliverable", {}) if isinstance(result, dict) else {}
            summary = deliverable.get("summary", "")
            print(f"\n📋 final result: {summary[:200]}...")

        elif event_type == SseEventType.ERROR:
            error = event.get("error", {})
            message = error.get("message", "")
            detail = error.get("detail", "")
            print(f"❌ Error: {message} {detail}")

    print(f"\nrouting trace: {' → '.join(agent_trace)}")
    print("=" * 60)

    return agent_trace


async def main():
    """Run all end-to-end tests"""
    print("\n🧪 ETL Team end-to-end testing")
    print("=" * 60)

    # test 2: ETL generate（Complete pipeline）
    trace2 = await test_etl_generation()

    # Verify route
    if "analyst" in trace2 and "architect" in trace2:
        print("✅ ETL The generated route is correct")
    else:
        print("❌ ETL Generate routing errors")


async def test_etl_generation():
    """test ETL Generate route：Analyst → Architect → Developer → Reviewer"""
    from src.modules.etl.agents import create_etl_team

    team = create_etl_team()

    print("\n" + "=" * 60)
    print("test scenario：ETL generate")
    print("input：Help me create a user wide table，Summary hive_catalog.lineage_db.ods_user data")
    print("expected route：Analyst → Architect → Developer → Reviewer")
    print("=" * 60)

    agent_trace = []
    seen_agents = set()

    async for event in team.stream(
        query="Help me design one ETL process：from hive_catalog.datapillar.t_order Read order data，Summarize order amount by user，write hive_catalog.datapillar.dws_user_order_summary，use overwrite mode",
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
                print(f"🚀 Agent start: {agent_name} ({agent_id})")

        elif event_type == SseEventType.AGENT_END:
            agent = event.get("agent", {})
            agent_name = agent.get("name", "")
            agent_id = agent.get("id", "")
            if agent_id in seen_agents:
                print(f"✅ Agent end: {agent_name}")

        elif event_type == SseEventType.TOOL_START:
            tool = event.get("tool", {})
            tool_name = tool.get("name", "")
            if tool_name:
                print(f"  🔧 Tool call: {tool_name}")

        elif event_type == SseEventType.RESULT:
            result = event.get("result", {})
            deliverable = result.get("deliverable", {}) if isinstance(result, dict) else {}
            summary = deliverable.get("summary", "")
            print(f"\n📋 final result: {summary[:100]}...")

        elif event_type == SseEventType.ERROR:
            error = event.get("error", {})
            message = error.get("message", "")
            detail = error.get("detail", "")
            print(f"❌ Error: {message} {detail}")

    print(f"\nrouting trace: {' → '.join(agent_trace)}")
    print("=" * 60)

    return agent_trace


if __name__ == "__main__":
    asyncio.run(main())
