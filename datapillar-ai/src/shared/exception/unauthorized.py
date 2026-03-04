# @author Sunny
# @date 2026-02-20

"""Unauthorized Abnormal."""

from src.shared.exception.base import DatapillarException
from src.shared.web.code import Code


class UnauthorizedException(DatapillarException):
    """Unauthenticated exception."""

    default_code = Code.UNAUTHORIZED
    default_type = "UNAUTHORIZED"
