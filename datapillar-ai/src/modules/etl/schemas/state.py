"""
Agent 状态定义

AgentState 是多智能体系统的全局共享状态，
所有 Agent 通过 State 进行数据传递。
"""

from typing import Any, Annotated, Sequence

from langchain_core.messages import BaseMessage
from langgraph.graph.message import add_messages
from pydantic import BaseModel, Field, field_validator

from src.modules.etl.schemas.kg_context import GlobalKGContext, AgentScopedContext
from src.modules.etl.schemas.plan import TestResult, Workflow
from src.modules.etl.schemas.requirement import AnalysisResult


class AgentState(BaseModel):
    """
    多智能体系统的全局共享状态

    所有 Agent 通过 State 进行数据传递和协作。
    使用 field_validator 自动将 dict 转换为对应的 Pydantic 对象。
    """

    # ==================== 会话信息 ====================
    session_id: str = Field(default="")
    user_id: str = Field(default="")

    # ==================== 对话历史 ====================
    messages: Annotated[Sequence[BaseMessage], add_messages] = Field(default_factory=list)

    # ==================== 用户输入 ====================
    user_input: str = Field(default="")

    # ==================== 知识图谱上下文（KnowledgeAgent 输出）====================
    # 全局知识图谱上下文（所有 Agent 共享的导航信息）
    global_kg_context: GlobalKGContext | None = None

    # Agent 专属上下文（每个 Agent 的指针 + 工具）
    # key: agent_type（analyst/architect/developer/tester）
    agent_contexts: dict[str, AgentScopedContext] = Field(default_factory=dict)

    # ==================== 需求分析（Analyst Agent 输出）====================
    analysis_result: AnalysisResult | None = None

    # ==================== 工作流方案（Architect Agent 输出）====================
    architecture_plan: Workflow | None = None

    # ==================== 测试结果（Tester Agent 输出）====================
    test_result: TestResult | None = None

    # ==================== DAG 输出（Finalize 输出）====================
    # Web-Admin 可渲染的工作流格式
    dag_output: dict[str, Any] | None = None

    # ==================== 执行控制 ====================
    current_agent: str | None = None
    next_agent: str | None = None

    # 迭代计数（用于控制反馈循环次数）
    iteration_count: int = Field(default=0)
    max_iterations: int = Field(default=3)

    # 澄清计数（用于控制澄清循环次数）
    clarification_count: int = Field(default=0)
    max_clarifications: int = Field(default=3)

    # ==================== 状态标记 ====================
    # 是否需要用户澄清
    needs_clarification: bool = Field(default=False)
    clarification_questions: list[str] = Field(default_factory=list)

    # 是否完成
    is_completed: bool = Field(default=False)

    # 错误信息
    error: str | None = None

    # ==================== AI 参考的 SQL ID（用于打分）====================
    # DeveloperAgent 参考的历史 SQL ID 列表，用户满意时给这些 SQL 加分
    referenced_sql_ids: list[str] = Field(default_factory=list)

    # ==================== 元信息 ====================
    metadata: dict[str, Any] = Field(default_factory=dict)

    class Config:
        arbitrary_types_allowed = True

    # ==================== 自动类型转换 ====================

    @field_validator("global_kg_context", mode="before")
    @classmethod
    def convert_global_kg_context(cls, v):
        """自动将 dict 转换为 GlobalKGContext"""
        if v is None:
            return None
        if isinstance(v, dict):
            return GlobalKGContext(**v)
        return v

    @field_validator("agent_contexts", mode="before")
    @classmethod
    def convert_agent_contexts(cls, v):
        """自动将 dict 中的值转换为 AgentScopedContext"""
        if v is None:
            return {}
        if isinstance(v, dict):
            result = {}
            for key, value in v.items():
                if isinstance(value, dict):
                    result[key] = AgentScopedContext(**value)
                else:
                    result[key] = value
            return result
        return v

    @field_validator("analysis_result", mode="before")
    @classmethod
    def convert_analysis_result(cls, v):
        """自动将 dict 转换为 AnalysisResult"""
        if v is None:
            return None
        if isinstance(v, dict):
            return AnalysisResult(**v)
        return v

    @field_validator("architecture_plan", mode="before")
    @classmethod
    def convert_architecture_plan(cls, v):
        """自动将 dict 转换为 Workflow"""
        if v is None:
            return None
        if isinstance(v, dict):
            return Workflow(**v)
        return v

    @field_validator("test_result", mode="before")
    @classmethod
    def convert_test_result(cls, v):
        """自动将 dict 转换为 TestResult"""
        if v is None:
            return None
        if isinstance(v, dict):
            return TestResult(**v)
        return v

    # ==================== 辅助方法 ====================

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

    # ==================== 类型安全的 Getter ====================

    def get_global_kg_context(self) -> GlobalKGContext | None:
        """类型安全地获取 global_kg_context"""
        v = self.global_kg_context
        if v is None:
            return None
        if isinstance(v, dict):
            return GlobalKGContext(**v)
        return v

    def get_agent_context(self, agent_type: str) -> AgentScopedContext | None:
        """获取指定 Agent 的专属上下文"""
        v = self.agent_contexts.get(agent_type)
        if v is None:
            return None
        if isinstance(v, dict):
            return AgentScopedContext(**v)
        return v

    def get_analysis_result(self) -> AnalysisResult | None:
        """类型安全地获取 analysis_result"""
        v = self.analysis_result
        if v is None:
            return None
        if isinstance(v, dict):
            return AnalysisResult(**v)
        return v

    def get_architecture_plan(self) -> Workflow | None:
        """类型安全地获取 architecture_plan"""
        v = self.architecture_plan
        if v is None:
            return None
        if isinstance(v, dict):
            return Workflow(**v)
        return v

    def get_test_result(self) -> TestResult | None:
        """类型安全地获取 test_result"""
        v = self.test_result
        if v is None:
            return None
        if isinstance(v, dict):
            return TestResult(**v)
        return v
