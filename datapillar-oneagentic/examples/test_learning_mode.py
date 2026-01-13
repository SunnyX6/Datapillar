"""
测试经验学习功能

场景：
1. 第一次执行任务，记录经验
2. 调用 save_experience 保存到向量库
3. 第二次执行相似任务，检索并注入经验上下文

使用 GLM-4.7 真实 API
"""

import asyncio
import os
import shutil
from pydantic import BaseModel, Field

from datapillar_oneagentic import (
    agent,
    tool,
    Datapillar,
    Process,
    AgentContext,
    datapillar_configure,
)


# === GLM 配置 ===
GLM_API_KEY = os.environ.get("GLM_API_KEY", "da90d1098b0d4126848881f56ee2197c.B77DUfAuh4To29o7")
GLM_MODEL = "glm-4.7"

# 经验存储路径
EXPERIENCE_PATH = "./data/experience_test"


# === 工具定义 ===

@tool
def search_knowledge_base(query: str) -> str:
    """搜索知识库

    Args:
        query: 搜索关键词

    Returns:
        搜索结果
    """
    # 模拟知识库
    kb = {
        "退款": "退款政策: 7天无理由退款，需保持商品完好",
        "发货": "发货时间: 下单后24小时内发货，节假日顺延",
        "保修": "保修政策: 电子产品保修1年，人为损坏不保",
        "优惠": "当前优惠: 满300减50，新用户首单9折",
    }
    for key, value in kb.items():
        if key in query:
            return value
    return f"未找到与'{query}'相关的信息"


@tool
def create_ticket(issue_type: str, description: str) -> str:
    """创建工单

    Args:
        issue_type: 工单类型 (退款/投诉/咨询)
        description: 问题描述

    Returns:
        工单信息
    """
    import random
    ticket_id = f"TK{random.randint(10000, 99999)}"
    return f"工单已创建，编号: {ticket_id}，类型: {issue_type}"


# === Agent 定义 ===

class CustomerServiceResult(BaseModel):
    """客服结果"""
    answer: str = Field(description="回答内容")
    ticket_id: str | None = Field(default=None, description="工单号（如有）")
    resolved: bool = Field(description="是否已解决")


@agent(
    id="customer_service",
    name="智能客服",
    description="处理客户咨询和问题",
    tools=["search_knowledge_base", "create_ticket"],
    deliverable_schema=CustomerServiceResult,
    temperature=0.3,
    max_steps=5,
)
class CustomerServiceAgent:
    """客服 Agent"""

    SYSTEM_PROMPT = """你是智能客服，负责处理客户咨询。

## 工作流程
1. 理解客户问题
2. 使用 search_knowledge_base 搜索相关信息
3. 如果需要人工处理，使用 create_ticket 创建工单
4. 给出解答

## 输出格式
{
  "answer": "回答内容",
  "ticket_id": "工单号或null",
  "resolved": true或false
}"""

    async def run(self, ctx: AgentContext) -> CustomerServiceResult:
        messages = ctx.build_messages(self.SYSTEM_PROMPT)
        messages = await ctx.invoke_tools(messages)
        return await ctx.get_output(messages)


def create_learning_team() -> Datapillar:
    """创建启用经验学习的客服团队"""
    return Datapillar(
        namespace="learning_demo",
        name="智能客服团队",
        agents=[CustomerServiceAgent],
        process=Process.SEQUENTIAL,
        enable_learning=True,  # 启用经验学习
        enable_share_context=True,
        verbose=True,
    )


async def test_first_execution(team: Datapillar):
    """第一次执行：记录经验"""
    print("\n" + "=" * 60)
    print("📝 第一次执行：记录经验")
    print("=" * 60)

    query = "我买的耳机想退货，怎么操作？"
    session_id = "learning_test_001"

    print(f"\n用户问题: {query}\n")

    async for event in team.stream(query=query, session_id=session_id):
        event_type = event.get("event")

        if event_type == "agent":
            agent_id = event["data"].get("agent_id")
            print(f"📍 Agent 执行: {agent_id}")

        elif event_type == "result":
            deliverables = event["data"].get("deliverables", {})
            for key, value in deliverables.items():
                print(f"\n[{key}] 回答:")
                if isinstance(value, dict):
                    print(f"  {value.get('answer', '')[:100]}...")
                    print(f"  已解决: {value.get('resolved')}")

    # 保存经验
    print("\n💾 保存经验到向量库...")
    success = await team.save_experience(
        session_id=session_id,
        feedback={"stars": 5, "helpful": True},
    )
    if success:
        print("✅ 经验保存成功！")
    else:
        print("❌ 经验保存失败")

    return success


async def test_second_execution(team: Datapillar):
    """第二次执行：检索经验"""
    print("\n" + "=" * 60)
    print("🔍 第二次执行：检索相似经验")
    print("=" * 60)

    # 相似的问题
    query = "我想退掉昨天买的手机，流程是什么？"
    session_id = "learning_test_002"

    print(f"\n用户问题: {query}")
    print("（这是一个相似的退货问题，应该能检索到之前的经验）\n")

    async for event in team.stream(query=query, session_id=session_id):
        event_type = event.get("event")

        if event_type == "start":
            # 检查是否注入了经验上下文
            print("🚀 开始执行")

        elif event_type == "agent":
            agent_id = event["data"].get("agent_id")
            print(f"📍 Agent 执行: {agent_id}")

        elif event_type == "result":
            deliverables = event["data"].get("deliverables", {})
            for key, value in deliverables.items():
                print(f"\n[{key}] 回答:")
                if isinstance(value, dict):
                    print(f"  {value.get('answer', '')[:100]}...")
                    print(f"  已解决: {value.get('resolved')}")


async def main():
    """主函数"""
    # 清理旧的经验数据
    if os.path.exists(EXPERIENCE_PATH):
        shutil.rmtree(EXPERIENCE_PATH)
        print(f"🗑️ 已清理旧的经验数据: {EXPERIENCE_PATH}")

    # 配置 LLM 和经验存储
    datapillar_configure(
        llm={
            "provider": "glm",
            "model": GLM_MODEL,
            "api_key": GLM_API_KEY,
        },
        embedding={
            "provider": "glm",
            "model": "embedding-3",
            "api_key": GLM_API_KEY,
        },
        agent={
            "learning_store": {
                "type": "lance",
                "path": EXPERIENCE_PATH,
            },
        },
    )

    print("=" * 60)
    print("🧠 经验学习功能测试")
    print("   模型:", GLM_MODEL)
    print("   经验存储:", EXPERIENCE_PATH)
    print("=" * 60)

    # 创建团队
    team = create_learning_team()

    # 第一次执行：记录并保存经验
    success = await test_first_execution(team)

    if success:
        # 第二次执行：检索经验
        # 需要创建新的团队实例，模拟新的会话
        print("\n⏳ 等待1秒，确保经验已写入...")
        await asyncio.sleep(1)

        team2 = create_learning_team()
        await test_second_execution(team2)

    print("\n" + "=" * 60)
    print("✨ 经验学习测试完成")
    print("=" * 60)


if __name__ == "__main__":
    asyncio.run(main())
