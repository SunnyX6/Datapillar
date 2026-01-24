"""
Datapillar OneAgentic 快速入门示例

本示例展示：
1. @tool 装饰器的使用（从简单到高级）
2. @agent 装饰器的完整声明式使用
3. Datapillar 团队的高级配置
4. 两个 Agent 组成的团队协作

运行命令：
    uv run python examples/quickstart.py

配置要求：
    1) LLM（团队执行需要）
       export DATAPILLAR_LLM_PROVIDER="openai"              # openai | anthropic | glm | deepseek | openrouter | ollama
       export DATAPILLAR_LLM_API_KEY="sk-xxx"
       export DATAPILLAR_LLM_MODEL="gpt-4o"
       # 可选：export DATAPILLAR_LLM_BASE_URL="https://api.openai.com/v1"
       # 可选：export DATAPILLAR_LLM_ENABLE_THINKING="false"
    2) Embedding（知识检索需要）
       export DATAPILLAR_EMBEDDING_PROVIDER="openai"        # openai | glm
       export DATAPILLAR_EMBEDDING_API_KEY="sk-xxx"
       export DATAPILLAR_EMBEDDING_MODEL="text-embedding-3-small"
       export DATAPILLAR_EMBEDDING_DIMENSION="1536"
       # 可选：export DATAPILLAR_EMBEDDING_BASE_URL="https://api.openai.com/v1"
"""

import asyncio
import json
import logging
from pydantic import BaseModel, Field

def _setup_example_logging() -> None:
    """示例脚本负责配置日志输出（不要在框架里改 root logger）。"""
    handler = logging.StreamHandler()
    handler.setFormatter(logging.Formatter("%(message)s"))
    dp_logger = logging.getLogger("datapillar_oneagentic")
    dp_logger.handlers.clear()
    dp_logger.addHandler(handler)
    # 默认不打开 DEBUG：避免 stream 场景下重复刷堆栈；结构化输出失败会用 ERROR 打印原始 LLM 输出用于调试。
    dp_logger.setLevel(logging.INFO)
    dp_logger.propagate = False


_setup_example_logging()

from datapillar_oneagentic import (
    # 装饰器
    agent,
    tool,
    # 核心类
    Datapillar,
    Process,
    AgentContext,
    # 配置
    DatapillarConfig,
)
from datapillar_oneagentic.knowledge import Knowledge, KnowledgeConfig, KnowledgeSource
from datapillar_oneagentic.providers.llm import EmbeddingBackend, Provider


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
    tools=[search_products, fetch_detail, check_inventory],  # 工具列表

    # === 交付物契约 ===
    deliverable_schema=ProductAnalysis,        # 交付物数据结构（Pydantic 模型）
    # 注意：交付物统一用 agent_id 存储和获取，无需单独指定 key

    # === 执行配置 ===
    # 结构化输出示例不要赌运气：温度设为 0，避免时好时坏。
    temperature=0.0,
    max_steps=10,                              # 最大工具调用次数

    # === 知识配置（可选）===
    # 注意：启用知识检索需要配置 knowledge.base_config.embedding
    knowledge=Knowledge(
        sources=[
            KnowledgeSource(
                name="示例知识库",
                source_type="doc",
                source_uri="kb_demo",
            )
        ],
    ),

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
完成分析后，你必须以纯 JSON 格式输出结果，不要包含任何其他文字：
```json
{
  "recommended_products": ["商品1", "商品2"],
  "reason": "推荐理由",
  "price_range": "价格区间如 1000-2000元",
  "confidence": 0.8
}
```

字段说明：
- recommended_products: 推荐商品列表（字符串数组）
- reason: 推荐理由（字符串）
- price_range: 价格区间（字符串）
- confidence: 推荐置信度（0-1 的数字，需求明确时 > 0.5）

## 注意事项
- 如果需求不明确，confidence 设为 0.5 以下
- 优先推荐库存充足的商品
- 考虑性价比
"""

    async def run(self, ctx: AgentContext) -> ProductAnalysis:
        """Agent 核心执行方法"""
        # 1. 构建消息（自动注入上下文）
        messages = ctx.build_messages(self.SYSTEM_PROMPT)

        # 2. 工具调用循环（ReAct 风格：思考-行动-观察）
        messages = await ctx.invoke_tools(messages)

        # 3. 获取结构化输出
        output: ProductAnalysis = await ctx.get_structured_output(messages)

        # 4. 业务判断：置信度低时请求澄清
        if output.confidence < 0.5:
            ctx.interrupt("需求不够明确，请补充信息")
            output = await ctx.get_structured_output(messages)

        return output


@agent(
    id="order_agent",
    name="订单助手",
    description="协助用户完成下单",
    tools=[create_order, check_inventory],
    deliverable_schema=OrderResult,
    temperature=0.0,  # 下单需要精确，温度设为 0
    max_steps=5,
)
class OrderAgent:
    """订单助手 Agent - 演示如何获取上游 Agent 的产出"""

    SYSTEM_PROMPT = """你是订单助手，负责协助用户完成下单。

## 上游推荐结果
{upstream_result}

## 工作流程
1. 根据上游推荐的商品，使用 check_inventory 确认库存
2. 使用 create_order 创建订单（商品名、数量1、用户提供的收货地址）

## 输出要求
完成下单后，你必须以纯 JSON 格式输出结果，不要包含任何其他文字：
```json
{{
  "success": true,
  "order_id": "订单号",
  "message": "订单创建成功说明"
}}
```

失败时也必须输出同结构 JSON（order_id 为 null）：
```json
{{
  "success": false,
  "order_id": null,
  "message": "失败原因"
}}
```"""

    async def run(self, ctx: AgentContext) -> OrderResult:
        """订单处理逻辑 - 演示获取上游 Agent 产出"""

        # === 从 store 获取上游 Agent 的产出 ===
        analysis = await ctx.get_deliverable(agent_id="shopping_advisor")

        if analysis:
            print(f"\n📥 获取到上游 Agent [shopping_advisor] 的产出:")
            print(f"   推荐商品: {analysis.get('recommended_products', [])}")
            print(f"   推荐理由: {analysis.get('reason', '')[:50]}...")
        else:
            print("\n⚠️ 未获取到上游 Agent [shopping_advisor] 的产出")
            ctx.interrupt("没有找到推荐商品，请先让购物顾问推荐商品")
            analysis = await ctx.get_deliverable(agent_id="shopping_advisor")
            if not analysis:
                return OrderResult(success=False, order_id=None, message="未获取到推荐商品")

        # 构建上游结果描述，传给 LLM
        upstream_result = (
            f"推荐商品: {analysis.get('recommended_products', [])}\n"
            f"推荐理由: {analysis.get('reason', '')}\n"
            f"价格区间: {analysis.get('price_range', '')}"
        )

        # 构建消息，注入上游产物信息
        prompt = self.SYSTEM_PROMPT.format(upstream_result=upstream_result)
        messages = ctx.build_messages(prompt)

        # 打印 messages 验证上下文共享
        print(f"\n🔍 验证跨 Agent 消息共享:")
        print(f"   消息数量: {len(messages)}")
        for i, msg in enumerate(messages):
            msg_type = type(msg).__name__
            content_preview = str(msg.content)[:80] if hasattr(msg, 'content') else ''
            print(f"   [{i}] {msg_type}: {content_preview}...")

        # 检查是否有收货地址
        if "地址" not in ctx.query and "送到" not in ctx.query and "配送" not in ctx.query:
            ctx.interrupt("请提供收货信息")

        messages = await ctx.invoke_tools(messages)
        return await ctx.get_structured_output(messages)


# ============================================================================
# 第四部分：Datapillar 团队 - 展示所有高级参数
# ============================================================================


def create_shopping_team(config: DatapillarConfig) -> Datapillar:
    """
    创建购物助手团队

    展示 Datapillar 的所有配置参数
    演示两个 Agent 顺序执行，下游 Agent 获取上游产出
    """
    team = Datapillar(
        # === 必填参数 ===
        config=config,
        namespace="shopping_demo",                 # 命名空间（数据隔离边界）
        name="购物助手团队",                        # 团队名称
        agents=[ShoppingAdvisorAgent, OrderAgent],  # 两个 Agent 顺序执行

        # === 执行模式 ===
        process=Process.SEQUENTIAL,            # SEQUENTIAL: 顺序执行
                                               # DYNAMIC: 动态委派（Agent 自主决定）

        # === 功能开关 ===
        enable_share_context=True,             # 启用 Agent 间上下文共享（默认 True）
        enable_learning=False,                 # 启用经验学习（默认 False）

        # === 调试 ===
        verbose=True,                          # 输出详细日志
    )
    return team


# ============================================================================
# 第五部分：运行示例
# ============================================================================


async def main():
    """主函数"""
    config = DatapillarConfig()
    if not config.llm.is_configured():
        supported = ", ".join(Provider.list_supported())
        raise RuntimeError(
            "请先配置 LLM：\n"
            "  export DATAPILLAR_LLM_PROVIDER=\"openai\"\n"
            "  export DATAPILLAR_LLM_API_KEY=\"sk-xxx\"\n"
            "  export DATAPILLAR_LLM_MODEL=\"gpt-4o\"\n"
            "可选：export DATAPILLAR_LLM_BASE_URL=\"https://api.openai.com/v1\"\n"
            "可选：export DATAPILLAR_LLM_ENABLE_THINKING=\"false\"\n"
            f"支持 provider: {supported}"
        )

    if not config.embedding.is_configured():
        supported = ", ".join(EmbeddingBackend.list_supported())
        raise RuntimeError(
            "请先配置 Embedding：\n"
            "  export DATAPILLAR_EMBEDDING_PROVIDER=\"openai\"\n"
            "  export DATAPILLAR_EMBEDDING_API_KEY=\"sk-xxx\"\n"
            "  export DATAPILLAR_EMBEDDING_MODEL=\"text-embedding-3-small\"\n"
            "  export DATAPILLAR_EMBEDDING_DIMENSION=\"1536\"\n"
            "可选：export DATAPILLAR_EMBEDDING_BASE_URL=\"https://api.openai.com/v1\"\n"
            f"支持 provider: {supported}"
        )

    # quickstart.py 固定使用 Lance 本地向量库，避免用户还要额外配 vector_store。
    config.knowledge = KnowledgeConfig(
        base_config={
            "embedding": config.embedding.model_dump(),
            "vector_store": {"type": "lance", "path": "./data/vectors"},
        }
    )

    # 创建团队
    team = create_shopping_team(config)

    print("=" * 60)
    print("🛒 购物助手团队已就绪")
    print(f"   模型: {config.llm.model}")
    print(f"   成员: 购物顾问 -> 订单助手（顺序执行）")
    print("   演示: 下游 Agent 通过 ctx.get_deliverable() 获取上游产出")
    print("=" * 60)

    # 示例查询
    query = "我想买一个降噪耳机，预算2000左右，送到北京市朝阳区望京SOHO"

    print(f"\n📝 用户需求: {query}\n")
    print("-" * 60)

    deliverables: dict[str, dict] = {}

    # 流式执行
    async for event in team.stream(
        query=query,
        session_id="demo_001",
    ):
        event_type = event.get("event")
        data = event.get("data", {})
        if event_type == "agent.start":
            agent = event.get("agent", {})
            print(f"\n🤖 [{agent.get('name')}] 开始工作...")
        elif event_type == "agent.thinking":
            message = data.get("message", {})
            thinking = message.get("content", "")
            if thinking:
                agent = event.get("agent", {})
                print(f"\n🧠 [{agent.get('id')}] 思考中...")
                if len(thinking) > 200:
                    print(f"   {thinking[:200]}...")
                else:
                    print(f"   {thinking}")
        elif event_type == "tool.call":
            tool = data.get("tool", {})
            print(f"   🔧 调用: {tool.get('name')}")
        elif event_type == "tool.result":
            tool = data.get("tool", {})
            result = str(tool.get("output", ""))
            if len(result) > 100:
                result = result[:100] + "..."
            print(f"   📋 结果: {result}")
        elif event_type == "agent.end":
            agent = event.get("agent", {})
            agent_id = agent.get("id")
            deliverable = data.get("deliverable")
            if agent_id and deliverable is not None:
                deliverables[agent_id] = deliverable
                print("   ✅ 完成")
                print(f"   📦 交付物: {json.dumps(deliverable, ensure_ascii=False)}")
        elif event_type == "agent.interrupt":
            interrupt_payload = data.get("interrupt", {}).get("payload")
            print(f"\n❓ 需要用户输入: {interrupt_payload}")
        elif event_type == "agent.failed":
            error = data.get("error", {})
            print(f"\n❌ 错误: {error.get('detail') or error.get('message')}")

    print(f"\n{'=' * 60}")
    print("📦 最终结果:")
    for key, value in deliverables.items():
        print(f"\n[{key}]")
        if isinstance(value, dict):
            for k, v in value.items():
                print(f"  {k}: {v}")
        else:
            print(f"  {value}")

    # === 验证 deliverable 存储 ===
    print("\n" + "-" * 60)
    print("🧪 验证 deliverable 存储（统一用 agent_id）:")
    if "shopping_advisor" in deliverables:
        print("  ✅ 正确：deliverable key 是 agent_id (shopping_advisor)")
    elif "analysis" in deliverables:
        print("  ❌ 错误：deliverable key 仍是旧的 deliverable_key (analysis)")
    else:
        print(f"  ⚠️ deliverable keys: {list(deliverables.keys())}")

    print("\n" + "=" * 60)
    print("✨ 演示完成")


if __name__ == "__main__":
    asyncio.run(main())
