# @author Sunny
# @date 2026-02-20

"""TooManyRequests 异常。"""

from src.shared.exception.base import DatapillarException
from src.shared.web.code import Code


class TooManyRequestsException(DatapillarException):
    """请求频率过高异常。"""

    default_code = Code.TOO_MANY_REQUESTS
    default_type = "TOO_MANY_REQUESTS"
