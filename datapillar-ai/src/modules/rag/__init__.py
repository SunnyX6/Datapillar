# @author Sunny
# @date 2026-01-28

"""RAG knowledge Wiki module."""

from src.modules.rag.api import router

MODULE_SCOPE = "biz"
MODULE_PREFIX = "/knowledge/wiki"
MODULE_TAGS = ["Knowledge Wiki"]

__all__ = ["router", "MODULE_SCOPE", "MODULE_PREFIX", "MODULE_TAGS"]
