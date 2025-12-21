"""
共享组件层

提供跨模块共享的工具、配置、认证等组件
"""

from src.shared.config import settings, model_manager

__all__ = [
    "settings",
    "model_manager",
]
