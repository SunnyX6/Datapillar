# @author Sunny
# @date 2026-02-20

"""TooManyRequests Abnormal."""

from src.shared.exception.base import DatapillarException
from src.shared.web.code import Code


class TooManyRequestsException(DatapillarException):
    """Request frequency is too high and abnormal."""

    default_code = Code.TOO_MANY_REQUESTS
    default_type = "TOO_MANY_REQUESTS"
