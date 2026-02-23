from __future__ import annotations

from fastapi import FastAPI, HTTPException
from fastapi.testclient import TestClient

from src.shared.exception import BadRequestException, register_exception_handlers
from src.shared.web.code import Code


def _build_app() -> FastAPI:
    app = FastAPI()
    register_exception_handlers(app)

    @app.get("/bad")
    async def bad() -> dict[str, str]:
        raise BadRequestException("参数错误")

    @app.get("/http")
    async def http_error() -> dict[str, str]:
        raise HTTPException(status_code=403, detail="禁止访问")

    @app.get("/boom")
    async def boom() -> dict[str, str]:
        raise RuntimeError("boom")

    @app.get("/items/{item_id}")
    async def read_item(item_id: int) -> dict[str, int]:
        return {"item_id": item_id}

    return app


def test_handler_maps_custom_exception() -> None:
    client = TestClient(_build_app(), raise_server_exceptions=False)

    response = client.get("/bad")

    assert response.status_code == 400
    assert response.json() == {
        "code": Code.BAD_REQUEST,
        "type": "BAD_REQUEST",
        "message": "参数错误",
        "retryable": False,
    }


def test_handler_maps_http_exception() -> None:
    client = TestClient(_build_app(), raise_server_exceptions=False)

    response = client.get("/http")

    assert response.status_code == 403
    assert response.json() == {
        "code": Code.FORBIDDEN,
        "type": "FORBIDDEN",
        "message": "禁止访问",
        "retryable": False,
    }


def test_handler_maps_runtime_exception() -> None:
    client = TestClient(_build_app(), raise_server_exceptions=False)

    response = client.get("/boom")

    assert response.status_code == 500
    assert response.json() == {
        "code": Code.INTERNAL_ERROR,
        "type": "INTERNAL_ERROR",
        "message": "boom",
        "retryable": False,
    }


def test_handler_maps_validation_exception_to_bad_request() -> None:
    client = TestClient(_build_app(), raise_server_exceptions=False)

    response = client.get("/items/abc")

    assert response.status_code == 400
    assert response.json() == {
        "code": Code.BAD_REQUEST,
        "type": "BAD_REQUEST",
        "message": "请求参数校验失败",
        "retryable": False,
    }
