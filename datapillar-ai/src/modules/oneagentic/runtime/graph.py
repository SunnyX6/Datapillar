"""
执行图

基于 LangGraph 的 Agent 执行图。

设计原则：
- 动态构建：从 AgentRegistry 获取 Agent
- 委派路由：通过 active_agent 控制
- 状态共享：使用 Blackboard
- 自动压缩：95% 阈值触发
"""

from __future__ import annotations

import logging

from langchain_core.messages import AIMessage, HumanMessage
from langgraph.graph import END, StateGraph
from langgraph.types import Command, interrupt

from src.modules.oneagentic.core.agent import AgentRegistry
from src.modules.oneagentic.core.types import AgentResult
from src.modules.oneagentic.memory.session_memory import SessionMemory
from src.modules.oneagentic.runtime.executor import get_executor
from src.modules.oneagentic.state.blackboard import Blackboard

logger = logging.getLogger(__name__)


class AgentGraph:
    """
    Agent 执行图

    从 AgentRegistry 动态构建执行图。
    """

    def __init__(self):
        """初始化执行图"""
        self._graph = self._build_graph()
        logger.info(f"📊 AgentGraph 初始化, Agent 数量: {AgentRegistry.count()}")

    def _build_graph(self) -> StateGraph:
        """构建执行图"""
        graph = StateGraph(Blackboard)

        # 获取所有 Agent ID
        agent_ids = AgentRegistry.list_ids()

        # 为每个 Agent 创建节点
        for agent_id in agent_ids:
            node_fn = self._create_agent_node(agent_id)
            graph.add_node(agent_id, node_fn)

        # 设置条件入口
        route_map = {agent_id: agent_id for agent_id in agent_ids}
        route_map["end"] = END
        graph.set_conditional_entry_point(self._route_entry, route_map)

        # 所有 Agent 执行完后返回 END
        for agent_id in agent_ids:
            graph.add_edge(agent_id, END)

        return graph

    def compile(self, checkpointer=None, store=None):
        """编译图"""
        return self._graph.compile(checkpointer=checkpointer, store=store)

    def _route_entry(self, state: Blackboard) -> str:
        """入口路由"""
        active = state.get("active_agent")
        if active and active in AgentRegistry.list_ids():
            return active
        return "end"

    def _create_agent_node(self, agent_id: str):
        """创建 Agent 节点"""

        async def agent_node(state: Blackboard) -> Command:
            session_id = state.get("session_id", "")
            user_id = state.get("user_id", "")

            # 从 messages 获取用户输入
            messages = state.get("messages", [])
            query = ""
            for msg in reversed(messages):
                if isinstance(msg, HumanMessage):
                    query = msg.content
                    break

            # 获取执行器
            executor = get_executor(agent_id)

            # 加载记忆
            memory_data = state.get("memory")
            if memory_data:
                memory = SessionMemory.model_validate(memory_data)
            else:
                memory = SessionMemory(session_id=session_id, user_id=user_id)

            # 执行
            result = await executor.execute(
                query=query,
                session_id=session_id,
                memory=memory,
                state=dict(state),
            )

            # 处理 Command（委派）
            if isinstance(result, Command):
                logger.info(f"🔄 [{agent_id}] 委派命令")
                return result

            # 处理 AgentResult
            return await self._handle_result(
                state=state,
                agent_id=agent_id,
                result=result,
                memory=memory,
            )

        return agent_node

    async def _handle_result(
        self,
        *,
        state: Blackboard,
        agent_id: str,
        result: AgentResult,
        memory: SessionMemory,
    ) -> Command:
        """处理 Agent 结果"""
        spec = AgentRegistry.get(agent_id)

        # 记录到对话历史
        if result.status == "completed":
            memory.add_agent_handover(
                from_agent=agent_id,
                to_agent="system",
                summary=f"完成: {result.summary or ''}",
            )
            if result.summary:
                memory.conversation.update_agent_summary(agent_id, result.summary)

        elif result.status == "failed":
            memory.add_agent_handover(
                from_agent=agent_id,
                to_agent="system",
                summary=f"失败: {result.error or ''}",
            )

        # 决定下一步
        next_agent = self._decide_next(agent_id, result)

        # 更新状态
        update_dict: dict = {
            "memory": memory.model_dump(mode="json"),
            "active_agent": next_agent,
            "last_agent_status": result.status,
            "last_agent_error": result.error if result.status in ("failed", "error") else None,
        }

        # 处理澄清
        if result.status == "needs_clarification" and result.clarification:
            logger.info(f"⏸️ [{agent_id}] 需要澄清")

            memory.add_clarification(agent_id, result.clarification.message)

            user_reply = interrupt(
                {
                    "type": "clarification",
                    "agent_id": agent_id,
                    "message": result.clarification.message,
                    "questions": result.clarification.questions,
                    "options": result.clarification.options,
                }
            )

            memory.add_user_message(user_reply)

            # 重新执行
            executor = get_executor(agent_id)
            result = await executor.execute(
                query=user_reply,
                session_id=state.get("session_id", ""),
                memory=memory,
                state=dict(state),
            )

            if isinstance(result, Command):
                return result

        # 流程结束时添加最终消息
        if next_agent is None and result.status == "completed":
            final_message = AIMessage(
                content=result.summary or "完成",
                additional_kwargs={
                    "deliverable": (
                        result.deliverable.model_dump(mode="json")
                        if hasattr(result.deliverable, "model_dump")
                        else result.deliverable
                    ),
                    "deliverable_type": result.deliverable_type,
                },
            )
            update_dict["messages"] = [final_message]

        # 检查是否需要压缩（95% 阈值）
        if memory.needs_compact():
            compact_result = await memory.compact()
            if compact_result.success and compact_result.removed_count > 0:
                update_dict["memory"] = memory.model_dump(mode="json")
                logger.info(
                    f"📦 自动压缩: 移除 {compact_result.removed_count} 条，"
                    f"节省 {compact_result.tokens_saved} tokens"
                )

        return Command(update=update_dict)

    def _decide_next(self, agent_id: str, result: AgentResult) -> str | None:
        """决定下一个 Agent"""
        from src.modules.oneagentic.core.types import AgentRole

        # 失败或需要澄清：暂停
        if result.status != "completed":
            return None

        # 获取 spec
        spec = AgentRegistry.get(agent_id)
        if not spec:
            return None

        # 对外 Agent：直接结束
        if spec.role == AgentRole.EXTERNAL:
            logger.info(f"✅ [{agent_id}] 对外 Agent 完成，流程结束")
            return None

        # 对内 Agent 但没有委派：也结束
        logger.info(f"✅ [{agent_id}] 完成，无委派，流程结束")
        return None
