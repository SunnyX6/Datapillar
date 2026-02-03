# -*- coding: utf-8 -*-
"""
Datapillar Airflow Plugin - Unified REST API for Airflow 3.x

封装 Airflow 官方 API，提供统一的接口给 workbench-service 调用。

API 设计：
├── DAG 管理
│   ├── POST   /dags              - 创建/部署 DAG
│   ├── DELETE /dags/{id}         - 删除 DAG 文件
│   ├── GET    /dags              - 列出 DAG
│   ├── GET    /dags/{id}         - DAG 详情
│   └── PATCH  /dags/{id}         - 暂停/恢复 DAG
│
├── DAG 版本管理
│   ├── GET    /dags/{id}/versions           - 列出 DAG 版本历史
│   └── GET    /dags/{id}/versions/{num}     - 获取特定版本详情
│
├── DAG Run 管理
│   ├── POST   /dags/{id}/runs           - 触发 DAG
│   ├── GET    /dags/{id}/runs           - 列出 Runs
│   └── GET    /dags/{id}/runs/{run_id}  - Run 状态
│
├── Task 管理
│   ├── GET    /dags/{id}/runs/{run_id}/tasks         - 任务列表
│   ├── GET    /dags/{id}/runs/{run_id}/tasks/{tid}   - 任务状态
│   ├── PATCH  /dags/{id}/runs/{run_id}/tasks/{tid}/state  - 设置任务状态 (success/failed/skipped)
│   ├── POST   /dags/{id}/runs/{run_id}/tasks/{tid}/rerun  - 重跑单个任务
│   ├── POST   /dags/{id}/runs/{run_id}/clear              - 批量清除任务
│   └── GET    /dags/{id}/runs/{run_id}/tasks/{tid}/logs   - 任务日志
│
└── 其他
    ├── GET    /health              - 健康检查
    └── POST   /dags/reserialize    - 强制刷新 DAG
"""

from fastapi import FastAPI, HTTPException, Query, Depends
from pydantic import BaseModel
from typing import Optional, Dict, Any, List
from datetime import datetime
import logging

from airflow.api_fastapi.core_api.security import GetUserDep

from .dag_generator import DagGenerator
from .config import get_config

logger = logging.getLogger(__name__)

# FastAPI App
app = FastAPI(
    title="Datapillar Airflow API",
    description="统一 API 封装 Airflow，屏蔽版本差异",
    version="2.0.0"
)


# ==================== Authentication ====================

def require_auth(user: GetUserDep):
    """验证用户已登录"""
    if not user:
        raise HTTPException(status_code=401, detail="Unauthorized")
    return user


# ==================== Request/Response Models ====================

class JobInfo(BaseModel):
    """任务定义 - 对应 job_info 表"""
    id: int
    job_name: str
    job_type: str  # component_code: SHELL, PYTHON, SQL, HTTP, SPARK
    job_params: Dict[str, Any] = {}
    timeout_seconds: Optional[int] = 0
    max_retry_times: Optional[int] = 0


class JobDependency(BaseModel):
    """依赖关系 - 对应 job_dependency 表"""
    job_id: int
    parent_job_id: int


class Workflow(BaseModel):
    """工作流配置 - 对应 job_workflow 表"""
    workflow_name: str
    description: Optional[str] = None
    trigger_type: Optional[int] = 1  # 1: cron, 2: manual, etc.
    trigger_value: Optional[str] = None  # cron 表达式
    timeout_seconds: Optional[int] = 0
    max_retry_times: Optional[int] = 0
    jobs: List[JobInfo]
    dependencies: List[JobDependency] = []


class DeployDagRequest(BaseModel):
    """部署 DAG 请求"""
    namespace: Optional[str] = None
    workflow: Workflow


class TriggerDagRequest(BaseModel):
    """触发 DAG 请求"""
    logical_date: Optional[str] = None  # ISO 格式，不填则使用当前时间
    conf: Optional[Dict[str, Any]] = None


class PatchDagRequest(BaseModel):
    """更新 DAG 请求"""
    is_paused: Optional[bool] = None


class RerunTaskRequest(BaseModel):
    """重跑任务请求"""
    downstream: bool = False
    upstream: bool = False


class SetTaskStateRequest(BaseModel):
    """设置任务状态请求"""
    new_state: str  # success, failed, skipped
    include_upstream: bool = False
    include_downstream: bool = False


class ClearTasksRequest(BaseModel):
    """清除任务请求"""
    task_ids: List[str]
    only_failed: bool = True
    reset_dag_runs: bool = True
    include_upstream: bool = False
    include_downstream: bool = False


class ApiResponse(BaseModel):
    """统一 API 响应"""
    success: bool
    message: Optional[str] = None
    data: Optional[Any] = None


# ==================== Helper Functions ====================

def get_airflow_session():
    """获取 Airflow 数据库 session"""
    from airflow.utils.session import create_session
    return create_session()


def serialize_dag(dag_model) -> Dict[str, Any]:
    """序列化 DAG 模型"""
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
    """序列化 DAG Run"""
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
    """序列化 Task Instance"""
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
    """序列化 DAG Version"""
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
    """健康检查"""
    return {"status": "ok", "service": "datapillar-airflow-plugin", "api_version": "v2"}


# ==================== DAG Management ====================

@app.post("/dags", response_model=ApiResponse, dependencies=[Depends(require_auth)])
async def deploy_dag(request: DeployDagRequest):
    """
    部署 DAG - 创建 DAG 文件到 Airflow dags 目录
    """
    try:
        generator = DagGenerator()
        dag_id = request.workflow.workflow_name
        dag_path = generator.generate(dag_id, request.workflow.model_dump())

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
async def delete_dag(dag_id: str):
    """
    删除 DAG - 删除 DAG 文件
    """
    try:
        generator = DagGenerator()
        generator.delete(dag_id)

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
    only_active: bool = Query(default=True, description="只显示活跃的 DAG（非 stale）"),
    tags: Optional[str] = Query(default=None, description="逗号分隔的标签过滤")
):
    """
    列出 DAG
    """
    try:
        from airflow.models import DagModel

        with get_airflow_session() as session:
            query = session.query(DagModel)

            if only_active:
                # Airflow 3.x: is_stale=False 表示活跃
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
async def get_dag(dag_id: str):
    """
    获取 DAG 详情
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
async def patch_dag(dag_id: str, request: PatchDagRequest):
    """
    更新 DAG - 暂停/恢复
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
    offset: int = Query(default=0, ge=0)
):
    """
    列出 DAG 版本历史
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
async def get_dag_version(dag_id: str, version_number: int):
    """
    获取 DAG 特定版本详情

    返回版本基本信息以及该版本的 DAG 结构（tasks 和 dependencies）
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

            # 基本版本信息
            result = serialize_dag_version(version)

            # 添加 DAG 结构信息（如果有序列化的 DAG）
            if version.serialized_dag:
                try:
                    dag_data = version.serialized_dag.data
                    if dag_data:
                        # 提取任务和依赖信息
                        tasks = []
                        raw_tasks = dag_data.get("dag", {}).get("tasks", [])
                        for task in raw_tasks:
                            # Airflow 3.x: 数据在 __var 内
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
async def trigger_dag(dag_id: str, request: Optional[TriggerDagRequest] = None):
    """
    触发 DAG Run
    """
    try:
        from airflow.models import DagModel, DagRun
        from airflow.utils.state import DagRunState
        from airflow.utils.types import DagRunType

        with get_airflow_session() as session:
            dag = session.query(DagModel).filter(DagModel.dag_id == dag_id).first()

            if not dag:
                raise HTTPException(status_code=404, detail=f"DAG not found: {dag_id}")

            # 解析 logical_date
            if request and request.logical_date:
                logical_date = datetime.fromisoformat(request.logical_date.replace('Z', '+00:00'))
            else:
                from datetime import timezone
                logical_date = datetime.now(timezone.utc)

            # 生成 run_id
            run_id = f"manual__{logical_date.isoformat()}"

            # 检查是否已存在
            existing = session.query(DagRun).filter(
                DagRun.dag_id == dag_id,
                DagRun.run_id == run_id
            ).first()

            if existing:
                raise HTTPException(
                    status_code=409,
                    detail=f"DAG Run already exists: {run_id}"
                )

            # 创建 DAG Run
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
    state: Optional[str] = Query(default=None, description="过滤状态: queued, running, success, failed")
):
    """
    列出 DAG Runs
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
async def get_dag_run(dag_id: str, run_id: str):
    """
    获取 DAG Run 详情
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
async def list_tasks(dag_id: str, run_id: str):
    """
    列出任务实例
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
async def get_task(dag_id: str, run_id: str, task_id: str):
    """
    获取任务详情
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
async def rerun_task(dag_id: str, run_id: str, task_id: str, request: Optional[RerunTaskRequest] = None):
    """
    重跑任务 - 清除任务状态让 scheduler 重新调度
    """
    try:
        from airflow.models import TaskInstance, DagRun
        from airflow.utils.state import DagRunState

        with get_airflow_session() as session:
            # 查找任务
            task = session.query(TaskInstance).filter(
                TaskInstance.dag_id == dag_id,
                TaskInstance.run_id == run_id,
                TaskInstance.task_id == task_id
            ).first()

            if not task:
                raise HTTPException(status_code=404, detail=f"Task not found: {dag_id}/{run_id}/{task_id}")

            # 清除任务状态
            task.state = None
            task.start_date = None
            task.end_date = None
            task.duration = None

            cleared_tasks = [task_id]

            # 更新 DAG Run 状态为 queued，让 scheduler 重新调度
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
async def set_task_state(dag_id: str, run_id: str, task_id: str, request: SetTaskStateRequest):
    """
    设置任务状态 - 直接将任务标记为 success/failed/skipped

    Airflow 3.x 通过 PATCH API 设置任务状态，而不是执行任务。
    """
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
            # 使用 DBDagBag 获取 SerializedDAG
            dag_bag = DBDagBag()
            dag = dag_bag.get_latest_version_of_dag(dag_id, session=session)

            if not dag:
                raise HTTPException(status_code=404, detail=f"DAG not found: {dag_id}")

            # 使用 SerializedDAG 的 set_task_instance_state 方法
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
async def clear_tasks(dag_id: str, run_id: str, request: ClearTasksRequest):
    """
    批量清除任务 - 让 scheduler 重新调度

    相当于 Airflow UI 的 "Clear" 操作。
    """
    try:
        from airflow.models.dagbag import DBDagBag
        from airflow.models.taskinstance import clear_task_instances
        from airflow.utils.state import DagRunState

        with get_airflow_session() as session:
            # 使用 DBDagBag 获取 SerializedDAG
            dag_bag = DBDagBag()
            dag = dag_bag.get_latest_version_of_dag(dag_id, session=session)

            if not dag:
                raise HTTPException(status_code=404, detail=f"DAG not found: {dag_id}")

            # 使用 DAG 的 clear 方法（dry_run=True 获取任务列表）
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

            # 实际清除
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
    try_number: int = Query(default=-1, description="尝试次数，-1 表示最新")
):
    """
    获取任务日志
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
async def reserialize_dag(dag_id: str):
    """
    强制刷新指定 DAG - 通过 DagPriorityParsingRequest 通知 DAG processor 立即重新解析
    """
    try:
        from airflow.models import DagModel
        from airflow.models.dagbag import DagPriorityParsingRequest

        with get_airflow_session() as session:
            dag = session.query(DagModel).filter(DagModel.dag_id == dag_id).first()

            if not dag:
                raise HTTPException(status_code=404, detail=f"DAG not found: {dag_id}")

            # 计算 id（与 Airflow 逻辑一致）
            import hashlib
            request_id = hashlib.md5(f"{dag.bundle_name}:{dag.relative_fileloc}".encode()).hexdigest()

            # 先删除已存在的请求，再插入新的
            session.query(DagPriorityParsingRequest).filter(
                DagPriorityParsingRequest.id == request_id
            ).delete()

            # 创建优先解析请求，DAG processor 会立即处理
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
