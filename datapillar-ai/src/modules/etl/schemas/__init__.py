"""
ETL 多智能体系统数据结构
"""

from src.modules.etl.agents.knowledge_agent import (
    AGENT_TOOLS_MAP,
    AgentType,
    ETLPointer,
    get_agent_tools,
)
from src.modules.etl.schemas.agent_result import (
    AgentResult,
    AgentResultStatus,
    ClarificationRequest,
    DelegationRequest,
)
from src.modules.etl.schemas.dag import (
    JobDependencyResponse,
    JobResponse,
    WorkflowResponse,
)
from src.modules.etl.schemas.plan import (
    Job,
    Stage,
    TestResult,
    Workflow,
)
from src.modules.etl.schemas.requests import BlackboardRequest
from src.modules.etl.schemas.requirement import (
    Ambiguity,
    AnalysisResult,
    DataTarget,
    Step,
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

__all__ = [
    # Agent Result - Agent 执行结果
    "AgentResult",
    "AgentResultStatus",
    "ClarificationRequest",
    "DelegationRequest",
    # Knowledge Agent - 知识服务
    "AgentType",
    "AGENT_TOOLS_MAP",
    "ETLPointer",
    "get_agent_tools",
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
