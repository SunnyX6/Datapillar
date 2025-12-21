"""
认证用户模型
"""

from pydantic import BaseModel


class CurrentUser(BaseModel):
    """当前用户信息"""

    user_id: int
    username: str
    email: str | None = None

    class Config:
        frozen = True
