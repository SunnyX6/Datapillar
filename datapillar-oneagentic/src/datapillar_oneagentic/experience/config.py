# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""Experience learning configuration."""

from pydantic import BaseModel, Field

from datapillar_oneagentic.storage.config import VectorStoreConfig


class LearningConfig(BaseModel):
    """Experience learning configuration."""

    vector_store: VectorStoreConfig = Field(
        default_factory=VectorStoreConfig,
        description="Vector store configuration for experience learning",
    )

    verify_threshold: float = Field(
        default=0.7,
        ge=0.0,
        le=1.0,
        description="Mark as verified when user satisfaction exceeds this value",
    )

    reject_threshold: float = Field(
        default=0.3,
        ge=0.0,
        le=1.0,
        description="Mark as rejected when user satisfaction is below this value",
    )

    retrieval_k: int = Field(
        default=5,
        ge=1,
        description="Default number of retrieved experiences",
    )
