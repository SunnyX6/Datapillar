from __future__ import annotations

import gzip
import json
from datetime import UTC, datetime, timedelta
from pathlib import Path

import jwt
import pytest
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ed25519
from fastapi import FastAPI, Request
from fastapi.testclient import TestClient

from src.shared.auth.gateway_assertion import (
    GatewayAssertionConfig,
    GatewayAssertionError,
    GatewayAssertionVerifier,
)
from src.shared.auth.middleware import AuthMiddleware
from src.shared.config.runtime import (
    build_runtime_config,
    clear_runtime_config_cache,
    set_runtime_config,
)
from src.shared.exception import register_exception_handlers


@pytest.fixture(autouse=True)
def _clear_runtime_config_cache():
    clear_runtime_config_cache()
    yield
    clear_runtime_config_cache()


@pytest.fixture
def eddsa_key_pair(tmp_path: Path):
    private_key = ed25519.Ed25519PrivateKey.generate()
    public_key = private_key.public_key()
    public_key_path = tmp_path / "gateway-assertion-public.pem"
    public_key_path.write_bytes(
        public_key.public_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PublicFormat.SubjectPublicKeyInfo,
        )
    )
    return private_key, public_key_path


def _build_assertion_token(
    private_key: ed25519.Ed25519PrivateKey,
    *,
    method: str,
    path: str,
    kid: str = "auth-dev-2026-02",
) -> str:
    now = datetime.now(UTC)
    payload = {
        "iss": "datapillar-auth",
        "aud": "datapillar-ai",
        "sub": "1001",
        "jti": "assertion-jti-1",
        "iat": int(now.timestamp()),
        "exp": int((now + timedelta(minutes=3)).timestamp()),
        "tenantId": 2002,
        "tenantCode": "tenant-2002",
        "username": "sunny",
        "email": "sunny@qq.com",
        "roles": ["ADMIN"],
        "impersonation": False,
        "method": method,
        "path": path,
    }
    return jwt.encode(payload, private_key, algorithm="EdDSA", headers={"kid": kid})


def _build_runtime_payload(public_key_path: Path) -> dict[str, object]:
    return {
        "app_port": 7003,
        "mysql_host": "127.0.0.1",
        "mysql_port": 3306,
        "mysql_database": "datapillar",
        "mysql_username": "root",
        "mysql_password": "Sunny.123456",
        "neo4j_uri": "bolt://127.0.0.1:7687",
        "neo4j_database": "neo4j",
        "neo4j_username": "neo4j",
        "neo4j_password": "123456asd",
        "redis_host": "127.0.0.1",
        "redis_port": 6379,
        "redis_db": 0,
        "redis_password": "",
        "gravitino_db_type": "mysql",
        "gravitino_db_host": "127.0.0.1",
        "gravitino_db_port": 3306,
        "gravitino_db_database": "gravitino",
        "gravitino_db_username": "root",
        "gravitino_db_password": "Sunny.123456",
        "gravitino_sync_metalake": "datapillar",
        "jwt_secret": "jwt-secret",
        "jwt_issuer": "datapillar-auth",
        "auth_enabled": True,
        "llm": {"retry": {"max_retries": 2}},
        "agent": {"max_steps": 10},
        "sql_summary": {
            "enabled": True,
            "batch_size": 5,
            "flush_interval_seconds": 300.0,
            "max_queue_size": 1000,
            "max_sql_length": 10000,
            "min_sql_length": 50,
        },
        "knowledge_wiki": {
            "storage": {"type": "local", "local_dir": "./data/knowledge_wiki"},
            "vector_store": {"provider": "milvus", "uri": "http://127.0.0.1:19530"},
            "embedding_batch_size": 20,
            "progress_step": 20,
        },
        "security": {
            "default_tenant_id": 1,
            "gateway_assertion": {
                "enabled": True,
                "header_name": "X-Gateway-Assertion",
                "issuer": "datapillar-auth",
                "audience": "datapillar-ai",
                "key_id": "auth-dev-2026-02",
                "public_key_path": str(public_key_path),
                "previous_key_id": "",
                "previous_public_key_path": "",
                "max_clock_skew_seconds": 5,
            },
            "key_storage": {
                "type": "local",
                "local_path": "/data/datapillar/privkeys",
                "s3": {
                    "endpoint_url": "",
                    "access_key": "",
                    "secret_key": "",
                    "bucket": "",
                    "region": "",
                    "prefix": "privkeys",
                },
            },
        },
    }


def _create_app() -> FastAPI:
    app = FastAPI()
    register_exception_handlers(app)
    app.add_middleware(AuthMiddleware)

    @app.get("/secure")
    async def secure(request: Request):
        return {
            "userId": request.state.current_user.user_id,
            "tenantId": request.state.current_user.tenant_id,
            "username": request.state.current_user.username,
        }

    @app.post("/secure-echo")
    async def secure_echo(request: Request):
        return {
            "tenantId": request.state.current_user.tenant_id,
            "payload": await request.json(),
        }

    return app


def test_gateway_assertion_verifier_success(eddsa_key_pair):
    private_key, public_key_path = eddsa_key_pair
    verifier = GatewayAssertionVerifier(
        GatewayAssertionConfig(
            enabled=True,
            header_name="X-Gateway-Assertion",
            issuer="datapillar-auth",
            audience="datapillar-ai",
            key_id="auth-dev-2026-02",
            public_key_path=str(public_key_path),
            previous_key_id=None,
            previous_public_key_path=None,
            max_clock_skew_seconds=5,
        )
    )
    token = _build_assertion_token(private_key, method="GET", path="/secure")

    context = verifier.verify(token, "GET", "/secure")

    assert context.user_id == 1001
    assert context.tenant_id == 2002
    assert context.tenant_code == "tenant-2002"
    assert context.username == "sunny"
    assert context.roles == ["ADMIN"]


def test_gateway_assertion_verifier_rejects_path_mismatch(eddsa_key_pair):
    private_key, public_key_path = eddsa_key_pair
    verifier = GatewayAssertionVerifier(
        GatewayAssertionConfig(
            enabled=True,
            header_name="X-Gateway-Assertion",
            issuer="datapillar-auth",
            audience="datapillar-ai",
            key_id="auth-dev-2026-02",
            public_key_path=str(public_key_path),
            previous_key_id=None,
            previous_public_key_path=None,
            max_clock_skew_seconds=5,
        )
    )
    token = _build_assertion_token(private_key, method="GET", path="/secure")

    with pytest.raises(GatewayAssertionError):
        verifier.verify(token, "GET", "/wrong")


def test_auth_middleware_rejects_missing_assertion(eddsa_key_pair):
    _, public_key_path = eddsa_key_pair
    set_runtime_config(build_runtime_config(_build_runtime_payload(public_key_path)))
    client = TestClient(_create_app(), raise_server_exceptions=False)

    response = client.get("/secure")

    assert response.status_code == 401
    assert response.json()["message"] == "缺少网关断言"


def test_auth_middleware_accepts_valid_assertion(eddsa_key_pair):
    private_key, public_key_path = eddsa_key_pair
    set_runtime_config(build_runtime_config(_build_runtime_payload(public_key_path)))
    client = TestClient(_create_app(), raise_server_exceptions=False)
    token = _build_assertion_token(private_key, method="GET", path="/secure")

    response = client.get("/secure", headers={"X-Gateway-Assertion": token})

    assert response.status_code == 200
    assert response.json()["userId"] == 1001
    assert response.json()["tenantId"] == 2002
    assert response.json()["username"] == "sunny"


def test_auth_middleware_accepts_valid_assertion_with_gzip_body(eddsa_key_pair):
    private_key, public_key_path = eddsa_key_pair
    set_runtime_config(build_runtime_config(_build_runtime_payload(public_key_path)))
    client = TestClient(_create_app(), raise_server_exceptions=False)
    token = _build_assertion_token(private_key, method="POST", path="/secure-echo")
    payload = {"message": "hello"}
    compressed = gzip.compress(json.dumps(payload).encode())

    response = client.post(
        "/secure-echo",
        headers={
            "X-Gateway-Assertion": token,
            "Content-Encoding": "gzip",
            "Content-Type": "application/json",
        },
        content=compressed,
    )

    assert response.status_code == 200
    assert response.json()["tenantId"] == 2002
    assert response.json()["payload"] == payload
