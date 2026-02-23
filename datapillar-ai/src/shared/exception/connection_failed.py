# @author Sunny
# @date 2026-02-20

"""ConnectionFailed 异常。"""

from src.shared.exception.base import DatapillarException
from src.shared.web.code import Code


class ConnectionFailedException(DatapillarException):
    """连接失败异常。"""

    default_code = Code.BAD_GATEWAY
    default_type = "BAD_GATEWAY"
    default_retryable = True
