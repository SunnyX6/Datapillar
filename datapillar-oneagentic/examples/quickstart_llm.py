"""
Datapillar OneAgentic LLM + Messages quickstart.

Run:
    uv run python examples/quickstart_llm.py

Requirements:
    export DATAPILLAR_LLM_PROVIDER="openai"              # openai | anthropic | glm | deepseek | openrouter | ollama
    export DATAPILLAR_LLM_API_KEY="sk-xxx"
    export DATAPILLAR_LLM_MODEL="gpt-4o"
    # Optional: export DATAPILLAR_LLM_BASE_URL="https://api.openai.com/v1"
    # Optional: export DATAPILLAR_LLM_ENABLE_THINKING="false"
"""

from __future__ import annotations

import asyncio
import json
import logging
from dataclasses import asdict

from datapillar_oneagentic import DatapillarConfig
from datapillar_oneagentic.log import setup_logging
from datapillar_oneagentic.messages import Message, Messages
from datapillar_oneagentic.providers.llm import LLMProvider, Provider, extract_usage

logger = logging.getLogger(__name__)


async def main() -> None:
    setup_logging(logging.INFO)
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

    llm = LLMProvider(config.llm)()

    messages = Messages().system(
        "You are a precise technical assistant. Keep responses concise and accurate."
    ).user(
        "Summarize Datapillar's value in one sentence."
    )

    response = await llm.ainvoke(messages)
    if not isinstance(response, Message):
        raise TypeError("LLM returned an unexpected type; expected Message")
    messages.append(response)

    logger.info("First reply: role=%s content=%s", response.role, response.content)

    usage = extract_usage(response)
    if usage:
        logger.info("Usage: %s", json.dumps(asdict(usage), ensure_ascii=False))


if __name__ == "__main__":
    asyncio.run(main())
