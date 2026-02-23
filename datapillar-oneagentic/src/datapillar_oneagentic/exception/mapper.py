# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-02-20
"""
Exception mapper.

Unifies vendor/runtime exceptions into framework exceptions.
"""

from __future__ import annotations

import asyncio
import json
from typing import Any

from datapillar_oneagentic.exception.agent_execution_failed import AgentExecutionFailedException
from datapillar_oneagentic.exception.agent_result_invalid import AgentResultInvalidException
from datapillar_oneagentic.exception.bad_request import BadRequestException
from datapillar_oneagentic.exception.base import DatapillarException
from datapillar_oneagentic.exception.connection_failed import ConnectionFailedException
from datapillar_oneagentic.exception.context_length_exceeded import (
    ContextLengthExceededException,
)
from datapillar_oneagentic.exception.internal import InternalException
from datapillar_oneagentic.exception.not_found import NotFoundException
from datapillar_oneagentic.exception.service_unavailable import (
    ServiceUnavailableException,
)
from datapillar_oneagentic.exception.timeout import TimeoutException
from datapillar_oneagentic.exception.too_many_requests import TooManyRequestsException
from datapillar_oneagentic.exception.unauthorized import UnauthorizedException

_CONTEXT_LENGTH_CODES = {
    "context_length_exceeded",
    "context_window_exceeded",
    "max_tokens_exceeded",
    "prompt_too_long",
}

_MODEL_NOT_FOUND_CODES = {
    "model_not_found",
    "deployment_not_found",
}

_UNAUTHORIZED_CLASS_NAMES = {
    "AuthenticationError",
    "PermissionDeniedError",
    "UnauthorizedError",
}

_NOT_FOUND_CLASS_NAMES = {
    "NotFoundError",
    "ModelNotFoundError",
}

_TOO_MANY_REQUESTS_CLASS_NAMES = {
    "RateLimitError",
    "TooManyRequestsError",
}

_BAD_REQUEST_CLASS_NAMES = {
    "BadRequestError",
    "InvalidRequestError",
    "UnprocessableEntityError",
}

_TIMEOUT_CLASS_NAMES = {
    "APITimeoutError",
    "ReadTimeout",
    "TimeoutException",
}

_CONNECTION_CLASS_NAMES = {
    "APIConnectionError",
    "ConnectError",
}

_SERVICE_UNAVAILABLE_CLASS_NAMES = {
    "InternalServerError",
    "ServiceUnavailableError",
}


def _to_int(value: Any) -> int | None:
    if isinstance(value, bool):
        return None
    if isinstance(value, int):
        return value
    if isinstance(value, str) and value.isdigit():
        return int(value)
    return None


def _extract_status_code(error: Exception) -> int | None:
    status = _to_int(getattr(error, "status_code", None))
    if status is not None:
        return status
    status = _to_int(getattr(error, "status", None))
    if status is not None:
        return status
    response = getattr(error, "response", None)
    if response is None:
        return None
    return _to_int(getattr(response, "status_code", None))


def _normalize_payload(payload: Any) -> dict[str, Any] | None:
    if isinstance(payload, dict):
        return payload
    if isinstance(payload, str):
        try:
            parsed = json.loads(payload)
        except json.JSONDecodeError:
            return None
        return parsed if isinstance(parsed, dict) else None
    return None


def _extract_vendor_payload(error: Exception) -> dict[str, Any] | None:
    body = getattr(error, "body", None)
    payload = _normalize_payload(body)
    if payload is not None:
        return payload

    response = getattr(error, "response", None)
    if response is None:
        return None
    json_method = getattr(response, "json", None)
    if not callable(json_method):
        return None
    try:
        payload = json_method()
    except Exception:
        return None
    return payload if isinstance(payload, dict) else None


def _extract_vendor_code(error: Exception) -> str | None:
    code = getattr(error, "code", None)
    if isinstance(code, str) and code:
        return code

    payload = _extract_vendor_payload(error)
    if payload is None:
        return None

    error_obj = payload.get("error")
    if isinstance(error_obj, dict):
        for key in ("code", "type"):
            value = error_obj.get(key)
            if isinstance(value, str) and value:
                return value

    for key in ("code", "type"):
        value = payload.get(key)
        if isinstance(value, str) and value:
            return value
    return None


def _extract_message(error: Exception) -> str:
    message = str(error)
    if message:
        return message
    return error.__class__.__name__


class ExceptionMapper:
    """Map external exceptions to framework exceptions."""

    @classmethod
    def map_llm_error(
        cls,
        error: Exception,
        *,
        provider: str | None = None,
        model: str | None = None,
    ) -> DatapillarException:
        if isinstance(error, DatapillarException):
            if provider and error.provider is None:
                error.provider = provider
            if model and error.model is None:
                error.model = model
            return error

        status_code = _extract_status_code(error)
        vendor_code = _extract_vendor_code(error)
        message = _extract_message(error)
        class_name = error.__class__.__name__

        context = {
            "cause": error,
            "provider": provider,
            "model": model,
            "status_code": status_code,
            "vendor_code": vendor_code,
        }

        if class_name in _TIMEOUT_CLASS_NAMES or isinstance(error, (asyncio.TimeoutError, TimeoutError)):
            return TimeoutException(message, **context)

        if class_name in _CONNECTION_CLASS_NAMES or isinstance(error, (ConnectionError, OSError)):
            return ConnectionFailedException(message, **context)

        if class_name in _UNAUTHORIZED_CLASS_NAMES:
            return UnauthorizedException(message, **context)

        if class_name in _NOT_FOUND_CLASS_NAMES:
            return NotFoundException(message, **context)

        if class_name in _TOO_MANY_REQUESTS_CLASS_NAMES:
            return TooManyRequestsException(message, **context)

        if class_name in _BAD_REQUEST_CLASS_NAMES:
            if vendor_code in _CONTEXT_LENGTH_CODES:
                return ContextLengthExceededException(message, **context)
            if vendor_code in _MODEL_NOT_FOUND_CODES:
                return NotFoundException(message, **context)
            return BadRequestException(message, **context)

        if class_name in _SERVICE_UNAVAILABLE_CLASS_NAMES:
            return ServiceUnavailableException(message, **context)

        if status_code in {401, 403}:
            return UnauthorizedException(message, **context)
        if status_code == 404:
            return NotFoundException(message, **context)
        if status_code == 429:
            return TooManyRequestsException(message, **context)
        if status_code in {408, 504}:
            return TimeoutException(message, **context)
        if status_code == 400:
            if vendor_code in _CONTEXT_LENGTH_CODES:
                return ContextLengthExceededException(message, **context)
            if vendor_code in _MODEL_NOT_FOUND_CODES:
                return NotFoundException(message, **context)
            return BadRequestException(message, **context)
        if status_code is not None and 500 <= status_code < 600:
            return ServiceUnavailableException(message, **context)

        return InternalException(message, **context)

    @classmethod
    def map_agent_error(
        cls,
        error: Exception,
        *,
        agent_id: str | None = None,
    ) -> DatapillarException:
        if isinstance(error, DatapillarException):
            if agent_id is not None and error.agent_id is None:
                error.agent_id = agent_id
            return error

        if isinstance(error, (asyncio.TimeoutError, TimeoutError)):
            return TimeoutException(
                "Agent execution timeout",
                cause=error,
                agent_id=agent_id,
            )

        if isinstance(error, (TypeError, ValueError)):
            return AgentResultInvalidException(
                _extract_message(error),
                cause=error,
                agent_id=agent_id,
            )

        return AgentExecutionFailedException(
            _extract_message(error),
            cause=error,
            agent_id=agent_id,
        )
