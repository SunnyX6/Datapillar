# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
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


class MilvusModelReranker:
    def __init__(
        self,
        *,
        model_kind: str,
        model_name: str,
        init_params: dict[str, Any] | None = None,
        call_defaults: dict[str, Any] | None = None,
    ) -> None:
        try:
            from pymilvus.model import reranker as milvus_reranker
        except ImportError as err:
            raise ImportError(
                "Milvus reranking requires pymilvus[model]. "
                "Install with: pip install \"pymilvus[model]\""
            ) from err

        kind = (model_kind or "bge").lower()
        if kind in {"bge", "bge_reranker", "bge_rerank"}:
            reranker_cls = milvus_reranker.BGERerankFunction
        elif kind in {"cross_encoder", "cross-encoder", "crossencoder"}:
            reranker_cls = milvus_reranker.CrossEncoderRerankFunction
        elif kind in {"cohere"}:
            reranker_cls = milvus_reranker.CohereRerankFunction
        elif kind in {"jina"}:
            reranker_cls = milvus_reranker.JinaRerankFunction
        elif kind in {"voyage"}:
            reranker_cls = milvus_reranker.VoyageRerankFunction
        else:
            raise ValueError(f"Unsupported Milvus reranker kind: {model_kind}")

        params = dict(init_params or {})
        params.setdefault("model_name", model_name)
        self._reranker = reranker_cls(**params)
        self._call_defaults = dict(call_defaults or {})
        self._init_param_keys = set(params.keys())

    def score(self, query: str, passages: list[str], **kwargs: Any) -> list[float]:
        call_params = dict(self._call_defaults)
        if "call" in kwargs:
            overrides = kwargs.get("call") or {}
            if not isinstance(overrides, dict):
                raise ValueError("Milvus reranker call params must be a dict.")
            call_params.update(overrides)
        else:
            for key, value in kwargs.items():
                if key in self._init_param_keys or key in {"init", "kind", "type"}:
                    continue
                call_params[key] = value

        top_k = call_params.pop("top_k", None)
        if top_k is None:
            top_k = len(passages)

        results = self._reranker(query=query, documents=passages, top_k=top_k, **call_params)

        scores = [0.0 for _ in passages]
        for item in results or []:
            index = getattr(item, "index", None)
            score = getattr(item, "score", None)
            if index is None or score is None:
                continue
            if 0 <= index < len(scores):
                scores[index] = float(score)
        return scores


def build_reranker(config: RerankConfig) -> Reranker:
    provider = (config.provider or "").lower()
    if provider in {"sentence_transformers", "st"}:
        params = dict(config.params or {})
        device = params.pop("device", None)
        batch_size = params.pop("batch_size", None)
        model = config.model or "cross-encoder/ms-marco-MiniLM-L-6-v2"
        return SentenceTransformersReranker(model=model, device=device, batch_size=batch_size)
    if provider in {"milvus"}:
        params = dict(config.params or {})
        init_params = {}
        call_defaults = {}
        if "init" in params or "call" in params:
            init_params = dict(params.get("init") or {})
            call_defaults = dict(params.get("call") or {})
        else:
            init_params = params
        kind = init_params.pop("kind", None) or init_params.pop("type", None) or "bge"
        model_name = init_params.pop("model_name", None) or config.model
        if not model_name:
            raise ValueError("Milvus reranker requires a model name.")
        return MilvusModelReranker(
            model_kind=str(kind),
            model_name=str(model_name),
            init_params=init_params,
            call_defaults=call_defaults,
        )
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
