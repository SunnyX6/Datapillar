"""
证据治理：分组与去重
"""

from __future__ import annotations

import math
import hashlib
from typing import Iterable

from datapillar_oneagentic.knowledge.models import KnowledgeChunk


def group_hits(
    hits: Iterable[tuple[KnowledgeChunk, float]],
    *,
    max_per_document: int,
) -> list[tuple[KnowledgeChunk, float]]:
    if max_per_document <= 0:
        return []

    groups: dict[tuple[str, str], list[tuple[KnowledgeChunk, float]]] = {}
    for chunk, score in hits:
        group_key = (chunk.doc_id, chunk.parent_id or chunk.doc_id)
        groups.setdefault(group_key, []).append((chunk, score))

    selected: list[tuple[KnowledgeChunk, float]] = []
    for items in groups.values():
        items.sort(key=lambda item: item[1], reverse=True)
        selected.extend(items[:max_per_document])

    selected.sort(key=lambda item: item[1], reverse=True)
    return selected


def dedupe_hits(
    hits: Iterable[tuple[KnowledgeChunk, float]],
    *,
    threshold: float | None,
) -> list[tuple[KnowledgeChunk, float]]:
    selected: list[tuple[KnowledgeChunk, float]] = []
    seen_hashes: set[str] = set()

    for chunk, score in hits:
        content_hash = chunk.content_hash or _hash_content(chunk.content)
        if content_hash and content_hash in seen_hashes:
            continue

        if threshold is not None and threshold > 0:
            if _is_semantic_duplicate(chunk, selected, threshold):
                continue

        if content_hash:
            seen_hashes.add(content_hash)
        selected.append((chunk, score))

    return selected


def _is_semantic_duplicate(
    chunk: KnowledgeChunk,
    selected: list[tuple[KnowledgeChunk, float]],
    threshold: float,
) -> bool:
    if not chunk.vector:
        return False
    for other, _ in selected:
        if not other.vector:
            continue
        if _cosine_similarity(chunk.vector, other.vector) >= threshold:
            return True
    return False


def _cosine_similarity(a: list[float], b: list[float]) -> float:
    dot = 0.0
    norm_a = 0.0
    norm_b = 0.0
    for av, bv in zip(a, b):
        dot += av * bv
        norm_a += av * av
        norm_b += bv * bv
    if norm_a == 0.0 or norm_b == 0.0:
        return 0.0
    return dot / (math.sqrt(norm_a) * math.sqrt(norm_b))


def _hash_content(content: str) -> str:
    return hashlib.sha256(content.encode("utf-8")).hexdigest()
