# @author Sunny
# @date 2026-02-19

"""LLM 管理模块。"""

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
