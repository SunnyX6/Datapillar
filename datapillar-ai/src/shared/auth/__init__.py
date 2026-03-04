# @author Sunny
# @date 2026-01-27

"""
Authentication module
"""

from src.shared.auth.dependencies import current_user_state, require_admin_role
from src.shared.auth.user import CurrentUser

__all__ = ["current_user_state", "require_admin_role", "CurrentUser"]
