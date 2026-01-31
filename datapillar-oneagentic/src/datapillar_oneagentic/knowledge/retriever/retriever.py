# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""Knowledge retriever."""

from __future__ import annotations

import asyncio
from dataclasses import asdict, is_dataclass
from typing import Any, Callable, TYPE_CHECKING

from pydantic import BaseModel

from datapillar_oneagentic.knowledge.config import KnowledgeConfig, KnowledgeRetrieveConfig
from datapillar_oneagentic.knowledge.models import (
    Knowledge,
    KnowledgeChunk,
    KnowledgeRef,
    KnowledgeRetrieveResult,
    KnowledgeSearchHit,
    KnowledgeRetrieve,
)
from datapillar_oneagentic.knowledge.retriever.evidence import dedupe_hits, group_hits
from datapillar_oneagentic.knowledge.retriever.query import (
    QueryRouteOutput,
    build_query_route,
    expand_queries,
)
from datapillar_oneagentic.knowledge.retriever.reranker import build_reranker, rerank_scores
from datapillar_oneagentic.providers.llm.embedding import EmbeddingProvider
from datapillar_oneagentic.storage.knowledge_stores.base import KnowledgeStore

if TYPE_CHECKING:
    from datapillar_oneagentic.providers.llm.llm import ResilientChatModel


class KnowledgeRetriever:
    """Knowledge retriever."""

    def __init__(
        self,
        *,
        store: KnowledgeStore,
        embedding_provider: EmbeddingProvider,
        retrieve_defaults: KnowledgeRetrieveConfig | None = None,
    ) -> None:
        self._store = store
        self._embedding_provider = embedding_provider
        self._defaults = retrieve_defaults or KnowledgeRetrieveConfig()
        self._initialized = False

    @classmethod
    def from_config(
        cls,
        *,
        namespace: str | None = None,
        config: KnowledgeConfig,
    ) -> "KnowledgeRetriever":
        if not namespace:
            raise ValueError("namespace is required for KnowledgeRetriever.from_config")
        from datapillar_oneagentic.knowledge.runtime import build_runtime

        runtime = build_runtime(namespace=namespace, config=config)
        return cls(
            store=runtime.store,
            embedding_provider=runtime.embedding_provider,
        )

    async def retrieve(
        self,
        *,
        query: str,
        knowledge: Knowledge | None = None,
        retrieve: KnowledgeRetrieve | None = None,
        filters: dict[str, Any] | None = None,
        search_params: dict[str, Any] | None = None,
        llm_provider: Callable[[], "ResilientChatModel"] | None = None,
    ) -> KnowledgeRetrieveResult:
        if not query:
            return KnowledgeRetrieveResult()

        await self._ensure_initialized()
        override = retrieve or (knowledge.retrieve if knowledge else None)
        explicit_method = _override_has_value(override, "method")
        explicit_rerank = _override_has_value(override, "rerank")
        retrieve = _merge_retrieve(self._defaults, override)

        route = await build_query_route(
            query=query,
            config=retrieve.routing,
            supports_hybrid=self._store.supports_hybrid,
            supports_full_text=self._store.supports_full_text,
            llm_provider=llm_provider if retrieve.routing.use_llm else None,
        )
        if route and route.use_rag is False and retrieve.routing.allow_no_rag:
            return KnowledgeRetrieveResult()
        if route:
            retrieve = _apply_route_decision(
                retrieve,
                route=route,
                explicit_method=explicit_method,
                explicit_rerank=explicit_rerank,
            )

        method = (retrieve.method or "hybrid").lower()
        if method not in {"semantic", "hybrid", "full_text"}:
            raise ValueError(f"Unsupported retrieval method: {method}")
        if method == "hybrid" and not self._store.supports_hybrid:
            raise ValueError("Hybrid retrieval is not supported by the current backend.")
        if method == "full_text" and not self._store.supports_full_text:
            raise ValueError("Full-text retrieval is not supported by the current backend.")
        if search_params:
            raise ValueError("search_params is not supported by the current vector store backend")

        result = await self._execute_retrieval(
            query=query,
            retrieve=retrieve,
            filters=filters,
            llm_provider=llm_provider,
            method=method,
        )

        return result

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
        filters: dict[str, Any] | None,
    ) -> list[KnowledgeSearchHit]:
        results: list[KnowledgeSearchHit] = []
        hits = await self._store.search_chunks(
            query_vector=query_vector,
            k=max(1, pool_k),
            filters=filters,
        )
        results.extend(hits)

        return _dedupe_chunk_id(results)

    async def _search_queries(
        self,
        *,
        queries: list[str],
        method: str,
        pool_k: int,
        filters: dict[str, Any] | None,
        rrf_k: int,
        expansion,
    ) -> list[KnowledgeSearchHit]:
        per_query_k = _resolve_per_query_k(pool_k, queries, expansion.per_query_k)
        tasks = []
        if method == "full_text":
            for item in queries:
                tasks.append(
                    self._store.full_text_search_chunks(
                        query_text=item,
                        k=max(1, per_query_k),
                        filters=filters,
                    )
                )
        else:
            query_vectors = await asyncio.gather(
                *[self._embedding_provider.embed_text(item) for item in queries]
            )
            if method == "hybrid":
                for item, vector in zip(queries, query_vectors):
                    tasks.append(
                        self._store.hybrid_search_chunks(
                            query_vector=vector,
                            query_text=item,
                            k=max(1, per_query_k),
                            filters=filters,
                            rrf_k=rrf_k,
                        )
                    )
            else:
                for vector in query_vectors:
                    tasks.append(
                        self._store.search_chunks(
                            query_vector=vector,
                            k=max(1, per_query_k),
                            filters=filters,
                        )
                    )

        results: list[KnowledgeSearchHit] = []
        for batch in await asyncio.gather(*tasks):
            results.extend(batch)
        return _dedupe_chunk_id(results)

    async def _execute_retrieval(
        self,
        *,
        query: str,
        retrieve: KnowledgeRetrieveConfig,
        filters: dict[str, Any] | None,
        llm_provider: Callable[[], "ResilientChatModel"] | None,
        method: str,
    ) -> KnowledgeRetrieveResult:
        queries = await expand_queries(
            query=query,
            config=retrieve.expansion,
            llm_provider=llm_provider if retrieve.expansion.use_llm else None,
        )
        if not queries:
            return KnowledgeRetrieveResult()

        hits = await self._search_queries(
            queries=queries,
            method=method,
            pool_k=_resolve_pool_k(retrieve),
            filters=filters,
            rrf_k=retrieve.tuning.rrf_k,
            expansion=retrieve.expansion,
        )
        if not hits:
            return KnowledgeRetrieveResult()

        ranked = _rank_by_score(hits)
        reranked = await _apply_rerank(query=query, ranked=ranked, retrieve=retrieve)
        filtered = _apply_score_threshold(reranked, retrieve.score_threshold)

        grouped = group_hits(filtered, max_per_document=retrieve.quality.max_per_document)
        if retrieve.quality.dedupe:
            grouped = dedupe_hits(grouped, threshold=retrieve.quality.dedupe_threshold)

        final = grouped[: retrieve.top_k]
        if not final:
            return KnowledgeRetrieveResult()

        context_hits = await self._resolve_context_hits(final, context=retrieve.context)
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
    async def _search_hybrid_in_namespace(
        self,
        *,
        query_vector: list[float],
        query_text: str,
        pool_k: int,
        filters: dict[str, Any] | None,
        rrf_k: int,
    ) -> list[KnowledgeSearchHit]:
        results: list[KnowledgeSearchHit] = []
        hits = await self._store.hybrid_search_chunks(
            query_vector=query_vector,
            query_text=query_text,
            k=max(1, pool_k),
            filters=filters,
            rrf_k=rrf_k,
        )
        results.extend(hits)
        return _dedupe_chunk_id(results)

    async def _resolve_context_hits(
        self,
        scored: list[tuple[KnowledgeChunk, float]],
        *,
        context,
    ) -> list[tuple[KnowledgeChunk, float]]:
        if not scored:
            return []

        mode = (context.mode or "parent").lower()
        if mode == "off":
            return scored

        apply_parent = mode in {"parent", "parent_window"}
        apply_window = mode in {"window", "parent_window"}

        resolved = scored
        if apply_parent:
            resolved = await self._resolve_parent_hits(scored)
        if apply_window:
            resolved = await self._resolve_window_hits(resolved, radius=context.window_radius)
        return resolved

    async def _resolve_parent_hits(
        self,
        scored: list[tuple[KnowledgeChunk, float]],
    ) -> list[tuple[KnowledgeChunk, float]]:
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
        seen_ids: set[str] = set()
        for chunk, score in scored:
            if chunk.chunk_type == "child" and chunk.parent_id:
                parent = parent_map.get(chunk.parent_id)
                if parent and parent.chunk_id not in seen_ids:
                    context_hits.append((parent, score))
                    seen_ids.add(parent.chunk_id)
                    continue
            if chunk.chunk_id not in seen_ids:
                context_hits.append((chunk, score))
                seen_ids.add(chunk.chunk_id)
        return context_hits

    async def _resolve_window_hits(
        self,
        scored: list[tuple[KnowledgeChunk, float]],
        *,
        radius: int,
    ) -> list[tuple[KnowledgeChunk, float]]:
        if radius <= 0:
            return scored

        anchor_ids = {chunk.chunk_id for chunk, _ in scored}
        window_map: dict[str, tuple[list[str], list[str]]] = {}
        extra_ids: set[str] = set()

        for chunk, _score in scored:
            metadata = chunk.metadata or {}
            prev_ids = list(metadata.get("window_prev_ids") or [])
            next_ids = list(metadata.get("window_next_ids") or [])
            if radius:
                prev_ids = prev_ids[-radius:]
                next_ids = next_ids[:radius]
            window_map[chunk.chunk_id] = (prev_ids, next_ids)
            for cid in prev_ids:
                if cid and cid not in anchor_ids:
                    extra_ids.add(cid)
            for cid in next_ids:
                if cid and cid not in anchor_ids:
                    extra_ids.add(cid)

        extra_chunks = await self._store.get_chunks(list(extra_ids))
        extra_map = {chunk.chunk_id: chunk for chunk in extra_chunks}

        context_hits: list[tuple[KnowledgeChunk, float]] = []
        seen: set[str] = set()

        for chunk, score in scored:
            prev_ids, next_ids = window_map.get(chunk.chunk_id, ([], []))
            for cid in prev_ids:
                if cid in seen or cid in anchor_ids:
                    continue
                neighbor = extra_map.get(cid)
                if neighbor:
                    context_hits.append((neighbor, score))
                    seen.add(cid)
            if chunk.chunk_id not in seen:
                context_hits.append((chunk, score))
                seen.add(chunk.chunk_id)
            for cid in next_ids:
                if cid in seen or cid in anchor_ids:
                    continue
                neighbor = extra_map.get(cid)
                if neighbor:
                    context_hits.append((neighbor, score))
                    seen.add(cid)

        return context_hits


def _merge_retrieve(
    base: KnowledgeRetrieveConfig,
    override,
) -> KnowledgeRetrieveConfig:
    data = base.model_dump()
    if override is not None:
        override_dict = _normalize_override(override)
        if "fallback" in override_dict:
            raise ValueError("Fallback has been removed from retrieval configuration.")
        if "hierarchical" in override_dict:
            raise ValueError("Hierarchical summary retrieval has been removed from retrieval configuration.")
        if "rerank" in override_dict:
            rerank_override = {
                k: v for k, v in (override_dict.pop("rerank") or {}).items() if v is not None
            }
            data["rerank"].update(rerank_override)
        if "params" in override_dict:
            params_override = {
                k: v for k, v in (override_dict.pop("params") or {}).items() if v is not None
            }
            data["params"].update(params_override)
        if "filtering" in override_dict:
            filtering_override = {k: v for k, v in (override_dict.pop("filtering") or {}).items() if v is not None}
            fields_override = filtering_override.pop("fields", None)
            data["filtering"].update(filtering_override)
            if fields_override:
                data["filtering"]["fields"].update(fields_override)
        if "routing" in override_dict:
            routing_override = {k: v for k, v in (override_dict.pop("routing") or {}).items() if v is not None}
            data["routing"].update(routing_override)
        if "expansion" in override_dict:
            expansion_override = {k: v for k, v in (override_dict.pop("expansion") or {}).items() if v is not None}
            data["expansion"].update(expansion_override)
        if "context" in override_dict:
            context_override = {k: v for k, v in (override_dict.pop("context") or {}).items() if v is not None}
            data["context"].update(context_override)
        for key in ("pool_k", "rerank_k", "rrf_k"):
            if key in override_dict:
                data["tuning"][key] = override_dict.pop(key)
        for key in ("max_per_document", "dedupe", "dedupe_threshold"):
            if key in override_dict:
                data["quality"][key] = override_dict.pop(key)
        data.update(override_dict)

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
        raise TypeError(f"Unsupported knowledge config type: {type(payload).__name__}")
    return {k: v for k, v in data.items() if v is not None}


def _override_has_value(override: Any, field_name: str) -> bool:
    if override is None:
        return False
    if is_dataclass(override) or isinstance(override, BaseModel):
        value = getattr(override, field_name, None)
    elif isinstance(override, dict):
        value = override.get(field_name)
    else:
        return False
    return value is not None


def _apply_route_decision(
    retrieve: KnowledgeRetrieveConfig,
    *,
    route: QueryRouteOutput,
    explicit_method: bool,
    explicit_rerank: bool,
) -> KnowledgeRetrieveConfig:
    if route.method and not explicit_method:
        retrieve.method = route.method
    if route.rerank is not None and not explicit_rerank:
        if route.rerank:
            if retrieve.rerank.mode == "off":
                retrieve.rerank.mode = "model"
        else:
            retrieve.rerank.mode = "off"
    return retrieve


def _resolve_pool_k(retrieve: KnowledgeRetrieveConfig) -> int:
    if retrieve.tuning.pool_k:
        return retrieve.tuning.pool_k
    return max(retrieve.top_k * 4, retrieve.top_k)


def _resolve_per_query_k(pool_k: int, queries: list[str], per_query_k: int | None) -> int:
    if per_query_k:
        return per_query_k
    total = len(queries)
    if total <= 1:
        return pool_k
    return max(1, (pool_k + total - 1) // total)


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
    if len(rerank_scores_list) < len(candidates):
        rerank_scores_list.extend([0.0] * (len(candidates) - len(rerank_scores_list)))
    elif len(rerank_scores_list) > len(candidates):
        rerank_scores_list = rerank_scores_list[: len(candidates)]
    reranked = list(zip([c[0] for c in candidates], rerank_scores_list))
    reranked = _apply_rerank_scoring(reranked, retrieve.rerank)

    if retrieve.rerank.score_threshold is not None:
        reranked = [
            (chunk, score)
            for chunk, score in reranked
            if score >= retrieve.rerank.score_threshold
        ]

    if not reranked:
        return ranked

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
            blended_score = weight * score + (1 - weight) * rerank_map[chunk.chunk_id]
        else:
            blended_score = score
        blended.append((chunk, blended_score))
    blended.sort(key=lambda item: item[1], reverse=True)
    return blended


def _dedupe_chunk_id(hits: list[KnowledgeSearchHit]) -> list[KnowledgeSearchHit]:
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
        raise ValueError("Retrieval results missing score_kind")
    if len(kinds) > 1:
        raise ValueError(f"Retrieval results have inconsistent score_kind: {kinds}")
    return kinds.pop()


def _is_better_score(a: float, b: float, score_kind: str) -> bool:
    if score_kind == "distance":
        return a < b
    if score_kind == "similarity":
        return a > b
    raise ValueError(f"Unsupported score_kind: {score_kind}")


def _rank_by_score(hits: list[KnowledgeSearchHit]) -> list[tuple[KnowledgeChunk, float]]:
    if not hits:
        return []
    score_kind = _resolve_score_kind(hits)
    reverse = score_kind == "similarity"
    ordered = sorted(hits, key=lambda item: item.score, reverse=reverse)
    return [(hit.chunk, _score_to_similarity(hit.score, score_kind)) for hit in ordered]


def _score_to_similarity(score: float, score_kind: str) -> float:
    if score_kind == "distance":
        return 1.0 / (1.0 + score)
    return score
