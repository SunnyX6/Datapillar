# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""Plain text parser."""

from __future__ import annotations

from datapillar_oneagentic.knowledge.models import DocumentInput, ParsedDocument
from datapillar_oneagentic.knowledge.parser.base import DocumentParser
from datapillar_oneagentic.knowledge.parser.utils import build_document_id, guess_mime_type, load_text, normalize_metadata


class TextParser(DocumentParser):
    supported_mime_types = {"text/plain"}
    supported_extensions = {".txt"}
    name = "text"

    def parse(self, doc_input: DocumentInput) -> ParsedDocument:
        text = load_text(doc_input)
        mime_type = guess_mime_type(doc_input)
        return ParsedDocument(
            document_id=build_document_id(),
            source_type="text",
            mime_type=mime_type,
            text=text,
            pages=[text] if text else [],
            metadata=normalize_metadata(doc_input.metadata),
        )
