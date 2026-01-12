"""
Datapillar OneAgentic 快速入门示例

本示例展示：
1. @tool 装饰器的使用（从简单到高级）
2. @agent 装饰器的完整声明式使用
3. Datapillar 团队的高级配置
4. 两个 Agent 组成的团队协作

运行命令：
    uv run python examples/quickstart.py
"""

import asyncio
from pydantic import BaseModel, Field

from datapillar_oneagentic import (
    # 装饰器
    agent,
    tool,
    # 核心类
    Datapillar,
    Process,
    AgentContext,
    Clarification,
    # 配置
    datapillar_configure,
    # A2A 远程调用（可选）
    A2AConfig,
    APIKeyAuth,
    # 存储后端（可选）
    MemoryCheckpointer,
    InMemoryDeliverableStore,
)


# ============================================================================
# GLM 配置
# ============================================================================
GLM_API_KEY = "da90d1098b0d4126848881f56ee2197c.B77DUfAuh4To29o7"
GLM_BASE_URL = "https://open.bigmodel.cn/api/paas/v4"
GLM_MODEL = "glm-4.7"


# ============================================================================
# 第一部分：@tool 装饰器使用示例（从简单到高级）
# ============================================================================


# --- 1. 最简单的用法：直接装饰函数 ---
# 工具名 = 函数名，docstring 自动解析为描述和参数说明
@tool
def search_products(keyword: str) -> str:
    """搜索商品目录

    Args:
        keyword: 搜索关键词
    """
    products = {
        "手机": ["iPhone 15 Pro (¥7999)", "Pixel 8 (¥4999)", "Galaxy S24 (¥5999)"],
        "电脑": ["MacBook Pro (¥14999)", "ThinkPad X1 (¥9999)", "Dell XPS (¥8999)"],
        "耳机": ["AirPods Pro (¥1899)", "Sony WH-1000XM5 (¥2499)", "Bose QC (¥2299)"],
    }
    for key, items in products.items():
        if key in keyword or keyword in key:
            return f"找到 {len(items)} 个商品:\n" + "\n".join(f"  - {item}" for item in items)
    return f"未找到与 '{keyword}' 相关的商品"


# --- 2. 自定义工具名称 ---
@tool("get_product_detail")
def fetch_detail(product_name: str) -> str:
    """获取商品详情

    Args:
        product_name: 商品名称
    """
    details = {
        "iPhone 15 Pro": "6.1英寸 OLED, A17 Pro芯片, 钛金属边框, 库存充足",
        "AirPods Pro": "主动降噪, H2芯片, 自适应音频, 库存充足",
        "MacBook Pro": "M3 Pro芯片, 18小时续航, 液晶视网膜屏, 库存紧张",
    }
    if product_name in details:
        return f"{product_name}: {details[product_name]}"
    return f"未找到 {product_name} 的详细信息"


# --- 3. 高级用法：使用 Pydantic Schema 定义复杂参数 ---
class OrderInput(BaseModel):
    """下单参数 Schema"""
    product_name: str = Field(description="商品名称")
    quantity: int = Field(default=1, ge=1, le=10, description="购买数量（1-10）")
    address: str = Field(description="收货地址")


@tool(args_schema=OrderInput)
def create_order(product_name: str, quantity: int, address: str) -> str:
    """创建订单

    Args:
        product_name: 商品名称
        quantity: 购买数量
        address: 收货地址
    """
    order_id = f"ORD{abs(hash(product_name + address)) % 100000:05d}"
    return f"✅ 订单创建成功！\n  订单号: {order_id}\n  商品: {product_name} x {quantity}\n  配送至: {address}"


# --- 4. 异步工具（适合 IO 密集型操作）---
@tool
async def check_inventory(product_name: str) -> str:
    """查询库存状态

    Args:
        product_name: 商品名称
    """
    await asyncio.sleep(0.1)  # 模拟异步 IO
    inventory = {"iPhone 15 Pro": 100, "AirPods Pro": 200, "MacBook Pro": 5}
    stock = inventory.get(product_name, 0)
    if stock > 50:
        return f"✅ {product_name} 库存充足（{stock}件）"
    elif stock > 0:
        return f"⚠️ {product_name} 库存紧张（仅剩{stock}件）"
    return f"❌ {product_name} 暂时缺货"


# ============================================================================
# 第二部分：定义交付物 Schema（Pydantic 模型）
# ============================================================================


class ProductAnalysis(BaseModel):
    """商品分析结果"""
    recommended_products: list[str] = Field(description="推荐商品列表")
    reason: str = Field(description="推荐理由")
    price_range: str = Field(description="价格区间")
    confidence: float = Field(ge=0, le=1, description="推荐置信度（0-1）")


class OrderResult(BaseModel):
    """下单结果"""
    success: bool = Field(description="是否成功")
    order_id: str | None = Field(default=None, description="订单号")
    message: str = Field(description="结果说明")


# ============================================================================
# 第三部分：@agent 装饰器 - 展示所有参数
# ============================================================================


@agent(
    # === 必填参数 ===
    id="shopping_advisor",                    # 唯一标识（小写字母开头，只能含小写字母、数字、下划线）
    name="购物顾问",                           # 显示名称

    # === 能力声明 ===
    description="根据用户需求推荐商品",         # 能力描述（用于团队协作时的介绍）
    tools=["search_products", "get_product_detail", "check_inventory"],  # 工具列表

    # === 交付物契约 ===
    deliverable_schema=ProductAnalysis,        # 交付物数据结构（Pydantic 模型）
    deliverable_key="analysis",                # 交付物标识（用于存储和下游获取）

    # === 执行配置 ===
    temperature=0.3,                           # LLM 温度（0-2，越高越有创造性）
    max_steps=10,                              # 最大工具调用次数

    # === 知识配置（可选）===
    knowledge_domains=[],                      # 知识领域 ID 列表（需要先注册知识）

    # === A2A 远程 Agent（可选）===
    # a2a_agents=[                             # 可调用的远程 Agent
    #     A2AConfig(
    #         endpoint="https://api.example.com/.well-known/agent-card.json",
    #         auth=APIKeyAuth(api_key="sk-xxx"),
    #     ),
    # ],
)
class ShoppingAdvisorAgent:
    """
    购物顾问 Agent

    展示 Agent 的完整工作节奏：
    1. 理解用户需求
    2. 调用工具搜索商品
    3. 查询详情和库存
    4. 综合分析给出推荐
    """

    SYSTEM_PROMPT = """你是一位专业的购物顾问。

## 工作流程
1. 理解用户的购物需求
2. 使用 search_products 搜索相关商品
3. 使用 get_product_detail 查看详情
4. 使用 check_inventory 确认库存
5. 综合分析，给出推荐

## 输出要求
请以 JSON 格式输出，包含以下字段：
- recommended_products: 推荐商品列表（字符串数组）
- reason: 推荐理由（字符串）
- price_range: 价格区间（字符串）
- confidence: 推荐置信度（0-1 的数字）

## 注意事项
- 如果需求不明确，置信度设为 0.5 以下
- 优先推荐库存充足的商品
- 考虑性价比
"""

    async def run(self, ctx: AgentContext) -> ProductAnalysis | Clarification:
        """Agent 核心执行方法"""
        # 1. 构建消息（自动注入上下文）
        messages = ctx.build_messages(self.SYSTEM_PROMPT)

        # 2. 工具调用循环（ReAct 风格：思考-行动-观察）
        messages = await ctx.invoke_tools(messages)

        # 3. 获取结构化输出
        output: ProductAnalysis = await ctx.get_output(messages)

        # 4. 业务判断：置信度低时请求澄清
        if output.confidence < 0.5:
            return ctx.clarify(
                message="需求不够明确，请补充信息",
                questions=["您的预算范围是多少？", "有品牌偏好吗？"],
            )

        return output


@agent(
    id="order_agent",
    name="订单助手",
    description="协助用户完成下单",
    tools=["create_order", "check_inventory"],
    deliverable_schema=OrderResult,
    deliverable_key="order",
    temperature=0.0,  # 下单需要精确，温度设为 0
    max_steps=5,
)
class OrderAgent:
    """订单助手 Agent"""

    SYSTEM_PROMPT = """你是订单助手，负责协助用户完成下单。

## 工作流程
1. 确认用户要购买的商品
2. 使用 check_inventory 确认库存
3. 使用 create_order 创建订单

## 输出要求
请以 JSON 格式输出：
- success: 是否成功（布尔值）
- order_id: 订单号（字符串，失败时为 null）
- message: 结果说明（字符串）
"""

    async def run(self, ctx: AgentContext) -> OrderResult | Clarification:
        """订单处理逻辑"""
        # 检查是否有收货地址
        if "地址" not in ctx.query and "送到" not in ctx.query and "配送" not in ctx.query:
            return ctx.clarify(
                message="请提供收货信息",
                questions=["您的收货地址是？"],
            )

        messages = ctx.build_messages(self.SYSTEM_PROMPT)
        messages = await ctx.invoke_tools(messages)
        return await ctx.get_output(messages)


# ============================================================================
# 第四部分：Datapillar 团队 - 展示所有高级参数
# ============================================================================


def create_shopping_team() -> Datapillar:
    """
    创建购物助手团队

    展示 Datapillar 的所有配置参数
    """
    team = Datapillar(
        # === 必填参数 ===
        name="购物助手团队",                    # 团队名称（全局唯一）
        agents=[ShoppingAdvisorAgent],         # 单 Agent 演示（简化流程）

        # === 执行模式 ===
        process=Process.SEQUENTIAL,            # SEQUENTIAL: 顺序执行
                                               # DYNAMIC: 动态委派（Agent 自主决定）

        # === 存储后端（可选，不传则用内存）===
        checkpointer=MemoryCheckpointer(),          # 状态持久化（支持 Redis/Postgres/SQLite）
        deliverable_store=InMemoryDeliverableStore(),  # 交付物存储（支持 Redis/Postgres）
        # learning_store=LanceVectorStore(path="./data/experience"),  # 经验学习存储

        # === 功能开关 ===
        enable_memory=True,                    # 启用对话记忆（默认 True）
        enable_learning=False,                 # 启用经验学习（默认 False，需配置 learning_store）
        enable_react=False,                    # 启用 ReAct 规划模式（默认 False）

        # === 调试 ===
        verbose=True,                          # 输出详细日志
    )
    return team


# ============================================================================
# 第五部分：运行示例
# ============================================================================


async def main():
    """主函数"""
    # 配置 LLM（使用 GLM）
    datapillar_configure(
        llm={
            "api_key": GLM_API_KEY,
            "base_url": GLM_BASE_URL,
            "model": GLM_MODEL,
            "timeout_seconds": 60,
            "retry": {"max_retries": 2},
        }
    )

    # 创建团队
    team = create_shopping_team()

    print("=" * 60)
    print("🛒 购物助手团队已就绪")
    print(f"   模型: {GLM_MODEL}")
    print(f"   成员: 购物顾问")
    print("=" * 60)

    # 示例查询
    query = "我想买一个降噪耳机，预算2000左右，送到北京市朝阳区望京SOHO"

    print(f"\n📝 用户需求: {query}\n")
    print("-" * 60)

    # 流式执行
    async for event in team.stream(
        query=query,
        session_id="demo_001",
        user_id="test_user",
    ):
        event_type = event.get("event")
        data = event.get("data", {})

        if event_type == "agent_start":
            print(f"\n🤖 [{data.get('agent_name')}] 开始工作...")
        elif event_type == "tool_call":
            print(f"   🔧 调用: {data.get('tool_name')}")
        elif event_type == "tool_result":
            result = data.get("result", "")
            if len(result) > 100:
                result = result[:100] + "..."
            print(f"   📋 结果: {result}")
        elif event_type == "agent_complete":
            print(f"   ✅ 完成")
        elif event_type == "clarification":
            print(f"\n❓ 需要澄清: {data.get('message')}")
            for q in data.get("questions", []):
                print(f"   - {q}")
        elif event_type == "result":
            print(f"\n{'=' * 60}")
            print("📦 最终结果:")
            deliverables = data.get("deliverables", {})
            for key, value in deliverables.items():
                print(f"\n[{key}]")
                if isinstance(value, dict):
                    for k, v in value.items():
                        print(f"  {k}: {v}")
                else:
                    print(f"  {value}")
        elif event_type == "error":
            print(f"\n❌ 错误: {data.get('detail')}")

    print("\n" + "=" * 60)
    print("✨ 演示完成")


if __name__ == "__main__":
    asyncio.run(main())
