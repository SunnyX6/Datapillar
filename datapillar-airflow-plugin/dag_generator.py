# -*- coding: utf-8 -*-
"""
Datapillar Airflow Plugin - DAG Generator

Generates Airflow DAG Python files from configuration using Jinja2 templates.
支持 Airflow 2.x 和 3.x 双版本。
"""

import os
from typing import Dict, Any
from jinja2 import Environment, FileSystemLoader
from airflow.configuration import conf
from airflow import __version__ as airflow_version
import logging

logger = logging.getLogger(__name__)

# 检测 Airflow 版本
AIRFLOW_MAJOR_VERSION = int(airflow_version.split(".")[0])
IS_AIRFLOW_3 = AIRFLOW_MAJOR_VERSION >= 3


class DagGenerator:
    """
    Generates Airflow DAG Python files from Datapillar DAG configuration.

    The generator uses Jinja2 templates to create standard Airflow DAG files
    that are automatically picked up by the Airflow scheduler.
    """

    # Prefix for all generated DAG files
    FILE_PREFIX = "datapillar_"

    def __init__(self):
        """Initialize the DAG generator"""
        # Get Airflow dags folder from configuration
        self.dags_folder = conf.get("core", "dags_folder")

        # Setup Jinja2 environment
        template_dir = os.path.join(os.path.dirname(__file__), "templates")
        self.env = Environment(
            loader=FileSystemLoader(template_dir),
            trim_blocks=True,
            lstrip_blocks=True
        )

    def get_dag_path(self, dag_id: str) -> str:
        """
        Get the file path for a DAG

        Args:
            dag_id: The DAG identifier

        Returns:
            Full path to the DAG Python file
        """
        filename = f"{self.FILE_PREFIX}{dag_id}.py"
        return os.path.join(self.dags_folder, filename)

    def generate(self, dag_id: str, dag_config: Dict[str, Any]) -> str:
        """
        Generate a DAG Python file from configuration

        Args:
            dag_id: The DAG identifier (namespace_name format)
            dag_config: DAG configuration dictionary

        Returns:
            Path to the generated DAG file
        """
        # Load template based on Airflow version
        template_name = "dag_template_v3.py.j2" if IS_AIRFLOW_3 else "dag_template_v2.py.j2"
        template = self.env.get_template(template_name)
        logger.info(f"Using template: {template_name} (Airflow {'3.x' if IS_AIRFLOW_3 else '2.x'})")

        # Render template
        content = template.render(
            dag_id=dag_id,
            config=dag_config
        )

        # Write to file
        dag_path = self.get_dag_path(dag_id)

        # Ensure directory exists
        os.makedirs(os.path.dirname(dag_path), exist_ok=True)

        with open(dag_path, "w", encoding="utf-8") as f:
            f.write(content)

        logger.info(f"Generated DAG file: {dag_path}")

        return dag_path

    def delete(self, dag_id: str) -> None:
        """
        Delete a DAG file

        Args:
            dag_id: The DAG identifier

        Raises:
            FileNotFoundError: If the DAG file doesn't exist
        """
        dag_path = self.get_dag_path(dag_id)

        if not os.path.exists(dag_path):
            raise FileNotFoundError(f"DAG file not found: {dag_path}")

        os.remove(dag_path)
        logger.info(f"Deleted DAG file: {dag_path}")

    def exists(self, dag_id: str) -> bool:
        """
        Check if a DAG file exists

        Args:
            dag_id: The DAG identifier

        Returns:
            True if the DAG file exists
        """
        return os.path.exists(self.get_dag_path(dag_id))

    def list_dags(self) -> list:
        """
        List all Datapillar-managed DAG files

        Returns:
            List of DAG IDs
        """
        dags = []
        prefix_len = len(self.FILE_PREFIX)

        for filename in os.listdir(self.dags_folder):
            if filename.startswith(self.FILE_PREFIX) and filename.endswith(".py"):
                dag_id = filename[prefix_len:-3]  # Remove prefix and .py
                dags.append(dag_id)

        return dags
