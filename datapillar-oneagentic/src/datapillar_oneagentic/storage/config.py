"""VectorStore configuration."""

from pydantic import BaseModel, Field, field_validator


class VectorStoreConfig(BaseModel):
    """VectorStore configuration (shared by knowledge and experience)."""

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

    @field_validator("type")
    @classmethod
    def validate_type(cls, v: str) -> str:
        supported = {"lance", "chroma", "milvus"}
        if v.lower() not in supported:
            raise ValueError(
                f"Unsupported vector_store type: '{v}'. Supported: {', '.join(sorted(supported))}"
            )
        return v.lower()
