"""
Markdown 解析器
"""

from __future__ import annotations

from datapillar_oneagentic.knowledge.models import DocumentInput, ParsedDocument
from datapillar_oneagentic.knowledge.parser.base import DocumentParser
from datapillar_oneagentic.knowledge.parser.utils import build_document_id, guess_mime_type, load_text, normalize_metadata


class MarkdownParser(DocumentParser):
    supported_mime_types = {"text/markdown"}
    supported_extensions = {".md", ".markdown"}
    name = "markdown"

    def parse(self, doc_input: DocumentInput) -> ParsedDocument:
        text = load_text(doc_input)
        mime_type = guess_mime_type(doc_input)
        content = _markdown_to_text(text)
        return ParsedDocument(
            document_id=build_document_id(),
            source_type="text",
            mime_type=mime_type,
            text=content,
            pages=[content] if content else [],
            metadata=normalize_metadata(doc_input.metadata),
        )


def _markdown_to_text(text: str) -> str:
    try:
        import markdown
    except ImportError as err:
        raise ImportError(
            "解析 Markdown 需要安装依赖：\n"
            "  pip install datapillar-oneagentic[knowledge]"
        ) from err

    try:
        from bs4 import BeautifulSoup
    except ImportError as err:
        raise ImportError(
            "解析 Markdown 需要安装依赖：\n"
            "  pip install datapillar-oneagentic[knowledge]"
        ) from err

    html = markdown.markdown(text)
    soup = BeautifulSoup(html, "lxml")
    return soup.get_text("\n")
