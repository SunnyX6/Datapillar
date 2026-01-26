"""Evaluation metric calculations."""

from __future__ import annotations

import hashlib
import math
import statistics
from collections.abc import Iterable

from datapillar_oneagentic.knowledge.evaluation.schema import LengthStats
from datapillar_oneagentic.knowledge.models import SourceSpan


def compute_length_stats(lengths: list[int]) -> LengthStats:
    if not lengths:
        return LengthStats(count=0, min=0, max=0, mean=0.0, std=0.0)

    mean_value = statistics.mean(lengths)
    std_value = statistics.pstdev(lengths) if len(lengths) > 1 else 0.0
    return LengthStats(
        count=len(lengths),
        min=min(lengths),
        max=max(lengths),
        mean=float(mean_value),
        std=float(std_value),
    )


def compute_duplicate_ratio(texts: list[str]) -> float:
    if not texts:
        return 0.0
    hashes = [hashlib.sha256(text.encode("utf-8")).hexdigest() for text in texts]
    unique = len(set(hashes))
    return (len(texts) - unique) / len(texts)


def compute_coverage_overlap(
    *,
    doc_length: int,
    spans: Iterable[SourceSpan],
) -> tuple[float | None, float | None]:
    if doc_length <= 0:
        return None, None

    intervals: list[tuple[int, int]] = []
    for span in spans:
        if span.start_offset is None or span.end_offset is None:
            continue
        if span.end_offset <= span.start_offset:
            continue
        intervals.append((span.start_offset, span.end_offset))

    if not intervals:
        return None, None

    merged = _merge_intervals(intervals)
    union_length = sum(end - start for start, end in merged)
    total_length = sum(end - start for start, end in intervals)
    if total_length <= 0:
        return None, None

    coverage_ratio = min(union_length / doc_length, 1.0)
    overlap_ratio = max((total_length - union_length) / total_length, 0.0)
    return coverage_ratio, overlap_ratio


def compute_ranking_metrics(
    *,
    retrieved_ids: list[str],
    relevant_ids: set[str],
    relevance: dict[str, int] | None,
    k_values: list[int],
) -> dict[str, float]:
    metrics: dict[str, float] = {}
    relevance_map = relevance or {}
    for k in k_values:
        top_ids = retrieved_ids[:k]
        hit = 1.0 if any(item in relevant_ids for item in top_ids) else 0.0
        recall = _recall_at_k(top_ids, relevant_ids)
        mrr = _mrr_at_k(top_ids, relevant_ids)
        ndcg = _ndcg_at_k(top_ids, relevant_ids, relevance_map)
        metrics[f"hit@{k}"] = hit
        metrics[f"recall@{k}"] = recall
        metrics[f"mrr@{k}"] = mrr
        metrics[f"ndcg@{k}"] = ndcg
    return metrics


def _merge_intervals(intervals: list[tuple[int, int]]) -> list[tuple[int, int]]:
    intervals_sorted = sorted(intervals, key=lambda item: item[0])
    merged: list[tuple[int, int]] = []
    for start, end in intervals_sorted:
        if not merged or start > merged[-1][1]:
            merged.append((start, end))
            continue
        prev_start, prev_end = merged[-1]
        merged[-1] = (prev_start, max(prev_end, end))
    return merged


def _recall_at_k(top_ids: list[str], relevant_ids: set[str]) -> float:
    if not relevant_ids:
        return 0.0
    hit_count = len([item for item in top_ids if item in relevant_ids])
    return hit_count / len(relevant_ids)


def _mrr_at_k(top_ids: list[str], relevant_ids: set[str]) -> float:
    for idx, item in enumerate(top_ids, 1):
        if item in relevant_ids:
            return 1.0 / idx
    return 0.0


def _ndcg_at_k(
    top_ids: list[str],
    relevant_ids: set[str],
    relevance_map: dict[str, int],
) -> float:
    if not relevant_ids:
        return 0.0
    gains = []
    for item in top_ids:
        if relevance_map:
            gains.append(relevance_map.get(item, 0))
        else:
            gains.append(1 if item in relevant_ids else 0)

    dcg = _dcg(gains)
    ideal_gains = sorted(
        [relevance_map.get(item, 0) for item in relevant_ids] if relevance_map else [1] * len(relevant_ids),
        reverse=True,
    )[: len(top_ids)]
    idcg = _dcg(ideal_gains)
    if idcg == 0:
        return 0.0
    return dcg / idcg


def _dcg(relevances: list[int]) -> float:
    score = 0.0
    for idx, rel in enumerate(relevances, 1):
        if rel <= 0:
            continue
        score += (2**rel - 1) / math.log2(idx + 1)
    return score
