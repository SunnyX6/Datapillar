# @author Sunny
# @date 2026-02-20

"""AlreadyExists 异常。"""

from src.shared.exception.base import DatapillarException
from src.shared.web.code import Code


class AlreadyExistsException(DatapillarException):
    """资源已存在异常。"""

    default_code = Code.CONFLICT
    default_type = "ALREADY_EXISTS"
