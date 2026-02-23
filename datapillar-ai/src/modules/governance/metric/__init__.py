# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
指标 AI 治理模块

路由: /api/ai/biz/governance/metric
"""

from src.modules.governance.metric.api import router

MODULE_SCOPE = "biz"

__all__ = ["router", "MODULE_SCOPE"]
