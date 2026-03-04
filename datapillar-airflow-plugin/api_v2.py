# -*- coding: utf-8 -*-
"""
Datapillar Airflow Plugin - REST API Extensions for Airflow 2.x

Flask Blueprint version，Compatible Airflow 2.x
"""

from flask import Blueprint, request, jsonify, g
from typing import Optional, Dict, Any, Tuple
import logging

from .dag_generator import DagGenerator
from .config import get_config

logger = logging.getLogger(__name__)
DAG_GENERATOR = DagGenerator()

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


def require_tenant(f):
    """Decorator for tenant header validation."""
    from functools import wraps
    @wraps(f)
    def decorated(*args, **kwargs):
        try:
            g.tenant_code = DAG_GENERATOR.normalize_tenant_code(request.headers.get("X-Tenant-Code"))
        except ValueError as exc:
            return jsonify({"success": False, "message": str(exc)}), 400
        return f(*args, **kwargs)
    return decorated


def ensure_tenant_dag_match(dag_id: str) -> Tuple[Optional[Tuple[Any, int]], Optional[Any]]:
    """Validate dag_id and tenant ownership."""
    try:
        dag_tenant, workflow_id = DAG_GENERATOR.parse_dag_id(dag_id)
    except ValueError as exc:
        return None, (jsonify({"success": False, "message": str(exc)}), 400)

    if dag_tenant != g.tenant_code:
        return None, (jsonify({"success": False, "message": "Tenant is not allowed to access this DAG"}), 403)

    return (dag_tenant, workflow_id), None


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
@require_tenant
def deploy_dag():
    """
    Deploy a new DAG to Airflow

    Request body:
    {
        "workflow_id": 123,
        "dag_id": "dp_tenant_w123",
        "workflow": {
            "workflow_name": "wf_123",
            "jobs": [],
            "dependencies": []
        }
    }
    """
    try:
        data = request.get_json()
        if not data:
            return jsonify({"success": False, "message": "Invalid JSON"}), 400

        workflow_id = data.get("workflow_id")
        workflow = data.get("workflow")
        provided_dag_id = data.get("dag_id")

        if workflow_id is None or not isinstance(workflow, dict):
            return jsonify({"success": False, "message": "Missing workflow_id or workflow"}), 400

        try:
            expected_dag_id = DAG_GENERATOR.build_dag_id(g.tenant_code, workflow_id)
        except ValueError as exc:
            return jsonify({"success": False, "message": str(exc)}), 400

        if isinstance(provided_dag_id, str) and provided_dag_id.strip().lower() != expected_dag_id:
            return jsonify({"success": False, "message": f"dag_id mismatch: expected {expected_dag_id}"}), 400

        dag_path = DAG_GENERATOR.generate(g.tenant_code, int(workflow_id), expected_dag_id, workflow)

        logger.info(f"DAG deployed: {expected_dag_id} at {dag_path}")

        return jsonify({
            "success": True,
            "message": f"DAG '{expected_dag_id}' deployed successfully",
            "data": {"dag_id": expected_dag_id, "path": dag_path}
        })
    except Exception as e:
        logger.exception(f"Failed to deploy DAG: {e}")
        return jsonify({"success": False, "message": str(e)}), 500


@blueprint.route("/dags/<dag_id>", methods=["DELETE"])
@require_auth
@require_tenant
def delete_dag(dag_id: str):
    """Delete a DAG file from Airflow"""
    try:
        identity, error_response = ensure_tenant_dag_match(dag_id)
        if error_response:
            return error_response

        _, workflow_id = identity
        DAG_GENERATOR.delete(g.tenant_code, workflow_id)

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
@require_tenant
def run_task(dag_id: str, run_id: str, task_id: str):
    """Run a single task instance via CLI"""
    import subprocess

    try:
        _, error_response = ensure_tenant_dag_match(dag_id)
        if error_response:
            return error_response

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
@require_tenant
def rerun_task(dag_id: str, run_id: str, task_id: str):
    """Rerun a task by clearing its state"""
    import subprocess

    try:
        _, error_response = ensure_tenant_dag_match(dag_id)
        if error_response:
            return error_response

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
@require_tenant
def test_task(dag_id: str, task_id: str):
    """Test run a task (dry run mode)"""
    import subprocess

    try:
        _, error_response = ensure_tenant_dag_match(dag_id)
        if error_response:
            return error_response

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
