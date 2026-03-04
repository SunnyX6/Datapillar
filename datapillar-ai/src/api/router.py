# @author Sunny
# @date 2026-01-27

"""
API route aggregation

Auto scan modules Directory，Register routes based on module configuration
unified prefix: /api/ai/{biz|admin}/（by app Unified mounting /api/ai）
"""

import importlib
import logging
import pkgutil

from fastapi import APIRouter, Depends

import src.modules as modules_pkg
from src.shared.auth.dependencies import require_admin_role

api_router = APIRouter()
logger = logging.getLogger(__name__)
_SUPPORTED_SCOPES = {"biz", "admin"}
_DEFAULT_SCOPE = "biz"


def _normalize_scope(raw_scope: object, *, module_path: str) -> str:
    scope = str(raw_scope or _DEFAULT_SCOPE).strip().lower()
    if scope not in _SUPPORTED_SCOPES:
        logger.warning(
            "module scope illegal，Rolled back biz: %s, scope=%s", module_path, raw_scope
        )
        return _DEFAULT_SCOPE
    return scope


def _scope_dependencies(scope: str) -> list:
    if scope == "admin":
        return [Depends(require_admin_role)]
    return []


def _register_module(module_path: str, *, parent_scope: str | None) -> None:
    """
    Recursively register module routes

    Modules need to be exported:
    - router: APIRouter Example
    - MODULE_PREFIX: routing prefix（Optional，Default module name）
    - MODULE_TAGS: tag list（Optional）
    """
    try:
        module = importlib.import_module(module_path)
    except Exception as exc:
        logger.error(
            "Module import failed，Route not registered: %s, error=%s",
            module_path,
            exc,
            exc_info=True,
        )
        return

    router: APIRouter | None = getattr(module, "router", None)
    if router is None:
        return

    # Get module name（last paragraph）
    module_name = module_path.split(".")[-1]

    # Prefer using module-defined prefixes，Otherwise use module name
    prefix = getattr(module, "MODULE_PREFIX", f"/{module_name}")
    tags = getattr(module, "MODULE_TAGS", [module_name.replace("_", " ").title()])

    scope = _normalize_scope(getattr(module, "MODULE_SCOPE", parent_scope), module_path=module_path)
    full_prefix = f"/{scope}{prefix}"

    api_router.include_router(
        router,
        prefix=full_prefix,
        tags=tags,
        dependencies=_scope_dependencies(scope),
    )


def _scan_modules() -> None:
    """scan modules Directory，Automatically register all modules"""
    base_path = modules_pkg.__path__
    base_name = modules_pkg.__name__

    for _importer, modname, ispkg in pkgutil.iter_modules(base_path):
        if modname.startswith("_"):
            continue

        module_path = f"{base_name}.{modname}"

        if ispkg:
            # Check if any submodules need to be registered
            try:
                pkg = importlib.import_module(module_path)
                sub_path = pkg.__path__

                parent_scope = _normalize_scope(
                    getattr(pkg, "MODULE_SCOPE", _DEFAULT_SCOPE),
                    module_path=module_path,
                )

                # First try registering the parent module itself
                _register_module(module_path, parent_scope=parent_scope)

                scan_submodules = bool(getattr(pkg, "MODULE_SCAN_SUBMODULES", True))
                if not scan_submodules:
                    continue

                # Scan submodules again
                for _, submodname, subispkg in pkgutil.iter_modules(sub_path):
                    if submodname.startswith("_"):
                        continue
                    sub_module_path = f"{module_path}.{submodname}"
                    if subispkg:
                        # Submodules are packages，Use the parent module name as part of the prefix
                        _register_submodule(sub_module_path, modname, parent_scope)
                    else:
                        pass  # Non-package submodules are not processed

            except Exception as exc:
                logger.error(
                    "Module scan failed，Route may be incomplete: %s, error=%s",
                    module_path,
                    exc,
                    exc_info=True,
                )
        else:
            _register_module(module_path, parent_scope=_DEFAULT_SCOPE)


def _register_submodule(module_path: str, parent_name: str, parent_scope: str) -> None:
    """Register submodule，The path is /{scope}/{parent}/{submodule}"""
    try:
        module = importlib.import_module(module_path)
    except Exception as exc:
        logger.error(
            "Submodule import failed，Route not registered: %s, error=%s",
            module_path,
            exc,
            exc_info=True,
        )
        return

    router: APIRouter | None = getattr(module, "router", None)
    if router is None:
        return

    module_name = module_path.split(".")[-1]
    prefix = getattr(module, "MODULE_PREFIX", f"/{module_name}")
    tags = getattr(module, "MODULE_TAGS", [f"{parent_name}/{module_name}"])

    scope = _normalize_scope(getattr(module, "MODULE_SCOPE", parent_scope), module_path=module_path)
    full_prefix = f"/{scope}/{parent_name}{prefix}"

    api_router.include_router(
        router,
        prefix=full_prefix,
        tags=tags,
        dependencies=_scope_dependencies(scope),
    )


# Perform automatic scan registration
_scan_modules()
