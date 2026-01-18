from __future__ import annotations

from dataclasses import dataclass

import pytest

from datapillar_oneagentic.knowledge.chunker import KnowledgeChunker
from datapillar_oneagentic.knowledge.chunker.models import ChunkPreview
from datapillar_oneagentic.knowledge.config import KnowledgeChunkConfig
from datapillar_oneagentic.knowledge.ingest.pipeline import KnowledgeIngestor
from datapillar_oneagentic.knowledge.models import Attachment, DocumentInput, ParsedDocument


def _parsed(text: str) -> ParsedDocument:
    return ParsedDocument(
        document_id="doc1",
        source_type="text",
        mime_type="text/plain",
        text=text,
        pages=[text],
        attachments=[],
        metadata={},
    )


def test_chunker_general_with_delimiter() -> None:
    config = KnowledgeChunkConfig(
        mode="general",
        general={"max_tokens": 8, "overlap": 0, "delimiter": "|"},
    )
    chunker = KnowledgeChunker(config=config)
    preview = chunker.preview(_parsed("hello|world|again"))

    assert [chunk.content for chunk in preview.chunks] == ["hello", "world", "again"]


def test_chunker_parent_child_creates_links() -> None:
    config = KnowledgeChunkConfig(
        mode="parent_child",
        parent_child={
            "parent": {"max_tokens": 8, "overlap": 0},
            "child": {"max_tokens": 4, "overlap": 0},
        },
    )
    chunker = KnowledgeChunker(config=config)
    preview = chunker.preview(_parsed("abcdefghijklm"))

    parents = [chunk for chunk in preview.chunks if chunk.chunk_type == "parent"]
    children = [chunk for chunk in preview.chunks if chunk.chunk_type == "child"]

    assert parents
    assert children
    parent_ids = {chunk.chunk_id for chunk in parents}
    assert all(child.parent_id in parent_ids for child in children)


def test_chunker_qa_mode_extracts_pairs() -> None:
    config = KnowledgeChunkConfig(mode="qa")
    chunker = KnowledgeChunker(config=config)
    preview = chunker.preview(_parsed("Q1: What?\nA1: Answer.\nQ2: Why?\nA2: Because."))

    assert len(preview.chunks) == 2
    assert preview.chunks[0].content.startswith("Q: ")


def test_chunker_qa_mode_fallbacks_to_general() -> None:
    config = KnowledgeChunkConfig(mode="qa")
    chunker = KnowledgeChunker(config=config)
    preview = chunker.preview(_parsed("No QA here"))

    assert preview.chunks


def test_chunker_applies_preprocess_rules() -> None:
    config = KnowledgeChunkConfig(
        mode="general",
        preprocess=["normalize_newlines", "collapse_whitespace", "remove_control", "strip"],
        general={"max_tokens": 100, "overlap": 0},
    )
    chunker = KnowledgeChunker(config=config)
    preview = chunker.preview(_parsed("a\x01\r\nb\t\tc "))

    assert preview.chunks[0].content == "a\nb c"


def test_chunker_invalid_rule_raises() -> None:
    config = KnowledgeChunkConfig(
        mode="general",
        preprocess=["unknown_rule"],
        general={"max_tokens": 10, "overlap": 0},
    )
    chunker = KnowledgeChunker(config=config)

    with pytest.raises(ValueError):
        chunker.preview(_parsed("text"))


def test_ingestor_preview_returns_attachments() -> None:
    attachment = Attachment(
        attachment_id="att1",
        name="att1.png",
        mime_type="image/png",
        content=b"img",
    )

    @dataclass
    class _StubParserRegistry:
        def parse(self, doc_input: DocumentInput) -> ParsedDocument:
            return ParsedDocument(
                document_id="doc1",
                source_type="text",
                mime_type="text/plain",
                text="hello",
                pages=["hello"],
                attachments=[attachment],
                metadata={},
            )

    class _StubEmbedder:
        async def embed_texts(self, texts: list[str]) -> list[list[float]]:
            return [[0.1, 0.2] for _ in texts]

    class _StubStore:
        async def upsert_sources(self, sources):
            return None

        async def upsert_docs(self, docs):
            return None

        async def upsert_chunks(self, chunks):
            return None

    ingestor = KnowledgeIngestor(
        store=_StubStore(),
        embedding_provider=_StubEmbedder(),
        config=KnowledgeChunkConfig(mode="general"),
        parser_registry=_StubParserRegistry(),
    )

    previews = ingestor.preview(documents=[DocumentInput(source="hello")])

    assert isinstance(previews[0], ChunkPreview)
    assert previews[0].attachments[0].attachment_id == "att1"
