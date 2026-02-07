from __future__ import annotations

from starlette.requests import Request

from src.shared.web import ApiResponse


def _make_request(path: str = "/api/ai/test", trace_id: str = "trace-123") -> Request:
    scope = {
        "type": "http",
        "method": "GET",
        "path": path,
        "headers": [(b"x-trace-id", trace_id.encode("utf-8"))],
        "query_string": b"",
        "scheme": "http",
        "server": ("testserver", 80),
        "client": ("testclient", 1234),
    }
    return Request(scope)


def test_api_response_success_payload() -> None:
    request = _make_request()
    payload = ApiResponse.success(
        request=request,
        data={"ok": True},
        message="Success",
        limit=10,
        offset=0,
        total=1,
    )

    assert payload["status"] == 200
    assert payload["code"] == "OK"
    assert payload["message"] == "Success"
    assert payload["data"] == {"ok": True}
    assert payload["path"] == "/api/ai/test"
    assert payload["traceId"] == "trace-123"
    assert payload["limit"] == 10
    assert payload["offset"] == 0
    assert payload["total"] == 1
    assert isinstance(payload["timestamp"], str)
    assert "T" in payload["timestamp"]


def test_api_response_error_payload() -> None:
    request = _make_request(path="/api/ai/error", trace_id="trace-999")
    payload = ApiResponse.error(
        request=request,
        status=400,
        code="INVALID_ARGUMENT",
        message="bad request",
    )

    assert payload["status"] == 400
    assert payload["code"] == "INVALID_ARGUMENT"
    assert payload["message"] == "bad request"
    assert payload["data"] is None
    assert payload["path"] == "/api/ai/error"
    assert payload["traceId"] == "trace-999"
    assert isinstance(payload["timestamp"], str)
    assert "T" in payload["timestamp"]
