# @author Sunny
# @date 2026-02-20

"""NotFound 异常。"""

from src.shared.exception.base import DatapillarException
from src.shared.web.code import Code


class NotFoundException(DatapillarException):
    """资源不存在异常。"""

    default_code = Code.NOT_FOUND
    default_type = "NOT_FOUND"
