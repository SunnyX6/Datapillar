# @author Sunny
# @date 2026-02-20

"""AI 服务异常体系。"""

from src.shared.exception.already_exists import AlreadyExistsException
from src.shared.exception.bad_request import BadRequestException
from src.shared.exception.base import DatapillarException
from src.shared.exception.conflict import ConflictException
from src.shared.exception.connection_failed import ConnectionFailedException
from src.shared.exception.forbidden import ForbiddenException
from src.shared.exception.handler import register_exception_handlers
from src.shared.exception.internal import InternalException
from src.shared.exception.mapper import ExceptionDetail, ExceptionMapper
from src.shared.exception.not_found import NotFoundException
from src.shared.exception.service_unavailable import ServiceUnavailableException
from src.shared.exception.too_many_requests import TooManyRequestsException
from src.shared.exception.unauthorized import UnauthorizedException
from src.shared.exception.unsupported_operation import UnsupportedOperationException

__all__ = [
    "DatapillarException",
    "BadRequestException",
    "UnauthorizedException",
    "ForbiddenException",
    "NotFoundException",
    "AlreadyExistsException",
    "ConflictException",
    "UnsupportedOperationException",
    "ConnectionFailedException",
    "TooManyRequestsException",
    "ServiceUnavailableException",
    "InternalException",
    "ExceptionMapper",
    "ExceptionDetail",
    "register_exception_handlers",
]
