# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""PDF parser."""

from __future__ import annotations

import io
import logging
import uuid

from datapillar_oneagentic.knowledge.models import Attachment, DocumentInput, ParsedDocument
from datapillar_oneagentic.knowledge.parser.base import DocumentParser
from datapillar_oneagentic.knowledge.parser.utils import build_document_id, guess_mime_type, load_bytes, normalize_metadata

logger = logging.getLogger(__name__)


class PdfParser(DocumentParser):
    supported_mime_types = {"application/pdf"}
    supported_extensions = {".pdf"}
    name = "pdf"

    def parse(self, doc_input: DocumentInput) -> ParsedDocument:
        data = load_bytes(doc_input)
        mime_type = guess_mime_type(doc_input)
        text, pages, attachments = _extract_pdf(data)
        return ParsedDocument(
            document_id=build_document_id(),
            source_type="file",
            mime_type=mime_type,
            text=text,
            pages=pages,
            attachments=attachments,
            metadata=normalize_metadata(doc_input.metadata),
        )


def _extract_pdf(data: bytes) -> tuple[str, list[str], list[Attachment]]:
    try:
        import pypdfium2 as pdfium
        import pypdfium2.raw as pdfium_c
    except ImportError as err:
        raise ImportError(
            "PDF parsing requires dependencies:\n"
            "  pip install datapillar-oneagentic[knowledge]"
        ) from err

    attachments: list[Attachment] = []
    pages: list[str] = []
    pdf = pdfium.PdfDocument(data)
    try:
        for page_number, page in enumerate(pdf):
            text_page = page.get_textpage()
            content = text_page.get_text_range() or ""
            text_page.close()

            image_text, image_attachments = _extract_images(page, pdfium_c)
            if image_text:
                content = f"{content}\n{image_text}" if content else image_text
            attachments.extend(image_attachments)
            pages.append(content)
            page.close()
    finally:
        pdf.close()

    return "\n\n".join(pages), pages, attachments


def _extract_images(page, pdfium_c) -> tuple[str, list[Attachment]]:
    image_refs = []
    attachments: list[Attachment] = []
    try:
        image_objects = page.get_objects(filter=(pdfium_c.FPDF_PAGEOBJ_IMAGE,))
        for obj in image_objects:
            buffer = io.BytesIO()
            try:
                obj.extract(buffer, fb_format="png")
                img_bytes = buffer.getvalue()
            except Exception as exc:
                logger.warning("PDF image extraction failed: %s", exc)
                continue
            if not img_bytes:
                continue
            attachment_id = uuid.uuid4().hex
            attachments.append(
                Attachment(
                    attachment_id=attachment_id,
                    name=f"{attachment_id}.png",
                    mime_type="image/png",
                    content=img_bytes,
                )
            )
            image_refs.append(f"![attachment]({attachment_id})")
    except Exception as exc:
        logger.warning("PDF image load failed: %s", exc)

    return "\n".join(image_refs), attachments
