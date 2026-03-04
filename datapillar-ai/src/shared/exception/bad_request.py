# @author Sunny
# @date 2026-02-20

"""BadRequest Abnormal."""

from src.shared.exception.base import DatapillarException
from src.shared.web.code import Code


class BadRequestException(DatapillarException):
    """Parameter error exception."""

    default_code = Code.BAD_REQUEST
    default_type = "BAD_REQUEST"
