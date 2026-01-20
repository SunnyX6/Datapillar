"""
知识检索器
"""

from __future__ import annotations

from dataclasses import asdict, is_dataclass
from typing import Any

from pydantic import BaseModel

from datapillar_oneagentic.knowledge.config import KnowledgeConfig, KnowledgeInjectConfig, KnowledgeRetrieveConfig
from datapillar_oneagentic.knowledge.models import (
    Knowledge,
    KnowledgeChunk,
    KnowledgeRef,
    KnowledgeRetrieveResult,
    KnowledgeScope,
    KnowledgeSearchHit,
)
from datapillar_oneagentic.knowledge.retriever.evidence import dedupe_hits, group_hits
from datapillar_oneagentic.knowledge.retriever.reranker import build_reranker, rerank_scores
from datapillar_oneagentic.providers.llm.embedding import EmbeddingProvider
from datapillar_oneagentic.storage.knowledge_stores.base import KnowledgeStore


class KnowledgeRetriever:
    """知识检索器"""

    def __init__(
        self,
        *,
        store: KnowledgeStore,
        embedding_provider: EmbeddingProvider,
        config: KnowledgeConfig,
    ) -> None:
        self._store = store
        self._embedding_provider = embedding_provider
        self._config = config
        self._initialized = False

    @classmethod
    def from_config(
        cls,
        *,
        namespace: str,
        config: KnowledgeConfig,
    ) -> "KnowledgeRetriever":
        from datapillar_oneagentic.knowledge.runtime import build_runtime

        runtime = build_runtime(namespace=namespace, base_config=config.base_config)
        return cls(
            store=runtime.store,
            embedding_provider=runtime.embedding_provider,
            config=config,
        )

    async def retrieve(
        self,
        *,
        query: str,
        knowledge: Knowledge,
        scope: KnowledgeScope | None = None,
    ) -> KnowledgeRetrieveResult:
        if not query:
            return KnowledgeRetrieveResult()

        await self._ensure_initialized()
        retrieve = _merge_retrieve(self._config.retrieve_config, knowledge.retrieve, knowledge.inject)
        inject = retrieve.inject

        method = (retrieve.method or "hybrid").lower()
        if method not in {"semantic", "hybrid"}:
            raise ValueError(f"不支持的检索方式: {method}")
        if method == "hybrid" and knowledge.sparse_embedder is None:
            raise ValueError("启用 hybrid 需要提供 sparse_embedder")
        inject_mode = (inject.mode or "tool").lower()
        if inject_mode not in {"system", "tool"}:
            raise ValueError(f"暂不支持的知识注入模式: {inject_mode}")

        query_vector = await self._embedding_provider.embed_text(query)
        sparse_query = None
        if method == "hybrid" and knowledge.sparse_embedder is not None:
            sparse_query = await knowledge.sparse_embedder.embed_text(query)

        hits = await self._search_in_namespace(
            query_vector=query_vector,
            pool_k=_resolve_pool_k(retrieve),
            scope=scope,
        )
        if not hits:
            return KnowledgeRetrieveResult()

        ranked = _rank_chunks(
            hits=hits,
            method=method,
            sparse_query=sparse_query,
            rrf_k=retrieve.tuning.rrf_k,
        )

        reranked = await _apply_rerank(query=query, ranked=ranked, retrieve=retrieve)
        filtered = _apply_score_threshold(reranked, retrieve.score_threshold)

        grouped = group_hits(filtered, max_per_document=retrieve.quality.max_per_document)
        if retrieve.quality.dedupe:
            grouped = dedupe_hits(grouped, threshold=retrieve.quality.dedupe_threshold)

        final = grouped[: retrieve.top_k]
        if not final:
            return KnowledgeRetrieveResult()

        context_hits = await self._resolve_context_hits(final)
        refs = [
            KnowledgeRef(
                source_id=chunk.source_id,
                doc_id=chunk.doc_id,
                chunk_id=chunk.chunk_id,
                score=score,
                version=chunk.version,
                query=query,
            )
            for chunk, score in context_hits
        ]

        return KnowledgeRetrieveResult(hits=context_hits, refs=refs)

    def resolve_inject_config(self, knowledge: Knowledge) -> KnowledgeInjectConfig:
        retrieve = _merge_retrieve(self._config.retrieve_config, knowledge.retrieve, knowledge.inject)
        return retrieve.inject

    async def close(self) -> None:
        await self._store.close()

    async def _ensure_initialized(self) -> None:
        if self._initialized:
            return
        await self._store.initialize()
        self._initialized = True

    async def _search_in_namespace(
        self,
        *,
        query_vector: list[float],
        pool_k: int,
        scope: KnowledgeScope | None,
    ) -> list[KnowledgeSearchHit]:
        results: list[KnowledgeSearchHit] = []
        if scope and scope.namespaces and len(scope.namespaces) > 1:
            raise ValueError("跨 namespace 检索尚未支持")
        if scope and scope.tags:
            raise ValueError("暂不支持按 tags 过滤")
        hits = await self._store.search_chunks(
            query_vector=query_vector,
            k=max(1, pool_k),
        )
        if scope and scope.document_ids:
            hits = [hit for hit in hits if hit.chunk.doc_id in set(scope.document_ids)]
        results.extend(hits)

        return _dedupe_by_chunk_id(results)

    async def _resolve_context_hits(
        self,
        scored: list[tuple[KnowledgeChunk, float]],
    ) -> list[tuple[KnowledgeChunk, float]]:
        if not scored:
            return []

        chunks = [chunk for chunk, _ in scored]
        has_child = any(chunk.chunk_type == "child" and chunk.parent_id for chunk in chunks)
        if not has_child:
            return scored

        parent_ids = []
        seen = set()
        for chunk in chunks:
            if chunk.chunk_type != "child" or not chunk.parent_id:
                continue
            if chunk.parent_id in seen:
                continue
            parent_ids.append(chunk.parent_id)
            seen.add(chunk.parent_id)

        parents = await self._store.get_chunks(parent_ids)
        parent_map = {item.chunk_id: item for item in parents}
        context_hits: list[tuple[KnowledgeChunk, float]] = []
        seen: set[str] = set()
        for chunk, score in scored:
            if chunk.chunk_type == "child" and chunk.parent_id:
                parent = parent_map.get(chunk.parent_id)
                if parent and parent.chunk_id not in seen:
                    context_hits.append((parent, score))
                    seen.add(parent.chunk_id)
                    continue
            if chunk.chunk_id not in seen:
                context_hits.append((chunk, score))
                seen.add(chunk.chunk_id)
        return context_hits


def _merge_retrieve(
    base: KnowledgeRetrieveConfig,
    override,
    inject_override,
) -> KnowledgeRetrieveConfig:
    data = base.model_dump()
    if override is not None:
        override_dict = _normalize_override(override)
        if "rerank" in override_dict:
            rerank_override = {
                k: v for k, v in (override_dict.pop("rerank") or {}).items() if v is not None
            }
            data["rerank"].update(rerank_override)
        if "inject" in override_dict:
            inject_override_data = {
                k: v for k, v in (override_dict.pop("inject") or {}).items() if v is not None
            }
            data["inject"].update(inject_override_data)
        for key in ("pool_k", "rerank_k", "rrf_k"):
            if key in override_dict:
                data["tuning"][key] = override_dict.pop(key)
        for key in ("max_per_document", "dedupe", "dedupe_threshold"):
            if key in override_dict:
                data["quality"][key] = override_dict.pop(key)
        data.update(override_dict)

    if inject_override is not None:
        data["inject"].update(_normalize_override(inject_override))

    return KnowledgeRetrieveConfig(**data)


def _normalize_override(payload: Any) -> dict[str, Any]:
    if payload is None:
        return {}
    if is_dataclass(payload):
        data = asdict(payload)
    elif isinstance(payload, BaseModel):
        data = payload.model_dump()
    elif isinstance(payload, dict):
        data = payload
    else:
        raise TypeError(f"不支持的知识配置类型: {type(payload).__name__}")
    return {k: v for k, v in data.items() if v is not None}


def _resolve_pool_k(retrieve: KnowledgeRetrieveConfig) -> int:
    if retrieve.tuning.pool_k:
        return retrieve.tuning.pool_k
    return max(retrieve.top_k * 4, retrieve.top_k)


def _resolve_rerank_k(retrieve: KnowledgeRetrieveConfig, total: int) -> int:
    if retrieve.tuning.rerank_k:
        return min(retrieve.tuning.rerank_k, total)
    return min(max(retrieve.top_k * 2, retrieve.top_k), total)


def _apply_score_threshold(
    ranked: list[tuple[KnowledgeChunk, float]],
    threshold: float | None,
) -> list[tuple[KnowledgeChunk, float]]:
    if threshold is None:
        return ranked
    return [(chunk, score) for chunk, score in ranked if score >= threshold]


async def _apply_rerank(
    *,
    query: str,
    ranked: list[tuple[KnowledgeChunk, float]],
    retrieve: KnowledgeRetrieveConfig,
) -> list[tuple[KnowledgeChunk, float]]:
    if not ranked:
        return []
    if retrieve.rerank.mode == "off":
        return ranked

    rerank_k = _resolve_rerank_k(retrieve, len(ranked))
    candidates = ranked[:rerank_k]
    passages = [chunk.content for chunk, _ in candidates]
    reranker = build_reranker(retrieve.rerank)
    scores = await rerank_scores(
        reranker,
        query=query,
        passages=passages,
        params=retrieve.rerank.params,
    )

    rerank_scores_list = list(scores)
    reranked = list(zip([c[0] for c in candidates], rerank_scores_list))
    reranked = _apply_rerank_scoring(reranked, retrieve.rerank)

    if retrieve.rerank.score_threshold is not None:
        reranked = [
            (chunk, score)
            for chunk, score in reranked
            if score >= retrieve.rerank.score_threshold
        ]

    if retrieve.rerank.top_n:
        reranked = sorted(reranked, key=lambda item: item[1], reverse=True)[
            : retrieve.rerank.top_n
        ]

    if retrieve.rerank.mode == "weighted":
        return _blend_scores(reranked, ranked, alpha=retrieve.rerank.params.get("alpha"))
    return sorted(reranked, key=lambda item: item[1], reverse=True)


def _apply_rerank_scoring(
    reranked: list[tuple[KnowledgeChunk, float]],
    config,
) -> list[tuple[KnowledgeChunk, float]]:
    mode = (config.score_mode or "rank").lower()
    scores = [score for _, score in reranked]
    if not scores:
        return reranked
    if mode == "raw":
        return reranked
    if mode == "normalize":
        normalize = (config.normalize or "min_max").lower()
        normalized = _normalize_scores(scores, normalize)
        return [(chunk, normalized[idx]) for idx, (chunk, _) in enumerate(reranked)]

    ordered = sorted(reranked, key=lambda item: item[1], reverse=True)
    return [(chunk, 1.0 / (idx + 1)) for idx, (chunk, _) in enumerate(ordered)]


def _normalize_scores(scores: list[float], mode: str) -> list[float]:
    if not scores:
        return scores
    if mode == "min_max":
        min_v, max_v = min(scores), max(scores)
        if max_v == min_v:
            return [1.0 for _ in scores]
        return [(s - min_v) / (max_v - min_v) for s in scores]
    if mode == "sigmoid":
        import math

        return [1.0 / (1.0 + math.exp(-s)) for s in scores]
    if mode == "softmax":
        import math

        max_v = max(scores)
        exps = [math.exp(s - max_v) for s in scores]
        total = sum(exps) or 1.0
        return [v / total for v in exps]
    if mode == "zscore":
        import math

        mean = sum(scores) / len(scores)
        var = sum((s - mean) ** 2 for s in scores) / len(scores)
        std = math.sqrt(var) or 1.0
        return [(s - mean) / std for s in scores]
    return scores


def _blend_scores(
    reranked: list[tuple[KnowledgeChunk, float]],
    original: list[tuple[KnowledgeChunk, float]],
    *,
    alpha: float | None,
) -> list[tuple[KnowledgeChunk, float]]:
    weight = alpha if alpha is not None else 0.5
    rerank_map = {chunk.chunk_id: score for chunk, score in reranked}
    blended: list[tuple[KnowledgeChunk, float]] = []
    for chunk, score in original:
        if chunk.chunk_id in rerank_map:
            blended.append((chunk, weight * score + (1 - weight) * rerank_map[chunk.chunk_id]))
    blended.sort(key=lambda item: item[1], reverse=True)
    return blended


def _dedupe_by_chunk_id(hits: list[KnowledgeSearchHit]) -> list[KnowledgeSearchHit]:
    if not hits:
        return []
    score_kind = _resolve_score_kind(hits)
    best: dict[str, KnowledgeSearchHit] = {}
    for hit in hits:
        chunk_id = hit.chunk.chunk_id
        existing = best.get(chunk_id)
        if not existing or _is_better_score(hit.score, existing.score, score_kind):
            best[chunk_id] = hit
    return list(best.values())


def _resolve_score_kind(hits: list[KnowledgeSearchHit]) -> str:
    kinds = {hit.score_kind for hit in hits if hit.score_kind}
    if not kinds:
        raise ValueError("检索结果缺少 score_kind")
    if len(kinds) > 1:
        raise ValueError(f"检索结果 score_kind 不一致: {kinds}")
    return kinds.pop()


def _is_better_score(a: float, b: float, score_kind: str) -> bool:
    if score_kind == "distance":
        return a < b
    if score_kind == "similarity":
        return a > b
    raise ValueError(f"不支持的 score_kind: {score_kind}")


def _rank_chunks(
    *,
    hits: list[KnowledgeSearchHit],
    method: str,
    sparse_query: dict[int, float] | None,
    rrf_k: int,
) -> list[tuple[KnowledgeChunk, float]]:
    if not hits:
        return []
    score_kind = _resolve_score_kind(hits)
    reverse = score_kind == "similarity"
    dense_sorted = sorted(hits, key=lambda item: item.score, reverse=reverse)

    if method == "semantic":
        return [(hit.chunk, _score_to_similarity(hit.score, score_kind)) for hit in dense_sorted]

    dense_rank = {hit.chunk.chunk_id: idx + 1 for idx, hit in enumerate(dense_sorted)}
    sparse_scores = {}
    if sparse_query:
        for hit in hits:
            chunk = hit.chunk
            sparse_scores[chunk.chunk_id] = _sparse_dot(sparse_query, chunk.sparse_vector or {})

    sparse_rank = {
        chunk_id: idx + 1
        for idx, (chunk_id, _) in enumerate(
            sorted(sparse_scores.items(), key=lambda item: item[1], reverse=True)
        )
    }

    ranked: list[tuple[KnowledgeChunk, float]] = []
    for hit in hits:
        chunk = hit.chunk
        dr = dense_rank.get(chunk.chunk_id, len(hits) + 1)
        sr = sparse_rank.get(chunk.chunk_id, len(hits) + 1)
        score = (1.0 / (rrf_k + dr)) + (1.0 / (rrf_k + sr))
        ranked.append((chunk, score))

    ranked.sort(key=lambda item: item[1], reverse=True)
    return ranked


def _sparse_dot(query: dict[int, float], doc: dict[int, float]) -> float:
    score = 0.0
    for key, weight in query.items():
        score += weight * doc.get(key, 0.0)
    return score


def _score_to_similarity(score: float, score_kind: str) -> float:
    if score_kind == "distance":
        return 1.0 / (1.0 + score)
    return score
