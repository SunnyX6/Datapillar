"""
Agent 状态定义

AgentState 是多智能体系统的全局共享状态，
所有 Agent 通过 State 进行数据传递。
"""

from typing import List, Dict, Any, Optional, Sequence, Annotated, Literal
from pydantic import BaseModel, Field
from langchain_core.messages import BaseMessage
from langgraph.graph.message import add_messages

from src.modules.etl.schemas.kg_context import KnowledgeContext
from src.modules.etl.schemas.requirement import AnalysisResult
from src.modules.etl.schemas.plan import Workflow, ReviewResult, TestResult


class AgentState(BaseModel):
    """
    多智能体系统的全局共享状态

    所有 Agent 通过 State 进行数据传递和协作。
    """

    # ==================== 会话信息 ====================
    session_id: str = Field(default="")
    user_id: str = Field(default="")

    # ==================== 对话历史 ====================
    messages: Annotated[Sequence[BaseMessage], add_messages] = Field(default_factory=list)

    # ==================== 用户输入 ====================
    user_input: str = Field(default="")

    # ==================== 知识上下文（Knowledge Agent 输出）====================
    knowledge_context: Optional[KnowledgeContext] = None

    # ==================== 需求分析（Analyst Agent 输出）====================
    analysis_result: Optional[AnalysisResult] = None

    # ==================== 工作流方案（Architect Agent 输出）====================
    architecture_plan: Optional[Workflow] = None

    # ==================== 评审结果（Reviewer Agent 输出）====================
    review_result: Optional[ReviewResult] = None

    # ==================== 测试结果（Tester Agent 输出）====================
    test_result: Optional[TestResult] = None

    # ==================== DAG 输出（Finalize 输出）====================
    # React Flow 可渲染的 DAG 格式
    dag_output: Optional[Dict[str, Any]] = None

    # ==================== 执行控制 ====================
    current_agent: Optional[str] = None
    next_agent: Optional[str] = None

    # 迭代计数（用于控制反馈循环次数）
    iteration_count: int = Field(default=0)
    max_iterations: int = Field(default=3)

    # ==================== 状态标记 ====================
    # 是否需要用户澄清
    needs_clarification: bool = Field(default=False)
    clarification_questions: List[str] = Field(default_factory=list)

    # 是否完成
    is_completed: bool = Field(default=False)

    # 错误信息
    error: Optional[str] = None

    # ==================== 元信息 ====================
    metadata: Dict[str, Any] = Field(default_factory=dict)

    class Config:
        arbitrary_types_allowed = True

    def can_iterate(self) -> bool:
        """是否可以继续迭代"""
        return self.iteration_count < self.max_iterations

    def increment_iteration(self) -> None:
        """增加迭代计数"""
        self.iteration_count += 1

    def set_error(self, error: str) -> None:
        """设置错误"""
        self.error = error

    def clear_error(self) -> None:
        """清除错误"""
        self.error = None
