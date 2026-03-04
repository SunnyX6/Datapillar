# @author Sunny
# @date 2026-02-19

"""
LLM Management module.DEPRECATED:- This module is reserved for historical link compatibility
- ETL The new link no longer relies on this module
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
