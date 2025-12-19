# -*- coding: utf-8 -*-
"""
Datapillar Airflow Plugin

支持 Airflow 2.x 和 3.x 的插件，自动检测版本并使用对应的 API 实现。

Usage:
    1. pip install datapillar-airflow-plugin
    2. Restart Airflow webserver
    3. Access API:
       - Airflow 2.x: http://{HOST}:{PORT}/datapillar/...
       - Airflow 3.x: http://{HOST}:{PORT}/plugins/datapillar/...

Extended Endpoints (not in official API):
    POST   /dags                                                      - Deploy DAG
    DELETE /dags/{dag_id}                                             - Delete DAG file
    POST   /dags/{dag_id}/dagRuns/{run_id}/taskInstances/{task_id}/run   - Run task
    POST   /dags/{dag_id}/dagRuns/{run_id}/taskInstances/{task_id}/rerun - Rerun task
    POST   /dags/{dag_id}/tasks/{task_id}/test                        - Test task
"""

import logging
from airflow import __version__ as airflow_version
from airflow.plugins_manager import AirflowPlugin

logger = logging.getLogger(__name__)

# 检测 Airflow 版本
AIRFLOW_MAJOR_VERSION = int(airflow_version.split(".")[0])
IS_AIRFLOW_3 = AIRFLOW_MAJOR_VERSION >= 3

logger.info(f"Datapillar Plugin: Detected Airflow {airflow_version} (major={AIRFLOW_MAJOR_VERSION})")


if IS_AIRFLOW_3:
    # Airflow 3.x: 使用 FastAPI
    from .api_v3 import app

    class DatapillarPlugin(AirflowPlugin):
        """Datapillar Airflow Plugin - REST API Extensions (Airflow 3.x)"""

        name = "datapillar"

        # Airflow 3.x: FastAPI apps
        fastapi_apps = [
            {
                "app": app,
                "url_prefix": "/plugins/datapillar",
                "name": "Datapillar API Extensions"
            }
        ]

else:
    # Airflow 2.x: 使用 Flask Blueprint
    from .api_v2 import blueprint

    class DatapillarPlugin(AirflowPlugin):
        """Datapillar Airflow Plugin - REST API Extensions (Airflow 2.x)"""

        name = "datapillar"

        # Airflow 2.x: Flask Blueprints
        flask_blueprints = [blueprint]
