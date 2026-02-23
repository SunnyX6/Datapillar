# @author Sunny
# @date 2026-02-20

"""AI 服务异常基类。"""

from __future__ import annotations

from typing import Any

from src.shared.web.code import Code

_DEFAULT_INTERNAL_MESSAGE = "服务器内部错误"


class DatapillarException(Exception):
    """统一业务异常基类。"""

    default_code: int = Code.INTERNAL_ERROR
    default_type: str = "INTERNAL_ERROR"
    default_retryable: bool = False

    def __init__(
        self,
        message: str,
        *,
        cause: Exception | None = None,
        code: int | None = None,
        error_type: str | None = None,
        context: dict[str, str] | None = None,
        retryable: bool | None = None,
        metadata: dict[str, Any] | None = None,
    ) -> None:
        resolved = _resolve_message(message, cause)
        self.message = resolved
        self.cause = cause
        self.code = int(code if code is not None else self.default_code)
        self.error_type = (error_type or self.default_type).strip() or self.default_type
        self.context = dict(context or {})
        self.retryable = bool(self.default_retryable if retryable is None else retryable)
        self.metadata = metadata or {}
        super().__init__(resolved)


def _resolve_message(message: str, cause: Exception | None) -> str:
    normalized = message.strip() if isinstance(message, str) else ""
    if normalized:
        return normalized
    if cause is not None:
        cause_message = str(cause).strip()
        if cause_message:
            return cause_message
    return _DEFAULT_INTERNAL_MESSAGE
