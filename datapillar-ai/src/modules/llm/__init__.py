# @author Sunny
# @date 2026-02-19

"""
LLM 管理模块。

DEPRECATED:
- 该模块保留给历史链路兼容
- ETL 新链路不再依赖本模块
"""

from src.modules.llm.api import router

MODULE_SCOPE = "admin"
MODULE_PREFIX = "/llms"
MODULE_TAGS = ["LLM Admin"]
MODULE_SCAN_SUBMODULES = False

__all__ = [
    "router",
    "MODULE_SCOPE",
    "MODULE_PREFIX",
    "MODULE_TAGS",
    "MODULE_SCAN_SUBMODULES",
]
