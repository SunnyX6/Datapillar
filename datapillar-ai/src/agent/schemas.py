# -*- coding: utf-8 -*-
"""
Agent 模块 Schema 定义

包含:
- API 请求/响应模型
- SSE 事件结构
- Agent 输入输出 Schema
"""

from __future__ import annotations

from typing import Any, Dict, Optional, Literal, List
from pydantic import BaseModel, Field, ConfigDict


# ============ API 请求模型 ============

class GenerateWorkflowRequest(BaseModel):
    """生成工作流请求"""
    userInput: Optional[str] = Field(None, description="用户输入")
    sessionId: Optional[str] = Field(None, description="会话ID")
    resumeValue: Optional[Any] = Field(None, description="断点恢复数据")
    projectId: Optional[int] = Field(None, description="项目ID")
    autoLearn: bool = Field(True, description="是否自动学习")


# ============ SSE 事件结构 ============

class AgentResponse(BaseModel):
    """AI 回复结构"""
    tool: Optional[str] = Field(None, description="调用的工具")
    data: Optional[Any] = Field(None, description="输出数据")


class AgentEventPayload(BaseModel):
    """SSE 事件载荷"""
    eventId: str
    title: str
    eventType: Literal[
        "session_started", "session_completed", "session_error",
        "session_interrupted", "agent_thinking", "call_tool", "plan", "code",
    ]
    description: str
    status: Literal["running", "completed", "error", "waiting"]
    is_found: bool
    response: AgentResponse


# ============ ReactFlow 工作流 Schema ============

class NodeData(BaseModel):
    """ReactFlow 节点数据"""
    label: str
    status: str = "idle"
    forbidden: bool = False
    model_config = ConfigDict(extra="allow")


class ReactFlowNode(BaseModel):
    """ReactFlow 节点"""
    id: str
    type: str
    data: NodeData


class ReactFlowEdge(BaseModel):
    """ReactFlow 边"""
    id: str
    source: str
    target: str
    sourceHandle: Optional[str] = "output"
    targetHandle: Optional[str] = "input"


# ============ Agent 输出 Schema ============

class RequirementOutput(BaseModel):
    """需求理解输出"""
    summary: str
    source_tables: List[str]
    target_table: Optional[str] = None
    operation_type: str


class QueryResult(BaseModel):
    """查询结果"""
    summary: str
    is_complete: bool
    data: Any = None


class PlanOutput(BaseModel):
    """执行计划输出"""
    workflowName: str
    description: str
    nodes: List[ReactFlowNode]
    edges: List[ReactFlowEdge]


class WorkflowOutput(BaseModel):
    """工作流输出"""
    workflowName: str
    taskType: str
    description: str
    nodes: List[ReactFlowNode]
    edges: List[ReactFlowEdge]
