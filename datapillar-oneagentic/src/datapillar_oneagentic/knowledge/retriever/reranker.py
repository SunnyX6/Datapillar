"""Reranker."""

from __future__ import annotations

import asyncio
from typing import Any, Protocol

from datapillar_oneagentic.knowledge.config import RerankConfig


class Reranker(Protocol):
    def score(self, query: str, passages: list[str], **kwargs: Any) -> list[float]:
        """Score candidate passages."""


class SentenceTransformersReranker:
    def __init__(self, *, model: str, device: str | None = None, batch_size: int | None = None) -> None:
        try:
            from sentence_transformers import CrossEncoder
        except ImportError as err:
            raise ImportError(
                "sentence_transformers reranking requires dependencies:\n"
                "  pip install datapillar-oneagentic[knowledge]"
            ) from err
        self._encoder = CrossEncoder(model, device=device)
        self._batch_size = batch_size

    def score(self, query: str, passages: list[str], **kwargs: Any) -> list[float]:
        pairs = [(query, passage) for passage in passages]
        params = dict(kwargs)
        params.pop("device", None)
        batch_size = params.pop("batch_size", self._batch_size)
        scores = self._encoder.predict(pairs, batch_size=batch_size, **params)
        return [float(score) for score in scores]


def build_reranker(config: RerankConfig) -> Reranker:
    provider = (config.provider or "").lower()
    if provider in {"sentence_transformers", "st"}:
        params = dict(config.params or {})
        device = params.pop("device", None)
        batch_size = params.pop("batch_size", None)
        model = config.model or "cross-encoder/ms-marco-MiniLM-L-6-v2"
        return SentenceTransformersReranker(model=model, device=device, batch_size=batch_size)
    raise ValueError(f"Unsupported rerank provider: {config.provider}")


async def rerank_scores(
    reranker: Reranker,
    *,
    query: str,
    passages: list[str],
    params: dict[str, Any] | None = None,
) -> list[float]:
    extra = params or {}
    return await asyncio.to_thread(reranker.score, query, passages, **extra)
