from __future__ import annotations

from src.shared.web import ApiResponse, Code


def test_api_response_success_payload() -> None:
    payload = ApiResponse.success(
        data={"ok": True},
        limit=10,
        offset=0,
        total=1,
    )

    assert payload["code"] == 0
    assert payload["data"] == {"ok": True}
    assert payload["limit"] == 10
    assert payload["offset"] == 0
    assert payload["total"] == 1


def test_api_response_success_without_data_omits_data_field() -> None:
    payload = ApiResponse.success()

    assert payload == {"code": 0}


def test_api_response_error_payload() -> None:
    payload = ApiResponse.error(
        code=Code.BAD_REQUEST,
        error_type="BAD_REQUEST",
        message="bad request",
        context={"field": "name"},
        trace_id="trace-1",
        retryable=False,
    )

    assert payload["code"] == Code.BAD_REQUEST
    assert payload["type"] == "BAD_REQUEST"
    assert payload["message"] == "bad request"
    assert payload["context"] == {"field": "name"}
    assert payload["traceId"] == "trace-1"
    assert payload["retryable"] is False
