# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-28

"""RAG 知识 Wiki 接口模型."""

from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field


class NamespaceCreateRequest(BaseModel):
    namespace: str = Field(..., min_length=1, max_length=128)
    description: str | None = Field(default=None, max_length=512)


class NamespaceUpdateRequest(BaseModel):
    description: str | None = Field(default=None, max_length=512)
    status: int | None = Field(default=None, ge=0, le=1)


class DocumentUpdateRequest(BaseModel):
    title: str | None = Field(default=None, max_length=255)


class ChunkJobRequest(BaseModel):
    chunk_mode: str | None = Field(default=None)
    chunk_config_json: dict[str, Any] | None = Field(default=None)
    reembed: bool = Field(default=True)


class ChunkEditRequest(BaseModel):
    content: str = Field(..., min_length=1)


class RetrieveRequest(BaseModel):
    namespace_id: int = Field(..., gt=0)
    query: str = Field(..., min_length=1)
    search_scope: str = Field(default="all")
    document_ids: list[str] | list[int] | None = Field(default=None)
    retrieval_mode: str = Field(default="hybrid")
    rerank_enabled: bool = Field(default=False)
    rerank_model: str | None = Field(default=None)
    top_k: int = Field(default=5, ge=1)
    score_threshold: float | None = Field(default=None, ge=0, le=1)
