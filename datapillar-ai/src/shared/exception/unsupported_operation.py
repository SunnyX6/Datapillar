# @author Sunny
# @date 2026-02-20

"""UnsupportedOperation 异常。"""

from src.shared.exception.base import DatapillarException
from src.shared.web.code import Code


class UnsupportedOperationException(DatapillarException):
    """不支持操作异常。"""

    default_code = Code.METHOD_NOT_ALLOWED
    default_type = "METHOD_NOT_ALLOWED"
