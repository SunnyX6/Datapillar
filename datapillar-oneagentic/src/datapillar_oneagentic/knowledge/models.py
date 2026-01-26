"""Knowledge models."""

from __future__ import annotations

import copy
import os
from dataclasses import dataclass, field, fields
from typing import Any, Protocol
from urllib.parse import urlparse

from datapillar_oneagentic.knowledge.identity import canonicalize_metadata

class SparseEmbeddingProvider(Protocol):
    """Sparse embedding interface (provided by callers)."""

    async def embed_text(self, text: str) -> dict[int, float]:
        """Embed a single text."""

    async def embed_texts(self, texts: list[str]) -> list[dict[int, float]]:
        """Embed a batch of texts."""


@dataclass
class DocumentInput:
    """Document input (parser entrypoint)."""

    source: str | bytes
    filename: str | None = None
    mime_type: str | None = None
    parser_hint: str | None = None
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass
class Attachment:
    """Attachment (non-text content such as images)."""

    attachment_id: str
    name: str
    mime_type: str
    content: bytes | None = None
    content_ref: str | None = None
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass
class ParsedDocument:
    """Parsed document structure."""

    document_id: str
    source_type: str
    mime_type: str
    text: str
    pages: list[str] = field(default_factory=list)
    attachments: list[Attachment] = field(default_factory=list)
    content_hash: str | None = None
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass
class SourceSpan:
    """Source span location info."""

    page: int | None = None
    start_offset: int | None = None
    end_offset: int | None = None
    block_id: str | None = None


@dataclass
class KnowledgeSource:
    """Knowledge source definition (for registration)."""

    name: str | None = None
    source_type: str = "doc"
    source_id: str | None = None
    source_uri: str | None = None
    tags: list[str] = field(default_factory=list)
    metadata: dict[str, Any] = field(default_factory=dict)
    content: str | bytes | None = None
    filename: str | None = None
    mime_type: str | None = None
    parser_hint: str | None = None

    def __post_init__(self) -> None:
        if not self.name:
            self.name = self.source_uri or "Untitled"

    def chunk(
        self,
        *,
        chunk_config: "KnowledgeChunkConfig",
        parser_registry: "ParserRegistry | None" = None,
    ) -> "ChunkPreview":
        if not self.source_uri:
            raise ValueError("source_uri cannot be empty")
        if chunk_config is None:
            raise ValueError("chunk_config cannot be empty")
        from datapillar_oneagentic.knowledge.chunker import KnowledgeChunker
        from datapillar_oneagentic.knowledge.parser import default_registry

        doc_input = _build_document_input(self)
        registry = parser_registry or default_registry()
        parsed = registry.parse(doc_input)
        chunker = KnowledgeChunker(config=chunk_config)
        return chunker.preview(parsed)

    async def ingest(
        self,
        *,
        namespace: str,
        config: "KnowledgeConfig",
        sparse_embedder: "SparseEmbeddingProvider | None" = None,
        parser_registry: "ParserRegistry | None" = None,
    ) -> None:
        if not self.source_uri:
            raise ValueError("source_uri cannot be empty")
        if not namespace:
            raise ValueError("namespace cannot be empty")
        if config is None:
            raise ValueError("config cannot be empty")
        from datapillar_oneagentic.knowledge.ingest.pipeline import KnowledgeIngestor
        from datapillar_oneagentic.knowledge.parser import default_registry
        from datapillar_oneagentic.knowledge.runtime import build_runtime

        runtime = build_runtime(namespace=namespace, base_config=config.base_config)
        await runtime.initialize()
        doc_input = _build_document_input(self)
        registry = parser_registry or default_registry()
        ingestor = KnowledgeIngestor(
            store=runtime.store,
            embedding_provider=runtime.embedding_provider,
            config=config.chunk_config,
            parser_registry=registry,
        )
        await ingestor.ingest(source=self, documents=[doc_input], sparse_embedder=sparse_embedder)
        await runtime.store.close()


@dataclass
class KnowledgeInject:
    """Injection overrides (agent-level)."""

    mode: str | None = None
    max_tokens: int | None = None
    max_chunks: int | None = None
    format: str | None = None


@dataclass
class KnowledgeRetrieve:
    """Retrieval overrides (agent-level)."""

    method: str | None = None
    top_k: int | None = None
    score_threshold: float | None = None
    pool_k: int | None = None
    rerank_k: int | None = None
    rrf_k: int | None = None
    max_per_document: int | None = None
    dedupe: bool | None = None
    dedupe_threshold: float | None = None
    rerank: dict[str, Any] | None = None
    inject: KnowledgeInject | None = None


@dataclass
class KnowledgeScope:
    """Retrieval scope (runtime)."""

    namespaces: list[str] | None = None
    document_ids: list[str] | None = None
    tags: list[str] | None = None


@dataclass
class Knowledge:
    """Agent knowledge configuration (declarative)."""

    sources: list[KnowledgeSource] = field(default_factory=list)
    retrieve: KnowledgeRetrieve | None = None
    inject: KnowledgeInject | None = None
    sparse_embedder: SparseEmbeddingProvider | None = field(default=None, repr=False, compare=False)

    def __deepcopy__(self, memo):
        return Knowledge(
            sources=copy.deepcopy(self.sources, memo),
            retrieve=copy.deepcopy(self.retrieve, memo),
            inject=copy.deepcopy(self.inject, memo),
            sparse_embedder=self.sparse_embedder,
        )


@dataclass
class KnowledgeDocument:
    """Knowledge document (ingestion input)."""

    doc_id: str
    source_id: str
    title: str
    content: str
    source_uri: str | None = None
    version: str = "1.0.0"
    language: str = "zh"
    status: str = "published"
    content_hash: str | None = None
    content_ref: str | None = None
    created_at: int | None = None
    updated_at: int | None = None
    vector: list[float] = field(default_factory=list)
    tags: list[str] = field(default_factory=list)
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass
class KnowledgeChunk:
    """Knowledge chunk (retrieval output)."""

    chunk_id: str
    doc_id: str
    source_id: str
    content: str
    vector: list[float] = field(default_factory=list)
    doc_title: str = ""
    parent_id: str | None = None
    chunk_type: str = "parent"
    content_hash: str | None = None
    sparse_vector: dict[int, float] | None = None
    token_count: int = 0
    chunk_index: int = 0
    section_path: str = ""
    version: str = "1.0.0"
    status: str = "published"
    source_spans: list[SourceSpan] = field(default_factory=list)
    metadata: dict[str, Any] = field(default_factory=dict)
    created_at: int | None = None
    updated_at: int | None = None


@dataclass
class KnowledgeSearchHit:
    """Knowledge retrieval hit (with score)."""

    chunk: KnowledgeChunk
    score: float
    score_kind: str


@dataclass
class KnowledgeRef:
    """Knowledge reference (for experience records)."""

    source_id: str
    doc_id: str
    chunk_id: str
    score: float
    version: str | None = None
    query: str | None = None

    def to_dict(self) -> dict[str, Any]:
        return {
            "source_id": self.source_id,
            "doc_id": self.doc_id,
            "chunk_id": self.chunk_id,
            "score": self.score,
            "version": self.version,
            "query": self.query,
        }


@dataclass
class KnowledgeRetrieveResult:
    """Knowledge retrieval result."""

    hits: list[tuple[KnowledgeChunk, float]] = field(default_factory=list)
    refs: list[KnowledgeRef] = field(default_factory=list)


def merge_knowledge(base: Knowledge | None, override: Knowledge | None) -> Knowledge | None:
    """Merge knowledge configs (team as base, agent as override)."""
    if base is None and override is None:
        return None
    if base is None:
        return copy.deepcopy(override)
    if override is None:
        return copy.deepcopy(base)

    merged = copy.deepcopy(base)
    merged.sources = _merge_sources(base.sources, override.sources)
    merged.retrieve = _merge_retrieve(base.retrieve, override.retrieve)
    merged.inject = _merge_inject(base.inject, override.inject)
    if override.sparse_embedder is not None:
        merged.sparse_embedder = override.sparse_embedder
    return merged


def _merge_sources(
    base_sources: list[KnowledgeSource],
    override_sources: list[KnowledgeSource],
) -> list[KnowledgeSource]:
    merged: list[KnowledgeSource] = []
    index: dict[str, int] = {}

    for source in base_sources:
        merged.append(copy.deepcopy(source))
        source_key = _source_merge_key(source)
        index[source_key] = len(merged) - 1

    for source in override_sources:
        source_key = _source_merge_key(source)
        if source_key in index:
            merged[index[source_key]] = copy.deepcopy(source)
        else:
            index[source_key] = len(merged)
            merged.append(copy.deepcopy(source))

    return merged


def _source_merge_key(source: KnowledgeSource) -> str:
    if source.source_id:
        return source.source_id
    canonical = canonicalize_metadata(source.metadata)
    return f"{source.source_type}|{source.source_uri or ''}|{canonical}"


def _build_document_input(source: KnowledgeSource) -> DocumentInput:
    source_uri = source.source_uri
    if not source_uri:
        raise ValueError("source_uri cannot be empty")
    payload, filename, mime_type, source_info = _resolve_source_payload(source)
    source_info.setdefault("source_type", source.source_type)
    source_info.setdefault("source_uri", source_uri)
    metadata = dict(source.metadata)
    _merge_source_info(metadata, source_info)
    return DocumentInput(
        source=payload,
        filename=filename,
        mime_type=mime_type,
        parser_hint=source.parser_hint,
        metadata=metadata,
    )


def _resolve_source_payload(
    source: KnowledgeSource,
) -> tuple[str | bytes, str | None, str | None, dict[str, Any]]:
    source_uri = source.source_uri or ""
    if source.content is not None:
        return _load_from_inline(source.content, source)
    if _is_url(source_uri):
        return _load_from_url(source_uri, source)
    if os.path.exists(source_uri):
        return _load_from_file(source_uri, source)
    return _load_from_text(source_uri, source)


def _load_from_url(
    source_uri: str,
    source: KnowledgeSource,
) -> tuple[bytes, str | None, str | None, dict[str, Any]]:
    import httpx

    response = httpx.get(source_uri, follow_redirects=True, timeout=30.0)
    response.raise_for_status()
    headers = response.headers
    content_type = headers.get("content-type")
    mime_type = source.mime_type or _normalize_content_type(content_type)
    filename = source.filename or _extract_filename(headers.get("content-disposition"))
    if not filename:
        filename = _filename_from_url(str(response.url)) or source.filename
    info = {
        "source_kind": "url",
        "final_url": str(response.url),
        "http_status": response.status_code,
        "http_content_type": content_type,
        "http_content_length": _to_int(headers.get("content-length")),
        "http_last_modified": headers.get("last-modified"),
        "http_etag": headers.get("etag"),
    }
    return response.content, filename, mime_type, info


def _load_from_file(
    source_uri: str,
    source: KnowledgeSource,
) -> tuple[str, str | None, str | None, dict[str, Any]]:
    stat = os.stat(source_uri)
    info = {
        "source_kind": "file",
        "file_size": stat.st_size,
        "file_mtime": int(stat.st_mtime),
        "file_ctime": int(stat.st_ctime),
        "file_path": os.path.abspath(source_uri),
    }
    filename = source.filename or os.path.basename(source_uri)
    return source_uri, filename, source.mime_type, info


def _load_from_text(
    content: str,
    source: KnowledgeSource,
) -> tuple[str, str | None, str | None, dict[str, Any]]:
    info = {
        "source_kind": "text",
        "text_length": len(content),
    }
    return content, source.filename, source.mime_type, info


def _load_from_inline(
    content: str | bytes,
    source: KnowledgeSource,
) -> tuple[str | bytes, str | None, str | None, dict[str, Any]]:
    length = len(content) if isinstance(content, (bytes, str)) else 0
    info = {
        "source_kind": "inline",
        "content_length": length,
    }
    return content, source.filename, source.mime_type, info


def _merge_source_info(metadata: dict[str, Any], info: dict[str, Any]) -> None:
    if "source_info" in metadata:
        metadata["source_info_auto"] = info
    else:
        metadata["source_info"] = info


def _extract_filename(value: str | None) -> str | None:
    if not value:
        return None
    parts = [part.strip() for part in value.split(";") if part.strip()]
    for part in parts:
        if part.lower().startswith("filename="):
            return part.split("=", 1)[1].strip().strip('"')
    return None


def _filename_from_url(url: str) -> str | None:
    parsed = urlparse(url)
    if not parsed.path:
        return None
    name = parsed.path.split("/")[-1]
    return name or None


def _normalize_content_type(value: str | None) -> str | None:
    if not value:
        return None
    return value.split(";", 1)[0].strip().lower() or None


def _to_int(value: str | None) -> int | None:
    if not value:
        return None
    if not value.isdigit():
        return None
    return int(value)


def _is_url(value: str) -> bool:
    return value.startswith("http://") or value.startswith("https://")


def _merge_inject(
    base: KnowledgeInject | None,
    override: KnowledgeInject | None,
) -> KnowledgeInject | None:
    if base is None and override is None:
        return None
    if base is None:
        return copy.deepcopy(override)
    if override is None:
        return copy.deepcopy(base)

    merged = copy.deepcopy(base)
    for field_item in fields(KnowledgeInject):
        value = getattr(override, field_item.name)
        if value is not None:
            setattr(merged, field_item.name, value)
    return merged


def _merge_retrieve(
    base: KnowledgeRetrieve | None,
    override: KnowledgeRetrieve | None,
) -> KnowledgeRetrieve | None:
    if base is None and override is None:
        return None
    if base is None:
        return copy.deepcopy(override)
    if override is None:
        return copy.deepcopy(base)

    merged = copy.deepcopy(base)
    for field_item in fields(KnowledgeRetrieve):
        if field_item.name == "rerank":
            if override.rerank is not None:
                if merged.rerank is None:
                    merged.rerank = {}
                for key, value in override.rerank.items():
                    if value is not None:
                        merged.rerank[key] = value
            continue
        if field_item.name == "inject":
            merged.inject = _merge_inject(base.inject, override.inject)
            continue

        value = getattr(override, field_item.name)
        if value is not None:
            setattr(merged, field_item.name, value)

    return merged
