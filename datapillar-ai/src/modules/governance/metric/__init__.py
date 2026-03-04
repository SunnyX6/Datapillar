# @author Sunny
# @date 2026-01-27

"""
indicator AI Governance module

routing: /api/ai/biz/governance/metric
"""

from src.modules.governance.metric.api import router

MODULE_SCOPE = "biz"

__all__ = ["router", "MODULE_SCOPE"]
