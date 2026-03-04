# @author Sunny
# @date 2026-02-06

"""
Runtime configuration contract（Nacos unique source）

Responsibilities：
- Unified definition of business runtime configuration contract
- startup phase Nacos Configure to do fail-fast Verify
- Provide centralized configuration reading entry to business modules
"""

from __future__ import annotations

from copy import deepcopy
from typing import Any, Self

from dynaconf import Dynaconf
from pydantic import BaseModel, Field, ValidationError, field_validator, model_validator

from src.shared.config.exceptions import ConfigurationError


class SQLSummaryRuntimeConfig(BaseModel):
    enabled: bool
    batch_size: int = Field(ge=1)
    flush_interval_seconds: float = Field(gt=0)
    max_queue_size: int = Field(ge=1)
    max_sql_length: int = Field(ge=1)
    min_sql_length: int = Field(ge=1)


class KnowledgeWikiStorageRuntimeConfig(BaseModel):
    type: str
    local_dir: str | None = None
    s3: dict[str, Any] | None = None

    @field_validator("type")
    @classmethod
    def _validate_storage_type(cls, value: str) -> str:
        normalized = value.strip().lower()
        if normalized not in {"local", "s3"}:
            raise ValueError("knowledge_wiki.storage.type Only supports local or s3")
        return normalized

    @model_validator(mode="after")
    def _validate_storage_payload(self) -> Self:
        if self.type == "local":
            if not self.local_dir or not self.local_dir.strip():
                raise ValueError("knowledge_wiki.storage.local_dir cannot be empty")
            return self

        if not isinstance(self.s3, dict):
            raise ValueError("knowledge_wiki.storage.s3 Must be an object")
        bucket = self.s3.get("bucket")
        if not isinstance(bucket, str) or not bucket.strip():
            raise ValueError("knowledge_wiki.storage.s3.bucket cannot be empty")
        return self


class KnowledgeWikiRuntimeConfig(BaseModel):
    storage: KnowledgeWikiStorageRuntimeConfig
    vector_store: dict[str, Any]
    embedding_batch_size: int = Field(ge=1)
    progress_step: int = Field(ge=1)

    @field_validator("vector_store")
    @classmethod
    def _validate_vector_store(cls, value: dict[str, Any]) -> dict[str, Any]:
        if not value:
            raise ValueError("knowledge_wiki.vector_store cannot be empty")
        return value


class KeyStorageRuntimeConfig(BaseModel):
    type: str
    local_path: str | None = None
    s3: dict[str, Any] | None = None

    @field_validator("type")
    @classmethod
    def _validate_key_storage_type(cls, value: str) -> str:
        normalized = value.strip().lower()
        if normalized == "object":
            normalized = "s3"
        if normalized not in {"local", "s3"}:
            raise ValueError("security.key_storage.type Only supports local or s3")
        return normalized

    @model_validator(mode="after")
    def _validate_key_storage_payload(self) -> Self:
        if self.type == "local":
            if not self.local_path or not self.local_path.strip():
                raise ValueError("security.key_storage.local_path cannot be empty")
            return self

        if self.type == "s3":
            if not isinstance(self.s3, dict):
                raise ValueError("security.key_storage.s3 Must be an object")
            bucket = self.s3.get("bucket")
            if not isinstance(bucket, str) or not bucket.strip():
                raise ValueError("security.key_storage.s3.bucket cannot be empty")
            return self

        return self


class TrustedIdentityRuntimeConfig(BaseModel):
    enabled: bool


class SecurityRuntimeConfig(BaseModel):
    default_tenant_id: int = Field(ge=1)
    key_storage: KeyStorageRuntimeConfig
    trusted_identity: TrustedIdentityRuntimeConfig


class RuntimeConfigContract(BaseModel):
    app_port: int = Field(ge=1, le=65535)
    mysql_host: str
    mysql_port: int = Field(ge=1, le=65535)
    mysql_database: str
    mysql_username: str
    mysql_password: str
    neo4j_uri: str
    neo4j_database: str
    neo4j_username: str
    neo4j_password: str
    redis_host: str
    redis_port: int = Field(ge=1, le=65535)
    redis_db: int = Field(ge=0)
    redis_password: str | None
    gravitino_db_type: str
    gravitino_db_host: str
    gravitino_db_port: int = Field(ge=1, le=65535)
    gravitino_db_database: str
    gravitino_db_username: str
    gravitino_db_password: str
    gravitino_sync_metalake: str
    llm: dict[str, Any]
    agent: dict[str, Any]
    sql_summary: SQLSummaryRuntimeConfig
    knowledge_wiki: KnowledgeWikiRuntimeConfig
    security: SecurityRuntimeConfig

    @field_validator(
        "mysql_host",
        "mysql_database",
        "mysql_username",
        "mysql_password",
        "neo4j_uri",
        "neo4j_database",
        "neo4j_username",
        "neo4j_password",
        "redis_host",
        "gravitino_db_type",
        "gravitino_db_host",
        "gravitino_db_database",
        "gravitino_db_username",
        "gravitino_db_password",
        "gravitino_sync_metalake",
    )
    @classmethod
    def _validate_non_empty_string(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("Cannot be an empty string")
        return value

    @field_validator("llm", "agent")
    @classmethod
    def _validate_non_empty_mapping(cls, value: dict[str, Any]) -> dict[str, Any]:
        if not value:
            raise ValueError("Cannot be a null object")
        return value


_runtime_config_cache: RuntimeConfigContract | None = None


def settings_to_dict(settings: Dynaconf) -> dict[str, Any]:
    to_dict = getattr(settings, "to_dict", None)
    if not callable(to_dict):
        raise ConfigurationError("Dynaconf settings Not supported to_dict")
    data = to_dict()
    if not isinstance(data, dict):
        raise ConfigurationError("Dynaconf settings The conversion result is not an object")
    return data


def build_runtime_config(payload: dict[str, Any]) -> RuntimeConfigContract:
    try:
        return RuntimeConfigContract.model_validate(payload)
    except ValidationError as exc:
        details: list[str] = []
        for err in exc.errors():
            loc = ".".join(str(item) for item in err.get("loc", ()))
            msg = err.get("msg", "Invalid configuration")
            details.append(f"{loc}: {msg}")
        raise ConfigurationError(
            f"Nacos Configuration verification failed: {'; '.join(details)}"
        ) from exc


def load_runtime_config(settings: Dynaconf) -> RuntimeConfigContract:
    config = build_runtime_config(settings_to_dict(settings))
    set_runtime_config(config)
    return config


def set_runtime_config(config: RuntimeConfigContract) -> None:
    global _runtime_config_cache
    _runtime_config_cache = config


def clear_runtime_config_cache() -> None:
    global _runtime_config_cache
    _runtime_config_cache = None


def get_runtime_config() -> RuntimeConfigContract:
    if _runtime_config_cache is None:
        raise ConfigurationError(
            "Runtime configuration not initialized，Please complete first Nacos guide"
        )
    return _runtime_config_cache


def get_sql_summary_config() -> dict[str, Any]:
    return get_runtime_config().sql_summary.model_dump()


def get_knowledge_wiki_config() -> dict[str, Any]:
    return get_runtime_config().knowledge_wiki.model_dump()


def get_llm_config() -> dict[str, Any]:
    return deepcopy(get_runtime_config().llm)


def get_agent_config() -> dict[str, Any]:
    return deepcopy(get_runtime_config().agent)


def get_key_storage_config() -> dict[str, Any]:
    return get_runtime_config().security.key_storage.model_dump()


def get_default_tenant_id() -> int:
    return int(get_runtime_config().security.default_tenant_id)
