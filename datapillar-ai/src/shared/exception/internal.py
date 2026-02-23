# @author Sunny
# @date 2026-02-20

"""Internal 异常。"""

from src.shared.exception.base import DatapillarException
from src.shared.web.code import Code


class InternalException(DatapillarException):
    """内部错误异常。"""

    default_code = Code.INTERNAL_ERROR
    default_type = "INTERNAL_ERROR"
