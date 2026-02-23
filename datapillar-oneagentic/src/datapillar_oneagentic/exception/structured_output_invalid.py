# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-02-20
"""Structured output invalid exception."""

from __future__ import annotations

from typing import Any

from datapillar_oneagentic.exception.base import DatapillarException


class StructuredOutputInvalidException(DatapillarException):
    """Raised when structured output parsing/validation fails."""

    def __init__(
        self,
        message: str,
        *,
        raw: Any | None = None,
        parsing_error: Any | None = None,
        **kwargs,
    ) -> None:
        metadata = dict(kwargs.pop("metadata", {}) or {})
        metadata.setdefault("raw", raw)
        metadata.setdefault("parsing_error", parsing_error)
        super().__init__(message, metadata=metadata, **kwargs)
        self.raw = raw
        self.parsing_error = parsing_error
