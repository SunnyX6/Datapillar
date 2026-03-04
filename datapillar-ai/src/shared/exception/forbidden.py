# @author Sunny
# @date 2026-02-20

"""Forbidden Abnormal."""

from src.shared.exception.base import DatapillarException
from src.shared.web.code import Code


class ForbiddenException(DatapillarException):
    """No permission exception."""

    default_code = Code.FORBIDDEN
    default_type = "FORBIDDEN"
