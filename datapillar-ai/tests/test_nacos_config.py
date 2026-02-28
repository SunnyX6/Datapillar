from __future__ import annotations

import importlib

import pytest
from dynaconf import Dynaconf

from src.shared.config.exceptions import ConfigurationError
from src.shared.config.nacos_client import (
    NacosBootstrapConfig,
    _is_usable_service_ip,
    _build_client_config,
    apply_nacos_config,
    load_nacos_config,
    parse_nacos_config_content,
    resolve_service_ip,
)
from src.shared.config.runtime import clear_runtime_config_cache


class StubNacosClient:
    def __init__(self, content: str | bytes | None):
        self._content = content

    async def get_config(self, _param):
        return self._content


@pytest.fixture(autouse=True)
def _clear_runtime_config_cache():
    clear_runtime_config_cache()
    yield
    clear_runtime_config_cache()


def _valid_runtime_config() -> dict[str, object]:
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
        "jwt_issuer": "datapillar",
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
            "vector_store": {
                "provider": "milvus",
                "uri": "http://127.0.0.1:19530",
            },
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
                "public_key_path": "classpath:shared/auth/security/gateway-assertion-dev-public.pem",
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


def test_parse_nacos_config_content_ok():
    content = """
app_name: "Datapillar AI"
llm:
  retry:
    max_retries: 2
"""
    data = parse_nacos_config_content(content, "datapillar-ai.yaml", "DATAPILLAR")
    assert data["app_name"] == "Datapillar AI"
    assert data["llm"]["retry"]["max_retries"] == 2


def test_parse_nacos_config_content_rejects_list():
    content = """
- a
- b
"""
    with pytest.raises(ConfigurationError):
        parse_nacos_config_content(content, "datapillar-ai.yaml", "DATAPILLAR")


@pytest.mark.asyncio
async def test_load_nacos_config_empty_raises():
    client = StubNacosClient("")
    with pytest.raises(ConfigurationError):
        await load_nacos_config(client, "datapillar-ai.yaml", "DATAPILLAR")


def test_apply_nacos_config_updates_settings():
    settings = Dynaconf(loaders=[])
    config = _valid_runtime_config()
    apply_nacos_config(settings, config)

    assert settings.mysql_host == "127.0.0.1"
    assert settings.sql_summary["batch_size"] == 5


def test_apply_nacos_config_missing_required_key_raises():
    settings = Dynaconf(loaders=[])
    config = _valid_runtime_config()
    del config["mysql_host"]

    with pytest.raises(ConfigurationError):
        apply_nacos_config(settings, config)


def test_settings_module_disables_env_override(monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setenv("DATAPILLAR_MYSQL_HOST", "env-host")

    settings_module = importlib.import_module("src.shared.config.settings")
    reloaded = importlib.reload(settings_module)
    assert reloaded.settings.get("mysql_host") is None


def test_build_client_config_uses_env_log_cache_dir(monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setenv("NACOS_LOG_DIR", "/tmp/custom-nacos-log")
    monkeypatch.setenv("NACOS_CACHE_DIR", "/tmp/custom-nacos-cache")

    config = NacosBootstrapConfig(
        server_addr="127.0.0.1:8848",
        namespace="dev",
        username="nacos",
        password="nacos",
        group="DATAPILLAR",
        data_id="datapillar-ai.yaml",
        service_name="datapillar-ai",
        cluster_name="DEFAULT",
        ephemeral=True,
        heartbeat_interval=5,
        watch_enabled=True,
    )

    client_config = _build_client_config(config)
    assert client_config.log_dir == "/tmp/custom-nacos-log"
    assert client_config.cache_dir == "/tmp/custom-nacos-cache"


def test_build_client_config_uses_safe_default_dirs(monkeypatch: pytest.MonkeyPatch):
    monkeypatch.delenv("NACOS_LOG_DIR", raising=False)
    monkeypatch.delenv("NACOS_CACHE_DIR", raising=False)

    config = NacosBootstrapConfig(
        server_addr="127.0.0.1:8848",
        namespace="dev",
        username="nacos",
        password="nacos",
        group="DATAPILLAR",
        data_id="datapillar-ai.yaml",
        service_name="datapillar-ai",
        cluster_name="DEFAULT",
        ephemeral=True,
        heartbeat_interval=5,
        watch_enabled=True,
    )

    client_config = _build_client_config(config)
    assert client_config.log_dir == "/tmp/datapillar-logs/nacos"
    assert client_config.cache_dir == "/tmp/datapillar-logs/nacos/cache"


@pytest.mark.parametrize(
    ("ip", "expected"),
    [
        ("192.168.0.100", True),
        ("10.0.0.3", True),
        ("127.0.0.1", False),
        ("0.0.0.0", False),
        ("169.254.1.1", False),
        ("198.18.0.1", False),
        ("198.19.255.254", False),
        ("", False),
        ("not-an-ip", False),
    ],
)
def test_is_usable_service_ip(ip: str, expected: bool):
    assert _is_usable_service_ip(ip) is expected


def test_resolve_service_ip_fails_fast_on_invalid_nacos_service_ip(
    monkeypatch: pytest.MonkeyPatch,
):
    monkeypatch.setenv("NACOS_SERVICE_IP", "198.18.0.1")
    monkeypatch.setenv("POD_IP", "192.168.0.100")
    monkeypatch.setenv("HOST_IP", "192.168.0.101")

    with pytest.raises(ConfigurationError, match="服务注册 IP 非法"):
        resolve_service_ip()


def test_resolve_service_ip_requires_explicit_env(monkeypatch: pytest.MonkeyPatch):
    monkeypatch.delenv("NACOS_SERVICE_IP", raising=False)
    monkeypatch.delenv("POD_IP", raising=False)
    monkeypatch.delenv("HOST_IP", raising=False)

    with pytest.raises(ConfigurationError, match="缺少服务注册 IP"):
        resolve_service_ip()
