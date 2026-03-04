# -*- coding: utf-8 -*-
"""
Datapillar Airflow Plugin - DAG Generator.

Generate Airflow DAG files from workflow definitions with tenant-isolated storage.
"""

import logging
import os
import re
from importlib import import_module
from typing import Any, Dict, List, Tuple

from airflow import __version__ as airflow_version
from airflow.configuration import conf
from jinja2 import Environment, FileSystemLoader

logger = logging.getLogger(__name__)

AIRFLOW_MAJOR_VERSION = int(airflow_version.split(".")[0])
IS_AIRFLOW_3 = AIRFLOW_MAJOR_VERSION >= 3

TENANT_CODE_PATTERN = re.compile(r"^[a-z0-9][a-z0-9_-]{1,63}$")
DAG_ID_PATTERN = re.compile(r"^dp_([a-z0-9][a-z0-9_-]{1,63})_w([1-9][0-9]*)$")
LEGACY_DAG_PREFIX = "datapillar_project_"


class DagGenerator:
    """Generate tenant-isolated DAG files for Datapillar workflows."""

    def __init__(self):
        self.dags_folder = conf.get("core", "dags_folder")
        template_dir = os.path.join(os.path.dirname(__file__), "templates")
        self.env = Environment(
            loader=FileSystemLoader(template_dir),
            trim_blocks=True,
            lstrip_blocks=True,
        )
        self.cleanup_legacy_artifacts()

    def cleanup_legacy_artifacts(self) -> Dict[str, int]:
        """Remove legacy flat DAG files and stale metadata rows."""
        deleted_files = self._cleanup_legacy_files()
        deleted_metadata = self._cleanup_legacy_metadata()
        if deleted_files > 0 or deleted_metadata > 0:
            logger.info(
                "Legacy Datapillar DAG artifacts cleaned: files=%s, metadata=%s",
                deleted_files,
                deleted_metadata,
            )
        return {"files": deleted_files, "metadata": deleted_metadata}

    def _cleanup_legacy_files(self) -> int:
        root = self.dags_folder
        if not os.path.isdir(root):
            return 0

        deleted = 0
        for filename in os.listdir(root):
            file_path = os.path.join(root, filename)
            if not os.path.isfile(file_path):
                continue
            if not filename.startswith(LEGACY_DAG_PREFIX):
                continue
            if not filename.endswith(".py"):
                continue
            os.remove(file_path)
            deleted += 1
        return deleted

    def _cleanup_legacy_metadata(self) -> int:
        try:
            from airflow.utils.session import create_session
        except BaseException as exc:
            logger.warning("Skip legacy DAG metadata cleanup: %s", exc)
            return 0

        model_specs = [
            ("airflow.models", "TaskInstance"),
            ("airflow.models", "DagRun"),
            ("airflow.models.dag_version", "DagVersion"),
            ("airflow.models.serialized_dag", "SerializedDagModel"),
            ("airflow.models.dag", "DagTag"),
            ("airflow.models", "DagModel"),
        ]

        deleted = 0
        with create_session() as session:
            for module_name, class_name in model_specs:
                model = self._load_model(module_name, class_name)
                if model is None:
                    continue
                try:
                    result = (
                        session.query(model)
                        .filter(model.dag_id.like(f"{LEGACY_DAG_PREFIX}%"))
                        .delete(synchronize_session=False)
                    )
                    session.commit()
                    deleted += int(result or 0)
                except BaseException as exc:
                    session.rollback()
                    logger.warning(
                        "Skip cleanup for model %s.%s: %s",
                        module_name,
                        class_name,
                        exc,
                    )
        return deleted

    def _load_model(self, module_name: str, class_name: str):
        try:
            module = import_module(module_name)
            return getattr(module, class_name, None)
        except BaseException:
            return None

    def parse_dag_id(self, dag_id: str) -> Tuple[str, int]:
        """Parse dag_id to (tenant_code, workflow_id)."""
        if dag_id is None:
            raise ValueError("dag_id is required")
        normalized = dag_id.strip().lower()
        match = DAG_ID_PATTERN.fullmatch(normalized)
        if not match:
            raise ValueError(f"Invalid dag_id format: {dag_id}")
        return match.group(1), int(match.group(2))

    def build_dag_id(self, tenant_code: str, workflow_id: int) -> str:
        """Build canonical dag_id from tenant and workflow id."""
        normalized_tenant = self.normalize_tenant_code(tenant_code)
        normalized_workflow_id = self.normalize_workflow_id(workflow_id)
        return f"dp_{normalized_tenant}_w{normalized_workflow_id}"

    def normalize_tenant_code(self, tenant_code: str) -> str:
        """Validate and normalize tenant code."""
        if tenant_code is None:
            raise ValueError("tenant_code is required")
        normalized = tenant_code.strip().lower()
        if not TENANT_CODE_PATTERN.fullmatch(normalized):
            raise ValueError(f"Invalid tenant code format: {tenant_code}")
        return normalized

    def normalize_workflow_id(self, workflow_id: int) -> int:
        """Validate workflow id."""
        try:
            normalized = int(workflow_id)
        except (TypeError, ValueError) as exc:
            raise ValueError("workflow_id must be a positive integer") from exc
        if normalized <= 0:
            raise ValueError("workflow_id must be a positive integer")
        return normalized

    def get_dag_path(self, tenant_code: str, workflow_id: int) -> str:
        """Resolve tenant-isolated DAG file path."""
        normalized_tenant = self.normalize_tenant_code(tenant_code)
        normalized_workflow_id = self.normalize_workflow_id(workflow_id)
        filename = f"wf_{normalized_workflow_id}.py"
        return os.path.join(self.dags_folder, "datapillar", normalized_tenant, filename)

    def generate(self, tenant_code: str, workflow_id: int, dag_id: str, dag_config: Dict[str, Any]) -> str:
        """Render and persist a DAG file under tenant-isolated directory."""
        expected_dag_id = self.build_dag_id(tenant_code, workflow_id)
        if dag_id.strip().lower() != expected_dag_id:
            raise ValueError(f"dag_id mismatch: expected {expected_dag_id}, got {dag_id}")

        template_name = "dag_template_v3.py.j2" if IS_AIRFLOW_3 else "dag_template_v2.py.j2"
        template = self.env.get_template(template_name)
        logger.info("Using template %s for Airflow %s", template_name, "3.x" if IS_AIRFLOW_3 else "2.x")

        content = template.render(dag_id=expected_dag_id, config=dag_config)
        dag_path = self.get_dag_path(tenant_code, workflow_id)

        os.makedirs(os.path.dirname(dag_path), exist_ok=True)
        with open(dag_path, "w", encoding="utf-8") as handle:
            handle.write(content)

        logger.info("Generated DAG file: %s", dag_path)
        return dag_path

    def delete(self, tenant_code: str, workflow_id: int) -> None:
        """Delete a DAG file using tenant and workflow id."""
        dag_path = self.get_dag_path(tenant_code, workflow_id)
        if not os.path.exists(dag_path):
            raise FileNotFoundError(f"DAG file not found: {dag_path}")
        os.remove(dag_path)
        logger.info("Deleted DAG file: %s", dag_path)

    def delete_by_dag_id(self, dag_id: str) -> None:
        """Delete a DAG file from canonical dag_id."""
        tenant_code, workflow_id = self.parse_dag_id(dag_id)
        self.delete(tenant_code, workflow_id)

    def exists(self, tenant_code: str, workflow_id: int) -> bool:
        """Check whether DAG file exists."""
        return os.path.exists(self.get_dag_path(tenant_code, workflow_id))

    def exists_by_dag_id(self, dag_id: str) -> bool:
        """Check whether DAG file exists by dag_id."""
        tenant_code, workflow_id = self.parse_dag_id(dag_id)
        return self.exists(tenant_code, workflow_id)

    def list_dags(self, tenant_code: str = None) -> List[str]:
        """List canonical dag_id values managed by Datapillar."""
        root = os.path.join(self.dags_folder, "datapillar")
        if not os.path.isdir(root):
            return []

        if tenant_code is not None:
            tenants = [self.normalize_tenant_code(tenant_code)]
        else:
            tenants = [
                name
                for name in os.listdir(root)
                if os.path.isdir(os.path.join(root, name)) and TENANT_CODE_PATTERN.fullmatch(name)
            ]

        dag_ids: List[str] = []
        for tenant in tenants:
            tenant_dir = os.path.join(root, tenant)
            if not os.path.isdir(tenant_dir):
                continue
            for filename in os.listdir(tenant_dir):
                if not filename.startswith("wf_") or not filename.endswith(".py"):
                    continue
                workflow_literal = filename[3:-3]
                if not workflow_literal.isdigit():
                    continue
                workflow_id = int(workflow_literal)
                if workflow_id <= 0:
                    continue
                dag_ids.append(self.build_dag_id(tenant, workflow_id))

        dag_ids.sort()
        return dag_ids
