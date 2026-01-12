"""
经验学习配置
"""

from pydantic import BaseModel, Field


class LearningConfig(BaseModel):
    """经验学习配置"""

    verify_threshold: float = Field(
        default=0.7,
        ge=0.0,
        le=1.0,
        description="用户满意度超过此值自动标记为 verified",
    )

    reject_threshold: float = Field(
        default=0.3,
        ge=0.0,
        le=1.0,
        description="用户满意度低于此值自动标记为 rejected",
    )

    retrieval_k: int = Field(
        default=5,
        ge=1,
        description="经验检索默认返回数量",
    )
