# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-28

"""RAG 知识 Wiki 模块."""

from src.modules.rag.api import router

MODULE_PREFIX = "/knowledge/wiki"
MODULE_TAGS = ["Knowledge Wiki"]

__all__ = ["router", "MODULE_PREFIX", "MODULE_TAGS"]
