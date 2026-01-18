"""
解析器基础接口
"""

from __future__ import annotations

from abc import ABC, abstractmethod

from datapillar_oneagentic.knowledge.models import DocumentInput, ParsedDocument


class DocumentParser(ABC):
    """文档解析器接口"""

    supported_mime_types: set[str] = set()
    supported_extensions: set[str] = set()
    name: str = ""

    @abstractmethod
    def parse(self, doc_input: DocumentInput) -> ParsedDocument:
        """解析文档输入"""
