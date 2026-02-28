from __future__ import annotations

from src.api.router import api_router
from src.app import app


def test_ai_router_uses_biz_or_admin_scope_prefix() -> None:
    ai_paths = [
        route.path
        for route in api_router.routes
        if route.path.startswith("/biz/") or route.path.startswith("/admin/")
    ]

    assert ai_paths
    assert all(path.startswith("/biz") or path.startswith("/admin") for path in ai_paths)
    assert "/admin/llms/chat" in ai_paths
    assert "/biz/llm/playground/chat" not in ai_paths
    assert "/biz/etl/chat" in ai_paths
    assert "/biz/etl/sse" in ai_paths
    assert "/biz/etl/abort" in ai_paths
    assert "/biz/etl/workflows/chat" not in ai_paths
    assert "/biz/etl/workflows/sse" not in ai_paths
    assert "/biz/etl/workflows/abort" not in ai_paths


def test_app_registers_scalar_and_scoped_openapi_routes() -> None:
    route_paths = {route.path for route in app.routes}

    assert "/api/ai/openapi.json" in route_paths
    assert "/api/ai/openapi/biz.json" not in route_paths
    assert "/api/ai/openapi/admin.json" not in route_paths
    assert "/api/ai/scalar" not in route_paths
    assert "/api/ai/admin/llms/chat" in route_paths
    assert "/api/ai/biz/llm/playground/chat" not in route_paths
    assert "/api/ai/biz/etl/chat" in route_paths
    assert "/api/ai/biz/etl/sse" in route_paths
    assert "/api/ai/biz/etl/abort" in route_paths
    assert "/api/ai/biz/etl/workflows/chat" not in route_paths
    assert "/api/ai/biz/etl/workflows/sse" not in route_paths
    assert "/api/ai/biz/etl/workflows/abort" not in route_paths
