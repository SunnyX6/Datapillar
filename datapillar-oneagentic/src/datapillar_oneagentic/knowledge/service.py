# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-29
"""Knowledge service: store + chunk + retrieve."""

from __future__ import annotations

import logging
import asyncio
from dataclasses import dataclass, replace
from typing import Any

from datapillar_oneagentic.knowledge.chunker.models import ChunkPreview
from datapillar_oneagentic.knowledge.chunker.cleaner import apply_preprocess
from datapillar_oneagentic.knowledge.config import KnowledgeChunkConfig, KnowledgeConfig, KnowledgeRetrieveConfig
from datapillar_oneagentic.knowledge.ingest.builder import apply_window_metadata, average_vectors, hash_content
from datapillar_oneagentic.knowledge.ingest.pipeline import KnowledgeIngestor
from datapillar_oneagentic.knowledge.models import (
    Knowledge,
    KnowledgeChunk,
    KnowledgeRef,
    KnowledgeSearchHit,
    KnowledgeRetrieve,
    KnowledgeRetrieveResult,
    KnowledgeSource,
    SparseEmbeddingProvider,
)
from datapillar_oneagentic.knowledge.parser import ParserRegistry, default_registry
from datapillar_oneagentic.knowledge.retriever.evidence import dedupe_hits, group_hits
from datapillar_oneagentic.knowledge.retriever import KnowledgeRetriever
from datapillar_oneagentic.knowledge.retriever.query import build_query_route, expand_queries
from datapillar_oneagentic.knowledge.retriever.retriever import (
    _apply_route_decision,
    _apply_rerank,
    _apply_score_threshold,
    _dedupe_chunk_id,
    _merge_retrieve,
    _override_has_value,
    _rank_by_score,
    _resolve_per_query_k,
    _resolve_pool_k,
)
from datapillar_oneagentic.knowledge.runtime import build_runtime
from datapillar_oneagentic.providers.llm.embedding import EmbeddingProvider
from datapillar_oneagentic.utils.time import now_ms

logger = logging.getLogger(__name__)


@dataclass
class KnowledgeChunkRequest:
    """Chunk request (dynamic)."""

    sources: list[KnowledgeSource]
    splitter: Any | None = None
    preview: bool = False
    parser_registry: ParserRegistry | None = None
    sparse_embedder: SparseEmbeddingProvider | None = None
    write: dict[str, Any] | None = None


@dataclass
class KnowledgeChunkEdit:
    """Chunk edit payload."""

    chunk_id: str
    content: str
    metadata: dict[str, Any] | None = None


class KnowledgeService:
    """Knowledge framework service (store + chunk + retrieve)."""

    def __init__(
        self,
        *,
        namespace: str | None = None,
        config: KnowledgeConfig,
        retrieve_defaults: KnowledgeRetrieveConfig | None = None,
    ) -> None:
        if config is None:
            raise ValueError("config cannot be empty")
        if not config.embedding.is_configured():
            raise ValueError("KnowledgeConfig.embedding is not configured")

        self._namespace = namespace
        self._config = config
        self._retrieve_defaults = retrieve_defaults or config.retrieve or KnowledgeRetrieveConfig()
        self._backend = config.vector_store.type
        self._initialized = False
        self._embedding_provider = EmbeddingProvider(config.embedding)
        self._runtime = None
        self._runtime_cache: dict[str, Any] = {}
        self._retriever = None
        if self._namespace is not None:
            self._runtime = build_runtime(namespace=self._namespace, config=config)
            self._runtime_cache = {self._namespace: self._runtime}
            self._retriever = KnowledgeRetriever(
                store=self._runtime.store,
                embedding_provider=self._runtime.embedding_provider,
                retrieve_defaults=self._retrieve_defaults,
            )

    @property
    def namespace(self) -> str | None:
        return self._namespace

    @property
    def config(self) -> KnowledgeConfig:
        return self._config

    @property
    def backend(self) -> str:
        return self._backend

    def raw_store(self) -> Any:
        """Return raw backend store (KnowledgeStore)."""
        if self._runtime is None:
            return None
        return self._runtime.store

    async def initialize(self) -> None:
        if self._initialized:
            return
        if self._runtime is not None:
            await self._runtime.initialize()
        self._initialized = True

    async def close(self) -> None:
        for runtime in self._runtime_cache.values():
            await runtime.store.close()

    async def chunk(
        self,
        request: KnowledgeChunkRequest,
        *,
        namespace: str,
    ) -> list[ChunkPreview] | None:
        if request is None:
            raise ValueError("chunk request cannot be empty")
        if not request.sources:
            raise ValueError("chunk request sources cannot be empty")

        await self.initialize()
        runtime = await self._get_runtime(namespace)
        return await self._chunk_vector_store(request, runtime=runtime)

    async def retrieve(
        self,
        *,
        query: str,
        namespaces: list[str],
        knowledge: Knowledge | None = None,
        retrieve: KnowledgeRetrieve | None = None,
        filters: dict[str, Any] | str | None = None,
        search_params: dict[str, Any] | None = None,
        llm_provider=None,
    ) -> KnowledgeRetrieveResult:
        if not query:
            return KnowledgeRetrieveResult()

        await self.initialize()

        if not namespaces:
            raise ValueError("namespaces cannot be empty")
        namespace_list = _unique_namespaces(namespaces)

        if filters is None:
            from datapillar_oneagentic.knowledge.filtering import build_auto_filters

            filter_config = self._retrieve_defaults.filtering
            filters = await build_auto_filters(
                query=query,
                config=filter_config,
                llm_provider=llm_provider if filter_config.use_llm else None,
            )

        resolved_filters = filters if isinstance(filters, dict) else None
        if (
            len(namespace_list) == 1
            and self._namespace is not None
            and namespace_list[0] == self._namespace
        ):
            if self._retriever is None:
                raise ValueError("Knowledge retriever is not initialized")
            return await self._retriever.retrieve(
                query=query,
                knowledge=knowledge,
                retrieve=retrieve,
                filters=resolved_filters,
                search_params=search_params,
                llm_provider=llm_provider,
            )

        return await self._retrieve_multi(
            query=query,
            namespaces=namespace_list,
            knowledge=knowledge,
            retrieve=retrieve,
            filters=resolved_filters,
            llm_provider=llm_provider,
        )

    async def list_chunks(
        self,
        *,
        filters: dict[str, Any] | None = None,
        limit: int = 50,
        offset: int = 0,
        order_by: str = "doc_id,parent_id,chunk_index",
        namespace: str,
    ) -> list[KnowledgeChunk]:
        if limit < 0:
            raise ValueError("limit must be >= 0")
        if offset < 0:
            raise ValueError("offset must be >= 0")

        await self.initialize()
        runtime = await self._get_runtime(namespace)

        chunks = await runtime.store.query_chunks(filters=filters, limit=None)
        ordered = _sort_chunks(chunks, order_by)
        if offset:
            ordered = ordered[offset:]
        if limit:
            ordered = ordered[:limit]
        else:
            ordered = []
        return ordered

    async def upsert_chunks(
        self,
        *,
        chunks: list[KnowledgeChunkEdit],
        sparse_embedder: SparseEmbeddingProvider | None = None,
        namespace: str,
    ) -> list[KnowledgeChunk]:
        if not chunks:
            raise ValueError("chunks cannot be empty")

        await self.initialize()
        runtime = await self._get_runtime(namespace)

        chunk_ids = [item.chunk_id for item in chunks]
        if len(set(chunk_ids)) != len(chunk_ids):
            raise ValueError("chunk_ids must be unique")

        existing_chunks = await runtime.store.get_chunks(chunk_ids)
        existing_map = {item.chunk_id: item for item in existing_chunks}
        missing = [chunk_id for chunk_id in chunk_ids if chunk_id not in existing_map]
        if missing:
            raise ValueError(f"Chunk ids not found: {', '.join(missing)}")

        doc_cache: dict[str, Any] = {}
        updated_chunks: list[KnowledgeChunk] = []
        for edit in chunks:
            existing = existing_map[edit.chunk_id]
            doc = await self._get_doc(existing.doc_id, cache=doc_cache, runtime=runtime)
            chunk_config = _load_chunk_config(doc)
            content = apply_preprocess(edit.content, chunk_config.preprocess)
            metadata = dict(existing.metadata or {})
            if edit.metadata:
                metadata.update(edit.metadata)
            updated = replace(
                existing,
                content=content,
                content_hash=hash_content(content),
                token_count=len(content),
                metadata=metadata,
                updated_at=now_ms(),
            )
            updated_chunks.append(updated)

        if runtime.store.supports_external_embeddings:
            vectors = await runtime.embedding_provider.embed_texts(
                [chunk.content for chunk in updated_chunks]
            )
            sparse_vectors = None
            use_sparse = sparse_embedder is not None and not runtime.store.supports_hybrid
            if use_sparse:
                sparse_vectors = await sparse_embedder.embed_texts([chunk.content for chunk in updated_chunks])

            for idx, chunk in enumerate(updated_chunks):
                chunk.vector = vectors[idx]
                if sparse_vectors:
                    chunk.sparse_vector = sparse_vectors[idx]

        await runtime.store.upsert_chunks(updated_chunks)

        await self._repair_docs(
            doc_ids=_unique_doc_ids(updated_chunks),
            doc_cache=doc_cache,
            runtime=runtime,
        )
        return updated_chunks

    async def delete_chunks(
        self,
        *,
        chunk_ids: list[str],
        namespace: str,
    ) -> int:
        if not chunk_ids:
            raise ValueError("chunk_ids cannot be empty")

        await self.initialize()
        runtime = await self._get_runtime(namespace)

        unique_ids = list(dict.fromkeys(chunk_ids))
        existing_chunks = await runtime.store.get_chunks(unique_ids)
        existing_map = {item.chunk_id: item for item in existing_chunks}
        missing = [chunk_id for chunk_id in unique_ids if chunk_id not in existing_map]
        if missing:
            raise ValueError(f"Chunk ids not found: {', '.join(missing)}")

        delete_ids = set(unique_ids)
        parent_ids = [chunk.chunk_id for chunk in existing_chunks if chunk.chunk_type == "parent"]
        for parent_id in parent_ids:
            children = await runtime.store.query_chunks(filters={"parent_id": parent_id}, limit=None)
            for child in children:
                delete_ids.add(child.chunk_id)

        deleted = await runtime.store.delete_chunks(list(delete_ids))
        await self._repair_docs(doc_ids=_unique_doc_ids(existing_chunks), doc_cache=None, runtime=runtime)
        return deleted

    async def _chunk_vector_store(
        self,
        request: KnowledgeChunkRequest,
        *,
        runtime,
    ) -> list[ChunkPreview] | None:
        registry = request.parser_registry or default_registry()
        ingestor = KnowledgeIngestor(
            store=runtime.store,
            embedding_provider=runtime.embedding_provider,
            parser_registry=registry,
        )
        previews = ingestor.preview(sources=request.sources)
        if request.preview:
            return previews
        await ingestor.ingest(
            sources=request.sources,
            sparse_embedder=request.sparse_embedder,
        )
        return previews

    async def _retrieve_multi(
        self,
        *,
        query: str,
        namespaces: list[str],
        knowledge: Knowledge | None,
        retrieve: KnowledgeRetrieve | None,
        filters: dict[str, Any] | None,
        llm_provider,
    ) -> KnowledgeRetrieveResult:
        runtimes = {ns: await self._get_runtime(ns) for ns in namespaces}
        supports_hybrid = all(runtime.store.supports_hybrid for runtime in runtimes.values())
        supports_full_text = all(runtime.store.supports_full_text for runtime in runtimes.values())
        override = retrieve or (knowledge.retrieve if knowledge else None)
        explicit_method = _override_has_value(override, "method")
        explicit_rerank = _override_has_value(override, "rerank")
        merged = _merge_retrieve(self._retrieve_defaults, override)

        route = await build_query_route(
            query=query,
            config=merged.routing,
            supports_hybrid=supports_hybrid,
            supports_full_text=supports_full_text,
            llm_provider=llm_provider if merged.routing.use_llm else None,
        )
        if route and route.use_rag is False and merged.routing.allow_no_rag:
            return KnowledgeRetrieveResult()
        if route:
            merged = _apply_route_decision(
                merged,
                route=route,
                explicit_method=explicit_method,
                explicit_rerank=explicit_rerank,
            )

        method = (merged.method or "hybrid").lower()
        if method not in {"semantic", "hybrid", "full_text"}:
            raise ValueError(f"Unsupported retrieval method: {method}")
        if method == "hybrid" and not supports_hybrid:
            raise ValueError("Hybrid retrieval requires all namespaces to support hybrid search.")
        if method == "full_text" and not supports_full_text:
            raise ValueError("Full-text retrieval requires all namespaces to support full-text search.")
        if len(namespaces) > 1 and merged.rerank.mode == "off":
            raise ValueError("Multi-namespace retrieval requires rerank to be enabled.")

        result = await self._retrieve_multi_once(
            query=query,
            runtimes=runtimes,
            retrieve=merged,
            filters=filters,
            llm_provider=llm_provider,
            method=method,
        )

        return result

    async def _retrieve_multi_once(
        self,
        *,
        query: str,
        runtimes: dict[str, Any],
        retrieve: KnowledgeRetrieveConfig,
        filters: dict[str, Any] | None,
        llm_provider,
        method: str,
    ) -> KnowledgeRetrieveResult:
        queries = await expand_queries(
            query=query,
            config=retrieve.expansion,
            llm_provider=llm_provider if retrieve.expansion.use_llm else None,
        )
        if not queries:
            return KnowledgeRetrieveResult()

        pool_k = _resolve_pool_k(retrieve)
        query_vectors = None
        if method != "full_text":
            query_vectors = await asyncio.gather(
                *[self._embedding_provider.embed_text(item) for item in queries]
            )

        store_map = {namespace: runtime.store for namespace, runtime in runtimes.items()}
        namespace_map: dict[str, str] = {}
        hits = await _search_queries_multi(
            queries=queries,
            query_vectors=query_vectors,
            method=method,
            pool_k=pool_k,
            filters=filters,
            rrf_k=retrieve.tuning.rrf_k,
            expansion=retrieve.expansion,
            store_map=store_map,
            namespace_map=namespace_map,
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

        store_map = {ns: runtime.store for ns, runtime in runtimes.items()}
        context_hits = await _resolve_context_hits_multi(
            final,
            context=retrieve.context,
            store_map=store_map,
            namespace_map=namespace_map,
        )
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

    async def _get_runtime(self, namespace: str):
        cached = self._runtime_cache.get(namespace)
        if cached is not None:
            await cached.initialize()
            return cached
        config = self._config.model_copy(deep=True)
        runtime = build_runtime(namespace=namespace, config=config)
        await runtime.initialize()
        self._runtime_cache[namespace] = runtime
        return runtime

    async def _get_doc(
        self,
        doc_id: str,
        *,
        cache: dict[str, Any] | None = None,
        runtime,
    ):
        if cache is not None and doc_id in cache:
            return cache[doc_id]
        doc = await runtime.store.get_doc(doc_id)
        if doc is None:
            raise ValueError(f"Document not found for chunk edit: {doc_id}")
        if cache is not None:
            cache[doc_id] = doc
        return doc

    async def _repair_docs(
        self,
        *,
        doc_ids: list[str],
        doc_cache: dict[str, Any] | None,
        runtime,
    ) -> None:
        store = runtime.store
        for doc_id in doc_ids:
            remaining = await store.query_chunks(filters={"doc_id": doc_id}, limit=None)
            if not remaining:
                await store.delete_doc(doc_id)
                continue

            doc = await self._get_doc(doc_id, cache=doc_cache, runtime=runtime)
            chunk_config = _load_chunk_config_optional(doc)
            if chunk_config and chunk_config.window.enabled:
                apply_window_metadata(chunks=remaining, config=chunk_config.window)
                await store.upsert_chunks(remaining)

            if store.supports_external_embeddings:
                vectors = [chunk.vector for chunk in remaining if chunk.vector]
                doc.vector = average_vectors(vectors)
            doc.updated_at = now_ms()
            await store.upsert_docs([doc])



def _load_chunk_config(doc) -> KnowledgeChunkConfig:
    metadata = doc.metadata or {}
    payload = metadata.get("chunk_config")
    if not payload:
        raise ValueError("chunk_config is missing in document metadata")
    if isinstance(payload, KnowledgeChunkConfig):
        return payload
    return KnowledgeChunkConfig.model_validate(payload)


def _load_chunk_config_optional(doc) -> KnowledgeChunkConfig | None:
    try:
        return _load_chunk_config(doc)
    except ValueError:
        return None


def _sort_chunks(chunks: list[KnowledgeChunk], order_by: str) -> list[KnowledgeChunk]:
    if not chunks:
        return []
    fields = [field.strip() for field in (order_by or "").split(",") if field.strip()]
    if not fields:
        return list(chunks)

    def _field_value(chunk: KnowledgeChunk, field: str):
        if field == "doc_id":
            return chunk.doc_id or ""
        if field == "parent_id":
            return chunk.parent_id or ""
        if field == "chunk_index":
            return chunk.chunk_index
        if field == "chunk_type":
            return chunk.chunk_type or ""
        if field == "updated_at":
            return chunk.updated_at or 0
        if field == "source_id":
            return chunk.source_id or ""
        if field == "chunk_id":
            return chunk.chunk_id or ""
        raise ValueError(f"Unsupported order_by field: {field}")

    return sorted(chunks, key=lambda item: tuple(_field_value(item, field) for field in fields))


def _unique_doc_ids(chunks: list[KnowledgeChunk]) -> list[str]:
    seen: set[str] = set()
    ordered: list[str] = []
    for chunk in chunks:
        if chunk.doc_id in seen:
            continue
        seen.add(chunk.doc_id)
        ordered.append(chunk.doc_id)
    return ordered


def _unique_namespaces(namespaces: list[str]) -> list[str]:
    seen: set[str] = set()
    ordered: list[str] = []
    for item in namespaces:
        value = (item or "").strip()
        if not value or value in seen:
            continue
        seen.add(value)
        ordered.append(value)
    return ordered


def _register_namespace(namespace_map: dict[str, str], chunk_id: str, namespace: str) -> None:
    existing = namespace_map.get(chunk_id)
    if existing and existing != namespace:
        raise ValueError(f"Chunk id collision across namespaces: {chunk_id}")
    namespace_map[chunk_id] = namespace


def _resolve_namespace(namespace_map: dict[str, str], chunk_id: str) -> str:
    namespace = namespace_map.get(chunk_id)
    if not namespace:
        raise ValueError(f"Namespace not found for chunk: {chunk_id}")
    return namespace


async def _resolve_context_hits_multi(
    scored: list[tuple[KnowledgeChunk, float]],
    *,
    context,
    store_map: dict[str, Any],
    namespace_map: dict[str, str],
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
        resolved = await _resolve_parent_hits_multi(
            resolved,
            store_map=store_map,
            namespace_map=namespace_map,
        )
    if apply_window:
        resolved = await _resolve_window_hits_multi(
            resolved,
            radius=context.window_radius,
            store_map=store_map,
            namespace_map=namespace_map,
        )
    return resolved


async def _resolve_parent_hits_multi(
    scored: list[tuple[KnowledgeChunk, float]],
    *,
    store_map: dict[str, Any],
    namespace_map: dict[str, str],
) -> list[tuple[KnowledgeChunk, float]]:
    chunks = [chunk for chunk, _ in scored]
    has_child = any(chunk.chunk_type == "child" and chunk.parent_id for chunk in chunks)
    if not has_child:
        return scored

    parent_ids_by_ns: dict[str, list[str]] = {}
    for chunk in chunks:
        if chunk.chunk_type != "child" or not chunk.parent_id:
            continue
        namespace = _resolve_namespace(namespace_map, chunk.chunk_id)
        parent_ids_by_ns.setdefault(namespace, [])
        if chunk.parent_id not in parent_ids_by_ns[namespace]:
            parent_ids_by_ns[namespace].append(chunk.parent_id)

    parent_map: dict[str, dict[str, KnowledgeChunk]] = {}
    for namespace, parent_ids in parent_ids_by_ns.items():
        store = store_map[namespace]
        parents = await store.get_chunks(parent_ids)
        for item in parents:
            _register_namespace(namespace_map, item.chunk_id, namespace)
        parent_map[namespace] = {item.chunk_id: item for item in parents}

    context_hits: list[tuple[KnowledgeChunk, float]] = []
    seen: set[str] = set()
    for chunk, score in scored:
        namespace = _resolve_namespace(namespace_map, chunk.chunk_id)
        key = f"{namespace}:{chunk.chunk_id}"
        if chunk.chunk_type == "child" and chunk.parent_id:
            parent = parent_map.get(namespace, {}).get(chunk.parent_id)
            if parent:
                parent_key = f"{namespace}:{parent.chunk_id}"
                if parent_key not in seen:
                    context_hits.append((parent, score))
                    seen.add(parent_key)
                    continue
        if key not in seen:
            context_hits.append((chunk, score))
            seen.add(key)
    return context_hits


async def _resolve_window_hits_multi(
    scored: list[tuple[KnowledgeChunk, float]],
    *,
    radius: int,
    store_map: dict[str, Any],
    namespace_map: dict[str, str],
) -> list[tuple[KnowledgeChunk, float]]:
    if radius <= 0:
        return scored

    anchor_keys = set()
    window_map: dict[str, tuple[list[str], list[str]]] = {}
    extra_ids: dict[str, set[str]] = {}

    for chunk, _score in scored:
        namespace = _resolve_namespace(namespace_map, chunk.chunk_id)
        key = f"{namespace}:{chunk.chunk_id}"
        anchor_keys.add(key)
        metadata = chunk.metadata or {}
        prev_ids = list(metadata.get("window_prev_ids") or [])
        next_ids = list(metadata.get("window_next_ids") or [])
        if radius:
            prev_ids = prev_ids[-radius:]
            next_ids = next_ids[:radius]
        window_map[key] = (prev_ids, next_ids)
        extra_ids.setdefault(namespace, set())
        for cid in prev_ids:
            if cid and f"{namespace}:{cid}" not in anchor_keys:
                extra_ids[namespace].add(cid)
        for cid in next_ids:
            if cid and f"{namespace}:{cid}" not in anchor_keys:
                extra_ids[namespace].add(cid)

    extra_map: dict[str, dict[str, KnowledgeChunk]] = {}
    for namespace, ids in extra_ids.items():
        if not ids:
            continue
        store = store_map[namespace]
        chunks = await store.get_chunks(list(ids))
        for item in chunks:
            _register_namespace(namespace_map, item.chunk_id, namespace)
        extra_map[namespace] = {item.chunk_id: item for item in chunks}

    context_hits: list[tuple[KnowledgeChunk, float]] = []
    seen: set[str] = set()

    for chunk, score in scored:
        namespace = _resolve_namespace(namespace_map, chunk.chunk_id)
        key = f"{namespace}:{chunk.chunk_id}"
        prev_ids, next_ids = window_map.get(key, ([], []))
        for cid in prev_ids:
            neighbor_key = f"{namespace}:{cid}"
            if neighbor_key in seen or neighbor_key in anchor_keys:
                continue
            neighbor = extra_map.get(namespace, {}).get(cid)
            if neighbor:
                context_hits.append((neighbor, score))
                seen.add(neighbor_key)
        if key not in seen:
            context_hits.append((chunk, score))
            seen.add(key)
        for cid in next_ids:
            neighbor_key = f"{namespace}:{cid}"
            if neighbor_key in seen or neighbor_key in anchor_keys:
                continue
            neighbor = extra_map.get(namespace, {}).get(cid)
            if neighbor:
                context_hits.append((neighbor, score))
                seen.add(neighbor_key)
    return context_hits


async def _search_queries_multi(
    *,
    queries: list[str],
    query_vectors: list[list[float]] | None,
    method: str,
    pool_k: int,
    filters: dict[str, Any] | None,
    rrf_k: int,
    expansion,
    store_map: dict[str, Any],
    namespace_map: dict[str, str],
) -> list[KnowledgeSearchHit]:
    per_query_k = _resolve_per_query_k(pool_k, queries, expansion.per_query_k)

    tasks = []
    for namespace, store in store_map.items():
        tasks.append(
            _search_namespace(
                namespace=namespace,
                store=store,
                queries=queries,
                query_vectors=query_vectors,
                method=method,
                per_query_k=per_query_k,
                filters=filters,
                rrf_k=rrf_k,
            )
        )

    hits: list[KnowledgeSearchHit] = []
    for namespace, batch in await asyncio.gather(*tasks):
        for hit in batch:
            _register_namespace(namespace_map, hit.chunk.chunk_id, namespace)
        hits.extend(batch)

    return _dedupe_chunk_id(hits)


async def _search_namespace(
    *,
    namespace: str,
    store,
    queries: list[str],
    query_vectors: list[list[float]] | None,
    method: str,
    per_query_k: int,
    filters: dict[str, Any] | None,
    rrf_k: int,
) -> tuple[str, list[KnowledgeSearchHit]]:
    tasks = []
    if method == "full_text":
        for query in queries:
            tasks.append(
                store.full_text_search_chunks(
                    query_text=query,
                    k=max(1, per_query_k),
                    filters=filters,
                )
            )
    elif method == "hybrid":
        if not query_vectors:
            raise ValueError("Query vectors are required for hybrid search.")
        for query, vector in zip(queries, query_vectors):
            tasks.append(
                store.hybrid_search_chunks(
                    query_vector=vector,
                    query_text=query,
                    k=max(1, per_query_k),
                    filters=filters,
                    rrf_k=rrf_k,
                )
            )
    else:
        if not query_vectors:
            raise ValueError("Query vectors are required for semantic search.")
        for vector in query_vectors:
            tasks.append(
                store.search_chunks(
                    query_vector=vector,
                    k=max(1, per_query_k),
                    filters=filters,
                )
            )

    results: list[KnowledgeSearchHit] = []
    for batch in await asyncio.gather(*tasks):
        results.extend(batch)
    return namespace, _dedupe_chunk_id(results)
