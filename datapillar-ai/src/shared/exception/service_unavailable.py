# @author Sunny
# @date 2026-02-20

"""ServiceUnavailable Abnormal."""

from src.shared.exception.base import DatapillarException
from src.shared.web.code import Code


class ServiceUnavailableException(DatapillarException):
    """Service unavailable exception."""

    default_code = Code.SERVICE_UNAVAILABLE
    default_type = "SERVICE_UNAVAILABLE"
    default_retryable = True
