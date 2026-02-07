# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-02-05

"""LLM 模型管理请求模型。"""

from __future__ import annotations

from decimal import Decimal
from enum import Enum

from pydantic import BaseModel, ConfigDict, Field


class ModelType(str, Enum):
    CHAT = "chat"
    EMBEDDINGS = "embeddings"
    RERANKING = "reranking"
    CODE = "code"


class ModelCreateRequest(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    model_id: str = Field(..., min_length=1, max_length=128, description="模型ID")
    name: str = Field(..., min_length=1, max_length=128, description="展示名称")
    provider_code: str = Field(..., min_length=1, max_length=32, description="供应商编码")
    model_type: ModelType = Field(..., description="模型类型")
    description: str | None = Field(default=None, max_length=512, description="描述")
    tags: list[str] | None = Field(default=None, description="标签")
    context_tokens: int | None = Field(default=None, ge=1, description="上下文长度")
    input_price_usd: Decimal | None = Field(default=None, ge=0, description="输入价格")
    output_price_usd: Decimal | None = Field(default=None, ge=0, description="输出价格")
    embedding_dimension: int | None = Field(default=None, ge=1, description="Embedding 维度")
    base_url: str | None = Field(default=None, max_length=255, description="Base URL")


class ModelUpdateRequest(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    name: str | None = Field(default=None, min_length=1, max_length=128, description="展示名称")
    description: str | None = Field(default=None, max_length=512, description="描述")
    tags: list[str] | None = Field(default=None, description="标签")
    context_tokens: int | None = Field(default=None, ge=1, description="上下文长度")
    input_price_usd: Decimal | None = Field(default=None, ge=0, description="输入价格")
    output_price_usd: Decimal | None = Field(default=None, ge=0, description="输出价格")
    embedding_dimension: int | None = Field(default=None, ge=1, description="Embedding 维度")
    base_url: str | None = Field(default=None, max_length=255, description="Base URL")


class ModelConnectRequest(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    api_key: str = Field(..., min_length=1, description="API Key")
    base_url: str | None = Field(default=None, max_length=255, description="Base URL")
