"""
知识模型
"""

from __future__ import annotations

import copy
from dataclasses import dataclass, field, fields
from typing import Any, Protocol


class SparseEmbeddingProvider(Protocol):
    """稀疏向量化接口（由使用者提供实现）"""

    async def embed_text(self, text: str) -> dict[int, float]:
        """向量化单条文本"""

    async def embed_texts(self, texts: list[str]) -> list[dict[int, float]]:
        """批量向量化文本"""


@dataclass
class DocumentInput:
    """文档输入（解析入口）"""

    source: str | bytes
    filename: str | None = None
    mime_type: str | None = None
    parser_hint: str | None = None
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass
class Attachment:
    """附件（图片等非文本内容）"""

    attachment_id: str
    name: str
    mime_type: str
    content: bytes | None = None
    content_ref: str | None = None
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass
class ParsedDocument:
    """解析后的文档结构"""

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
    """文档原文定位信息"""

    page: int | None = None
    start_offset: int | None = None
    end_offset: int | None = None
    block_id: str | None = None


@dataclass
class KnowledgeSource:
    """知识来源定义（注册用）"""

    source_id: str
    name: str
    source_type: str
    source_uri: str | None = None
    tags: list[str] = field(default_factory=list)
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass
class KnowledgeInject:
    """注入配置（Agent 覆盖项）"""

    mode: str | None = None
    max_tokens: int | None = None
    max_chunks: int | None = None
    format: str | None = None


@dataclass
class KnowledgeRetrieve:
    """检索配置（Agent 覆盖项）"""

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
    """检索范围（运行时）"""

    namespaces: list[str] | None = None
    document_ids: list[str] | None = None
    tags: list[str] | None = None


@dataclass
class Knowledge:
    """Agent 知识配置（声明式）"""

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
    """知识文档（入库输入）"""

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
    """知识分片（检索输出）"""

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
    """知识检索命中结果（含分数）"""

    chunk: KnowledgeChunk
    score: float
    score_kind: str


@dataclass
class KnowledgeRef:
    """知识引用（写入经验）"""

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
    """知识检索结果"""

    hits: list[tuple[KnowledgeChunk, float]] = field(default_factory=list)
    refs: list[KnowledgeRef] = field(default_factory=list)


def merge_knowledge(base: Knowledge | None, override: Knowledge | None) -> Knowledge | None:
    """合并知识配置（团队为 base，Agent 为 override）"""
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
        index[source.source_id] = len(merged) - 1

    for source in override_sources:
        if source.source_id in index:
            merged[index[source.source_id]] = copy.deepcopy(source)
        else:
            index[source.source_id] = len(merged)
            merged.append(copy.deepcopy(source))

    return merged


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
