# @author Sunny
# @date 2026-01-27

"""
authenticated user model
"""

from pydantic import BaseModel


class CurrentUser(BaseModel):
    """Current user information"""

    user_id: int
    tenant_id: int
    tenant_code: str
    username: str
    issuer: str
    subject: str
    email: str | None = None
    roles: tuple[str, ...] = ()

    class Config:
        frozen = True
