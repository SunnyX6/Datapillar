# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""Parser base interface."""

from __future__ import annotations

from abc import ABC, abstractmethod

from datapillar_oneagentic.knowledge.models import DocumentInput, ParsedDocument


class DocumentParser(ABC):
    """Document parser interface."""

    supported_mime_types: set[str] = set()
    supported_extensions: set[str] = set()
    name: str = ""

    @abstractmethod
    def parse(self, doc_input: DocumentInput) -> ParsedDocument:
        """Parse a document input."""
