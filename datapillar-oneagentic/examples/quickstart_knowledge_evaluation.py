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

说明：
    - 评估集文档无需传 source_id，系统根据 doc_id 自动派生。
"""

import asyncio
import json
import os
from pathlib import Path

from datapillar_oneagentic.knowledge import (
    KnowledgeChunkConfig,
    KnowledgeConfig,
    KnowledgeEvaluator,
    KnowledgeRetrieveConfig,
    load_eval_set,
)


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
