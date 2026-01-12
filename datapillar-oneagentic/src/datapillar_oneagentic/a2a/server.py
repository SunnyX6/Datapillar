"""
A2A Server - 统一网关

将 Datapillar 团队暴露为 A2A 协议服务端。

架构：
- 单一入口，路由到任意已注册团队
- 客户端通过 metadata.team_id 指定目标团队
- 支持同步和流式响应

端点：
- GET  /.well-known/agent-card.json  → 返回网关 AgentCard
- POST /a2a/tasks/send               → 同步执行任务
- POST /a2a/tasks/stream             → 流式执行任务

使用示例：
```python
from datapillar_oneagentic.a2a.server import a2a_router

# 在 FastAPI 应用中注册
app.include_router(a2a_router)
```
"""

from __future__ import annotations

import logging
import uuid
from typing import Any

from fastapi import APIRouter, HTTPException, Request
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

from datapillar_oneagentic.a2a.card import AgentCard, AgentSkill
from datapillar_oneagentic.a2a.client import TaskState
from datapillar_oneagentic.core.datapillar import Datapillar

logger = logging.getLogger(__name__)


# ==================== 请求/响应模型 ====================


class A2AMessageRequest(BaseModel):
    """A2A 消息"""

    role: str = Field(default="user", description="角色")
    content: str = Field(..., description="消息内容")


class A2ATaskRequest(BaseModel):
    """A2A 任务请求"""

    message: A2AMessageRequest = Field(..., description="用户消息")
    context_id: str | None = Field(default=None, description="上下文 ID")
    task_id: str | None = Field(default=None, description="任务 ID")
    metadata: dict[str, Any] = Field(default_factory=dict, description="元数据")
    history: list[dict[str, Any]] = Field(default_factory=list, description="对话历史")


class A2ATaskResponse(BaseModel):
    """A2A 任务响应"""

    status: str = Field(..., description="任务状态")
    result: str = Field(default="", description="结果内容")
    error: str | None = Field(default=None, description="错误信息")
    task_id: str | None = Field(default=None, description="任务 ID")
    context_id: str | None = Field(default=None, description="上下文 ID")
    history: list[dict[str, Any]] = Field(default_factory=list, description="对话历史")


# ==================== 路由 ====================


a2a_router = APIRouter(tags=["A2A"])


def _get_gateway_agent_card() -> AgentCard:
    """生成网关的 AgentCard，包含所有已注册团队"""
    skills = []

    for name, team in Datapillar._active_teams.items():
        skills.append(
            AgentSkill(
                id=team.team_id,
                name=name,
                description=f"团队: {name}, 模式: {team.process.value}",
                tags=["datapillar", "team"],
            )
        )

    return AgentCard(
        name="Datapillar A2A Gateway",
        description="Datapillar 智能体团队网关，支持路由到任意已注册团队",
        version="1.0.0",
        skills=skills,
        metadata={
            "streaming": True,
            "multi_turn": True,
            "team_routing": True,
        },
    )


@a2a_router.get("/.well-known/agent-card.json")
async def get_agent_card() -> dict[str, Any]:
    """
    获取网关 AgentCard

    返回所有已注册团队作为 skills。
    """
    card = _get_gateway_agent_card()
    return card.to_dict()


@a2a_router.post("/a2a/tasks/send")
async def send_task(request: A2ATaskRequest) -> A2ATaskResponse:
    """
    同步执行任务

    通过 metadata.team_id 或 metadata.team_name 指定目标团队。
    """
    # 获取目标团队
    team = _resolve_team(request.metadata)
    if not team:
        return A2ATaskResponse(
            status=TaskState.FAILED.value,
            error=_get_team_not_found_error(request.metadata),
        )

    # 生成 ID
    task_id = request.task_id or f"task_{uuid.uuid4().hex[:8]}"
    context_id = request.context_id or f"ctx_{uuid.uuid4().hex[:8]}"

    # 执行任务
    try:
        result = await team.kickoff(
            inputs={"query": request.message.content},
            session_id=context_id,
            user_id="a2a_client",
        )

        if result.success:
            return A2ATaskResponse(
                status=TaskState.COMPLETED.value,
                result=result.summary,
                task_id=task_id,
                context_id=context_id,
            )
        else:
            return A2ATaskResponse(
                status=TaskState.FAILED.value,
                error=result.error,
                task_id=task_id,
                context_id=context_id,
            )

    except Exception as e:
        logger.error(f"A2A 任务执行失败: {e}", exc_info=True)
        return A2ATaskResponse(
            status=TaskState.FAILED.value,
            error=str(e),
            task_id=task_id,
            context_id=context_id,
        )


@a2a_router.post("/a2a/tasks/stream")
async def stream_task(request: A2ATaskRequest) -> StreamingResponse:
    """
    流式执行任务

    通过 metadata.team_id 或 metadata.team_name 指定目标团队。
    返回 SSE 格式的事件流。
    """
    import json

    # 获取目标团队
    team = _resolve_team(request.metadata)
    if not team:
        error_msg = _get_team_not_found_error(request.metadata)

        async def error_stream():
            yield f"data: {json.dumps({'status': 'failed', 'error': error_msg})}\n\n"
            yield "data: [DONE]\n\n"

        return StreamingResponse(
            error_stream(),
            media_type="text/event-stream",
        )

    # 生成 ID
    task_id = request.task_id or f"task_{uuid.uuid4().hex[:8]}"
    context_id = request.context_id or f"ctx_{uuid.uuid4().hex[:8]}"

    async def event_stream():
        try:
            final_result = ""
            final_status = TaskState.WORKING.value

            async for event in team.stream(
                query=request.message.content,
                session_id=context_id,
                user_id="a2a_client",
            ):
                # 转发事件
                event["task_id"] = task_id
                event["context_id"] = context_id
                yield f"data: {json.dumps(event, ensure_ascii=False)}\n\n"

                # 捕获最终结果
                if event.get("event") == "result":
                    final_result = event.get("data", {}).get("message", "")
                    final_status = TaskState.COMPLETED.value
                elif event.get("event") == "error":
                    final_result = event.get("data", {}).get("detail", "")
                    final_status = TaskState.FAILED.value

            # 发送完成事件
            yield f"data: {json.dumps({'status': final_status, 'result': final_result, 'task_id': task_id, 'context_id': context_id}, ensure_ascii=False)}\n\n"
            yield "data: [DONE]\n\n"

        except Exception as e:
            logger.error(f"A2A 流式任务失败: {e}", exc_info=True)
            yield f"data: {json.dumps({'status': 'failed', 'error': str(e)})}\n\n"
            yield "data: [DONE]\n\n"

    return StreamingResponse(
        event_stream(),
        media_type="text/event-stream",
    )


@a2a_router.get("/a2a/teams")
async def list_teams() -> dict[str, Any]:
    """
    列出所有已注册团队

    便捷接口，用于查看可用团队。
    """
    teams = []
    for name, team in Datapillar._active_teams.items():
        teams.append({
            "name": name,
            "team_id": team.team_id,
            "process": team.process.value,
            "agents": team._agent_ids,
            "entry_agent": team._entry_agent_id,
        })

    return {
        "count": len(teams),
        "teams": teams,
    }


# ==================== 辅助函数 ====================


def _resolve_team(metadata: dict[str, Any]) -> Datapillar | None:
    """
    从 metadata 解析目标团队

    支持两种方式：
    - metadata.team_id: 通过 team_id 查找
    - metadata.team_name: 通过团队名称查找
    """
    # 方式 1: team_id
    team_id = metadata.get("team_id")
    if team_id:
        for team in Datapillar._active_teams.values():
            if team.team_id == team_id:
                return team

    # 方式 2: team_name
    team_name = metadata.get("team_name")
    if team_name:
        return Datapillar.get_team(team_name)

    # 方式 3: 如果只有一个团队，默认使用
    if len(Datapillar._active_teams) == 1:
        return next(iter(Datapillar._active_teams.values()))

    return None


def _get_team_not_found_error(metadata: dict[str, Any]) -> str:
    """生成团队未找到的错误信息"""
    team_id = metadata.get("team_id")
    team_name = metadata.get("team_name")
    available = list(Datapillar._active_teams.keys())

    if team_id:
        return f"团队 team_id='{team_id}' 不存在。可用团队: {available}"
    if team_name:
        return f"团队 '{team_name}' 不存在。可用团队: {available}"
    return f"未指定目标团队。请在 metadata 中设置 team_id 或 team_name。可用团队: {available}"
