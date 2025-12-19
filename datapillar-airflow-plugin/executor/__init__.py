# -*- coding: utf-8 -*-
"""
Datapillar Component Executor Framework

可扩展的组件执行器框架。

使用方式：
1. 继承 BaseExecutor
2. 实现 execute() 方法
3. 使用 @register 装饰器注册

示例：
    from datapillar_airflow_plugin.executor import BaseExecutor, register

    @register("MY_COMPONENT")
    class MyComponentExecutor(BaseExecutor):
        def execute(self, params: dict) -> ExecutorResult:
            # 实现执行逻辑
            return ExecutorResult(success=True, output="done")
"""

import logging
from abc import ABC, abstractmethod
from typing import Any, Dict, Type

logger = logging.getLogger(__name__)


class ExecutorResult:
    """执行结果"""

    def __init__(self, success: bool, output: str = "", error: str = ""):
        self.success = success
        self.output = output
        self.error = error

    def to_dict(self) -> Dict[str, Any]:
        return {
            "success": self.success,
            "output": self.output,
            "error": self.error
        }

    def __repr__(self):
        return f"ExecutorResult(success={self.success}, output={self.output[:50]}..., error={self.error[:50]}...)"


class BaseExecutor(ABC):
    """
    执行器基类

    所有组件执行器必须继承此类并实现 execute() 方法。
    """

    @abstractmethod
    def execute(self, params: Dict[str, Any]) -> ExecutorResult:
        """
        执行组件逻辑

        Args:
            params: 组件参数（由用户在前端填写）

        Returns:
            ExecutorResult: 执行结果
        """
        pass

    def validate_params(self, params: Dict[str, Any]) -> None:
        """
        验证参数（可选覆盖）

        Args:
            params: 组件参数

        Raises:
            ValueError: 参数无效时抛出
        """
        pass


# ==================== 执行器注册表 ====================

_EXECUTOR_REGISTRY: Dict[str, Type[BaseExecutor]] = {}


def register(component_code: str):
    """
    执行器注册装饰器

    使用方式：
        @register("SHELL")
        class ShellExecutor(BaseExecutor):
            ...

    Args:
        component_code: 组件代码（与 job_component 表中的 component_code 对应）
    """
    def decorator(cls: Type[BaseExecutor]):
        code = component_code.upper()
        if code in _EXECUTOR_REGISTRY:
            logger.warning(f"Executor for '{code}' already registered, overwriting...")
        _EXECUTOR_REGISTRY[code] = cls
        logger.info(f"Registered executor: {code} -> {cls.__name__}")
        return cls
    return decorator


def get_executor(component_code: str) -> BaseExecutor:
    """
    获取执行器实例

    Args:
        component_code: 组件代码

    Returns:
        BaseExecutor: 执行器实例

    Raises:
        ValueError: 组件未注册时抛出
    """
    code = component_code.upper()
    executor_cls = _EXECUTOR_REGISTRY.get(code)

    if not executor_cls:
        available = list(_EXECUTOR_REGISTRY.keys())
        raise ValueError(f"Unknown component: {code}. Available: {available}")

    return executor_cls()


def list_executors() -> Dict[str, str]:
    """
    列出所有已注册的执行器

    Returns:
        Dict[str, str]: {component_code: executor_class_name}
    """
    return {code: cls.__name__ for code, cls in _EXECUTOR_REGISTRY.items()}


# ==================== 执行入口 ====================

def execute_component(component_code: str, params: Dict[str, Any], **context) -> Dict[str, Any]:
    """
    通用组件执行入口

    这个函数会被 Airflow PythonOperator 调用。

    Args:
        component_code: 组件代码（SHELL, PYTHON, HTTP, ...）
        params: 组件参数（用户在前端填写的值）
        context: Airflow 上下文

    Returns:
        执行结果字典

    Raises:
        ValueError: 组件未注册
        RuntimeError: 执行失败
    """
    logger.info(f"Executing component: {component_code}")
    logger.debug(f"Params: {params}")

    executor = get_executor(component_code)

    # 验证参数
    executor.validate_params(params)

    # 执行
    result = executor.execute(params)

    if not result.success:
        raise RuntimeError(f"Component {component_code} failed: {result.error}")

    logger.info(f"Component {component_code} completed successfully")
    return result.to_dict()


# ==================== 自动加载内置执行器 ====================

def _load_builtin_executors():
    """加载内置执行器"""
    from datapillar_airflow_plugin.executor import shell


# 模块加载时自动注册内置执行器
_load_builtin_executors()
