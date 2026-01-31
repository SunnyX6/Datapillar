# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""VectorStore configuration."""

from typing import Any

from pydantic import BaseModel, Field, field_validator
from pydantic.config import ConfigDict


class VectorStoreConfig(BaseModel):
    """VectorStore configuration (shared by knowledge and experience)."""

    model_config = ConfigDict(extra="allow")

    type: str = Field(
        default="lance",
        description="Type: lance | chroma | milvus",
    )
    path: str | None = Field(
        default=None,
        description="Local storage path (lance/chroma)",
    )
    uri: str | None = Field(
        default=None,
        description="Milvus connection URI",
    )
    host: str | None = Field(
        default=None,
        description="Chroma remote host",
    )
    port: int = Field(
        default=8000,
        description="Chroma remote port",
    )
    token: str | None = Field(
        default=None,
        description="Milvus authentication token",
    )
    params: dict[str, Any] = Field(
        default_factory=dict,
        description="Backend-specific passthrough parameters",
    )
    index_params: dict[str, Any] = Field(
        default_factory=dict,
        description="Milvus dense index parameters (index_type/metric_type/params)",
    )
    sparse_index_params: dict[str, Any] = Field(
        default_factory=dict,
        description="Milvus sparse index parameters (index_type/metric_type/params)",
    )
    search_params: dict[str, Any] = Field(
        default_factory=dict,
        description="Milvus dense search parameters (metric_type/params)",
    )
    sparse_search_params: dict[str, Any] = Field(
        default_factory=dict,
        description="Milvus sparse search parameters (metric_type/params)",
    )

    @field_validator("type")
    @classmethod
    def validate_type(cls, v: str) -> str:
        supported = {"lance", "chroma", "milvus"}
        if v.lower() not in supported:
            raise ValueError(
                f"Unsupported vector_store type: '{v}'. Supported: {', '.join(sorted(supported))}"
            )
        return v.lower()
