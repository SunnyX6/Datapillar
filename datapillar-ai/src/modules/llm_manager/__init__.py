# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-02-05

"""模型管理模块（LLM Manager）。"""

from src.modules.llm_manager.api import router

MODULE_PREFIX = "/llm_manager"
MODULE_TAGS = ["LLM Manager"]

__all__ = ["router", "MODULE_PREFIX", "MODULE_TAGS"]
