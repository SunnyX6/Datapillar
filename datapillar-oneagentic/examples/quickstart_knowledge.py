"""
知识 RAG 快速使用示例：文档切分 + 检索

运行命令：
    uv run python examples/quickstart_knowledge.py

依赖安装：
    pip install datapillar-oneagentic[lance,knowledge]

Embedding 配置（二选一）：
    1) 环境变量（推荐）：
       export GLM_EMBEDDING_API_KEY="sk-xxx"
       export GLM_EMBEDDING_MODEL="embedding-3"
       export GLM_EMBEDDING_DIMENSION="1024"
    2) 配置文件（参考 examples/config.example.toml）
说明：
    - source_id 由系统生成，不需要传入。
    - content 可选；传了 content 就不会读取 source_uri 的内容。
    - source_uri 写本地完整路径且文件存在时，会自动读取文件内容；filename/mime_type 可选。
    - namespace 必须显式传入（由 ingest/retrieve 内部创建 store 绑定）。
    - 检索默认只在当前 namespace 内进行，跨 namespace 检索暂不支持。
"""

import asyncio
import os

from datapillar_oneagentic.context import ContextBuilder
from datapillar_oneagentic.knowledge import (
    Knowledge,
    KnowledgeChunkConfig,
    KnowledgeConfig,
    KnowledgeRetrieveConfig,
    KnowledgeSource,
    KnowledgeRetriever,
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
    namespace = "demo_knowledge"
    knowledge_config = KnowledgeConfig(
        base_config={
            "embedding": embedding_config,
            "vector_store": {"type": "lance", "path": "./data/vectors"},
        },
        chunk_config=KnowledgeChunkConfig(
            mode="general",
            preprocess=["normalize_newlines", "remove_control", "collapse_whitespace"],
            general={"max_tokens": 80, "overlap": 10},
        ),
        retrieve_config=KnowledgeRetrieveConfig(
            method="semantic",
            top_k=3,
        ),
    )

    sample_text = (
        "Datapillar 是一个数据开发 SaaS 平台，覆盖数据集成、建模与质量治理。\n"
        "知识框架提供文档解析、预处理、切分、向量化与检索增强能力。\n"
        "检索支持语义召回与混合召回，结果再经 rerank 与去重后注入上下文。\n"
        "namespace 作为硬隔离边界，避免不同业务线的知识互相污染。\n"
        "doc_id 使用确定性 hash 生成，重复入库会触发重建而不是叠加存储。\n"
        "典型场景包括数据开发助手、团队知识库、企业文档问答与 Agent 增强。"
    )
    source = KnowledgeSource(
        name="示例知识库",
        source_type="doc",
        source_uri="demo.txt",
        content=sample_text,
        filename="demo.txt",
        metadata={"title": "Datapillar 介绍"},
    )

    preview = source.chunk(chunk_config=knowledge_config.chunk_config)
    print(f"切分预览：chunks={len(preview.chunks)}, attachments={len(preview.attachments)}")
    if preview.chunks:
        print("首个分片预览：")
        print(preview.chunks[0].content)

    await source.ingest(
        namespace=namespace,
        config=knowledge_config,
    )

    retriever = KnowledgeRetriever.from_config(namespace=namespace, config=knowledge_config)
    knowledge = Knowledge(sources=[source])
    result = await retriever.retrieve(query="Datapillar 的核心能力是什么？", knowledge=knowledge)
    inject_config = retriever.resolve_inject_config(knowledge)
    context = ContextBuilder.build_knowledge_context(
        chunks=[chunk for chunk, _ in result.hits],
        inject=inject_config,
    )
    print("\n检索结果：")
    print(context or "未找到相关知识。")
    await retriever.close()


if __name__ == "__main__":
    asyncio.run(main())
