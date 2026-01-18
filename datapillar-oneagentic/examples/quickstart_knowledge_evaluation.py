"""
知识评估示例：切分与检索质量评估

运行命令：
    uv run python examples/quickstart_knowledge_evaluation.py

依赖安装：
    pip install datapillar-oneagentic[lance,knowledge]

Embedding 配置：
    export GLM_EMBEDDING_API_KEY="sk-xxx"
    export GLM_EMBEDDING_MODEL="embedding-3"
    export GLM_EMBEDDING_DIMENSION="1024"
"""

import asyncio
import json
import os
from pathlib import Path

from datapillar_oneagentic import DatapillarConfig
from datapillar_oneagentic.knowledge import (
    KnowledgeChunkConfig,
    KnowledgeEvaluator,
    KnowledgeRetrieveConfig,
    load_eval_set,
)
from datapillar_oneagentic.providers.llm import EmbeddingProviderClient
from datapillar_oneagentic.storage import create_knowledge_store


async def main() -> None:
    embedding_api_key = os.environ.get("GLM_EMBEDDING_API_KEY")
    embedding_model = os.environ.get("GLM_EMBEDDING_MODEL")
    embedding_dimension_raw = os.environ.get("GLM_EMBEDDING_DIMENSION")

    if not embedding_api_key or not embedding_model or not embedding_dimension_raw:
        raise RuntimeError("请先配置 GLM_EMBEDDING_API_KEY、GLM_EMBEDDING_MODEL、GLM_EMBEDDING_DIMENSION")

    try:
        embedding_dimension = int(embedding_dimension_raw)
    except ValueError as exc:
        raise RuntimeError("GLM_EMBEDDING_DIMENSION 必须为整数") from exc

    embedding_config = {
        "provider": "glm",
        "api_key": embedding_api_key,
        "model": embedding_model,
        "dimension": embedding_dimension,
    }
    config = DatapillarConfig(embedding=embedding_config)

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

    knowledge_store = create_knowledge_store(
        "demo_knowledge_eval",
        vector_store_config=config.vector_store,
        embedding_config=config.embedding,
    )
    await knowledge_store.initialize()

    embedding_provider = EmbeddingProviderClient(config.embedding)
    evaluator = KnowledgeEvaluator(
        store=knowledge_store,
        embedding_provider=embedding_provider,
        chunk_config=chunk_config,
        retrieve_config=retrieve_config,
    )

    report = await evaluator.evaluate(evalset)
    print("评估摘要：")
    print(json.dumps(report.summary(), ensure_ascii=False, indent=2))

    await knowledge_store.close()


if __name__ == "__main__":
    asyncio.run(main())
