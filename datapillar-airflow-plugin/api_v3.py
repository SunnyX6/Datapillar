# -*- coding: utf-8 -*-
"""
Datapillar Airflow Plugin - Unified REST API for Airflow 3.x

encapsulation Airflow official API,Provide a unified interface to studio-service call.API design:├── DAG management
│ ├── POST /dags - create/deploy DAG
│ ├── DELETE /dags/{id} - Delete DAG File
│ ├── GET /dags - list DAG
│ ├── GET /dags/{id} - DAG Details
│ └── PATCH /dags/{id} - pause/restore DAG
│
├── DAG Version management
│ ├── GET /dags/{id}/versions - list DAG Version history
│ └── GET /dags/{id}/versions/{num} - Get specific version details
│
├── DAG Run management
│ ├── POST /dags/{id}/runs - trigger DAG
│ ├── GET /dags/{id}/runs - list Runs
│ └── GET /dags/{id}/runs/{run_id} - Run Status
│
├── Task management
│ ├── GET /dags/{id}/runs/{run_id}/tasks - task list
│ ├── GET /dags/{id}/runs/{run_id}/tasks/{tid} - Task status
│ ├── PATCH /dags/{id}/runs/{run_id}/tasks/{tid}/state - Set task status (success/failed/skipped)
│ ├── POST /dags/{id}/runs/{run_id}/tasks/{tid}/rerun - Rerun a single task
│ ├── POST /dags/{id}/runs/{run_id}/clear - Batch cleaning tasks
│ └── GET /dags/{id}/runs/{run_id}/tasks/{tid}/logs - Mission log
│
└── Others
    ├── GET /health - health check
    └── POST /dags/reserialize - Force refresh DAG
"""

from fastapi import FastAPI, HTTPException, Query, Depends, Header
from pydantic import BaseModel, Field
from typing import Optional, Dict, Any, List
from datetime import datetime
import logging

from airflow.api_fastapi.core_api.security import GetUserDep

from .dag_generator import DagGenerator

logger = logging.getLogger(__name__)
DAG_GENERATOR = DagGenerator()

# FastAPI App
app = FastAPI(
    title="Datapillar Airflow API",
    description="unify API encapsulation Airflow,Block version differences",
    version="2.0.0"
)


# ==================== Authentication ====================

def require_auth(user: GetUserDep):
    """Verify user is logged in"""
    if not user:
        raise HTTPException(status_code=401, detail="Unauthorized")
    return user


# ==================== Request/Response Models ====================

class JobInfo(BaseModel):
    """task definition - Correspond job_info table"""
    id: int
    job_name: str
    job_type: str  # component_code: SHELL, PYTHON, SQL, HTTP, SPARK
    job_params: Dict[str, Any] = {}
    timeout_seconds: Optional[int] = 0
    max_retry_times: Optional[int] = 0


class JobDependency(BaseModel):
    """Dependencies - Correspond job_dependency table"""
    job_id: int
    parent_job_id: int


class Workflow(BaseModel):
    """Workflow configuration - Correspond job_workflow table"""
    workflow_name: str
    description: Optional[str] = None
    trigger_type: Optional[int] = 1  # 1: cron, 2: manual, etc.
    trigger_value: Optional[str] = None  # ISO Format,If left blank,the current time will be used
    timeout_seconds: Optional[int] = 0
    max_retry_times: Optional[int] = 0
    jobs: List[JobInfo]
    dependencies: List[JobDependency] = []


class DeployDagRequest(BaseModel):
    """Deploy DAG request."""
    workflow_id: int = Field(..., ge=1)
    dag_id: Optional[str] = None
    workflow: Workflow


class TriggerDagRequest(BaseModel):
    """trigger DAG Request"""
    logical_date: Optional[str] = None  # success,failed,skipped
    conf: Optional[Dict[str, Any]] = None


class PatchDagRequest(BaseModel):
    """update DAG Request"""
    is_paused: Optional[bool] = None


class RerunTaskRequest(BaseModel):
    """Request to rerun task"""
    downstream: bool = False
    upstream: bool = False


class SetTaskStateRequest(BaseModel):
    """Set task status request"""
    new_state: str  # success, failed, skipped
    include_upstream: bool = False
    include_downstream: bool = False


class ClearTasksRequest(BaseModel):
    """Clear task request"""
    task_ids: List[str]
    only_failed: bool = True
    reset_dag_runs: bool = True
    include_upstream: bool = False
    include_downstream: bool = False


class ApiResponse(BaseModel):
    """unify API response"""
    success: bool
    message: Optional[str] = None
    data: Optional[Any] = None


def require_tenant_code(x_tenant_code: Optional[str] = Header(default=None, alias="X-Tenant-Code")) -> str:
    """Validate and normalize tenant header."""
    try:
        return DAG_GENERATOR.normalize_tenant_code(x_tenant_code)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


def require_tenant_dag_match(
        dag_id: str,
        tenant_code: str = Depends(require_tenant_code)) -> str:
    """Ensure dag_id belongs to current tenant."""
    try:
        dag_tenant, _ = DAG_GENERATOR.parse_dag_id(dag_id)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    if dag_tenant != tenant_code:
        raise HTTPException(status_code=403, detail="Tenant is not allowed to access this DAG")
    return tenant_code


# ==================== Helper Functions ====================

def get_airflow_session():
    """Get Airflow database session"""
    from airflow.utils.session import create_session
    return create_session()


def serialize_dag(dag_model) -> Dict[str, Any]:
    """serialization DAG model"""
    return {
        "dag_id": dag_model.dag_id,
        "description": dag_model.description,
        "is_paused": dag_model.is_paused,
        "is_stale": dag_model.is_stale,
        "fileloc": dag_model.fileloc,
        "owners": dag_model.owners,
        "tags": [t.name for t in dag_model.tags] if dag_model.tags else [],
        "timetable_summary": dag_model.timetable_summary,
        "last_parsed_time": dag_model.last_parsed_time.isoformat() if dag_model.last_parsed_time else None,
    }


def serialize_dagrun(dagrun) -> Dict[str, Any]:
    """serialization DAG Run"""
    return {
        "run_id": dagrun.run_id,
        "dag_id": dagrun.dag_id,
        "logical_date": dagrun.logical_date.isoformat() if dagrun.logical_date else None,
        "start_date": dagrun.start_date.isoformat() if dagrun.start_date else None,
        "end_date": dagrun.end_date.isoformat() if dagrun.end_date else None,
        "state": str(dagrun.state) if dagrun.state else None,
        "run_type": str(dagrun.run_type) if dagrun.run_type else None,
        "conf": dagrun.conf,
    }


def serialize_task_instance(ti) -> Dict[str, Any]:
    """serialization Task Instance"""
    return {
        "task_id": ti.task_id,
        "dag_id": ti.dag_id,
        "run_id": ti.run_id,
        "state": str(ti.state) if ti.state else None,
        "start_date": ti.start_date.isoformat() if ti.start_date else None,
        "end_date": ti.end_date.isoformat() if ti.end_date else None,
        "duration": ti.duration,
        "try_number": ti.try_number,
        "max_tries": ti.max_tries,
        "operator": ti.operator,
        "pool": ti.pool,
        "queue": ti.queue,
    }


def serialize_dag_version(version) -> Dict[str, Any]:
    """serialization DAG Version"""
    return {
        "id": str(version.id),
        "version_number": version.version_number,
        "dag_id": version.dag_id,
        "bundle_name": version.bundle_name,
        "bundle_version": version.bundle_version,
        "created_at": version.created_at.isoformat() if version.created_at else None,
        "last_updated": version.last_updated.isoformat() if version.last_updated else None,
    }


# ==================== Health Check ====================

@app.get("/health")
async def health_check():
    """health check"""
    return {"status": "ok", "service": "datapillar-airflow-plugin", "api_version": "v2"}


# ==================== DAG Management ====================

@app.post("/dags", response_model=ApiResponse, dependencies=[Depends(require_auth)])
async def deploy_dag(request: DeployDagRequest, tenant_code: str = Depends(require_tenant_code)):
    """
    deploy DAG - create DAG file to Airflow dags Directory
    """
    try:
        dag_id = DAG_GENERATOR.build_dag_id(tenant_code, request.workflow_id)
        if request.dag_id is not None and request.dag_id.strip().lower() != dag_id:
            raise HTTPException(status_code=400, detail=f"dag_id mismatch: expected {dag_id}")
        dag_path = DAG_GENERATOR.generate(tenant_code, request.workflow_id, dag_id, request.workflow.model_dump())

        logger.info(f"DAG deployed: {dag_id} at {dag_path}")

        return ApiResponse(
            success=True,
            message=f"DAG '{dag_id}' deployed successfully",
            data={"dag_id": dag_id, "path": dag_path}
        )
    except Exception as e:
        logger.exception(f"Failed to deploy DAG: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.delete("/dags/{dag_id}", response_model=ApiResponse, dependencies=[Depends(require_auth)])
async def delete_dag(dag_id: str, tenant_code: str = Depends(require_tenant_dag_match)):
    """
    Delete DAG - Delete DAG File
    """
    try:
        _, workflow_id = DAG_GENERATOR.parse_dag_id(dag_id)
        DAG_GENERATOR.delete(tenant_code, workflow_id)

        logger.info(f"DAG file deleted: {dag_id}")

        return ApiResponse(
            success=True,
            message=f"DAG '{dag_id}' file deleted"
        )
    except FileNotFoundError:
        raise HTTPException(status_code=404, detail=f"DAG file not found: {dag_id}")
    except Exception as e:
        logger.exception(f"Failed to delete DAG: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/dags", response_model=ApiResponse, dependencies=[Depends(require_auth)])
async def list_dags(
    limit: int = Query(default=100, ge=1, le=1000),
    offset: int = Query(default=0, ge=0),
    only_active: bool = Query(default=True, description="Show only active DAG(Not stale)"),
    tags: Optional[str] = Query(default=None, description="Comma separated tag filtering"),
    tenant_code: str = Depends(require_tenant_code),
):
    """
    list DAG
    """
    try:
        from airflow.models import DagModel

        with get_airflow_session() as session:
            query = session.query(DagModel).filter(DagModel.dag_id.like(f"dp_{tenant_code}_w%"))

            if only_active:
                # ==================== DAG Version Management ====================
                query = query.filter(DagModel.is_stale == False)

            if tags:
                tag_list = [t.strip() for t in tags.split(",")]
                query = query.filter(DagModel.tags.any(name=tag_list[0]))

            total = query.count()
            dags = query.order_by(DagModel.dag_id).offset(offset).limit(limit).all()

            return ApiResponse(
                success=True,
                data={
                    "total": total,
                    "dags": [serialize_dag(d) for d in dags]
                }
            )
    except Exception as e:
        logger.exception(f"Failed to list DAGs: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/dags/{dag_id}", response_model=ApiResponse, dependencies=[Depends(require_auth)])
async def get_dag(dag_id: str, tenant_code: str = Depends(require_tenant_dag_match)):
    """
    Get DAG Details
    """
    try:
        from airflow.models import DagModel

        with get_airflow_session() as session:
            dag = session.query(DagModel).filter(DagModel.dag_id == dag_id).first()

            if not dag:
                raise HTTPException(status_code=404, detail=f"DAG not found: {dag_id}")

            return ApiResponse(
                success=True,
                data=serialize_dag(dag)
            )
    except HTTPException:
        raise
    except Exception as e:
        logger.exception(f"Failed to get DAG: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.patch("/dags/{dag_id}", response_model=ApiResponse, dependencies=[Depends(require_auth)])
async def patch_dag(dag_id: str, request: PatchDagRequest, tenant_code: str = Depends(require_tenant_dag_match)):
    """
    update DAG - pause/restore
    """
    try:
        from airflow.models import DagModel

        with get_airflow_session() as session:
            dag = session.query(DagModel).filter(DagModel.dag_id == dag_id).first()

            if not dag:
                raise HTTPException(status_code=404, detail=f"DAG not found: {dag_id}")

            if request.is_paused is not None:
                dag.is_paused = request.is_paused

            session.commit()

            return ApiResponse(
                success=True,
                message=f"DAG '{dag_id}' updated",
                data=serialize_dag(dag)
            )
    except HTTPException:
        raise
    except Exception as e:
        logger.exception(f"Failed to update DAG: {e}")
        raise HTTPException(status_code=500, detail=str(e))


# ==================== DAG Version Management ====================

@app.get("/dags/{dag_id}/versions", response_model=ApiResponse, dependencies=[Depends(require_auth)])
async def list_dag_versions(
    dag_id: str,
    limit: int = Query(default=25, ge=1, le=100),
    offset: int = Query(default=0, ge=0),
    tenant_code: str = Depends(require_tenant_dag_match),
):
    """
    list DAG Version history
    """
    try:
        from airflow.models.dag_version import DagVersion

        with get_airflow_session() as session:
            query = session.query(DagVersion).filter(DagVersion.dag_id == dag_id)

            total = query.count()
            versions = query.order_by(DagVersion.version_number.desc()).offset(offset).limit(limit).all()

            return ApiResponse(
                success=True,
                data={
                    "total": total,
                    "versions": [serialize_dag_version(v) for v in versions]
                }
            )
    except Exception as e:
        logger.exception(f"Failed to list DAG versions: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/dags/{dag_id}/versions/{version_number}", response_model=ApiResponse, dependencies=[Depends(require_auth)])
async def get_dag_version(dag_id: str, version_number: int, tenant_code: str = Depends(require_tenant_dag_match)):
    """
    Get DAG Specific version details

    Returns basic version information and the DAG structure(tasks and dependencies)
    """
    try:
        from airflow.models.dag_version import DagVersion
        from sqlalchemy.orm import joinedload

        with get_airflow_session() as session:
            version = session.query(DagVersion).options(
                joinedload(DagVersion.serialized_dag)
            ).filter(
                DagVersion.dag_id == dag_id,
                DagVersion.version_number == version_number
            ).first()

            if not version:
                raise HTTPException(
                    status_code=404,
                    detail=f"DAG version not found: {dag_id} v{version_number}"
                )

            # add DAG structural information(If there is serialization DAG)
            result = serialize_dag_version(version)

            # Extract task and dependency information
            if version.serialized_dag:
                try:
                    dag_data = version.serialized_dag.data
                    if dag_data:
                        # Airflow 3.x:data in __var within
                        tasks = []
                        raw_tasks = dag_data.get("dag", {}).get("tasks", [])
                        for task in raw_tasks:
                            # ==================== DAG Run Management ====================
                            task_var = task.get("__var", task)
                            tasks.append({
                                "task_id": task_var.get("task_id"),
                                "operator": task_var.get("task_type") or task_var.get("_task_type"),
                                "downstream_task_ids": task_var.get("downstream_task_ids", []),
                            })
                        result["tasks"] = tasks
                        result["task_count"] = len(tasks)
                except Exception as parse_error:
                    logger.warning(f"Failed to parse serialized DAG: {parse_error}")
                    result["tasks"] = []
                    result["task_count"] = 0

            return ApiResponse(
                success=True,
                data=result
            )
    except HTTPException:
        raise
    except Exception as e:
        logger.exception(f"Failed to get DAG version: {e}")
        raise HTTPException(status_code=500, detail=str(e))


# ==================== DAG Run Management ====================

@app.post("/dags/{dag_id}/runs", response_model=ApiResponse, dependencies=[Depends(require_auth)])
async def trigger_dag(
    dag_id: str,
    request: Optional[TriggerDagRequest] = None,
    tenant_code: str = Depends(require_tenant_dag_match),
):
    """
    trigger DAG Run
    """
    try:
        from airflow.models import DagModel, DagRun
        from airflow.utils.state import DagRunState
        from airflow.utils.types import DagRunType

        with get_airflow_session() as session:
            dag = session.query(DagModel).filter(DagModel.dag_id == dag_id).first()

            if not dag:
                raise HTTPException(status_code=404, detail=f"DAG not found: {dag_id}")

            # generate run_id
            if request and request.logical_date:
                logical_date = datetime.fromisoformat(request.logical_date.replace('Z', '+00:00'))
            else:
                from datetime import timezone
                logical_date = datetime.now(timezone.utc)

            # Check if it already exists
            run_id = f"manual__{logical_date.isoformat()}"

            # create DAG Run
            existing = session.query(DagRun).filter(
                DagRun.dag_id == dag_id,
                DagRun.run_id == run_id
            ).first()

            if existing:
                raise HTTPException(
                    status_code=409,
                    detail=f"DAG Run already exists: {run_id}"
                )

            # ==================== Task Management ====================
            dag_run = DagRun(
                dag_id=dag_id,
                run_id=run_id,
                logical_date=logical_date,
                run_type=DagRunType.MANUAL,
                state=DagRunState.QUEUED,
                conf=request.conf if request else None,
            )

            session.add(dag_run)
            session.commit()
            session.refresh(dag_run)

            logger.info(f"DAG Run triggered: {dag_id}/{run_id}")

            return ApiResponse(
                success=True,
                message=f"DAG '{dag_id}' triggered",
                data=serialize_dagrun(dag_run)
            )
    except HTTPException:
        raise
    except Exception as e:
        logger.exception(f"Failed to trigger DAG: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/dags/{dag_id}/runs", response_model=ApiResponse, dependencies=[Depends(require_auth)])
async def list_dag_runs(
    dag_id: str,
    limit: int = Query(default=25, ge=1, le=100),
    offset: int = Query(default=0, ge=0),
    state: Optional[str] = Query(default=None, description="filter status:queued,running,success,failed"),
    tenant_code: str = Depends(require_tenant_dag_match),
):
    """
    list DAG Runs
    """
    try:
        from airflow.models import DagRun

        with get_airflow_session() as session:
            query = session.query(DagRun).filter(DagRun.dag_id == dag_id)

            if state:
                query = query.filter(DagRun.state == state)

            total = query.count()
            runs = query.order_by(DagRun.logical_date.desc()).offset(offset).limit(limit).all()

            return ApiResponse(
                success=True,
                data={
                    "total": total,
                    "runs": [serialize_dagrun(r) for r in runs]
                }
            )
    except Exception as e:
        logger.exception(f"Failed to list DAG runs: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/dags/{dag_id}/runs/{run_id}", response_model=ApiResponse, dependencies=[Depends(require_auth)])
async def get_dag_run(dag_id: str, run_id: str, tenant_code: str = Depends(require_tenant_dag_match)):
    """
    Get DAG Run Details
    """
    try:
        from airflow.models import DagRun

        with get_airflow_session() as session:
            dag_run = session.query(DagRun).filter(
                DagRun.dag_id == dag_id,
                DagRun.run_id == run_id
            ).first()

            if not dag_run:
                raise HTTPException(status_code=404, detail=f"DAG Run not found: {dag_id}/{run_id}")

            return ApiResponse(
                success=True,
                data=serialize_dagrun(dag_run)
            )
    except HTTPException:
        raise
    except Exception as e:
        logger.exception(f"Failed to get DAG run: {e}")
        raise HTTPException(status_code=500, detail=str(e))


# ==================== Task Management ====================

@app.get("/dags/{dag_id}/runs/{run_id}/tasks", response_model=ApiResponse, dependencies=[Depends(require_auth)])
async def list_tasks(dag_id: str, run_id: str, tenant_code: str = Depends(require_tenant_dag_match)):
    """
    List task instances
    """
    try:
        from airflow.models import TaskInstance

        with get_airflow_session() as session:
            tasks = session.query(TaskInstance).filter(
                TaskInstance.dag_id == dag_id,
                TaskInstance.run_id == run_id
            ).order_by(TaskInstance.task_id).all()

            return ApiResponse(
                success=True,
                data={
                    "total": len(tasks),
                    "tasks": [serialize_task_instance(t) for t in tasks]
                }
            )
    except Exception as e:
        logger.exception(f"Failed to list tasks: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/dags/{dag_id}/runs/{run_id}/tasks/{task_id}", response_model=ApiResponse, dependencies=[Depends(require_auth)])
async def get_task(dag_id: str, run_id: str, task_id: str, tenant_code: str = Depends(require_tenant_dag_match)):
    """
    Get task details
    """
    try:
        from airflow.models import TaskInstance

        with get_airflow_session() as session:
            task = session.query(TaskInstance).filter(
                TaskInstance.dag_id == dag_id,
                TaskInstance.run_id == run_id,
                TaskInstance.task_id == task_id
            ).first()

            if not task:
                raise HTTPException(status_code=404, detail=f"Task not found: {dag_id}/{run_id}/{task_id}")

            return ApiResponse(
                success=True,
                data=serialize_task_instance(task)
            )
    except HTTPException:
        raise
    except Exception as e:
        logger.exception(f"Failed to get task: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/dags/{dag_id}/runs/{run_id}/tasks/{task_id}/rerun", response_model=ApiResponse, dependencies=[Depends(require_auth)])
async def rerun_task(
    dag_id: str,
    run_id: str,
    task_id: str,
    request: Optional[RerunTaskRequest] = None,
    tenant_code: str = Depends(require_tenant_dag_match),
):
    """
    rerun mission - Clear task status scheduler Rescheduling
    """
    try:
        from airflow.models import TaskInstance, DagRun
        from airflow.utils.state import DagRunState

        with get_airflow_session() as session:
            # Clear task status
            task = session.query(TaskInstance).filter(
                TaskInstance.dag_id == dag_id,
                TaskInstance.run_id == run_id,
                TaskInstance.task_id == task_id
            ).first()

            if not task:
                raise HTTPException(status_code=404, detail=f"Task not found: {dag_id}/{run_id}/{task_id}")

            # update DAG Run The status is queued,let scheduler Rescheduling
            task.state = None
            task.start_date = None
            task.end_date = None
            task.duration = None

            cleared_tasks = [task_id]

            # use DBDagBag Get SerializedDAG
            dag_run = session.query(DagRun).filter(
                DagRun.dag_id == dag_id,
                DagRun.run_id == run_id
            ).first()

            if dag_run:
                dag_run.state = DagRunState.QUEUED

            session.commit()

            logger.info(f"Task cleared for rerun: {dag_id}/{run_id}/{task_id}")

            return ApiResponse(
                success=True,
                message=f"Task '{task_id}' scheduled for re-execution",
                data={
                    "dag_id": dag_id,
                    "run_id": run_id,
                    "task_id": task_id,
                    "cleared_tasks": cleared_tasks
                }
            )
    except HTTPException:
        raise
    except Exception as e:
        logger.exception(f"Failed to rerun task: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.patch("/dags/{dag_id}/runs/{run_id}/tasks/{task_id}/state", response_model=ApiResponse, dependencies=[Depends(require_auth)])
async def set_task_state(
    dag_id: str,
    run_id: str,
    task_id: str,
    request: SetTaskStateRequest,
    tenant_code: str = Depends(require_tenant_dag_match),
):
    """
    Set task status - Mark the task directly as success/failed/skipped

    Airflow 3.x Pass PATCH API Set task status,rather than performing tasks."""
    try:
        from airflow.models.dagbag import DBDagBag
        from airflow.utils.state import TaskInstanceState

        valid_states = ["success", "failed", "skipped"]
        new_state = request.new_state.lower()
        if new_state not in valid_states:
            raise HTTPException(
                status_code=400,
                detail=f"Invalid state: {new_state}. Must be one of {valid_states}"
            )

        state_map = {
            "success": TaskInstanceState.SUCCESS,
            "failed": TaskInstanceState.FAILED,
            "skipped": TaskInstanceState.SKIPPED,
        }

        with get_airflow_session() as session:
            # use SerializedDAG of set_task_instance_state method
            dag_bag = DBDagBag()
            dag = dag_bag.get_latest_version_of_dag(dag_id, session=session)

            if not dag:
                raise HTTPException(status_code=404, detail=f"DAG not found: {dag_id}")

            # use DBDagBag Get SerializedDAG
            affected_tis = dag.set_task_instance_state(
                task_id=task_id,
                run_id=run_id,
                state=state_map[new_state],
                upstream=request.include_upstream,
                downstream=request.include_downstream,
                commit=True,
                session=session,
            )

            affected_count = len(affected_tis) if affected_tis else 0
            logger.info(f"Task state set: {dag_id}/{run_id}/{task_id} -> {new_state}, affected {affected_count} tasks")

            return ApiResponse(
                success=True,
                message=f"Task '{task_id}' state set to '{new_state}'",
                data={
                    "dag_id": dag_id,
                    "run_id": run_id,
                    "task_id": task_id,
                    "new_state": new_state,
                    "affected_tasks": affected_count
                }
            )
    except HTTPException:
        raise
    except Exception as e:
        logger.exception(f"Failed to set task state: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/dags/{dag_id}/runs/{run_id}/clear", response_model=ApiResponse, dependencies=[Depends(require_auth)])
async def clear_tasks(
    dag_id: str,
    run_id: str,
    request: ClearTasksRequest,
    tenant_code: str = Depends(require_tenant_dag_match),
):
    """
    Batch cleaning tasks - let scheduler Rescheduling

    Equivalent to Airflow UI of "Clear" Operation."""
    try:
        from airflow.models.dagbag import DBDagBag
        from airflow.models.taskinstance import clear_task_instances
        from airflow.utils.state import DagRunState

        with get_airflow_session() as session:
            # use DAG of clear method(dry_run=True Get task list)
            dag_bag = DBDagBag()
            dag = dag_bag.get_latest_version_of_dag(dag_id, session=session)

            if not dag:
                raise HTTPException(status_code=404, detail=f"DAG not found: {dag_id}")

            # actual clearing
            task_instances = dag.clear(
                run_id=run_id,
                task_ids=request.task_ids,
                only_failed=request.only_failed,
                dry_run=True,
                session=session,
            )

            if not task_instances:
                return ApiResponse(
                    success=True,
                    message="No task instances to clear",
                    data={"cleared_count": 0}
                )

            # ==================== DAG Reserialize ====================
            clear_task_instances(
                task_instances,
                session,
                DagRunState.QUEUED if request.reset_dag_runs else False,
            )

            cleared_count = len(task_instances)
            cleared_task_ids = list(set(ti.task_id for ti in task_instances))

            logger.info(f"Tasks cleared: {dag_id}/{run_id}, count={cleared_count}")

            return ApiResponse(
                success=True,
                message=f"Cleared {cleared_count} task instance(s)",
                data={
                    "dag_id": dag_id,
                    "run_id": run_id,
                    "cleared_count": cleared_count,
                    "cleared_task_ids": cleared_task_ids
                }
            )
    except HTTPException:
        raise
    except Exception as e:
        logger.exception(f"Failed to clear tasks: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/dags/{dag_id}/runs/{run_id}/tasks/{task_id}/logs", response_model=ApiResponse, dependencies=[Depends(require_auth)])
async def get_task_logs(
    dag_id: str,
    run_id: str,
    task_id: str,
    try_number: int = Query(default=-1, description="number of attempts,-1 Indicates the latest"),
    tenant_code: str = Depends(require_tenant_dag_match),
):
    """
    Get task log
    """
    try:
        from airflow.models import TaskInstance
        from airflow.utils.log.log_reader import TaskLogReader

        with get_airflow_session() as session:
            task = session.query(TaskInstance).filter(
                TaskInstance.dag_id == dag_id,
                TaskInstance.run_id == run_id,
                TaskInstance.task_id == task_id
            ).first()

            if not task:
                raise HTTPException(status_code=404, detail=f"Task not found: {dag_id}/{run_id}/{task_id}")

            actual_try = try_number if try_number > 0 else task.try_number

            try:
                log_reader = TaskLogReader()
                logs, metadata = log_reader.read_log_chunks(task, actual_try, metadata={})

                log_content = ""
                for log_chunk in logs:
                    if isinstance(log_chunk, tuple):
                        log_content += log_chunk[1]
                    else:
                        log_content += str(log_chunk)

                return ApiResponse(
                    success=True,
                    data={
                        "dag_id": dag_id,
                        "run_id": run_id,
                        "task_id": task_id,
                        "try_number": actual_try,
                        "logs": log_content
                    }
                )
            except Exception as log_error:
                logger.warning(f"Failed to read logs: {log_error}")
                return ApiResponse(
                    success=True,
                    data={
                        "dag_id": dag_id,
                        "run_id": run_id,
                        "task_id": task_id,
                        "try_number": actual_try,
                        "logs": f"Log not available: {str(log_error)}"
                    }
                )
    except HTTPException:
        raise
    except Exception as e:
        logger.exception(f"Failed to get task logs: {e}")
        raise HTTPException(status_code=500, detail=str(e))


# ==================== DAG Reserialize ====================

@app.post("/dags/{dag_id}/reserialize", response_model=ApiResponse, dependencies=[Depends(require_auth)])
async def reserialize_dag(dag_id: str, tenant_code: str = Depends(require_tenant_dag_match)):
    """
    Force refresh specified DAG - Pass DagPriorityParsingRequest Notification DAG processor Reparse now
    """
    try:
        from airflow.models import DagModel
        from airflow.models.dagbag import DagPriorityParsingRequest

        with get_airflow_session() as session:
            dag = session.query(DagModel).filter(DagModel.dag_id == dag_id).first()

            if not dag:
                raise HTTPException(status_code=404, detail=f"DAG not found: {dag_id}")

            # Delete existing requests first,Insert new one
            import hashlib
            request_id = hashlib.md5(f"{dag.bundle_name}:{dag.relative_fileloc}".encode()).hexdigest()

            # Create a priority resolution request,DAG processor will be processed immediately
            session.query(DagPriorityParsingRequest).filter(
                DagPriorityParsingRequest.id == request_id
            ).delete()

            # Create a priority parsing request; DAG processor will handle it immediately
            parsing_request = DagPriorityParsingRequest(
                bundle_name=dag.bundle_name,
                relative_fileloc=dag.relative_fileloc
            )
            session.add(parsing_request)
            session.commit()

            logger.info(f"DAG reparse requested: {dag_id}, bundle={dag.bundle_name}, file={dag.relative_fileloc}")

            return ApiResponse(
                success=True,
                message=f"DAG '{dag_id}' reparse requested",
                data={
                    "dag_id": dag_id,
                    "bundle_name": dag.bundle_name,
                    "relative_fileloc": dag.relative_fileloc
                }
            )
    except HTTPException:
        raise
    except Exception as e:
        logger.exception(f"Failed to reserialize DAG: {e}")
        raise HTTPException(status_code=500, detail=str(e))
