# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""HTML parser."""

from __future__ import annotations

from datapillar_oneagentic.knowledge.models import DocumentInput, ParsedDocument
from datapillar_oneagentic.knowledge.parser.base import DocumentParser
from datapillar_oneagentic.knowledge.parser.utils import build_document_id, guess_mime_type, load_text, normalize_metadata


class HtmlParser(DocumentParser):
    supported_mime_types = {"text/html"}
    supported_extensions = {".html", ".htm"}
    name = "html"

    def parse(self, doc_input: DocumentInput) -> ParsedDocument:
        html = load_text(doc_input)
        mime_type = guess_mime_type(doc_input)
        content = _html_to_text(html)
        return ParsedDocument(
            document_id=build_document_id(),
            source_type="text",
            mime_type=mime_type,
            text=content,
            pages=[content] if content else [],
            metadata=normalize_metadata(doc_input.metadata),
        )


def _html_to_text(html: str) -> str:
    try:
        from readability import Document as ReadabilityDocument
    except ImportError as err:
        raise ImportError(
            "HTML parsing requires dependencies:\n"
            "  pip install datapillar-oneagentic[knowledge]"
        ) from err

    try:
        from bs4 import BeautifulSoup
    except ImportError as err:
        raise ImportError(
            "HTML parsing requires dependencies:\n"
            "  pip install datapillar-oneagentic[knowledge]"
        ) from err

    try:
        doc = ReadabilityDocument(html)
        content = doc.summary()
    except Exception:
        content = html

    soup = BeautifulSoup(content, "lxml")
    return soup.get_text("\n")
