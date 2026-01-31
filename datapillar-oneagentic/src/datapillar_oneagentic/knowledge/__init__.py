# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""Knowledge module."""

from __future__ import annotations

from importlib import import_module
from typing import Any

__all__ = [
    "Knowledge",
    "KnowledgeSource",
    "KnowledgeRetrieve",
    "ParsedDocument",
    "Attachment",
    "SourceSpan",
    "KnowledgeDocument",
    "KnowledgeChunk",
    "KnowledgeSearchHit",
    "KnowledgeRef",
    "KnowledgeRetrieveResult",
    "merge_knowledge",
    "SparseEmbeddingProvider",
    "KnowledgeChunkConfig",
    "KnowledgeChunkGeneralConfig",
    "KnowledgeChunkParentChildConfig",
    "KnowledgeChunkQAConfig",
    "KnowledgeWindowConfig",
    "KnowledgeRetrieveConfig",
    "MetadataFilterConfig",
    "QueryExpansionConfig",
    "QueryRouterConfig",
    "ContextResolveConfig",
    "RerankConfig",
    "RetrieveTuningConfig",
    "RetrieveQualityConfig",
    "KnowledgeConfig",
    "KnowledgeService",
    "KnowledgeChunkRequest",
    "KnowledgeChunkEdit",
    "KnowledgeIngestor",
    "KnowledgeChunker",
    "BM25SparseEmbedder",
    "EvalDocument",
    "EvalQuery",
    "EvalSet",
    "LengthStats",
    "ChunkingDocReport",
    "ChunkingSummaryReport",
    "ChunkingReport",
    "RetrievalQueryReport",
    "RetrievalSummaryReport",
    "RetrievalReport",
    "EvaluationReport",
    "KnowledgeEvaluator",
    "load_eval_set",
]

_EXPORTS: dict[str, str] = {
    # config
    "KnowledgeChunkConfig": "datapillar_oneagentic.knowledge.config",
    "KnowledgeChunkGeneralConfig": "datapillar_oneagentic.knowledge.config",
    "KnowledgeChunkParentChildConfig": "datapillar_oneagentic.knowledge.config",
    "KnowledgeChunkQAConfig": "datapillar_oneagentic.knowledge.config",
    "KnowledgeConfig": "datapillar_oneagentic.knowledge.config",
    "KnowledgeWindowConfig": "datapillar_oneagentic.knowledge.config",
    "MetadataFilterConfig": "datapillar_oneagentic.knowledge.config",
    "QueryExpansionConfig": "datapillar_oneagentic.knowledge.config",
    "QueryRouterConfig": "datapillar_oneagentic.knowledge.config",
    "ContextResolveConfig": "datapillar_oneagentic.knowledge.config",
    "KnowledgeRetrieveConfig": "datapillar_oneagentic.knowledge.config",
    "RerankConfig": "datapillar_oneagentic.knowledge.config",
    "RetrieveQualityConfig": "datapillar_oneagentic.knowledge.config",
    "RetrieveTuningConfig": "datapillar_oneagentic.knowledge.config",
    # models
    "Attachment": "datapillar_oneagentic.knowledge.models",
    "Knowledge": "datapillar_oneagentic.knowledge.models",
    "KnowledgeChunk": "datapillar_oneagentic.knowledge.models",
    "KnowledgeDocument": "datapillar_oneagentic.knowledge.models",
    "KnowledgeSearchHit": "datapillar_oneagentic.knowledge.models",
    "KnowledgeRef": "datapillar_oneagentic.knowledge.models",
    "KnowledgeRetrieve": "datapillar_oneagentic.knowledge.models",
    "KnowledgeRetrieveResult": "datapillar_oneagentic.knowledge.models",
    "KnowledgeSource": "datapillar_oneagentic.knowledge.models",
    "ParsedDocument": "datapillar_oneagentic.knowledge.models",
    "SparseEmbeddingProvider": "datapillar_oneagentic.knowledge.models",
    "SourceSpan": "datapillar_oneagentic.knowledge.models",
    "merge_knowledge": "datapillar_oneagentic.knowledge.models",
    # service
    "KnowledgeChunkEdit": "datapillar_oneagentic.knowledge.service",
    "KnowledgeChunkRequest": "datapillar_oneagentic.knowledge.service",
    "KnowledgeService": "datapillar_oneagentic.knowledge.service",
    # ingest
    "KnowledgeIngestor": "datapillar_oneagentic.knowledge.ingest.pipeline",
    # chunker
    "KnowledgeChunker": "datapillar_oneagentic.knowledge.chunker",
    # sparse embedder
    "BM25SparseEmbedder": "datapillar_oneagentic.knowledge.sparse_embedder",
    # evaluation
    "ChunkingDocReport": "datapillar_oneagentic.knowledge.evaluation",
    "ChunkingReport": "datapillar_oneagentic.knowledge.evaluation",
    "ChunkingSummaryReport": "datapillar_oneagentic.knowledge.evaluation",
    "EvalDocument": "datapillar_oneagentic.knowledge.evaluation",
    "EvalQuery": "datapillar_oneagentic.knowledge.evaluation",
    "EvalSet": "datapillar_oneagentic.knowledge.evaluation",
    "EvaluationReport": "datapillar_oneagentic.knowledge.evaluation",
    "KnowledgeEvaluator": "datapillar_oneagentic.knowledge.evaluation",
    "LengthStats": "datapillar_oneagentic.knowledge.evaluation",
    "RetrievalQueryReport": "datapillar_oneagentic.knowledge.evaluation",
    "RetrievalReport": "datapillar_oneagentic.knowledge.evaluation",
    "RetrievalSummaryReport": "datapillar_oneagentic.knowledge.evaluation",
    "load_eval_set": "datapillar_oneagentic.knowledge.evaluation",
}


def __getattr__(name: str) -> Any:
    module_name = _EXPORTS.get(name)
    if module_name is None:
        raise AttributeError(f"module '{__name__}' has no attribute '{name}'")
    module = import_module(module_name)
    value = getattr(module, name)
    globals()[name] = value
    return value


def __dir__() -> list[str]:
    return sorted(set(globals()).union(__all__))
