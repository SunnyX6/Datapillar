"""
ETL 多智能体编排器

使用 LangGraph 实现智能体协作：
- 条件路由（根据测试结果决定下一步）
- 反馈循环（测试不通过 → 重新开发）
- 人机交互（需求澄清、方案确认、反馈收集）
- 迭代控制（最大迭代次数限制）
"""

import logging
from typing import Any, AsyncGenerator, Literal

from langchain_core.messages import AIMessage, HumanMessage
from langgraph.checkpoint.base import BaseCheckpointSaver
from langgraph.checkpoint.memory import InMemorySaver
from langgraph.graph import END, StateGraph
from langgraph.types import Command, interrupt

from src.modules.etl.agents import (
    AnalystAgent,
    ArchitectAgent,
    DeveloperAgent,
    KnowledgeAgent,
    TesterAgent,
)
from src.modules.etl.memory import MemoryManager
from src.modules.etl.schemas.dag import WorkflowResponse, convert_workflow
from src.modules.etl.schemas.kg_context import AgentScopedContext, AgentType, GlobalKGContext
from src.modules.etl.schemas.plan import Workflow
from src.modules.etl.schemas.requirement import AnalysisResult
from src.modules.etl.schemas.state import AgentState

logger = logging.getLogger(__name__)


class EtlOrchestrator:
    """
    ETL 多智能体编排器

    工作流程：
    1. KnowledgeAgent: 检索相关知识
    2. AnalystAgent: 分析需求（可能需要用户澄清）
    3. ArchitectAgent: 设计方案
    4. DeveloperAgent: 生成代码
    5. TesterAgent: 测试验证（不通过 → 回到 4）
    6. FeedbackHandler: 收集用户反馈（满意时给参考的 SQL 加分）
    7. END: 输出最终结果
    """

    def __init__(
        self,
        checkpointer: BaseCheckpointSaver | None = None,
        max_iterations: int = 3,
        agent_max_retries: int = 2,
    ):
        """
        初始化编排器

        Args:
            checkpointer: LangGraph checkpoint 存储
            max_iterations: 最大迭代次数（测试循环）
            agent_max_retries: Agent 执行失败时的最大重试次数
        """
        self.checkpointer = checkpointer or InMemorySaver()
        self.max_iterations = max_iterations
        self.agent_max_retries = agent_max_retries

        # 初始化 Memory
        self.memory = MemoryManager()

        # 初始化所有 Agent
        self.knowledge_agent = KnowledgeAgent()
        self.analyst_agent = AnalystAgent()
        self.architect_agent = ArchitectAgent()
        self.developer_agent = DeveloperAgent()
        self.tester_agent = TesterAgent()

        # 构建 LangGraph
        self.graph = self._build_graph()

        logger.info("✅ EtlOrchestrator 初始化完成")

    def _wrap_agent_with_retry(self, agent, agent_name: str):
        """
        包装 Agent，添加重试机制

        Args:
            agent: Agent 实例
            agent_name: Agent 名称（用于日志和错误提示）

        Returns:
            包装后的 Agent 函数
        """
        # 不应重试的错误（前置依赖缺失）
        non_retryable_errors = [
            "缺少需求分析结果",
            "缺少架构方案",
            "缺少",  # 通用前缀
        ]

        def is_retryable_error(error_str: str) -> bool:
            """判断错误是否可重试"""
            for pattern in non_retryable_errors:
                if pattern in error_str:
                    return False
            return True

        async def wrapped(state: AgentState) -> Command:
            last_error = None

            for attempt in range(self.agent_max_retries + 1):
                try:
                    if attempt > 0:
                        logger.warning(f"🔄 {agent_name} 第 {attempt} 次重试...")

                    result = await agent(state)

                    # 检查返回结果中是否有 error 字段
                    if isinstance(result, Command) and result.update:
                        error = result.update.get("error")
                        if error:
                            # 检查是否可重试
                            if not is_retryable_error(str(error)):
                                logger.info(f"ℹ️ {agent_name} 错误不可重试: {error}")
                                return result

                            if attempt < self.agent_max_retries:
                                logger.warning(f"⚠️ {agent_name} 返回错误: {error}，准备重试...")
                                last_error = Exception(error)
                                continue

                    return result

                except Exception as e:
                    # 检查是否是 LangGraph interrupt 异常，如果是则直接传播
                    # interrupt 异常不应被重试机制捕获
                    if "Interrupt" in type(e).__name__ or "interrupt" in str(type(e)).lower():
                        raise

                    last_error = e
                    logger.warning(
                        f"⚠️ {agent_name} 执行异常 (尝试 {attempt + 1}/{self.agent_max_retries + 1}): {e}"
                    )

                    if attempt < self.agent_max_retries:
                        continue

            # 所有重试都失败了，返回友好错误
            error_msg = self._format_agent_error(agent_name, last_error)
            logger.error(f"❌ {agent_name} 最终失败: {last_error}")

            return Command(
                update={
                    "messages": [AIMessage(content=error_msg)],
                    "current_agent": agent_name,
                    "error": str(last_error),
                }
            )

        return wrapped

    def _format_agent_error(self, agent_name: str, error: Exception) -> str:
        """格式化 Agent 错误为用户友好提示"""
        error_str = str(error)

        # JSON 解析错误
        if "JSON" in error_str or "json" in error_str:
            return f"{agent_name} 处理失败：AI 响应格式异常，已重试多次仍无法解析。请简化需求描述后重试。"

        # 超时错误
        if "timeout" in error_str.lower():
            return f"{agent_name} 处理失败：请求超时，请稍后重试。"

        # API 限流
        if "rate" in error_str.lower() or "limit" in error_str.lower():
            return f"{agent_name} 处理失败：AI 服务繁忙，请稍后重试。"

        # 通用错误
        return f"{agent_name} 处理失败：{error_str[:100]}。请重试或简化需求。"

    def _build_graph(self):
        """
        构建 LangGraph 状态图

        流程：
        START → knowledge → analyst → [clarification?] → architect
              → developer → tester → [passed?] → feedback → finalize → END

        错误处理：
        - 每个 Agent 有重试机制（最多重试 N 次）
        - 重试耗尽后，路由检查 error 字段，有错误则跳转到 finalize 终止流程

        Returns:
            编译后的 StateGraph
        """
        builder = StateGraph(AgentState)

        # ===== 添加节点（Agent 节点带重试机制）=====
        builder.add_node(
            "knowledge_agent",
            self._wrap_agent_with_retry(self.knowledge_agent, "知识检索专家")
        )
        builder.add_node(
            "analyst_agent",
            self._wrap_agent_with_retry(self.analyst_agent, "需求分析师")
        )
        builder.add_node("clarification_handler", self._handle_clarification)
        builder.add_node(
            "architect_agent",
            self._wrap_agent_with_retry(self.architect_agent, "数据架构师")
        )
        builder.add_node(
            "developer_agent",
            self._wrap_agent_with_retry(self.developer_agent, "数据开发")
        )
        builder.add_node(
            "tester_agent",
            self._wrap_agent_with_retry(self.tester_agent, "测试验证")
        )
        builder.add_node("feedback_handler", self._handle_feedback)
        builder.add_node("finalize", self._finalize)

        # ===== 设置入口 =====
        builder.set_entry_point("knowledge_agent")

        # ===== 添加边（所有节点都检查错误）=====

        # knowledge → analyst（检查错误）
        builder.add_conditional_edges(
            "knowledge_agent",
            self._check_error_and_continue,
            {
                "continue": "analyst_agent",
                "error": "finalize",
            },
        )

        # analyst → [条件路由：错误/澄清/继续]
        builder.add_conditional_edges(
            "analyst_agent",
            self._route_after_analyst,
            {
                "clarification": "clarification_handler",
                "continue": "architect_agent",
                "error": "finalize",
            },
        )

        # clarification → analyst（重新分析）
        builder.add_edge("clarification_handler", "analyst_agent")

        # architect → developer（检查错误）
        builder.add_conditional_edges(
            "architect_agent",
            self._check_error_and_continue,
            {
                "continue": "developer_agent",
                "error": "finalize",
            },
        )

        # developer → tester（检查错误）
        builder.add_conditional_edges(
            "developer_agent",
            self._check_error_and_continue,
            {
                "continue": "tester_agent",
                "error": "finalize",
            },
        )

        # tester → [条件路由：错误/通过/失败/达到最大迭代]
        builder.add_conditional_edges(
            "tester_agent",
            self._route_after_test,
            {
                "passed": "feedback_handler",
                "failed": "developer_agent",
                "max_iterations": "feedback_handler",
                "error": "finalize",
            },
        )

        # feedback → finalize
        builder.add_edge("feedback_handler", "finalize")

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

    def _check_error_and_continue(self, state: AgentState) -> Literal["continue", "error"]:
        """检查是否有错误，决定继续还是终止"""
        if state.error:
            logger.error(f"❌ 流程终止，原因: {state.error}")
            return "error"
        return "continue"

    def _route_after_analyst(self, state: AgentState) -> Literal["clarification", "continue", "error"]:
        """分析完成后的路由"""
        # 先检查错误
        if state.error:
            logger.error(f"❌ AnalystAgent 失败，流程终止: {state.error}")
            return "error"

        if state.needs_clarification:
            # 检查是否达到最大澄清次数
            if state.clarification_count >= state.max_clarifications:
                logger.warning(f"已达到最大澄清次数 {state.max_clarifications}，强制继续")
                return "continue"
            return "clarification"
        return "continue"

    def _route_after_test(self, state: AgentState) -> Literal["passed", "failed", "max_iterations", "error"]:
        """测试完成后的路由"""
        # 先检查错误
        if state.error:
            logger.error(f"❌ TesterAgent 失败，流程终止: {state.error}")
            return "error"

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
            logger.info(f"⏸️ 需要用户澄清 (第 {state.clarification_count + 1} 次): {questions}")

            # 使用 interrupt 暂停执行，等待用户输入
            user_response = interrupt({
                "type": "clarification",
                "questions": questions,
                "message": "请回答以下问题以便继续分析",
                "clarification_count": state.clarification_count + 1,
                "max_clarifications": state.max_clarifications,
            })

            # 用户回答后，更新状态
            return Command(
                update={
                    "messages": [HumanMessage(content=f"用户澄清: {user_response}")],
                    "user_input": f"{state.user_input}\n用户补充: {user_response}",
                    "needs_clarification": False,
                    "clarification_questions": [],
                    "clarification_count": state.clarification_count + 1,
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
        """处理用户反馈（人机交互）

        收集用户反馈，如果满意则给 AI 参考过的历史 SQL 加分。
        """
        # 构建结果摘要
        plan = state.architecture_plan
        if isinstance(plan, dict):
            plan_name = plan.get("name", "ETL 工作流")
            job_count = len(plan.get("jobs", []))
        else:
            plan_name = plan.name if plan else "ETL 工作流"
            job_count = len(plan.jobs) if plan else 0

        result_summary = f"生成了 {plan_name}，包含 {job_count} 个 Job"

        # 提取测试警告（如果有）
        test_warnings = []
        test_result = state.test_result
        if test_result:
            if isinstance(test_result, dict):
                test_warnings = test_result.get("validation_warnings", [])
            else:
                test_warnings = getattr(test_result, "validation_warnings", [])

        logger.info("⏸️ 收集用户反馈...")

        # 构建 interrupt 消息
        feedback_message = "请对生成结果进行评价"
        if test_warnings:
            feedback_message = f"请对生成结果进行评价（检测到 {len(test_warnings)} 个警告，建议仔细确认）"

        # 使用 interrupt 暂停执行，等待用户反馈
        feedback_response = interrupt({
            "type": "feedback_request",
            "message": feedback_message,
            "result_summary": result_summary,
            "sql_preview": None,
            "validation_warnings": test_warnings,
            "options": [
                {"value": "satisfied", "label": "👍 满意，直接采纳"},
                {"value": "unsatisfied", "label": "👎 不满意，重新生成"},
                {"value": "need_modification", "label": "✏️ 需要修改"},
                {"value": "skip", "label": "⏭️ 跳过"},
            ],
        })

        # 解析反馈
        rating = feedback_response.get("rating", "skip") if isinstance(feedback_response, dict) else feedback_response

        logger.info(f"收到用户反馈: {rating}")

        # 如果用户满意，给参考的历史 SQL 加分
        if rating == "satisfied":
            await self._increment_referenced_sql_use_count(state)

        return Command(
            update={
                "current_agent": "feedback_handler",
                "metadata": {**state.metadata, "user_feedback": {"rating": rating}},
            }
        )

    async def _increment_referenced_sql_use_count(self, state: AgentState) -> None:
        """给 AI 参考过的历史 SQL 加分"""
        from src.infrastructure.repository import KnowledgeRepository

        # 从 state.referenced_sql_ids 获取 DeveloperAgent 实际参考的 SQL ID
        referenced_sql_ids = state.referenced_sql_ids or []

        if not referenced_sql_ids:
            logger.info("📊 本次无参考历史 SQL，跳过打分")
            return

        logger.info(f"📊 准备为 {len(referenced_sql_ids)} 个参考 SQL 加分: {referenced_sql_ids}")

        for sql_id in referenced_sql_ids:
            try:
                await KnowledgeRepository.increment_sql_use_count(sql_id)
                logger.info(f"✅ SQL 加分成功: {sql_id}")
            except Exception as e:
                logger.warning(f"SQL 加分失败: {sql_id}, {e}")

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

            # 转换为工作流响应格式
            workflow_response = convert_workflow(plan_obj)
            dag_output = workflow_response.model_dump()

            # 生成摘要
            job_count = len(workflow_response.jobs)
            dep_count = len(workflow_response.dependencies)
            summary = f"生成完成：{workflow_response.workflowName}，共 {job_count} 个任务、{dep_count} 条依赖"
            logger.info(f"📊 {summary}")
        else:
            summary = "工作流生成完成，但缺少架构方案"

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
    ) -> dict[str, Any]:
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
            "referenced_sql_ids": [],  # 显式清空，避免跨 session 污染
        }

        final_state = await self.graph.ainvoke(initial_state, config=config)
        return final_state

    async def stream(
        self,
        user_input: str,
        session_id: str,
        user_id: str,
        resume_value: Any | None = None,
    ) -> AsyncGenerator[dict[str, Any], None]:
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
            # 首次执行 - 显式初始化所有需要的字段
            input_data = {
                "session_id": session_id,
                "user_id": user_id,
                "user_input": user_input,
                "messages": [HumanMessage(content=user_input)],
                "max_iterations": self.max_iterations,
                "referenced_sql_ids": [],  # 显式清空，避免跨 session 污染
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

                    # messages 是 LangGraph 内部状态，不输出给前端
                    if isinstance(output, dict):
                        output.pop("messages", None)

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

            # 完成 - 只输出前端需要的最终结果
            final_data = {}
            if snapshot and snapshot.values:
                final_data["dag_output"] = snapshot.values.get("dag_output")
                final_data["is_completed"] = snapshot.values.get("is_completed", False)

            yield {
                "event_type": "session_completed",
                "data": final_data,
            }

        except Exception as e:
            logger.error(f"流式执行失败: {e}", exc_info=True)
            yield {
                "event_type": "session_error",
                "data": {"error": str(e)},
            }

    def _build_config(self, session_id: str, user_id: str) -> dict[str, Any]:
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
            "developer_agent": "数据开发",
            "tester_agent": "测试验证",
            "clarification_handler": "需求澄清",
            "feedback_handler": "反馈收集",
            "learning_handler": "学习沉淀",
            "finalize": "完成处理",
        }
        return names.get(node, node)


async def create_etl_orchestrator(
    checkpointer: BaseCheckpointSaver | None = None,
    max_iterations: int = 3,
    agent_max_retries: int = 2,
) -> EtlOrchestrator:
    """
    创建 ETL 编排器

    Args:
        checkpointer: LangGraph checkpoint 存储
        max_iterations: 最大迭代次数
        agent_max_retries: Agent 执行失败时的最大重试次数

    Returns:
        EtlOrchestrator 实例
    """
    return EtlOrchestrator(
        checkpointer=checkpointer,
        max_iterations=max_iterations,
        agent_max_retries=agent_max_retries,
    )
