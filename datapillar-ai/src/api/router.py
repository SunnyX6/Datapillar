"""
API 路由聚合

自动扫描 modules 目录，根据模块配置注册路由
统一前缀: /api/ai/
"""

import importlib
import pkgutil
from typing import Optional

from fastapi import APIRouter

import src.modules as modules_pkg


api_router = APIRouter()


def _register_module(module_path: str, parent_prefix: str = "/ai") -> None:
    """
    递归注册模块路由

    模块需要导出:
    - router: APIRouter 实例
    - MODULE_PREFIX: 路由前缀（可选，默认用模块名）
    - MODULE_TAGS: 标签列表（可选）
    """
    try:
        module = importlib.import_module(module_path)
    except ImportError:
        return

    router: Optional[APIRouter] = getattr(module, "router", None)
    if router is None:
        return

    # 获取模块名（最后一段）
    module_name = module_path.split(".")[-1]

    # 优先使用模块定义的前缀，否则用模块名
    prefix = getattr(module, "MODULE_PREFIX", f"/{module_name}")
    tags = getattr(module, "MODULE_TAGS", [module_name.replace("_", " ").title()])

    full_prefix = f"{parent_prefix}{prefix}"

    api_router.include_router(router, prefix=full_prefix, tags=tags)


def _scan_modules() -> None:
    """扫描 modules 目录，自动注册所有模块"""
    base_path = modules_pkg.__path__
    base_name = modules_pkg.__name__

    for importer, modname, ispkg in pkgutil.iter_modules(base_path):
        if modname.startswith("_"):
            continue

        module_path = f"{base_name}.{modname}"

        if ispkg:
            # 检查是否有子模块需要注册
            try:
                pkg = importlib.import_module(module_path)
                sub_path = pkg.__path__

                # 先尝试注册父模块本身
                _register_module(module_path)

                # 再扫描子模块
                for _, submodname, subispkg in pkgutil.iter_modules(sub_path):
                    if submodname.startswith("_"):
                        continue
                    sub_module_path = f"{module_path}.{submodname}"
                    if subispkg:
                        # 子模块是包，使用父模块名作为前缀的一部分
                        _register_submodule(sub_module_path, modname)
                    else:
                        pass  # 非包的子模块不处理

            except ImportError:
                pass
        else:
            _register_module(module_path)


def _register_submodule(module_path: str, parent_name: str) -> None:
    """注册子模块，路径为 /ai/{parent}/{submodule}"""
    try:
        module = importlib.import_module(module_path)
    except ImportError:
        return

    router: Optional[APIRouter] = getattr(module, "router", None)
    if router is None:
        return

    module_name = module_path.split(".")[-1]
    prefix = getattr(module, "MODULE_PREFIX", f"/{module_name}")
    tags = getattr(module, "MODULE_TAGS", [f"{parent_name}/{module_name}"])

    full_prefix = f"/ai/{parent_name}{prefix}"

    api_router.include_router(router, prefix=full_prefix, tags=tags)


# 执行自动扫描注册
_scan_modules()
