# @author Sunny
# @date 2026-02-20

"""NotFound Abnormal."""

from src.shared.exception.base import DatapillarException
from src.shared.web.code import Code


class NotFoundException(DatapillarException):
    """There is no exception in the resource."""

    default_code = Code.NOT_FOUND
    default_type = "NOT_FOUND"
