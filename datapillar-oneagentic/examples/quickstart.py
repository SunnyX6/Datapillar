# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""
Datapillar OneAgentic quickstart example.

This example shows:
1. @tool decorator usage (simple to advanced)
2. @agent decorator with full declarative spec
3. Advanced Datapillar team configuration
4. Two-agent collaboration

Run:
    uv run python examples/quickstart.py

Requirements:
    1) LLM (team execution)
       export DATAPILLAR_LLM_PROVIDER="openai"              # openai | anthropic | glm | deepseek | openrouter | ollama
       export DATAPILLAR_LLM_API_KEY="sk-xxx"
       export DATAPILLAR_LLM_MODEL="gpt-4o"
       # Optional: export DATAPILLAR_LLM_BASE_URL="https://api.openai.com/v1"
       # Optional: export DATAPILLAR_LLM_ENABLE_THINKING="false"
    2) Embedding (knowledge retrieval)
       export DATAPILLAR_EMBEDDING_PROVIDER="openai"        # openai | glm
       export DATAPILLAR_EMBEDDING_API_KEY="sk-xxx"
       export DATAPILLAR_EMBEDDING_MODEL="text-embedding-3-small"
       export DATAPILLAR_EMBEDDING_DIMENSION="1536"
       # Optional: export DATAPILLAR_EMBEDDING_BASE_URL="https://api.openai.com/v1"
"""

import asyncio
import json
import logging
from pydantic import BaseModel, Field

from datapillar_oneagentic.log import setup_logging

def _setup_example_logging() -> None:
    """Configure example logging (align with framework defaults)."""
    setup_logging(logging.INFO)


_setup_example_logging()

from datapillar_oneagentic import (
    # Decorators
    agent,
    tool,
    # Core classes
    Datapillar,
    Process,
    AgentContext,
    # Config
    DatapillarConfig,
)
from datapillar_oneagentic.knowledge import (
    BM25SparseEmbedder,
    KnowledgeConfig,
    KnowledgeSource,
)
from datapillar_oneagentic.providers.llm import EmbeddingBackend, Provider


# ============================================================================
# Part 1: @tool decorator usage (simple to advanced)
# ============================================================================

TEAM_NAMESPACE = "shopping_demo"

DEMO_KNOWLEDGE_TEXT = (
    "Noise-canceling headphones emphasize comfort, ANC, and battery life.\n"
    "Budget options under $200 often include multi-device pairing and USB-C charging.\n"
    "Orders require a shipping address and preferred delivery city.\n"
)

DEMO_SPARSE_EMBEDDER = BM25SparseEmbedder()

DEMO_KNOWLEDGE_SOURCE = KnowledgeSource(
    source=DEMO_KNOWLEDGE_TEXT,
    chunk={
        "mode": "general",
        "general": {"max_tokens": 200, "overlap": 40},
    },
    name="Example knowledge base",
    source_type="doc",
    filename="kb_demo.txt",
)

# --- 1. Simplest usage: decorate a function ---
# Tool name = function name; docstring is used for description/args.
@tool
def search_products(keyword: str) -> str:
    """Search product catalog.

    Args:
        keyword: search keyword
    """
    products = {
        "phones": ["iPhone 15 Pro ($799)", "Pixel 8 ($499)", "Galaxy S24 ($599)"],
        "laptops": ["MacBook Pro ($1499)", "ThinkPad X1 ($999)", "Dell XPS ($899)"],
        "headphones": ["AirPods Pro ($189)", "Sony WH-1000XM5 ($249)", "Bose QC ($229)"],
    }
    for key, items in products.items():
        if key in keyword or keyword in key:
            return f"Found {len(items)} products:\n" + "\n".join(f"  - {item}" for item in items)
    return f"No products found for '{keyword}'"


# --- 2. Custom tool name ---
@tool("get_product_detail")
def fetch_detail(product_name: str) -> str:
    """Get product details.

    Args:
        product_name: product name
    """
    details = {
        "iPhone 15 Pro": "6.1-inch OLED, A17 Pro chip, titanium frame, in stock",
        "AirPods Pro": "Active noise canceling, H2 chip, adaptive audio, in stock",
        "MacBook Pro": "M3 Pro chip, 18-hour battery, Retina display, low stock",
    }
    if product_name in details:
        return f"{product_name}: {details[product_name]}"
    return f"No details found for {product_name}"


# --- 3. Advanced usage: Pydantic schema for complex params ---
class OrderInput(BaseModel):
    """Order input schema."""
    product_name: str = Field(description="Product name")
    quantity: int = Field(default=1, ge=1, le=10, description="Quantity (1-10)")
    address: str = Field(description="Shipping address")


@tool(args_schema=OrderInput)
def create_order(product_name: str, quantity: int, address: str) -> str:
    """Create order.

    Args:
        product_name: product name
        quantity: quantity
        address: shipping address
    """
    order_id = f"ORD{abs(hash(product_name + address)) % 100000:05d}"
    return (
        "Order created successfully.\n"
        f"  Order ID: {order_id}\n"
        f"  Product: {product_name} x {quantity}\n"
        f"  Ship to: {address}"
    )


# --- 4. Async tool (IO-bound) ---
@tool
async def check_inventory(product_name: str) -> str:
    """Check inventory status.

    Args:
        product_name: product name
    """
    await asyncio.sleep(0.1)  # Simulate async IO.
    inventory = {"iPhone 15 Pro": 100, "AirPods Pro": 200, "MacBook Pro": 5}
    stock = inventory.get(product_name, 0)
    if stock > 50:
        return f"{product_name} in stock ({stock} units)"
    elif stock > 0:
        return f"{product_name} low stock (only {stock} left)"
    return f"{product_name} out of stock"


# ============================================================================
# Part 2: Deliverable schemas (Pydantic models)
# ============================================================================


class ProductAnalysis(BaseModel):
    """Product analysis result."""
    recommended_products: list[str] = Field(description="Recommended products")
    reason: str = Field(description="Recommendation rationale")
    price_range: str = Field(description="Price range")
    confidence: float = Field(ge=0, le=1, description="Confidence (0-1)")


class OrderResult(BaseModel):
    """Order result."""
    success: bool = Field(description="Whether order succeeded")
    order_id: str | None = Field(default=None, description="Order ID")
    message: str = Field(description="Result message")


# ============================================================================
# Part 3: @agent decorator - full parameter showcase
# ============================================================================


@agent(
    # === Required ===
    id="shopping_advisor",                    # Identifier (lowercase letter start; [a-z0-9_])
    name="Shopping Advisor",                  # Display name

    # === Capabilities ===
    description="Recommend products based on user needs",
    tools=[search_products, fetch_detail, check_inventory],

    # === Deliverable contract ===
    deliverable_schema=ProductAnalysis,        # Pydantic schema
    # Deliverables are stored/retrieved by agent_id.

    # === Execution config ===
    # Structured output is sensitive to randomness; keep temperature at 0.
    temperature=0.0,
    max_steps=10,                              # Max tool calls

    # === A2A remote agents (optional) ===
    # a2a_agents=[                             # Remote agents you can delegate to
    #     A2AConfig(
    #         endpoint="https://api.example.com/.well-known/agent-card.json",
    #         auth=APIKeyAuth(api_key="sk-xxx"),
    #     ),
    # ],
)
class ShoppingAdvisorAgent:
    """
    Shopping advisor agent.

    Demonstrates the full workflow:
    1. Understand user needs
    2. Search products via tools
    3. Check details and inventory
    4. Analyze and recommend
    """

    SYSTEM_PROMPT = """You are a professional shopping advisor.

## Workflow
1. Understand the user's shopping needs
2. Use search_products to find relevant items
3. Use get_product_detail for details
4. Use check_inventory to confirm stock
5. Analyze and recommend

## Output requirements
Return pure JSON only, with no extra text:
```json
{
  "recommended_products": ["Product 1", "Product 2"],
  "reason": "Recommendation rationale",
  "price_range": "Price range such as $100-$200",
  "confidence": 0.8
}
```

Field notes:
- recommended_products: list of product names
- reason: recommendation rationale
- price_range: price range as string
- confidence: confidence between 0-1 (use > 0.5 when requirements are clear)

## Notes
- If requirements are unclear, set confidence below 0.5
- Prefer items in stock
- Consider value for money
"""

    async def run(self, ctx: AgentContext) -> ProductAnalysis:
        """Core execution method."""
        # 1. Build messages.
        messages = ctx.messages().system(self.SYSTEM_PROMPT).user(ctx.query)

        # 2. Tool loop (ReAct style: think-act-observe).
        messages = await ctx.invoke_tools(messages)

        # 3. Get structured output.
        output: ProductAnalysis = await ctx.get_structured_output(messages)

        # 4. Business logic: request clarification if confidence is low.
        if output.confidence < 0.5:
            ctx.interrupt("Requirements are unclear. Please provide more details.")
            output = await ctx.get_structured_output(messages)

        return output


@agent(
    id="order_agent",
    name="Order Assistant",
    description="Help the user place an order",
    tools=[create_order, check_inventory],
    deliverable_schema=OrderResult,
    temperature=0.0,  # Ordering requires precision.
    max_steps=5,
)
class OrderAgent:
    """Order assistant agent demonstrating upstream deliverable usage."""

    SYSTEM_PROMPT = """You are the order assistant responsible for completing checkout.

## Upstream recommendation
{upstream_result}

## Workflow
1. Use check_inventory to confirm stock for recommended products
2. Use create_order to create the order (product name, quantity=1, user-provided address)

## Output requirements
Return pure JSON only, with no extra text:
```json
{{
  "success": true,
  "order_id": "ORDER_ID",
  "message": "Order created successfully"
}}
```

On failure, return the same JSON schema (order_id is null):
```json
{{
  "success": false,
  "order_id": null,
  "message": "Failure reason"
}}
```"""

    async def run(self, ctx: AgentContext) -> OrderResult:
        """Order handling logic using upstream agent output."""

        # === Fetch upstream agent deliverable from store ===
        analysis = await ctx.get_deliverable(agent_id="shopping_advisor")

        if analysis:
            print("\nUpstream deliverable received from [shopping_advisor]:")
            print(f"  Recommended products: {analysis.get('recommended_products', [])}")
            print(f"  Rationale: {analysis.get('reason', '')[:50]}...")
        else:
            print("\nUpstream deliverable from [shopping_advisor] was not found")
            ctx.interrupt("No recommended products found. Ask the shopping advisor to recommend products first.")
            analysis = await ctx.get_deliverable(agent_id="shopping_advisor")
            if not analysis:
                return OrderResult(success=False, order_id=None, message="No recommended products found")

        # Build upstream summary for the LLM.
        upstream_result = (
            f"Recommended products: {analysis.get('recommended_products', [])}\n"
            f"Rationale: {analysis.get('reason', '')}\n"
            f"Price range: {analysis.get('price_range', '')}"
        )

        # Build messages with upstream context.
        prompt = self.SYSTEM_PROMPT.format(upstream_result=upstream_result)
        messages = ctx.messages().system(prompt).user(ctx.query)

        # Log messages to preview the user-defined message sequence.
        print("\nMessage sequence preview (user-defined):")
        print(f"  Message count: {len(messages)}")
        for i, msg in enumerate(messages):
            msg_type = type(msg).__name__
            content_preview = str(msg.content)[:80] if hasattr(msg, 'content') else ''
            print(f"  [{i}] {msg_type}: {content_preview}...")

        # Check if shipping address is present in the user query.
        if "address" not in ctx.query.lower() and "ship to" not in ctx.query.lower() and "deliver to" not in ctx.query.lower():
            ctx.interrupt("Please provide a shipping address.")

        messages = await ctx.invoke_tools(messages)
        return await ctx.get_structured_output(messages)


# ============================================================================
# Part 4: Datapillar team - advanced parameters
# ============================================================================


def create_shopping_team(config: DatapillarConfig, knowledge_config: KnowledgeConfig) -> Datapillar:
    """
    Create the shopping assistant team.

    Demonstrates Datapillar configuration and sequential execution,
    with downstream agents consuming upstream deliverables.
    """
    team = Datapillar(
        # === Required ===
        config=config,
        namespace=TEAM_NAMESPACE,                  # Namespace (data isolation boundary)
        name="Shopping Assistant Team",            # Team name
        agents=[ShoppingAdvisorAgent, OrderAgent],  # Two agents in sequence

        # === Execution mode ===
        process=Process.SEQUENTIAL,            # SEQUENTIAL: ordered execution
                                               # DYNAMIC: delegation by agents

        # === Feature flags ===
        enable_share_context=True,             # Share context across agents
        enable_learning=False,                 # Experience learning

        # === Debug ===
        verbose=True,                          # Verbose logging
        knowledge=knowledge_config,
    )
    return team


# ============================================================================
# Part 5: Run the example
# ============================================================================


async def main():
    """Main entry."""
    config = DatapillarConfig()
    if not config.llm.is_configured():
        supported = ", ".join(Provider.list_supported())
        raise RuntimeError(
            "Please configure LLM first:\n"
            "  export DATAPILLAR_LLM_PROVIDER=\"openai\"\n"
            "  export DATAPILLAR_LLM_API_KEY=\"sk-xxx\"\n"
            "  export DATAPILLAR_LLM_MODEL=\"gpt-4o\"\n"
            "Optional: export DATAPILLAR_LLM_BASE_URL=\"https://api.openai.com/v1\"\n"
            "Optional: export DATAPILLAR_LLM_ENABLE_THINKING=\"false\"\n"
            f"Supported providers: {supported}"
        )

    if not config.embedding.is_configured():
        supported = ", ".join(EmbeddingBackend.list_supported())
        raise RuntimeError(
            "Please configure embedding first:\n"
            "  export DATAPILLAR_EMBEDDING_PROVIDER=\"openai\"\n"
            "  export DATAPILLAR_EMBEDDING_API_KEY=\"sk-xxx\"\n"
            "  export DATAPILLAR_EMBEDDING_MODEL=\"text-embedding-3-small\"\n"
            "  export DATAPILLAR_EMBEDDING_DIMENSION=\"1536\"\n"
            "Optional: export DATAPILLAR_EMBEDDING_BASE_URL=\"https://api.openai.com/v1\"\n"
            f"Supported providers: {supported}"
        )

    # quickstart.py uses a local Lance vector store to avoid extra configuration.
    knowledge_config = KnowledgeConfig(
        namespaces=[TEAM_NAMESPACE],
        embedding=config.embedding.model_dump(),
        vector_store={"type": "lance", "path": "./data/vectors"},
    )

    await DEMO_KNOWLEDGE_SOURCE.ingest(
        config=knowledge_config,
        sparse_embedder=DEMO_SPARSE_EMBEDDER,
        namespace=TEAM_NAMESPACE,
    )

    # Create team.
    team = create_shopping_team(config, knowledge_config)

    print("=" * 60)
    print("Shopping assistant team is ready")
    print(f"  Model: {config.llm.model}")
    print("  Members: Shopping Advisor -> Order Assistant (sequential)")
    print("  Demo: downstream agent uses ctx.get_deliverable() for upstream output")
    print("=" * 60)

    # Example query.
    query = "I want noise-canceling headphones with a $200 budget, ship to 123 Main St, Seattle."

    print(f"\nUser request: {query}\n")
    print("-" * 60)

    deliverables: dict[str, dict] = {}

    # Stream execution.
    async for event in team.stream(
        query=query,
        session_id="demo_001",
    ):
        event_type = event.get("event")
        data = event.get("data", {})
        if event_type == "agent.start":
            agent = event.get("agent", {})
            print(f"\n[{agent.get('name')}] started...")
        elif event_type == "agent.thinking":
            message = data.get("message", {})
            thinking = message.get("content", "")
            if thinking:
                agent = event.get("agent", {})
                print(f"\n[{agent.get('id')}] thinking...")
                if len(thinking) > 200:
                    print(f"  {thinking[:200]}...")
                else:
                    print(f"  {thinking}")
        elif event_type == "tool.call":
            tool = data.get("tool", {})
            print(f"  Tool call: {tool.get('name')}")
        elif event_type == "tool.result":
            tool = data.get("tool", {})
            result = str(tool.get("output", ""))
            if len(result) > 100:
                result = result[:100] + "..."
            print(f"  Tool result: {result}")
        elif event_type == "agent.end":
            agent = event.get("agent", {})
            agent_id = agent.get("id")
            deliverable = data.get("deliverable")
            if agent_id and deliverable is not None:
                deliverables[agent_id] = deliverable
                print("  Completed")
                print(f"  Deliverable: {json.dumps(deliverable, ensure_ascii=False)}")
        elif event_type == "agent.interrupt":
            interrupt_payload = data.get("interrupt", {}).get("payload")
            print(f"\nUser input required: {interrupt_payload}")
        elif event_type == "agent.failed":
            error = data.get("error", {})
            print(f"\nError: {error.get('detail') or error.get('message')}")

    print(f"\n{'=' * 60}")
    print("Final results:")
    for key, value in deliverables.items():
        print(f"\n[{key}]")
        if isinstance(value, dict):
            for k, v in value.items():
                print(f"  {k}: {v}")
        else:
            print(f"  {value}")

    # === Verify deliverable storage ===
    print("\n" + "-" * 60)
    print("Verify deliverable storage (keyed by agent_id):")
    if "shopping_advisor" in deliverables:
        print("  OK: deliverable key is agent_id (shopping_advisor)")
    elif "analysis" in deliverables:
        print("  ERROR: deliverable key is still old deliverable_key (analysis)")
    else:
        print(f"  Deliverable keys: {list(deliverables.keys())}")

    print("\n" + "=" * 60)
    print("Demo completed")


if __name__ == "__main__":
    asyncio.run(main())
