# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""Parser registry."""

from __future__ import annotations

import logging

from datapillar_oneagentic.knowledge.models import DocumentInput, ParsedDocument
from datapillar_oneagentic.knowledge.parser.base import DocumentParser
from datapillar_oneagentic.knowledge.parser.utils import extract_extension, guess_mime_type

logger = logging.getLogger(__name__)


class ParserRegistry:
    """Parser registry."""

    def __init__(self) -> None:
        self._by_mime: dict[str, type[DocumentParser]] = {}
        self._by_ext: dict[str, type[DocumentParser]] = {}

    def register(self, parser_cls: type[DocumentParser]) -> None:
        for mime in parser_cls.supported_mime_types:
            self._by_mime[mime] = parser_cls
        for ext in parser_cls.supported_extensions:
            self._by_ext[ext.lower()] = parser_cls

    def parse(self, doc_input: DocumentInput) -> ParsedDocument:
        parser = self.resolve(doc_input)
        parsed = parser.parse(doc_input)
        if parser.name:
            parsed.metadata.setdefault("parser", parser.name)
        return parsed

    def resolve(self, doc_input: DocumentInput) -> DocumentParser:
        mime_type = guess_mime_type(doc_input)
        if mime_type in self._by_mime:
            return self._by_mime[mime_type]()
        ext = extract_extension(doc_input)
        if ext and ext in self._by_ext:
            return self._by_ext[ext]()
        if "text/plain" in self._by_mime:
            return self._by_mime["text/plain"]()
        raise ValueError(f"No parser available: mime_type={mime_type}, ext={ext}")


def default_registry() -> ParserRegistry:
    registry = ParserRegistry()
    from datapillar_oneagentic.knowledge.parser.csv import CsvParser
    from datapillar_oneagentic.knowledge.parser.docx import DocxParser
    from datapillar_oneagentic.knowledge.parser.html import HtmlParser
    from datapillar_oneagentic.knowledge.parser.markdown import MarkdownParser
    from datapillar_oneagentic.knowledge.parser.pdf import PdfParser
    from datapillar_oneagentic.knowledge.parser.text import TextParser
    from datapillar_oneagentic.knowledge.parser.xlsx import XlsxParser

    registry.register(TextParser)
    registry.register(MarkdownParser)
    registry.register(HtmlParser)
    registry.register(PdfParser)
    registry.register(DocxParser)
    registry.register(CsvParser)
    registry.register(XlsxParser)
    return registry
