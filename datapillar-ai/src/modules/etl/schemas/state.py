"""
Agent 状态定义

AgentState 是多智能体系统的全局共享状态，
所有 Agent 通过 State 进行数据传递。
"""

from typing import Any, Annotated, Sequence

from langchain_core.messages import BaseMessage
from langgraph.graph.message import add_messages
from pydantic import BaseModel, Field, field_validator

from src.modules.etl.schemas.kg_context import AgentScopedContext
from src.modules.etl.schemas.plan import TestResult, Workflow
from src.modules.etl.schemas.requests import BlackboardRequest
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

    # ==================== 知识上下文（KnowledgeAgent 输出）====================
    # Agent 专属上下文（每个 Agent 的指针 + 工具 allowlist）
    # key: agent_type（analyst/architect/developer/tester）
    agent_contexts: dict[str, AgentScopedContext] = Field(default_factory=dict)

    # ==================== 黑板请求（协作与人机交互）====================
    pending_requests: list[BlackboardRequest] = Field(default_factory=list)

    # ==================== 人机交互结果（控制面）====================
    # 原始用户回复（按 request_id 聚合，便于审计与复盘）
    human_responses: dict[str, Any] = Field(default_factory=dict)
    # writeback_key 对应的结构化写回（用于 UI 控件选择等；避免污染 metadata）
    human_writebacks: dict[str, Any] = Field(default_factory=dict)

    # ==================== 关键决策（业务主线）====================
    # 架构师需要的组件选择（来自 human_in_the_loop 的 writeback）
    selected_component: str | None = None
    selected_component_id: int | None = None

    # ==================== 委派与请求审计（控制面）====================
    # 防止同一 Agent 重复委派造成死循环
    delegation_counters: dict[str, int] = Field(default_factory=dict)
    # delegate 请求完成记录（按 request_id）
    request_results: dict[str, Any] = Field(default_factory=dict)

    # ==================== 路由辅助（控制面）====================
    last_node: str | None = None

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

    # ==================== 状态标记 ====================
    # 是否完成
    is_completed: bool = Field(default=False)

    # 人机交互上限（避免无限 interrupt）
    human_request_count: int = Field(default=0)
    max_human_requests: int = Field(default=6)

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

    @field_validator("metadata", mode="before")
    @classmethod
    def validate_metadata_reserved_keys(cls, v):
        """
        metadata 仅允许存放调试信息，禁止承载任何业务主线或编排控制字段。

        说明：
        - 关键决策/控制面字段必须放在 AgentState 显式字段里（例如 selected_component / request_results 等）
        - 这样可以避免 metadata 退化为“垃圾堆”，导致代码可读性与可维护性崩溃
        """
        if v is None:
            return {}
        if not isinstance(v, dict):
            return v
        reserved = {
            "selected_component",
            "selected_component_id",
            "human_responses",
            "human_writebacks",
            "delegation_counters",
            "request_results",
            "last_node",
        }
        bad = sorted(set(v.keys()) & reserved)
        if bad:
            raise ValueError(f"metadata 禁止包含保留字段: {bad}（请迁移到 AgentState 显式字段）")
        legacy_patterns = (
            "_delegate_knowledge_count",
            "delegate_developer_for_sql_count",
        )
        legacy_hits = [k for k in v.keys() if any(p in k for p in legacy_patterns)]
        if legacy_hits:
            raise ValueError(f"metadata 禁止包含旧版委派计数 key: {sorted(legacy_hits)}（请迁移到 delegation_counters）")
        return v

    @field_validator("pending_requests", mode="before")
    @classmethod
    def convert_pending_requests(cls, v):
        """自动将 dict 转换为 BlackboardRequest"""
        if v is None:
            return []
        if isinstance(v, list):
            converted = []
            for item in v:
                if isinstance(item, dict):
                    converted.append(BlackboardRequest(**item))
                else:
                    converted.append(item)
            return converted
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
