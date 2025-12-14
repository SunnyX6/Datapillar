"""
ETL 多智能体编排器

使用 LangGraph 实现智能体协作：
- 条件路由（根据评审/测试结果决定下一步）
- 反馈循环（评审不通过 → 重新设计，测试不通过 → 重新开发）
- 人机交互（需求澄清、方案确认、反馈收集）
- 迭代控制（最大迭代次数限制）
- 自我进化学习（反馈收集 → 案例沉淀 → 知识更新）
"""

import json
import logging
from typing import AsyncGenerator, Optional, Dict, Any, Literal
from datetime import datetime

from langgraph.graph import StateGraph, END
from langgraph.checkpoint.base import BaseCheckpointSaver
from langgraph.checkpoint.memory import InMemorySaver
from langgraph.types import Command, interrupt
from langchain_core.messages import HumanMessage, AIMessage

from src.agent.etl_agents.schemas.state import AgentState
from src.agent.etl_agents.schemas.plan import Workflow, ReviewResult, TestResult
from src.agent.etl_agents.schemas.dag import workflow_to_react_flow, ReactFlowDag
from src.agent.etl_agents.agents import (
    KnowledgeAgent,
    AnalystAgent,
    ArchitectAgent,
    DeveloperAgent,
    ReviewerAgent,
    TesterAgent,
)
from src.agent.etl_agents.memory import MemoryManager
from src.agent.etl_agents.learning import (
    FeedbackCollector,
    FeedbackRating,
    LearningLoop,
)

logger = logging.getLogger(__name__)


class EtlOrchestrator:
    """
    ETL 多智能体编排器

    工作流程：
    1. KnowledgeAgent: 检索相关知识
    2. AnalystAgent: 分析需求（可能需要用户澄清）
    3. ArchitectAgent: 设计方案
    4. ReviewerAgent: 评审方案（不通过 → 回到 3）
    5. DeveloperAgent: 生成代码
    6. TesterAgent: 测试验证（不通过 → 回到 5）
    7. FeedbackHandler: 收集用户反馈
    8. LearningHandler: 根据反馈进行学习
    9. END: 输出最终结果
    """

    def __init__(
        self,
        checkpointer: Optional[BaseCheckpointSaver] = None,
        max_iterations: int = 3,
        enable_learning: bool = True,
    ):
        """
        初始化编排器

        Args:
            checkpointer: LangGraph checkpoint 存储
            max_iterations: 最大迭代次数（评审/测试循环）
            enable_learning: 是否启用自我进化学习
        """
        self.checkpointer = checkpointer or InMemorySaver()
        self.max_iterations = max_iterations
        self.enable_learning = enable_learning

        # 初始化 Memory
        self.memory = MemoryManager()

        # 初始化所有 Agent
        self.knowledge_agent = KnowledgeAgent(memory=self.memory)
        self.analyst_agent = AnalystAgent()
        self.architect_agent = ArchitectAgent()
        self.developer_agent = DeveloperAgent()
        self.reviewer_agent = ReviewerAgent()
        self.tester_agent = TesterAgent()

        # 初始化学习组件
        self.feedback_collector = FeedbackCollector()
        self.learning_loop = LearningLoop(case_library=self.memory.case_library)

        # 构建 LangGraph
        self.graph = self._build_graph()

        logger.info("✅ EtlOrchestrator 初始化完成（学习模式: %s）", enable_learning)

    def _build_graph(self):
        """
        构建 LangGraph 状态图

        流程：
        START → knowledge → analyst → [clarification?] → architect → reviewer
            → [approved?] → developer → tester → [passed?] → feedback → learning → END
                         ↑_______________↓          ↑________↓

        Returns:
            编译后的 StateGraph
        """
        builder = StateGraph(AgentState)

        # ===== 添加节点 =====
        builder.add_node("knowledge_agent", self.knowledge_agent)
        builder.add_node("analyst_agent", self.analyst_agent)
        builder.add_node("clarification_handler", self._handle_clarification)
        builder.add_node("architect_agent", self.architect_agent)
        builder.add_node("reviewer_agent", self.reviewer_agent)
        builder.add_node("developer_agent", self.developer_agent)
        builder.add_node("tester_agent", self.tester_agent)
        builder.add_node("feedback_handler", self._handle_feedback)
        builder.add_node("learning_handler", self._handle_learning)
        builder.add_node("finalize", self._finalize)

        # ===== 设置入口 =====
        builder.set_entry_point("knowledge_agent")

        # ===== 添加边 =====
        # knowledge → analyst
        builder.add_edge("knowledge_agent", "analyst_agent")

        # analyst → [条件路由]
        builder.add_conditional_edges(
            "analyst_agent",
            self._route_after_analyst,
            {
                "clarification": "clarification_handler",
                "continue": "architect_agent",
            },
        )

        # clarification → architect
        builder.add_edge("clarification_handler", "architect_agent")

        # architect → reviewer
        builder.add_edge("architect_agent", "reviewer_agent")

        # reviewer → [条件路由]
        builder.add_conditional_edges(
            "reviewer_agent",
            self._route_after_review,
            {
                "approved": "developer_agent",
                "rejected": "architect_agent",
                "max_iterations": "developer_agent",
            },
        )

        # developer → tester
        builder.add_edge("developer_agent", "tester_agent")

        # tester → [条件路由]
        builder.add_conditional_edges(
            "tester_agent",
            self._route_after_test,
            {
                "passed": "feedback_handler",
                "failed": "developer_agent",
                "max_iterations": "feedback_handler",
            },
        )

        # feedback → learning
        builder.add_edge("feedback_handler", "learning_handler")

        # learning → finalize
        builder.add_edge("learning_handler", "finalize")

        # finalize → END
        builder.add_edge("finalize", END)

        # 编译图
        if self.checkpointer:
            graph = builder.compile(checkpointer=self.checkpointer)
        else:
            graph = builder.compile()

        logger.info("LangGraph 状态图编译完成")
        return graph

    # ===== 路由函数 =====

    def _route_after_analyst(self, state: AgentState) -> Literal["clarification", "continue"]:
        """分析完成后的路由"""
        if state.needs_clarification:
            return "clarification"
        return "continue"

    def _route_after_review(self, state: AgentState) -> Literal["approved", "rejected", "max_iterations"]:
        """评审完成后的路由"""
        # 检查迭代次数
        if state.iteration_count >= self.max_iterations:
            logger.warning(f"已达到最大迭代次数 {self.max_iterations}，强制继续")
            return "max_iterations"

        review_result = state.review_result
        if review_result:
            if isinstance(review_result, dict):
                approved = review_result.get("approved", False)
            else:
                approved = getattr(review_result, "approved", False)

            if approved:
                return "approved"

        return "rejected"

    def _route_after_test(self, state: AgentState) -> Literal["passed", "failed", "max_iterations"]:
        """测试完成后的路由"""
        # 检查迭代次数
        if state.iteration_count >= self.max_iterations:
            logger.warning(f"已达到最大迭代次数 {self.max_iterations}，强制结束")
            return "max_iterations"

        test_result = state.test_result
        if test_result:
            if isinstance(test_result, dict):
                passed = test_result.get("passed", False)
            else:
                passed = getattr(test_result, "passed", False)

            if passed:
                return "passed"

        return "failed"

    # ===== 节点处理函数 =====

    async def _handle_clarification(self, state: AgentState) -> Command:
        """处理需求澄清（人机交互）"""
        questions = state.clarification_questions or []

        if questions:
            logger.info(f"⏸️ 需要用户澄清: {questions}")

            # 使用 interrupt 暂停执行，等待用户输入
            user_response = interrupt({
                "type": "clarification",
                "questions": questions,
                "message": "请回答以下问题以便继续分析",
            })

            # 用户回答后，更新状态
            return Command(
                update={
                    "messages": [HumanMessage(content=f"用户澄清: {user_response}")],
                    "user_input": f"{state.user_input}\n用户补充: {user_response}",
                    "needs_clarification": False,
                    "clarification_questions": [],
                    "current_agent": "clarification_handler",
                }
            )

        return Command(
            update={
                "needs_clarification": False,
                "current_agent": "clarification_handler",
            }
        )

    async def _handle_feedback(self, state: AgentState) -> Command:
        """处理用户反馈（人机交互）"""
        if not self.enable_learning:
            logger.info("学习模式未启用，跳过反馈收集")
            return Command(
                update={
                    "current_agent": "feedback_handler",
                    "metadata": {**state.metadata, "feedback_skipped": True},
                }
            )

        # 构建结果摘要
        plan = state.architecture_plan
        if isinstance(plan, dict):
            plan_name = plan.get("name", "ETL 工作流")
            job_count = len(plan.get("jobs", []))
        else:
            plan_name = plan.name if plan else "ETL 工作流"
            job_count = len(plan.jobs) if plan else 0

        result_summary = f"生成了 {plan_name}，包含 {job_count} 个 Job"

        # 提取 SQL 预览（如果有）
        sql_preview = None
        if plan:
            jobs = plan.get("jobs", []) if isinstance(plan, dict) else plan.jobs
            for job in jobs[:3]:  # 只展示前 3 个 Job 的 SQL
                if isinstance(job, dict):
                    sql = job.get("sql_template") or job.get("sql")
                else:
                    sql = getattr(job, "sql_template", None) or getattr(job, "sql", None)
                if sql:
                    sql_preview = (sql_preview or "") + f"\n-- {job.get('name', '') if isinstance(job, dict) else job.name}\n{sql}\n"

        logger.info("⏸️ 收集用户反馈...")

        # 使用 interrupt 暂停执行，等待用户反馈
        feedback_response = interrupt({
            "type": "feedback_request",
            "message": "请对生成结果进行评价",
            "result_summary": result_summary,
            "sql_preview": sql_preview,
            "options": [
                {"value": "satisfied", "label": "👍 满意，直接采纳"},
                {"value": "unsatisfied", "label": "👎 不满意，重新生成"},
                {"value": "need_modification", "label": "✏️ 需要修改"},
                {"value": "skip", "label": "⏭️ 跳过"},
            ],
        })

        # 解析反馈
        feedback_data = {
            "rating": feedback_response.get("rating", "skip") if isinstance(feedback_response, dict) else feedback_response,
            "comment": feedback_response.get("comment") if isinstance(feedback_response, dict) else None,
            "modified_sql": feedback_response.get("modified_sql") if isinstance(feedback_response, dict) else None,
        }

        logger.info(f"收到用户反馈: {feedback_data['rating']}")

        return Command(
            update={
                "current_agent": "feedback_handler",
                "metadata": {**state.metadata, "user_feedback": feedback_data},
            }
        )

    async def _handle_learning(self, state: AgentState) -> Command:
        """处理学习（根据反馈进行案例沉淀）"""
        if not self.enable_learning:
            return Command(
                update={"current_agent": "learning_handler"}
            )

        feedback_data = state.metadata.get("user_feedback", {})
        rating = feedback_data.get("rating", "skip")

        if rating == "skip":
            logger.info("用户跳过反馈，不进行学习")
            return Command(
                update={"current_agent": "learning_handler"}
            )

        # 构建 Feedback 对象
        from src.agent.etl_agents.learning import Feedback, FeedbackRating

        try:
            feedback = Feedback(
                rating=FeedbackRating(rating),
                comment=feedback_data.get("comment"),
                modified_sql=feedback_data.get("modified_sql"),
            )
        except ValueError:
            feedback = Feedback(rating=FeedbackRating.SKIP)

        # 提取学习所需信息
        analysis = state.analysis_result
        if isinstance(analysis, dict):
            intent = analysis.get("intent", {})
            intent_summary = intent.get("summary", "unknown")
            source_tables = [ds.get("table_name", "") for ds in intent.get("data_sources", [])]
            target_tables = []
            target = intent.get("data_target")
            if target:
                target_tables = [target.get("table_name", "")]
        else:
            intent_summary = analysis.intent.summary if analysis and analysis.intent else "unknown"
            source_tables = [ds.table_name for ds in (analysis.intent.data_sources if analysis and analysis.intent else [])]
            target_tables = [analysis.intent.data_target.table_name] if analysis and analysis.intent and analysis.intent.data_target else []

        # 提取 SQL（从 plan 中）
        sql_text = None
        plan = state.architecture_plan
        if plan:
            jobs = plan.get("jobs", []) if isinstance(plan, dict) else plan.jobs
            sql_parts = []
            for job in jobs:
                sql = job.get("sql_template") if isinstance(job, dict) else getattr(job, "sql_template", None)
                if sql:
                    sql_parts.append(sql)
            if sql_parts:
                sql_text = "\n\n".join(sql_parts)

        # 执行学习
        try:
            learn_result = await self.learning_loop.learn_from_feedback(
                feedback=feedback,
                user_query=state.user_input,
                sql_text=feedback.modified_sql or sql_text,
                source_tables=source_tables,
                target_tables=target_tables,
                intent=intent_summary,
                session_id=state.session_id,
            )
            logger.info(f"学习完成: {learn_result}")

        except Exception as e:
            logger.error(f"学习失败: {e}", exc_info=True)
            learn_result = {"action": "error", "message": str(e)}

        return Command(
            update={
                "current_agent": "learning_handler",
                "metadata": {**state.metadata, "learn_result": learn_result},
            }
        )

    async def _finalize(self, state: AgentState) -> Command:
        """最终处理 - 生成可渲染的 DAG"""
        logger.info("🎉 工作流完成，生成 DAG 输出")

        # 获取 plan
        plan = state.architecture_plan
        dag_output = None

        if plan:
            # 转换为 Workflow 对象
            if isinstance(plan, dict):
                plan_obj = Workflow(**plan)
            else:
                plan_obj = plan

            # 转换为 React Flow DAG 格式
            dag = workflow_to_react_flow(plan_obj)
            dag_output = dag.model_dump()

            # 生成摘要
            summary = f"生成完成：{dag.summary()}"
            logger.info(f"📊 {summary}")
        else:
            summary = "工作流生成完成，但缺少架构方案"

        # 获取学习结果
        learn_result = state.metadata.get("learn_result", {})
        learn_action = learn_result.get("action", "")

        if learn_action == "saved_success_case":
            summary += "（已保存为成功案例）"
        elif learn_action == "saved_failure_case":
            summary += "（已记录失败分析）"

        return Command(
            update={
                "messages": [AIMessage(content=summary)],
                "dag_output": dag_output,
                "current_agent": "finalize",
                "is_completed": True,
            }
        )

    # ===== 公共接口 =====

    async def run(
        self,
        user_input: str,
        session_id: str,
        user_id: str,
    ) -> Dict[str, Any]:
        """
        同步执行（等待完成）

        Args:
            user_input: 用户输入
            session_id: 会话ID
            user_id: 用户ID

        Returns:
            最终状态
        """
        config = self._build_config(session_id, user_id)

        initial_state = {
            "session_id": session_id,
            "user_id": user_id,
            "user_input": user_input,
            "messages": [HumanMessage(content=user_input)],
            "max_iterations": self.max_iterations,
        }

        final_state = await self.graph.ainvoke(initial_state, config=config)
        return final_state

    async def stream(
        self,
        user_input: str,
        session_id: str,
        user_id: str,
        resume_value: Optional[Any] = None,
    ) -> AsyncGenerator[Dict[str, Any], None]:
        """
        流式执行

        Args:
            user_input: 用户输入
            session_id: 会话ID
            user_id: 用户ID
            resume_value: 恢复值（用于中断恢复）

        Yields:
            事件流
        """
        config = self._build_config(session_id, user_id)

        if resume_value is not None:
            # 恢复执行
            input_data = Command(resume=resume_value)
        else:
            # 首次执行
            input_data = {
                "session_id": session_id,
                "user_id": user_id,
                "user_input": user_input,
                "messages": [HumanMessage(content=user_input)],
                "max_iterations": self.max_iterations,
            }

        # 发送开始事件
        yield {
            "event_type": "session_started",
            "data": {"session_id": session_id},
        }

        try:
            async for event in self.graph.astream_events(
                input_data,
                config=config,
                version="v2",
            ):
                kind = event.get("event")
                name = event.get("name")
                meta = event.get("metadata", {})
                node = meta.get("langgraph_node")

                # Agent 开始
                if kind == "on_chain_start" and node:
                    yield {
                        "event_type": "agent_started",
                        "agent": node,
                        "data": {"name": self._agent_display_name(node)},
                    }

                # Agent 结束
                elif kind == "on_chain_end" and node:
                    output = event.get("data", {}).get("output")
                    if isinstance(output, Command):
                        output = output.update if hasattr(output, "update") else {}

                    yield {
                        "event_type": "agent_completed",
                        "agent": node,
                        "data": output,
                    }

                # 工具调用
                elif kind == "on_tool_start":
                    yield {
                        "event_type": "tool_called",
                        "tool": name,
                        "data": event.get("data", {}).get("input", {}),
                    }

            # 检查中断
            snapshot = await self.graph.aget_state(config)
            if snapshot.tasks and snapshot.tasks[0].interrupts:
                interrupt_data = snapshot.tasks[0].interrupts[0].value
                yield {
                    "event_type": "session_interrupted",
                    "data": interrupt_data,
                }
                return

            # 完成
            yield {
                "event_type": "session_completed",
                "data": snapshot.values if snapshot else {},
            }

        except Exception as e:
            logger.error(f"流式执行失败: {e}", exc_info=True)
            yield {
                "event_type": "session_error",
                "data": {"error": str(e)},
            }

    def _build_config(self, session_id: str, user_id: str) -> Dict[str, Any]:
        """构建配置"""
        thread_id = f"etl:user:{user_id}:session:{session_id}"
        return {
            "configurable": {
                "thread_id": thread_id,
                "session_id": session_id,
                "user_id": user_id,
            }
        }

    @staticmethod
    def _agent_display_name(node: str) -> str:
        """获取 Agent 展示名称"""
        names = {
            "knowledge_agent": "知识检索专家",
            "analyst_agent": "需求分析师",
            "architect_agent": "数据架构师",
            "reviewer_agent": "方案评审",
            "developer_agent": "数据开发",
            "tester_agent": "测试验证",
            "clarification_handler": "需求澄清",
            "feedback_handler": "反馈收集",
            "learning_handler": "学习沉淀",
            "finalize": "完成处理",
        }
        return names.get(node, node)


async def create_etl_orchestrator(
    checkpointer: Optional[BaseCheckpointSaver] = None,
    max_iterations: int = 3,
    enable_learning: bool = True,
) -> EtlOrchestrator:
    """
    创建 ETL 编排器

    Args:
        checkpointer: LangGraph checkpoint 存储
        max_iterations: 最大迭代次数
        enable_learning: 是否启用自我进化学习

    Returns:
        EtlOrchestrator 实例
    """
    return EtlOrchestrator(
        checkpointer=checkpointer,
        max_iterations=max_iterations,
        enable_learning=enable_learning,
    )
