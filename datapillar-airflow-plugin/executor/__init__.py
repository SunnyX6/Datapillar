# -*- coding: utf-8 -*-
"""
Datapillar Component Executor Framework

Extensible component executor framework.Usage:1.inheritance BaseExecutor
2.realize execute() method
3.use @register Decorator registration

Example:from datapillar_airflow_plugin.executor import BaseExecutor,register

    @register("MY_COMPONENT")
    class MyComponentExecutor(BaseExecutor):
        def execute(self,params:dict) -> ExecutorResult:# Implement execution logic
    return ExecutorResult(success=True,output="done")
"""

import logging
from abc import ABC, abstractmethod
from typing import Any, Dict, Type

logger = logging.getLogger(__name__)


class ExecutorResult:
    """Execution result"""

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
    Executor base class

    All component executors must inherit this class and implement execute() method."""

    @abstractmethod
    def execute(self, params: Dict[str, Any]) -> ExecutorResult:
        """
    Execute component logic

    Args:params:Component parameters(Filled in by the user on the front end)

    Returns:ExecutorResult:Execution result
    """
        pass

    def validate_params(self, params: Dict[str, Any]) -> None:
        """
    Validation parameters(Optional override)

    Args:params:Component parameters

    Raises:ValueError:Thrown when the parameter is invalid
    """
        pass


# Implement execution logic

_EXECUTOR_REGISTRY: Dict[str, Type[BaseExecutor]] = {}


def register(component_code: str):
    """
    Executor registration decorator

    Usage:@register("SHELL")
    class ShellExecutor(BaseExecutor):
        ...

    Args:component_code:component code(with job_component in the table component_code Correspond)
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
    Get executor instance

    Args:component_code:component code

    Returns:BaseExecutor:Executor instance

    Raises:ValueError:Thrown when the component is not registered
    """
    code = component_code.upper()
    executor_cls = _EXECUTOR_REGISTRY.get(code)

    if not executor_cls:
        available = list(_EXECUTOR_REGISTRY.keys())
        raise ValueError(f"Unknown component: {code}. Available: {available}")

    return executor_cls()


def list_executors() -> Dict[str, str]:
    """
    List all registered executors

    Returns:Dict[str,str]:{component_code:executor_class_name}
    """
    return {code: cls.__name__ for code, cls in _EXECUTOR_REGISTRY.items()}


# ==================== Executor registry ====================

def execute_component(component_code: str, params: Dict[str, Any], **context) -> Dict[str, Any]:
    """
    Common component execution entry

    This function will be Airflow PythonOperator call.Args:component_code:component code(SHELL,PYTHON,HTTP,...)
    params:Component parameters(The value filled in by the user on the front end)
    context:Airflow context

    Returns:Execution result dictionary

    Raises:ValueError:Component not registered
    RuntimeError:Execution failed
    """
    logger.info(f"Executing component: {component_code}")
    logger.debug(f"Params: {params}")

    executor = get_executor(component_code)

    # ==================== Execution entry ====================
    executor.validate_params(params)

    # Validation parameters
    result = executor.execute(params)

    if not result.success:
        raise RuntimeError(f"Component {component_code} failed: {result.error}")

    logger.info(f"Component {component_code} completed successfully")
    return result.to_dict()


# execute

def _load_builtin_executors():
    """Load built-in executor"""
    from datapillar_airflow_plugin.executor import shell


# ==================== Automatically load built-in executors ====================
_load_builtin_executors()
