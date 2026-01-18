"""
VectorStore 配置
"""

from pydantic import BaseModel, Field, field_validator


class VectorStoreConfig(BaseModel):
    """VectorStore 配置（知识与经验共用）"""

    type: str = Field(
        default="lance",
        description="类型: lance | chroma | milvus",
    )
    path: str | None = Field(
        default=None,
        description="本地存储路径（lance/chroma）",
    )
    uri: str | None = Field(
        default=None,
        description="Milvus 连接 URI",
    )
    host: str | None = Field(
        default=None,
        description="Chroma 远程服务器地址",
    )
    port: int = Field(
        default=8000,
        description="Chroma 远程服务器端口",
    )
    token: str | None = Field(
        default=None,
        description="Milvus 认证令牌",
    )

    @field_validator("type")
    @classmethod
    def validate_type(cls, v: str) -> str:
        supported = {"lance", "chroma", "milvus"}
        if v.lower() not in supported:
            raise ValueError(f"不支持的 vector_store 类型: '{v}'。支持: {', '.join(sorted(supported))}")
        return v.lower()
