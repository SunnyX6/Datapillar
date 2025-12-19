# -*- coding: utf-8 -*-
"""
Datapillar Airflow Plugin - REST API Extensions for Airflow 2.x

Flask Blueprint 版本，兼容 Airflow 2.x
"""

from flask import Blueprint, request, jsonify
from typing import Optional, Dict, Any
import logging

from .dag_generator import DagGenerator
from .config import get_config

logger = logging.getLogger(__name__)

# Flask Blueprint
blueprint = Blueprint(
    "datapillar",
    __name__,
    url_prefix="/datapillar"
)


# ==================== Authentication ====================

def verify_token() -> bool:
    """Verify API token if configured"""
    config = get_config()
    expected_token = config.get("api_token")
    if expected_token:
        token = request.headers.get("X-Datapillar-Token")
        if token != expected_token:
            return False
    return True


def require_auth(f):
    """Decorator for authentication"""
    from functools import wraps
    @wraps(f)
    def decorated(*args, **kwargs):
        if not verify_token():
            return jsonify({"success": False, "message": "Unauthorized"}), 401
        return f(*args, **kwargs)
    return decorated


# ==================== API Endpoints ====================

@blueprint.route("/health", methods=["GET"])
def health_check():
    """Health check endpoint"""
    return jsonify({
        "status": "ok",
        "service": "datapillar-airflow-plugin",
        "api_version": "v1",
        "airflow_version": "2.x"
    })


@blueprint.route("/dags", methods=["POST"])
@require_auth
def deploy_dag():
    """
    Deploy a new DAG to Airflow

    Request body:
    {
        "namespace": "test",
        "dag": {
            "name": "my_dag",
            "description": "...",
            "nodes": [...],
            "edges": [...]
        }
    }
    """
    try:
        data = request.get_json()
        if not data:
            return jsonify({"success": False, "message": "Invalid JSON"}), 400

        namespace = data.get("namespace")
        dag_config = data.get("dag")

        if not namespace or not dag_config:
            return jsonify({"success": False, "message": "Missing namespace or dag"}), 400

        generator = DagGenerator()
        dag_id = f"{namespace}_{dag_config.get('name')}"
        dag_path = generator.generate(dag_id, dag_config)

        logger.info(f"DAG deployed: {dag_id} at {dag_path}")

        return jsonify({
            "success": True,
            "message": f"DAG '{dag_id}' deployed successfully",
            "data": {"dag_id": dag_id, "path": dag_path}
        })
    except Exception as e:
        logger.exception(f"Failed to deploy DAG: {e}")
        return jsonify({"success": False, "message": str(e)}), 500


@blueprint.route("/dags/<dag_id>", methods=["DELETE"])
@require_auth
def delete_dag(dag_id: str):
    """Delete a DAG file from Airflow"""
    try:
        generator = DagGenerator()
        generator.delete(dag_id)

        logger.info(f"DAG file deleted: {dag_id}")

        return jsonify({
            "success": True,
            "message": f"DAG '{dag_id}' file deleted"
        })
    except FileNotFoundError:
        return jsonify({"success": False, "message": f"DAG file not found: {dag_id}"}), 404
    except Exception as e:
        logger.exception(f"Failed to delete DAG: {e}")
        return jsonify({"success": False, "message": str(e)}), 500


@blueprint.route("/dags/<dag_id>/dagRuns/<run_id>/taskInstances/<task_id>/run", methods=["POST"])
@require_auth
def run_task(dag_id: str, run_id: str, task_id: str):
    """Run a single task instance via CLI"""
    import subprocess

    try:
        from airflow.models import DagRun

        dag_runs = DagRun.find(dag_id=dag_id, run_id=run_id)
        if not dag_runs:
            return jsonify({"success": False, "message": f"DAG run not found: {dag_id}/{run_id}"}), 404

        execution_date = dag_runs[0].execution_date.isoformat()

        data = request.get_json() or {}
        cmd = ["airflow", "tasks", "run", dag_id, task_id, execution_date]

        if data.get("mark_success"):
            cmd.append("--mark-success")
        if data.get("force"):
            cmd.append("--force")

        result = subprocess.run(cmd, capture_output=True, text=True, timeout=300)

        if result.returncode == 0:
            return jsonify({
                "success": True,
                "message": f"Task '{task_id}' executed",
                "data": {
                    "dag_id": dag_id,
                    "run_id": run_id,
                    "task_id": task_id,
                    "output": result.stdout
                }
            })
        else:
            return jsonify({
                "success": False,
                "message": f"Task execution failed: {result.stderr}"
            }), 500

    except Exception as e:
        logger.exception(f"Failed to run task: {e}")
        return jsonify({"success": False, "message": str(e)}), 500


@blueprint.route("/dags/<dag_id>/dagRuns/<run_id>/taskInstances/<task_id>/rerun", methods=["POST"])
@require_auth
def rerun_task(dag_id: str, run_id: str, task_id: str):
    """Rerun a task by clearing its state"""
    import subprocess

    try:
        from airflow.models import DagRun

        dag_runs = DagRun.find(dag_id=dag_id, run_id=run_id)
        if not dag_runs:
            return jsonify({"success": False, "message": f"DAG run not found: {dag_id}/{run_id}"}), 404

        execution_date = dag_runs[0].execution_date.isoformat()

        data = request.get_json() or {}
        cmd = [
            "airflow", "tasks", "clear", dag_id,
            "-t", task_id,
            "-s", execution_date,
            "-e", execution_date,
            "-y"
        ]

        if data.get("downstream"):
            cmd.append("-d")
        if data.get("upstream"):
            cmd.append("-u")

        result = subprocess.run(cmd, capture_output=True, text=True, timeout=300)

        if result.returncode == 0:
            return jsonify({
                "success": True,
                "message": f"Task '{task_id}' scheduled for re-execution",
                "data": {
                    "dag_id": dag_id,
                    "run_id": run_id,
                    "task_id": task_id
                }
            })
        else:
            return jsonify({
                "success": False,
                "message": f"Failed to rerun task: {result.stderr}"
            }), 500

    except Exception as e:
        logger.exception(f"Failed to rerun task: {e}")
        return jsonify({"success": False, "message": str(e)}), 500


@blueprint.route("/dags/<dag_id>/tasks/<task_id>/test", methods=["POST"])
@require_auth
def test_task(dag_id: str, task_id: str):
    """Test run a task (dry run mode)"""
    import subprocess

    try:
        data = request.get_json()
        if not data or not data.get("execution_date"):
            return jsonify({"success": False, "message": "Missing execution_date"}), 400

        execution_date = data["execution_date"]
        cmd = ["airflow", "tasks", "test", dag_id, task_id, execution_date]

        result = subprocess.run(cmd, capture_output=True, text=True, timeout=600)

        return jsonify({
            "success": result.returncode == 0,
            "message": "Task test completed" if result.returncode == 0 else "Task test failed",
            "data": {
                "dag_id": dag_id,
                "task_id": task_id,
                "execution_date": execution_date,
                "output": result.stdout,
                "error": result.stderr if result.returncode != 0 else None
            }
        })

    except Exception as e:
        logger.exception(f"Failed to test task: {e}")
        return jsonify({"success": False, "message": str(e)}), 500
