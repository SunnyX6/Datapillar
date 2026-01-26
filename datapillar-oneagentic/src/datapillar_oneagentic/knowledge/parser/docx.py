"""DOCX parser."""

from __future__ import annotations

import io

from datapillar_oneagentic.knowledge.models import DocumentInput, ParsedDocument
from datapillar_oneagentic.knowledge.parser.base import DocumentParser
from datapillar_oneagentic.knowledge.parser.utils import build_document_id, guess_mime_type, load_bytes, normalize_metadata


class DocxParser(DocumentParser):
    supported_mime_types = {
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    }
    supported_extensions = {".docx"}
    name = "docx"

    def parse(self, doc_input: DocumentInput) -> ParsedDocument:
        data = load_bytes(doc_input)
        mime_type = guess_mime_type(doc_input)
        content = _docx_to_text(data)
        return ParsedDocument(
            document_id=build_document_id(),
            source_type="file",
            mime_type=mime_type,
            text=content,
            pages=[content] if content else [],
            metadata=normalize_metadata(doc_input.metadata),
        )


def _docx_to_text(data: bytes) -> str:
    try:
        from docx import Document
        from docx.table import Table
        from docx.text.paragraph import Paragraph
    except ImportError as err:
        raise ImportError(
            "DOCX parsing requires dependencies:\n"
            "  pip install datapillar-oneagentic[knowledge]"
        ) from err

    doc = Document(io.BytesIO(data))
    lines = []
    for block in doc.iter_inner_content():
        if isinstance(block, Paragraph):
            if block.text.strip():
                lines.append(block.text.strip())
        elif isinstance(block, Table):
            for row in block.rows:
                cells = [cell.text.strip() for cell in row.cells]
                if any(cells):
                    lines.append("\t".join(cells))
    return "\n".join(lines)
