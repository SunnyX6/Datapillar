# @author Sunny
# @date 2026-02-20

"""UnsupportedOperation Abnormal."""

from src.shared.exception.base import DatapillarException
from src.shared.web.code import Code


class UnsupportedOperationException(DatapillarException):
    """Operation exceptions are not supported."""

    default_code = Code.METHOD_NOT_ALLOWED
    default_type = "METHOD_NOT_ALLOWED"
