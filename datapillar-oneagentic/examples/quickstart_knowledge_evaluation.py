"""
知识评估示例：切分与检索质量评估

运行命令：
    uv run python examples/quickstart_knowledge_evaluation.py

依赖安装：
    pip install datapillar-oneagentic[lance,knowledge]

Embedding 配置：
    export DATAPILLAR_EMBEDDING_PROVIDER="openai"          # openai | glm
    export DATAPILLAR_EMBEDDING_API_KEY="sk-xxx"
    export DATAPILLAR_EMBEDDING_MODEL="text-embedding-3-small"
    export DATAPILLAR_EMBEDDING_DIMENSION="1536"
    # 可选：export DATAPILLAR_EMBEDDING_BASE_URL="https://api.openai.com/v1"

说明：
    - 评估集文档无需传 source_id，系统根据 doc_id 自动派生。
"""

import asyncio
import json
from pathlib import Path

from datapillar_oneagentic import DatapillarConfig
from datapillar_oneagentic.providers.llm import EmbeddingBackend

from datapillar_oneagentic.knowledge import (
    KnowledgeChunkConfig,
    KnowledgeConfig,
    KnowledgeEvaluator,
    KnowledgeRetrieveConfig,
    load_eval_set,
)


async def main() -> None:
    config = DatapillarConfig()
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
    embedding_config = config.embedding.model_dump()

    evalset_path = Path(__file__).with_name("knowledge_evalset.json")
    evalset = load_eval_set(evalset_path)

    chunk_config = KnowledgeChunkConfig(
        mode="general",
        general={"max_tokens": 800, "overlap": 0},
    )
    retrieve_config = KnowledgeRetrieveConfig(
        method="semantic",
        top_k=max(evalset.k_values),
    )

    knowledge_config = KnowledgeConfig(
        base_config={
            "embedding": embedding_config,
            "vector_store": {"type": "lance", "path": "./data/vectors"},
        },
        chunk_config=chunk_config,
        retrieve_config=retrieve_config,
    )
    evaluator = await KnowledgeEvaluator.from_config(
        namespace="demo_knowledge_eval",
        config=knowledge_config,
    )

    report = await evaluator.evaluate(evalset)
    print("评估摘要：")
    print(json.dumps(report.summary(), ensure_ascii=False, indent=2))

    await evaluator.close()


if __name__ == "__main__":
    asyncio.run(main())
