# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
认证用户模型
"""

from pydantic import BaseModel


class CurrentUser(BaseModel):
    """当前用户信息"""

    user_id: int
    tenant_id: int
    username: str
    email: str | None = None

    class Config:
        frozen = True
