# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""Built-in sparse embedders for knowledge retrieval."""

from __future__ import annotations

import math
import re
from collections import Counter
from typing import Callable

from datapillar_oneagentic.knowledge.models import SparseEmbeddingProvider

_TOKEN_PATTERN = re.compile(r"\b\w+\b", re.UNICODE)


def _default_tokenizer(text: str) -> list[str]:
    return _TOKEN_PATTERN.findall(text.lower())


class BM25SparseEmbedder(SparseEmbeddingProvider):
    """
    Lightweight BM25 sparse embedder.

    Notes:
        - This embedder is corpus-bound: call embed_texts on the document corpus before querying.
        - Keep the same instance for both ingestion and retrieval to preserve token IDs and IDF.
    """

    def __init__(
        self,
        *,
        tokenizer: Callable[[str], list[str]] | None = None,
        k1: float = 1.5,
        b: float = 0.75,
        min_idf: float = 1e-6,
    ) -> None:
        if k1 <= 0:
            raise ValueError("k1 must be > 0")
        if not 0 <= b <= 1:
            raise ValueError("b must be between 0 and 1")
        if min_idf < 0:
            raise ValueError("min_idf must be >= 0")
        self._tokenizer = tokenizer or _default_tokenizer
        self._k1 = k1
        self._b = b
        self._min_idf = min_idf
        self._term_to_id: dict[str, int] = {}
        self._idf_by_id: dict[int, float] = {}
        self._avgdl = 1.0
        self._fitted = False

    async def embed_text(self, text: str) -> dict[int, float]:
        if not self._fitted:
            raise RuntimeError("BM25SparseEmbedder must be fitted via embed_texts before querying")
        tokens = self._tokenizer(text or "")
        if not tokens:
            return {}
        freqs = Counter(tokens)
        vector: dict[int, float] = {}
        for term, tf in freqs.items():
            term_id = self._term_to_id.get(term)
            if term_id is None:
                continue
            idf = self._idf_by_id.get(term_id, self._min_idf)
            vector[term_id] = idf * float(tf)
        return vector

    async def embed_texts(self, texts: list[str]) -> list[dict[int, float]]:
        if not texts:
            return []
        tokenized = [self._tokenizer(text or "") for text in texts]
        if not self._fitted:
            self._fit(tokenized)
        return [self._build_doc_vector(tokens) for tokens in tokenized]

    def _fit(self, tokenized: list[list[str]]) -> None:
        doc_count = len(tokenized)
        if doc_count == 0:
            self._fitted = True
            return
        total_len = sum(len(tokens) for tokens in tokenized)
        self._avgdl = max(total_len / doc_count, 1.0)
        doc_freq: Counter[str] = Counter()
        for tokens in tokenized:
            for term in set(tokens):
                doc_freq[term] += 1
        for term, df in doc_freq.items():
            term_id = self._term_to_id.setdefault(term, len(self._term_to_id))
            idf = math.log(1.0 + (doc_count - df + 0.5) / (df + 0.5))
            self._idf_by_id[term_id] = max(idf, self._min_idf)
        self._fitted = True

    def _build_doc_vector(self, tokens: list[str]) -> dict[int, float]:
        if not tokens:
            return {}
        freqs = Counter(tokens)
        doc_len = max(len(tokens), 1)
        norm = 1 - self._b + (self._b * doc_len / self._avgdl)
        vector: dict[int, float] = {}
        for term, tf in freqs.items():
            term_id = self._term_to_id.get(term)
            if term_id is None:
                term_id = len(self._term_to_id)
                self._term_to_id[term] = term_id
                self._idf_by_id[term_id] = self._min_idf
            denom = float(tf) + self._k1 * norm
            if denom <= 0:
                continue
            weight = (float(tf) * (self._k1 + 1.0)) / denom
            vector[term_id] = weight
        return vector
