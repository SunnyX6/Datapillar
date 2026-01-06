"""
ETL 多智能体系统数据结构
"""

from src.modules.etl.schemas.agent_result import (
    AgentResult,
    AgentResultStatus,
    ClarificationRequest,
    DelegationRequest,
)
from src.modules.etl.schemas.analyst import (
    AnalysisResult,
    DataTarget,
    Step,
)
from src.modules.etl.schemas.dag import (
    JobDependencyResponse,
    JobResponse,
    WorkflowResponse,
)
from src.modules.etl.schemas.requests import BlackboardRequest
from src.modules.etl.schemas.review import (
    ReviewResult,
)
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
from src.modules.etl.schemas.workflow import (
    Job,
    Stage,
    Workflow,
)

__all__ = [
    # Agent Result - Agent 执行结果
    "AgentResult",
    "AgentResultStatus",
    "ClarificationRequest",
    "DelegationRequest",
    # Requirement - 需求分析
    "AnalysisResult",
    "Step",
    "DataTarget",
    # Workflow - 工作流
    "Workflow",
    "Job",
    "Stage",
    # Review - 评审
    "ReviewResult",
    # DAG / Workflow Response
    "WorkflowResponse",
    "JobResponse",
    "JobDependencyResponse",
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
