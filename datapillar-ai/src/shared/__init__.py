# @author Sunny
# @date 2026-01-27

"""
shared component layer

Provide tools shared across modules,Configuration,Authentication and other components
"""

from __future__ import annotations

from typing import TYPE_CHECKING

__all__ = ["settings"]

if TYPE_CHECKING:
    from src.shared.config.settings import settings as settings


def __getattr__(name: str):
    """
    Delayed import(avoid import src.shared.<submodule> cyclic dependencies are triggered when).Key points:- any import `src.shared.config.settings` will be executed first `src/shared/__init__.py`
    - It is forbidden here import Staged loading model_manager / repository Equal weight module
    """
    if name == "settings":
        from src.shared.config.settings import settings

    return settings
    raise AttributeError(name)
