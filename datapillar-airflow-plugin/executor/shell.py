# -*- coding: utf-8 -*-
"""Shell 脚本执行器"""

import subprocess
import logging
from typing import Any, Dict

from datapillar_airflow_plugin.executor import BaseExecutor, ExecutorResult, register

logger = logging.getLogger(__name__)


@register("SHELL")
class ShellExecutor(BaseExecutor):
    """
    Shell 脚本执行器

    参数：
        script: Shell 脚本内容
        timeout: 超时时间（秒），默认 60
    """

    def validate_params(self, params: Dict[str, Any]) -> None:
        if not params.get("script"):
            raise ValueError("Parameter 'script' is required")

    def execute(self, params: Dict[str, Any]) -> ExecutorResult:
        script = params.get("script", "")
        timeout = params.get("timeout", 60)

        try:
            logger.info(f"Executing shell script: {script[:100]}...")

            result = subprocess.run(
                script,
                shell=True,
                capture_output=True,
                text=True,
                timeout=timeout
            )

            if result.returncode == 0:
                logger.info("Shell script executed successfully")
                return ExecutorResult(success=True, output=result.stdout)
            else:
                logger.error(f"Shell script failed with code {result.returncode}")
                return ExecutorResult(
                    success=False,
                    output=result.stdout,
                    error=result.stderr or f"Exit code: {result.returncode}"
                )

        except subprocess.TimeoutExpired:
            return ExecutorResult(success=False, error=f"Script timeout after {timeout}s")
        except Exception as e:
            logger.exception(f"Shell execution error: {e}")
            return ExecutorResult(success=False, error=str(e))
