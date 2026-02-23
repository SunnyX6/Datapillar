# @author Sunny
# @date 2026-02-20

"""Unauthorized 异常。"""

from src.shared.exception.base import DatapillarException
from src.shared.web.code import Code


class UnauthorizedException(DatapillarException):
    """未认证异常。"""

    default_code = Code.UNAUTHORIZED
    default_type = "UNAUTHORIZED"
