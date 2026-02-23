from __future__ import annotations

from fastapi import HTTPException
from fastapi.exceptions import RequestValidationError

from src.shared.exception import BadRequestException, ExceptionMapper
from src.shared.web.code import Code


def test_exception_mapper_maps_bad_request_exception() -> None:
    detail = ExceptionMapper.resolve(BadRequestException("参数错误"))

    assert detail.http_status == 400
    assert detail.error_code == Code.BAD_REQUEST
    assert detail.error_type == "BAD_REQUEST"
    assert detail.message == "参数错误"
    assert detail.server_error is False


def test_exception_mapper_maps_request_validation_error_to_bad_request() -> None:
    exc = RequestValidationError(
        [
            {
                "type": "int_parsing",
                "loc": ("path", "item_id"),
                "msg": "Input should be a valid integer",
                "input": "abc",
            }
        ]
    )

    detail = ExceptionMapper.resolve(exc)

    assert detail.http_status == 400
    assert detail.error_code == Code.BAD_REQUEST
    assert detail.error_type == "BAD_REQUEST"


def test_exception_mapper_maps_http_conflict() -> None:
    detail = ExceptionMapper.resolve(HTTPException(status_code=409, detail="冲突"))

    assert detail.http_status == 409
    assert detail.error_code == Code.CONFLICT
    assert detail.error_type == "CONFLICT"
    assert detail.message == "冲突"


def test_exception_mapper_maps_duplicate_message_to_already_exists() -> None:
    detail = ExceptionMapper.resolve(RuntimeError("Duplicate entry abc for key uk_name"))

    assert detail.http_status == 409
    assert detail.error_code == Code.CONFLICT
    assert detail.error_type == "ALREADY_EXISTS"


def test_exception_mapper_maps_unknown_exception_to_internal() -> None:
    detail = ExceptionMapper.resolve(RuntimeError("boom"))

    assert detail.http_status == 500
    assert detail.error_code == Code.INTERNAL_ERROR
    assert detail.error_type == "INTERNAL_ERROR"
    assert detail.server_error is True
