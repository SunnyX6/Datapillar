"""
ETL 多智能体系统数据结构
"""

from src.modules.etl.schemas.kg_context import (
    ETLPointer,
    AgentScopedContext,
    AgentType,
    AGENT_TOOLS_MAP,
)
from src.modules.etl.schemas.requirement import (
    AnalysisResult,
    Step,
    DataTarget,
    Ambiguity,
)
from src.modules.etl.schemas.plan import (
    Workflow,
    Job,
    Stage,
    TestResult,
)
from src.modules.etl.schemas.dag import (
    WorkflowResponse,
    JobResponse,
    JobDependencyResponse,
)
from src.modules.etl.schemas.state import AgentState
from src.modules.etl.schemas.requests import BlackboardRequest
from src.modules.etl.schemas.sse_msg import (
    SseAgent,
    SseError,
    SseEvent,
    SseEventType,
    SseInterrupt,
    SseLevel,
    SseLlm,
    SseMessage,
    SseResult,
    SseSpan,
    SseState,
    SseTool,
)

__all__ = [
    # KG Context - 知识图谱上下文
    "AgentScopedContext",
    "AgentType",
    "AGENT_TOOLS_MAP",
    "ETLPointer",
    # Requirement
    "AnalysisResult",
    "Step",
    "Stage",
    "DataTarget",
    "Ambiguity",
    # Plan
    "Workflow",
    "Job",
    "TestResult",
    # DAG / Workflow Response
    "WorkflowResponse",
    "JobResponse",
    "JobDependencyResponse",
    # State
    "AgentState",
    # Blackboard Requests
    "BlackboardRequest",
    # SSE Message - 消息流协议
    "SseEvent",
    "SseEventType",
    "SseState",
    "SseLevel",
    "SseAgent",
    "SseSpan",
    "SseMessage",
    "SseTool",
    "SseLlm",
    "SseInterrupt",
    "SseResult",
    "SseError",
]
