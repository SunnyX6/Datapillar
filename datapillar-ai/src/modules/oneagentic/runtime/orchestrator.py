"""
编排器（基建层）

负责执行图的基础设施：
- 状态持久化（Checkpoint）
- 交付物存储（DeliverableStore）
- 经验学习（ExperienceLearner）
- 事件发送（EventBus）
- 断点恢复
- 会话管理

通常由 Datapillar 内部调用，不直接暴露给业务侧。
"""

from __future__ import annotations

import logging
import time
from collections.abc import AsyncGenerator
from typing import TYPE_CHECKING, Any

from langchain_core.messages import HumanMessage
from langgraph.graph import END, StateGraph
from langgraph.types import Command

from src.modules.oneagentic.core.agent import AgentRegistry
from src.modules.oneagentic.events import (
    AgentCompletedEvent,
    AgentStartedEvent,
    SessionCompletedEvent,
    SessionStartedEvent,
    ToolCalledEvent,
    ToolCompletedEvent,
    event_bus,
)
from src.modules.oneagentic.experience import (
    AlwaysSavePolicy,
    Episode,
    EpisodeStep,
    ExperienceLearner,
    ExperienceStore,
    Outcome,
    SedimentationPolicy,
)
from src.modules.oneagentic.integrations.checkpoint import Checkpoint
from src.modules.oneagentic.integrations.deliverable import DeliverableStore
from src.modules.oneagentic.sse.event import SseEvent
from src.modules.oneagentic.state.blackboard import Blackboard, create_blackboard

if TYPE_CHECKING:
    pass

logger = logging.getLogger(__name__)


class Orchestrator:
    """
    编排器（基建层）

    提供执行图的基础设施能力，通常由 Datapillar 内部调用。

    使用方式：
    1. 传入外部构建的图（推荐，由 Datapillar 调用）
    2. 自动构建图（兼容旧代码）
    """

    def __init__(
        self,
        *,
        name: str = "OneAgentic",
        team_id: str | None = None,
        # 外部传入图（由 Datapillar 构建）
        graph: StateGraph | None = None,
        entry_agent_id: str | None = None,
        agent_ids: list[str] | None = None,
        # 功能开关
        enable_learning: bool = False,
        enable_react: bool = False,
        # 兼容旧参数
        auto_discover: bool = False,
        experience_store: ExperienceStore | None = None,
        experience_policy: SedimentationPolicy | None = None,
    ):
        """
        初始化编排器

        参数：
        - name: 编排器名称
        - team_id: 团队 ID（用于隔离 Checkpoint/DeliverableStore）
        - graph: 外部传入的执行图（由 Datapillar 构建）
        - entry_agent_id: 入口 Agent ID
        - agent_ids: Agent ID 列表（用于获取名称）
        - enable_learning: 是否启用经验学习
        - enable_react: 是否启用 ReAct 模式
        - auto_discover: 是否自动发现 Agent（兼容旧代码）
        - experience_store: 经验存储（兼容旧参数）
        - experience_policy: 沉淀策略
        """
        self.name = name
        self.team_id = team_id or "default"
        self._enable_react = enable_react
        self._agent_ids = agent_ids or []

        # 经验学习
        self._experience_learner: ExperienceLearner | None = None
        if enable_learning or experience_store:
            store = experience_store or ExperienceStore()
            self._experience_learner = ExperienceLearner(
                store=store,
                policy=experience_policy or AlwaysSavePolicy(),
            )
            logger.info("经验学习已启用")

        # 使用外部传入的图，或自动构建
        if graph is not None:
            # 外部传入（由 Datapillar 调用）
            self._graph = graph
            self._entry_agent_id = entry_agent_id or (agent_ids[0] if agent_ids else "")
            logger.info(
                f"Orchestrator 初始化（外部图）: {name} ({self.team_id}), 入口: {self._entry_agent_id}"
            )
        else:
            # 自动构建（兼容旧代码）
            if auto_discover:
                self._auto_discover()
            from src.modules.oneagentic.runtime.graph import AgentGraph

            self.agent_graph = AgentGraph()
            self._entry_agent_id = self._get_entry_agent_id()
            self._graph = self._build_react_graph() if enable_react else self._build_graph()
            mode_str = "ReAct 模式" if enable_react else "直接模式"
            logger.info(
                f"Orchestrator 初始化（自动构建）: {name}, 入口: {self._entry_agent_id}, {mode_str}"
            )

    def _auto_discover(self) -> None:
        """自动发现 Agent 模块"""
        from importlib.metadata import entry_points

        eps = entry_points(group="oneagentic.agents")
        for ep in eps:
            ep.load()
            logger.info(f"自动发现: {ep.name} -> {ep.value}")

    def _get_entry_agent_id(self) -> str:
        """获取入口 Agent ID"""
        entry = AgentRegistry.get_entry_agent()
        if entry:
            return entry.id

        # 没有配置入口，使用第一个
        agent_ids = AgentRegistry.list_ids()
        if agent_ids:
            logger.warning(f"未配置入口 Agent，使用: {agent_ids[0]}")
            return agent_ids[0]

        raise RuntimeError("没有注册任何 Agent")

    def _build_graph(self) -> StateGraph:
        """构建主图"""
        graph = StateGraph(Blackboard)

        # 注册 Agent 子图
        graph.add_node("agents", self.agent_graph._graph.compile())
        graph.add_node("finalize", self._finalize_node)

        # 入口
        graph.set_entry_point("agents")

        # 路由
        graph.add_conditional_edges(
            "agents",
            self._route_from_agents,
            {"agents": "agents", "finalize": "finalize"},
        )

        graph.add_edge("finalize", END)

        return graph

    def _route_from_agents(self, state: Blackboard) -> str:
        """Agent 执行后的路由"""
        if state.get("active_agent"):
            return "agents"
        return "finalize"

    def _build_react_graph(self) -> StateGraph:
        """
        构建 ReAct 模式执行图

        流程：
        1. react_controller: 规划/反思
        2. agents: 执行任务
        3. finalize: 完成

        路由：
        - react_controller → agents: 有任务需要执行
        - agents → react_controller: 任务完成，需要反思
        - react_controller → finalize: 流程结束
        """
        from functools import partial

        from src.infrastructure.llm.client import call_llm
        from src.modules.oneagentic.react.controller import react_controller_node

        graph = StateGraph(Blackboard)

        # 创建带 LLM 的 controller 节点
        llm = call_llm(temperature=0.0)
        controller_with_llm = partial(react_controller_node, llm=llm)

        # 注册节点
        graph.add_node("react_controller", controller_with_llm)
        graph.add_node("agents", self.agent_graph._graph.compile())
        graph.add_node("finalize", self._finalize_node)

        # 入口：从 controller 开始（规划）
        graph.set_entry_point("react_controller")

        # 路由：controller → agents 或 finalize
        graph.add_conditional_edges(
            "react_controller",
            self._route_from_controller,
            {"agents": "agents", "finalize": "finalize"},
        )

        # 路由：agents → controller（反思）
        graph.add_edge("agents", "react_controller")

        graph.add_edge("finalize", END)

        return graph

    def _route_from_controller(self, state: Blackboard) -> str:
        """Controller 执行后的路由"""
        if state.get("active_agent"):
            return "agents"
        return "finalize"

    async def _finalize_node(self, state: Blackboard) -> Command:
        """完成节点"""
        return Command(update={})

    async def compile(self):
        """
        编译图

        集成 Checkpoint 和 DeliverableStore。
        """
        async with Checkpoint.get_saver() as checkpointer:
            store = await DeliverableStore.get_store_instance()
            return self._graph.compile(checkpointer=checkpointer, store=store)

    def _get_thread_id(self, session_id: str, user_id: str) -> str:
        """生成 thread_id（包含 team_id 实现隔离）"""
        return f"{self.team_id}:user:{user_id}:session:{session_id}"

    async def stream(
        self,
        *,
        query: str,
        session_id: str,
        user_id: str,
        task_type: str = "general",
    ) -> AsyncGenerator[dict[str, Any], None]:
        """
        流式执行

        参数：
        - query: 用户输入
        - session_id: 会话 ID
        - user_id: 用户 ID
        - task_type: 任务类型（用于经验学习分类，默认 "general"）

        返回：
        - SSE 事件流（dict 格式）
        """
        thread_id = self._get_thread_id(session_id, user_id)
        config = {"configurable": {"thread_id": thread_id}}

        # 记录开始时间
        session_start_time = time.time()
        agent_count = 0
        tool_count = 0

        # 发送会话开始事件
        event_bus.emit(
            self,
            SessionStartedEvent(
                session_id=session_id,
                user_id=user_id,
                query=query,
            ),
        )

        # 经验学习：开始记录
        episode: Episode | None = None
        current_step: EpisodeStep | None = None
        if self._experience_learner:
            episode = self._experience_learner.start_episode(
                session_id=session_id,
                user_id=user_id,
                goal=query,
                team_id=self.name,
                task_type=task_type,
            )

        # 获取 checkpointer 和 store
        async with Checkpoint.get_saver() as checkpointer:
            store = await DeliverableStore.get_store_instance()
            app = self._graph.compile(checkpointer=checkpointer, store=store)

            # 检查是否有断点需要恢复
            existing_state = await app.aget_state(config)
            has_pending = existing_state and existing_state.next

            if has_pending:
                # 断点恢复：不需要新的 input，继续执行
                logger.info(f"检测到断点，恢复执行: thread={thread_id}, next={existing_state.next}")
                initial_state = None
            else:
                # 新执行：构建初始状态
                initial_state = create_blackboard(session_id=session_id, user_id=user_id)
                initial_state["messages"] = [HumanMessage(content=query)]
                initial_state["active_agent"] = self._entry_agent_id

            # 跟踪当前 Agent
            current_agent_id: str | None = None
            current_agent_name: str | None = None
            agent_start_time: float = 0.0

            # 跳过的节点
            _SKIP_NODES = {"__start__", "agents", "finalize"}

            # 执行结果跟踪
            has_error = False
            error_message: str | None = None

            # 流式执行
            try:
                async for event in app.astream_events(initial_state, config, version="v2"):
                    event_type = event.get("event", "")
                    event_name = event.get("name", "")
                    event_data = event.get("data", {})
                    event_metadata = event.get("metadata", {})
                    run_id = event.get("run_id")

                    # Agent 开始
                    if event_type == "on_chain_start":
                        node = event_metadata.get("langgraph_node", "")
                        if node and node not in _SKIP_NODES:
                            current_agent_id = node
                            current_agent_name = self._get_agent_name(node)
                            agent_start_time = time.time()
                            agent_count += 1

                            # 发送 Agent 开始事件
                            event_bus.emit(
                                self,
                                AgentStartedEvent(
                                    agent_id=current_agent_id,
                                    agent_name=current_agent_name,
                                    session_id=session_id,
                                    query=query,
                                ),
                            )

                            # 经验学习：记录 Agent 开始
                            if episode and self._should_learn(node):
                                current_step = EpisodeStep(
                                    agent_id=node,
                                    agent_name=current_agent_name,
                                    task_description=query[:100],
                                )

                            yield SseEvent.agent_start(
                                agent_id=current_agent_id,
                                agent_name=current_agent_name,
                                run_id=run_id,
                            ).to_dict()

                    # Agent 结束
                    elif event_type == "on_chain_end":
                        node = event_metadata.get("langgraph_node", "")
                        if node and node not in _SKIP_NODES:
                            agent_duration = (time.time() - agent_start_time) * 1000

                            # 发送 Agent 完成事件
                            event_bus.emit(
                                self,
                                AgentCompletedEvent(
                                    agent_id=node,
                                    agent_name=self._get_agent_name(node),
                                    session_id=session_id,
                                    duration_ms=agent_duration,
                                ),
                            )

                            # 经验学习：记录 Agent 结束
                            if episode and current_step and current_step.agent_id == node:
                                current_step.complete(
                                    outcome=Outcome.SUCCESS,
                                    output_summary="执行完成",
                                )
                                self._experience_learner.record_step(episode, current_step)
                                current_step = None

                            yield SseEvent.agent_end(
                                agent_id=node,
                                agent_name=self._get_agent_name(node),
                                run_id=run_id,
                            ).to_dict()

                    # 工具开始
                    elif event_type == "on_tool_start":
                        tool_count += 1

                        # 发送工具调用事件
                        event_bus.emit(
                            self,
                            ToolCalledEvent(
                                agent_id=current_agent_id or "",
                                tool_name=event_name,
                                tool_input=event_data.get("input", {}),
                            ),
                        )

                        # 经验学习：记录工具使用
                        if current_step:
                            current_step.tools_used.append(event_name)
                            current_step.tool_calls_count += 1

                        yield SseEvent.tool_start(
                            agent_id=current_agent_id or "",
                            agent_name=current_agent_name or "",
                            tool_name=event_name,
                            tool_input=event_data.get("input", {}),
                            run_id=run_id,
                        ).to_dict()

                    # 工具结束
                    elif event_type == "on_tool_end":
                        output = event_data.get("output", "")
                        if isinstance(output, str) and len(output) > 500:
                            output = output[:500] + "..."

                        # 发送工具完成事件
                        event_bus.emit(
                            self,
                            ToolCompletedEvent(
                                agent_id=current_agent_id or "",
                                tool_name=event_name,
                                tool_output=output,
                            ),
                        )

                        yield SseEvent.tool_end(
                            agent_id=current_agent_id or "",
                            agent_name=current_agent_name or "",
                            tool_name=event_name,
                            tool_output=output,
                            run_id=run_id,
                        ).to_dict()

                    # LLM 开始
                    elif event_type == "on_chat_model_start":
                        yield SseEvent.llm_start(
                            agent_id=current_agent_id or "",
                            agent_name=current_agent_name or "",
                            run_id=run_id,
                        ).to_dict()

                    # LLM 结束
                    elif event_type == "on_chat_model_end":
                        yield SseEvent.llm_end(
                            agent_id=current_agent_id or "",
                            agent_name=current_agent_name or "",
                            run_id=run_id,
                        ).to_dict()

            except Exception as e:
                has_error = True
                error_message = str(e)
                logger.error(f"执行出错: {e}")

                # 经验学习：记录失败步骤
                if episode and current_step:
                    current_step.complete(
                        outcome=Outcome.FAILURE,
                        error=error_message,
                    )
                    self._experience_learner.record_step(episode, current_step)

                raise

            # 获取最终状态
            final_state = await app.aget_state(config)
            final_message = "完成"
            deliverable = None
            deliverable_type = None

            if final_state and final_state.values:
                from langchain_core.messages import AIMessage

                messages = final_state.values.get("messages", [])

                for msg in reversed(messages):
                    if isinstance(msg, AIMessage):
                        final_message = msg.content or "完成"
                        deliverable = msg.additional_kwargs.get("deliverable")
                        deliverable_type = msg.additional_kwargs.get("deliverable_type")
                        break

            # 经验学习：完成并保存
            episode_id: str | None = None
            if episode and self._experience_learner:
                outcome = Outcome.FAILURE if has_error else Outcome.SUCCESS
                result = await self._experience_learner.complete_and_learn(
                    episode,
                    outcome=outcome,
                    result_summary=final_message[:200] if final_message else "",
                    deliverable_type=deliverable_type,
                    deliverable=deliverable,
                )
                if result.saved:
                    episode_id = episode.episode_id
                    logger.info(f"经验已保存: {episode_id}, 质量分: {result.quality_score:.2f}")

            yield SseEvent.result_event(
                message=final_message,
                deliverable=deliverable,
                deliverable_type=deliverable_type,
                episode_id=episode_id,
            ).to_dict()

            # 发送会话完成事件
            session_duration = (time.time() - session_start_time) * 1000
            event_bus.emit(
                self,
                SessionCompletedEvent(
                    session_id=session_id,
                    user_id=user_id,
                    result=deliverable,
                    duration_ms=session_duration,
                    agent_count=agent_count,
                    tool_count=tool_count,
                ),
            )

    def _should_learn(self, agent_id: str) -> bool:
        """判断 Agent 是否参与经验学习"""
        spec = AgentRegistry.get(agent_id)
        return spec.learn if spec else True

    def _get_agent_name(self, agent_id: str) -> str:
        """获取 Agent 名称"""
        spec = AgentRegistry.get(agent_id)
        return spec.name if spec else agent_id

    async def delete_session(self, session_id: str, user_id: str) -> None:
        """
        删除会话

        清理 Checkpoint 和 DeliverableStore 中的数据。

        参数：
        - session_id: 会话 ID
        - user_id: 用户 ID
        """
        thread_id = self._get_thread_id(session_id, user_id)

        # 删除 Checkpoint
        await Checkpoint.delete_thread(thread_id)

        # 删除交付物
        store = await DeliverableStore.get_store_instance()
        await DeliverableStore.clear(store, session_id, self.team_id)

        logger.info(f"会话已删除: team={self.team_id}, session={session_id}, user={user_id}")

    async def compact_session(self, session_id: str, user_id: str) -> dict:
        """
        手动压缩会话记忆

        类似 Claude Code 的 /compact 命令。

        参数：
        - session_id: 会话 ID
        - user_id: 用户 ID

        返回：
        - 压缩结果：
            - success: 是否成功
            - removed_count: 移除的条目数
            - tokens_saved: 节省的 token 数
            - message: 结果消息
        """
        from src.modules.oneagentic.memory.session_memory import SessionMemory

        thread_id = self._get_thread_id(session_id, user_id)
        config = {"configurable": {"thread_id": thread_id}}

        # 获取 checkpointer 和当前状态
        async with Checkpoint.get_saver() as checkpointer:
            store = await DeliverableStore.get_store_instance()
            app = self._graph.compile(checkpointer=checkpointer, store=store)

            # 获取当前状态
            state = await app.aget_state(config)
            if not state or not state.values:
                return {
                    "success": False,
                    "message": "会话不存在或没有状态",
                    "removed_count": 0,
                    "tokens_saved": 0,
                }

            # 获取记忆
            memory_data = state.values.get("memory")
            if not memory_data:
                return {
                    "success": False,
                    "message": "会话没有记忆数据",
                    "removed_count": 0,
                    "tokens_saved": 0,
                }

            # 恢复 SessionMemory
            memory = SessionMemory.model_validate(memory_data)

            # 执行压缩
            compact_result = await memory.compact()

            if not compact_result.success:
                return {
                    "success": False,
                    "message": compact_result.error or "压缩失败",
                    "removed_count": 0,
                    "tokens_saved": 0,
                }

            if compact_result.removed_count == 0:
                return {
                    "success": True,
                    "message": "无需压缩",
                    "removed_count": 0,
                    "tokens_saved": 0,
                }

            # 更新状态
            await app.aupdate_state(
                config,
                {"memory": memory.model_dump(mode="json")},
            )

            logger.info(
                f"📦 手动压缩完成: session={session_id}, "
                f"removed={compact_result.removed_count}, "
                f"saved={compact_result.tokens_saved} tokens"
            )

            return {
                "success": True,
                "message": f"压缩完成，移除 {compact_result.removed_count} 条记录",
                "removed_count": compact_result.removed_count,
                "tokens_saved": compact_result.tokens_saved,
            }

    async def get_session_stats(self, session_id: str, user_id: str) -> dict:
        """
        获取会话统计信息

        参数：
        - session_id: 会话 ID
        - user_id: 用户 ID

        返回：
        - 统计信息
        """
        from src.modules.oneagentic.memory.session_memory import SessionMemory

        thread_id = self._get_thread_id(session_id, user_id)
        config = {"configurable": {"thread_id": thread_id}}

        async with Checkpoint.get_saver() as checkpointer:
            store = await DeliverableStore.get_store_instance()
            app = self._graph.compile(checkpointer=checkpointer, store=store)

            state = await app.aget_state(config)
            if not state or not state.values:
                return {"exists": False}

            memory_data = state.values.get("memory")
            if not memory_data:
                return {"exists": True, "has_memory": False}

            memory = SessionMemory.model_validate(memory_data)
            stats = memory.get_stats()
            stats["exists"] = True
            stats["has_memory"] = True

            return stats
