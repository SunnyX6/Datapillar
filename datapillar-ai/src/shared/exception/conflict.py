# @author Sunny
# @date 2026-02-20

"""Conflict Abnormal."""

from src.shared.exception.base import DatapillarException
from src.shared.web.code import Code


class ConflictException(DatapillarException):
    """Business conflict exception."""

    default_code = Code.CONFLICT
    default_type = "CONFLICT"
