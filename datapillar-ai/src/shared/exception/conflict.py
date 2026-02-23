# @author Sunny
# @date 2026-02-20

"""Conflict 异常。"""

from src.shared.exception.base import DatapillarException
from src.shared.web.code import Code


class ConflictException(DatapillarException):
    """业务冲突异常。"""

    default_code = Code.CONFLICT
    default_type = "CONFLICT"
