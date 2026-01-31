# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""
Tools module.

Public API (application use):
- tool: decorator for defining tools

Framework internal (not for application use):
- create_delegation_tool: delegation tool factory
"""

from datapillar_oneagentic.tools.delegation import (
    create_delegation_tool,
    create_delegation_tools,
)
from datapillar_oneagentic.tools.registry import tool

# Expose application-facing API only.
__all__ = [
    "tool",
]
