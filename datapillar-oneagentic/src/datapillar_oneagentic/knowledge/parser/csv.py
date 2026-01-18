"""
CSV 解析器
"""

from __future__ import annotations

import csv
import io

from datapillar_oneagentic.knowledge.models import DocumentInput, ParsedDocument
from datapillar_oneagentic.knowledge.parser.base import DocumentParser
from datapillar_oneagentic.knowledge.parser.utils import build_document_id, guess_mime_type, load_text, normalize_metadata


class CsvParser(DocumentParser):
    supported_mime_types = {"text/csv"}
    supported_extensions = {".csv"}
    name = "csv"

    def parse(self, doc_input: DocumentInput) -> ParsedDocument:
        text = load_text(doc_input)
        mime_type = guess_mime_type(doc_input)
        content = _csv_to_text(text)
        return ParsedDocument(
            document_id=build_document_id(),
            source_type="file",
            mime_type=mime_type,
            text=content,
            pages=[content] if content else [],
            metadata=normalize_metadata(doc_input.metadata),
        )


def _csv_to_text(text: str) -> str:
    reader = csv.reader(io.StringIO(text))
    lines = []
    for row in reader:
        lines.append("\t".join([cell.strip() for cell in row if cell is not None]))
    return "\n".join(lines)
