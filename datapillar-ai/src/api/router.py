# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
API 路由聚合

自动扫描 modules 目录，根据模块配置注册路由
统一前缀: /api/ai/{biz|admin}/（由 app 统一挂载 /api/ai）
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
        logger.warning("模块 scope 非法，已回退 biz: %s, scope=%s", module_path, raw_scope)
        return _DEFAULT_SCOPE
    return scope


def _scope_dependencies(scope: str) -> list:
    if scope == "admin":
        return [Depends(require_admin_role)]
    return []


def _register_module(module_path: str, *, parent_scope: str | None) -> None:
    """
    递归注册模块路由

    模块需要导出:
    - router: APIRouter 实例
    - MODULE_PREFIX: 路由前缀（可选，默认用模块名）
    - MODULE_TAGS: 标签列表（可选）
    """
    try:
        module = importlib.import_module(module_path)
    except Exception as exc:
        logger.error("模块导入失败，路由未注册: %s, error=%s", module_path, exc, exc_info=True)
        return

    router: APIRouter | None = getattr(module, "router", None)
    if router is None:
        return

    # 获取模块名（最后一段）
    module_name = module_path.split(".")[-1]

    # 优先使用模块定义的前缀，否则用模块名
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
    """扫描 modules 目录，自动注册所有模块"""
    base_path = modules_pkg.__path__
    base_name = modules_pkg.__name__

    for _importer, modname, ispkg in pkgutil.iter_modules(base_path):
        if modname.startswith("_"):
            continue

        module_path = f"{base_name}.{modname}"

        if ispkg:
            # 检查是否有子模块需要注册
            try:
                pkg = importlib.import_module(module_path)
                sub_path = pkg.__path__

                parent_scope = _normalize_scope(
                    getattr(pkg, "MODULE_SCOPE", _DEFAULT_SCOPE),
                    module_path=module_path,
                )

                # 先尝试注册父模块本身
                _register_module(module_path, parent_scope=parent_scope)

                scan_submodules = bool(getattr(pkg, "MODULE_SCAN_SUBMODULES", True))
                if not scan_submodules:
                    continue

                # 再扫描子模块
                for _, submodname, subispkg in pkgutil.iter_modules(sub_path):
                    if submodname.startswith("_"):
                        continue
                    sub_module_path = f"{module_path}.{submodname}"
                    if subispkg:
                        # 子模块是包，使用父模块名作为前缀的一部分
                        _register_submodule(sub_module_path, modname, parent_scope)
                    else:
                        pass  # 非包的子模块不处理

            except Exception as exc:
                logger.error(
                    "模块扫描失败，路由可能不完整: %s, error=%s", module_path, exc, exc_info=True
                )
        else:
            _register_module(module_path, parent_scope=_DEFAULT_SCOPE)


def _register_submodule(module_path: str, parent_name: str, parent_scope: str) -> None:
    """注册子模块，路径为 /{scope}/{parent}/{submodule}"""
    try:
        module = importlib.import_module(module_path)
    except Exception as exc:
        logger.error("子模块导入失败，路由未注册: %s, error=%s", module_path, exc, exc_info=True)
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


# 执行自动扫描注册
_scan_modules()
