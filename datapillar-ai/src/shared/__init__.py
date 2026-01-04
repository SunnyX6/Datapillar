"""
共享组件层

提供跨模块共享的工具、配置、认证等组件
"""

from __future__ import annotations

from typing import TYPE_CHECKING

__all__ = ["settings"]

if TYPE_CHECKING:
    from src.shared.config.settings import settings as settings


def __getattr__(name: str):
    """
    延迟导入（避免 import src.shared.<submodule> 时触发循环依赖）。

    关键点：
    - 任何 import `src.shared.config.settings` 都会先执行 `src/shared/__init__.py`
    - 这里禁止在 import 阶段加载 model_manager / repository 等重模块
    """
    if name == "settings":
        from src.shared.config.settings import settings

        return settings
    raise AttributeError(name)
