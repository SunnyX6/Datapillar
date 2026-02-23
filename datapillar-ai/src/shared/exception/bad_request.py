# @author Sunny
# @date 2026-02-20

"""BadRequest 异常。"""

from src.shared.exception.base import DatapillarException
from src.shared.web.code import Code


class BadRequestException(DatapillarException):
    """参数错误异常。"""

    default_code = Code.BAD_REQUEST
    default_type = "BAD_REQUEST"
