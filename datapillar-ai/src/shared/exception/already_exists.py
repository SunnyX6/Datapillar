# @author Sunny
# @date 2026-02-20

"""AlreadyExists Abnormal."""

from src.shared.exception.base import DatapillarException
from src.shared.web.code import Code


class AlreadyExistsException(DatapillarException):
    """There is an exception in the resource."""

    default_code = Code.CONFLICT
    default_type = "ALREADY_EXISTS"
