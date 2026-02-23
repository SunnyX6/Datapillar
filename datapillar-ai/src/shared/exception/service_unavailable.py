# @author Sunny
# @date 2026-02-20

"""ServiceUnavailable 异常。"""

from src.shared.exception.base import DatapillarException
from src.shared.web.code import Code


class ServiceUnavailableException(DatapillarException):
    """服务不可用异常。"""

    default_code = Code.SERVICE_UNAVAILABLE
    default_type = "SERVICE_UNAVAILABLE"
    default_retryable = True
