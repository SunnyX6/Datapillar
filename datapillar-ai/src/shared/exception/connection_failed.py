# @author Sunny
# @date 2026-02-20

"""ConnectionFailed Abnormal."""

from src.shared.exception.base import DatapillarException
from src.shared.web.code import Code


class ConnectionFailedException(DatapillarException):
    """Connection failure exception."""

    default_code = Code.BAD_GATEWAY
    default_type = "BAD_GATEWAY"
    default_retryable = True
