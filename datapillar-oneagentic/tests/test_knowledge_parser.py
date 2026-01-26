from __future__ import annotations

from datapillar_oneagentic.knowledge.models import Attachment, DocumentInput
from datapillar_oneagentic.knowledge.parser import default_registry
from datapillar_oneagentic.knowledge.parser.csv import CsvParser
from datapillar_oneagentic.knowledge.parser.pdf import PdfParser


def test_registry_sets() -> None:
    registry = default_registry()
    doc = DocumentInput(source="hello", mime_type="text/plain")

    parsed = registry.parse(doc)

    assert parsed.metadata.get("parser") == "text"
    assert parsed.text == "hello"


def test_registry_fallbacks() -> None:
    registry = default_registry()
    doc = DocumentInput(source="fallback", mime_type="application/unknown")

    parsed = registry.parse(doc)

    assert parsed.metadata.get("parser") == "text"
    assert parsed.text == "fallback"


def test_registry_resolves() -> None:
    registry = default_registry()
    doc = DocumentInput(source="plain", filename="doc.txt")

    parsed = registry.parse(doc)

    assert parsed.metadata.get("parser") == "text"
    assert parsed.text == "plain"


def test_csv_parser() -> None:
    parser = CsvParser()
    doc = DocumentInput(source="a,b\nc,d", filename="demo.csv")

    parsed = parser.parse(doc)

    assert parsed.text == "a\tb\nc\td"


def test_docx_parser(monkeypatch) -> None:
    from datapillar_oneagentic.knowledge.parser import docx as docx_module

    monkeypatch.setattr(docx_module, "_docx_to_text", lambda _: "docx-content")
    registry = default_registry()
    doc = DocumentInput(
        source=b"docx",
        mime_type="application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    )

    parsed = registry.parse(doc)

    assert parsed.metadata.get("parser") == "docx"
    assert parsed.text == "docx-content"


def test_markdown_parser(monkeypatch) -> None:
    from datapillar_oneagentic.knowledge.parser import markdown as markdown_module

    monkeypatch.setattr(markdown_module, "_markdown_to_text", lambda _: "md-content")
    registry = default_registry()
    doc = DocumentInput(source="**hi**", mime_type="text/markdown")

    parsed = registry.parse(doc)

    assert parsed.metadata.get("parser") == "markdown"
    assert parsed.text == "md-content"


def test_html_parser(monkeypatch) -> None:
    from datapillar_oneagentic.knowledge.parser import html as html_module

    monkeypatch.setattr(html_module, "_html_to_text", lambda _: "html-content")
    registry = default_registry()
    doc = DocumentInput(source="<p>hi</p>", mime_type="text/html")

    parsed = registry.parse(doc)

    assert parsed.metadata.get("parser") == "html"
    assert parsed.text == "html-content"


def test_xlsx_parser(monkeypatch) -> None:
    from datapillar_oneagentic.knowledge.parser import xlsx as xlsx_module

    monkeypatch.setattr(xlsx_module, "_xlsx_to_text", lambda _: "xlsx-content")
    registry = default_registry()
    doc = DocumentInput(
        source=b"xlsx",
        mime_type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    )

    parsed = registry.parse(doc)

    assert parsed.metadata.get("parser") == "xlsx"
    assert parsed.text == "xlsx-content"


def test_pdf_parser(monkeypatch) -> None:
    attachment = Attachment(
        attachment_id="att1",
        name="att1.png",
        mime_type="image/png",
        content=b"img",
    )

    monkeypatch.setattr(
        "datapillar_oneagentic.knowledge.parser.pdf._extract_pdf",
        lambda _: ("pdf-content", ["p1"], [attachment]),
    )
    registry = default_registry()
    doc = DocumentInput(source=b"%PDF", mime_type="application/pdf")

    parsed = registry.parse(doc)

    assert parsed.metadata.get("parser") == "pdf"
    assert parsed.text == "pdf-content"
    assert parsed.attachments[0].attachment_id == "att1"


def test_pdf_parser2(monkeypatch) -> None:
    parser = PdfParser()
    monkeypatch.setattr(
        "datapillar_oneagentic.knowledge.parser.pdf._extract_pdf",
        lambda _: ("text", ["p1", "p2"], []),
    )

    parsed = parser.parse(DocumentInput(source=b"%PDF", mime_type="application/pdf"))

    assert parsed.text == "text"
    assert len(parsed.pages) == 2
