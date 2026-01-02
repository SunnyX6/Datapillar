"""
共享组件层

提供跨模块共享的工具、配置、认证等组件
"""

from __future__ import annotations

__all__ = ["settings", "model_manager", "ModelConfig", "ModelManager"]


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
    if name in {"model_manager", "ModelConfig", "ModelManager"}:
        from src.shared.config.models import model_manager, ModelConfig, ModelManager

        return {
            "model_manager": model_manager,
            "ModelConfig": ModelConfig,
            "ModelManager": ModelManager,
        }[name]
    raise AttributeError(name)
