from __future__ import annotations

import logging

from datapillar_oneagentic.log.context import ContextFilter

_LOGGER_NAME = "datapillar_oneagentic"
_HANDLER_FLAG = "_datapillar_log_handler"

_LOG_FORMAT = (
    "%(asctime)s.%(msecs)03d %(levelname)-5s %(name)s - "
    "[event=%(event)s namespace=%(namespace)s session_id=%(session_id)s "
    "agent_id=%(agent_id)s request_id=%(request_id)s trace_id=%(trace_id)s "
    "span_id=%(span_id)s] %(message)s"
)
_DATE_FORMAT = "%Y-%m-%d %H:%M:%S"


def _parse_level(level: str | int) -> int:
    if isinstance(level, int):
        return level
    return getattr(logging, str(level).upper(), logging.INFO)


def _build_formatter() -> logging.Formatter:
    return logging.Formatter(fmt=_LOG_FORMAT, datefmt=_DATE_FORMAT)


def _ensure_handler(logger: logging.Logger, level: int) -> None:
    for handler in logger.handlers:
        if getattr(handler, _HANDLER_FLAG, False):
            handler.setLevel(level)
            return

    handler = logging.StreamHandler()
    handler.setLevel(level)
    handler.setFormatter(_build_formatter())
    handler.addFilter(ContextFilter())
    setattr(handler, _HANDLER_FLAG, True)
    logger.addHandler(handler)


def setup_logging(level: str | int = "INFO") -> logging.Logger:
    logger = logging.getLogger(_LOGGER_NAME)
    parsed_level = _parse_level(level)
    logger.setLevel(parsed_level)
    logger.propagate = False
    _ensure_handler(logger, parsed_level)
    return logger
