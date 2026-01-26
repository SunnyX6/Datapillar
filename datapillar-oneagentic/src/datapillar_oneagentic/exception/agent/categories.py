"""
Agent error categories.
"""

from __future__ import annotations

from enum import Enum


class AgentErrorCategory(str, Enum):
    """Agent error categories."""

    BUSINESS = "business"  # Business failure (input/task issue)
    SYSTEM = "system"  # System failure (runtime error)
    PROTOCOL = "protocol"  # Protocol/contract error (type/return/config)
    DEPENDENCY = "dependency"  # Dependency failure (external tools/network)
