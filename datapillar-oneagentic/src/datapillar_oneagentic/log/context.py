from __future__ import annotations

import logging
from contextvars import ContextVar
from typing import Any, Iterable

_CONTEXT_FIELDS: tuple[str, ...] = (
    "namespace",
    "session_id",
    "agent_id",
    "request_id",
    "trace_id",
    "span_id",
)

_context_var: ContextVar[dict[str, Any] | None] = ContextVar(
    "datapillar_log_context",
    default=None,
)


def _select_context(fields: dict[str, Any]) -> dict[str, Any]:
    return {key: fields.get(key) for key in _CONTEXT_FIELDS if key in fields}


def get_log_context() -> dict[str, Any]:
    current = _context_var.get()
    if not current:
        return {}
    return dict(current)


def set_log_context(**fields: Any) -> None:
    _context_var.set(_select_context(fields))


def clear_log_context() -> None:
    _context_var.set({})


class _ContextBinder:
    def __init__(self, token) -> None:
        self._token = token

    def __enter__(self) -> _ContextBinder:
        return self

    def __exit__(self, exc_type, exc, tb) -> None:
        _context_var.reset(self._token)


def bind_log_context(**fields: Any) -> _ContextBinder:
    current = get_log_context()
    merged = {**current, **_select_context(fields)}
    token = _context_var.set(merged)
    return _ContextBinder(token)


def _ensure_record_fields(record: logging.LogRecord, fields: Iterable[str]) -> None:
    for field in fields:
        if not hasattr(record, field):
            setattr(record, field, None)


class ContextFilter(logging.Filter):
    def filter(self, record: logging.LogRecord) -> bool:
        ctx = get_log_context()
        for key in _CONTEXT_FIELDS:
            if not hasattr(record, key):
                setattr(record, key, ctx.get(key))

        if not hasattr(record, "event"):
            record.event = "log"

        if not hasattr(record, "data"):
            record.data = None
        elif record.data is not None and not isinstance(record.data, dict):
            record.data = {"value": record.data}

        _ensure_record_fields(
            record,
            (
                "duration_ms",
                "error_type",
                "error",
            ),
        )

        if record.exc_info:
            if record.error is None:
                record.error = logging.Formatter().formatException(record.exc_info)
            if record.error_type is None:
                record.error_type = record.exc_info[0].__name__

        return True
