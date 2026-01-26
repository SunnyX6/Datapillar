from __future__ import annotations

import pytest

from datapillar_oneagentic.knowledge import BM25SparseEmbedder


@pytest.mark.asyncio
async def test_bm25_sparse_embedder_requires_fit() -> None:
    embedder = BM25SparseEmbedder()
    with pytest.raises(RuntimeError):
        await embedder.embed_text("alpha")


@pytest.mark.asyncio
async def test_bm25_sparse_embedder_vectors() -> None:
    embedder = BM25SparseEmbedder()
    docs = ["alpha beta", "beta gamma"]
    doc_vectors = await embedder.embed_texts(docs)

    assert len(doc_vectors) == 2
    assert all(isinstance(vec, dict) for vec in doc_vectors)
    assert doc_vectors[0]

    query_vector = await embedder.embed_text("beta")
    assert query_vector
    assert set(query_vector.keys()) & set(doc_vectors[0].keys())
