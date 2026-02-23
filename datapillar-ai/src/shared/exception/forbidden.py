# @author Sunny
# @date 2026-02-20

"""Forbidden 异常。"""

from src.shared.exception.base import DatapillarException
from src.shared.web.code import Code


class ForbiddenException(DatapillarException):
    """无权限异常。"""

    default_code = Code.FORBIDDEN
    default_type = "FORBIDDEN"
