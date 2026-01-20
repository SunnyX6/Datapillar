"""
LangGraph 节点工厂

职责：
- 创建 Agent 节点函数（供 graphs/*.py 注册到图）
- 执行 Agent 并处理结果
- 存储 deliverable 到 Store
- 管理上下文（messages、timeline）

不负责：
- 路由决策（由 graphs/*.py 的条件边处理）
- 执行模式判断（由具体的 graph 文件处理）
"""

from __future__ import annotations

import logging
from typing import TYPE_CHECKING, Any

from langchain_core.messages import AIMessage, HumanMessage
from langgraph.config import get_store
from langgraph.store.base import BaseStore
from langgraph.types import Command

from datapillar_oneagentic.context import ContextBuilder
from datapillar_oneagentic.exception import AgentError, AgentErrorCategory, AgentErrorClassifier
from datapillar_oneagentic.core.status import ExecutionStatus, FailureKind
from datapillar_oneagentic.core.types import AgentResult, SessionKey
from datapillar_oneagentic.core.graphs.mapreduce.reducer import reduce_map_results
from datapillar_oneagentic.core.graphs.mapreduce.schemas import (
    MapReducePlan,
    MapReduceResult,
    MapReduceTask,
)
from datapillar_oneagentic.todo.session_todo import SessionTodoList, TodoUpdate
from datapillar_oneagentic.todo.store import (
    apply_todo_plan,
    apply_todo_updates,
)
from datapillar_oneagentic.todo.tool import extract_todo_plan_ops, extract_todo_updates
from datapillar_oneagentic.exception import RecoveryAction
from datapillar_oneagentic.tools.registry import tool as register_tool

if TYPE_CHECKING:
    from datapillar_oneagentic.core.agent import AgentSpec
    from datapillar_oneagentic.context.compaction import Compactor
    from datapillar_oneagentic.context.timeline.recorder import TimelineRecorder
    from datapillar_oneagentic.experience import ExperienceLearner
    from datapillar_oneagentic.knowledge import KnowledgeRetriever


def _extract_text(content: str | list | None) -> str:
    """
    从 Message.content 提取纯文本

    LangChain 的 Message.content 类型是 Union[str, List[Union[str, Dict]]]，
    多模态消息时 content 可能是 list。此函数统一提取文本内容。
    """
    if content is None:
        return ""
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        texts = []
        for item in content:
            if isinstance(item, str):
                texts.append(item)
            elif isinstance(item, dict) and item.get("type") == "text":
                texts.append(item.get("text", ""))
        return "\n".join(texts)
    return str(content)

logger = logging.getLogger(__name__)


def _extract_latest_task_id(state: dict) -> str | None:
    """从 state.messages 中提取最近的 TASK 指令 ID（仅 ReAct 模式）"""
    messages = state.get("messages", [])
    for msg in reversed(messages):
        if (
            isinstance(msg, AIMessage)
            and getattr(msg, "name", None) == "react_controller"
        ):
            text = _extract_text(msg.content)
            if text.startswith("【TASK "):
                # 期望格式: 【TASK t1】...
                try:
                    header = text.split("】", 1)[0]  # 【TASK t1
                    return header.replace("【TASK ", "").strip()
                except Exception:
                    return None
    return None


def _build_step_result_message(
    *,
    state: dict,
    agent_id: str,
    status: ExecutionStatus | str | None,
    failure_kind: FailureKind | str | None,
    error: str | None,
    deliverable_key: str | None,
) -> AIMessage:
    """构建框架写入的执行结果事件（用于稳定给 Reflector/回放提供证据）"""
    task_id = _extract_latest_task_id(state)

    parts = [f"【RESULT】agent={agent_id}"]
    if task_id:
        parts.append(f"task={task_id}")
    if status:
        status_value = status.value if hasattr(status, "value") else status
        parts.append(f"status={status_value}")
    if failure_kind:
        kind_value = failure_kind.value if hasattr(failure_kind, "value") else failure_kind
        parts.append(f"failure_kind={kind_value}")
    if deliverable_key:
        parts.append(f"deliverable={deliverable_key}")
    if error:
        parts.append(f"error={error}")

    return AIMessage(content=" ".join(parts), name="datapillar")


class NodeFactory:
    """
    节点工厂

    创建 LangGraph 节点函数，封装 Agent 执行和结果处理逻辑。
    """

    def __init__(
        self,
        *,
        agent_specs: list[AgentSpec],
        agent_ids: list[str],
        get_executor,
        enable_share_context: bool = True,
        compactor: "Compactor",
        timeline_recorder: "TimelineRecorder",
        knowledge_retriever: "KnowledgeRetriever | None" = None,
        experience_learner: "ExperienceLearner | None" = None,
    ):
        """
        初始化节点工厂

        Args:
            agent_specs: Agent 规格列表
            agent_ids: Agent ID 列表
            get_executor: 获取执行器的函数
            enable_share_context: 是否启用 Agent 间上下文共享（通过 messages 字段）
            knowledge_retriever: 知识检索器（可选）
            experience_learner: 经验学习器（可选）
        """
        self._agent_specs = agent_specs
        self._agent_ids = agent_ids
        self._spec_by_id = {spec.id: spec for spec in agent_specs}
        self._get_executor = get_executor
        self._enable_share_context = enable_share_context
        self._compactor = compactor
        self._timeline_recorder = timeline_recorder
        self._knowledge_retriever = knowledge_retriever
        self._experience_learner = experience_learner

    async def _build_knowledge_context(
        self,
        *,
        spec: AgentSpec | None,
        query: str,
        session_id: str,
        force_system: bool = False,
    ) -> str | None:
        if not spec or not spec.knowledge or self._knowledge_retriever is None:
            return None
        inject = self._knowledge_retriever.resolve_inject_config(spec.knowledge)
        inject_mode = (inject.mode or "tool").lower()
        if inject_mode == "tool" and not force_system:
            return None
        try:
            if inject_mode == "tool" and force_system:
                logger.info(f"MapReduce reducer 不支持 tool 注入，已强制使用 system: {spec.id}")
            result = await self._knowledge_retriever.retrieve(
                query=query,
                knowledge=spec.knowledge,
            )
            if result.refs and self._experience_learner is not None:
                refs = [ref.to_dict() for ref in result.refs]
                self._experience_learner.record_knowledge(session_id, refs)
            knowledge_text = ContextBuilder.build_knowledge_context(
                chunks=[chunk for chunk, _ in result.hits],
                inject=inject,
            )
            return knowledge_text or None
        except ValueError:
            raise
        except Exception as exc:
            logger.warning(f"知识检索失败: {exc}")
            return None

    def _build_knowledge_tools(
        self,
        *,
        spec: AgentSpec | None,
        session_id: str,
    ) -> list[Any]:
        if not spec or not spec.knowledge or self._knowledge_retriever is None:
            return []
        inject = self._knowledge_retriever.resolve_inject_config(spec.knowledge)
        inject_mode = (inject.mode or "tool").lower()
        if inject_mode != "tool":
            return []
        existing_names = {getattr(t, "name", "") for t in (spec.tools or [])}
        if "knowledge_retrieve" in existing_names:
            logger.warning(f"工具名冲突：knowledge_retrieve 已存在，跳过注入: {spec.id}")
            return []

        @register_tool("knowledge_retrieve")
        async def knowledge_retrieve(query: str) -> str:
            """检索知识并返回内容（由 Agent 按需调用）"""
            result = await self._knowledge_retriever.retrieve(
                query=query,
                knowledge=spec.knowledge,
            )
            if result.refs and self._experience_learner is not None:
                refs = [ref.to_dict() for ref in result.refs]
                self._experience_learner.record_knowledge(session_id, refs)
            knowledge_text = ContextBuilder.build_knowledge_context(
                chunks=[chunk for chunk, _ in result.hits],
                inject=inject,
            )
            return knowledge_text or "未找到相关知识。"

        return [knowledge_retrieve]

    async def _build_todo_context(
        self,
        *,
        state: dict,
    ) -> str | None:
        todo_data = state.get("todo")
        if not todo_data:
            return None

        try:
            todo = SessionTodoList.model_validate(todo_data)
        except Exception as exc:
            logger.warning(f"Todo 解析失败: {exc}")
            return None
        todo_prompt = todo.to_prompt()
        return todo_prompt or None

    async def _should_clear_todo(
        self,
        *,
        current_todo: dict | None,
    ) -> bool:
        if current_todo:
            try:
                todo_list = SessionTodoList.model_validate(current_todo)
            except Exception as exc:
                logger.warning(f"Todo 解析失败: {exc}")
                return False
        else:
            todo_list = None

        if todo_list is None:
            return False
        if not todo_list.items:
            return True
        return todo_list.is_completed()

    async def _persist_deliverable(
        self,
        *,
        agent_id: str,
        result: Any,
        store: BaseStore,
        namespace: str,
        session_id: str,
        deliverable_keys: list[str],
        require_completed: bool = True,
    ) -> bool:
        """写入 deliverable 到 Store，成功时返回是否写入"""
        if not result:
            return False
        if require_completed:
            if not hasattr(result, "status") or result.status != ExecutionStatus.COMPLETED:
                return False

        deliverable = getattr(result, "deliverable", None)
        if not deliverable:
            return False
        if not store:
            return False

        deliverable_value = (
            deliverable.model_dump(mode="json")
            if hasattr(deliverable, "model_dump")
            else deliverable
        )

        deliverable_namespace = ("deliverables", namespace, session_id)

        try:
            await store.aput(deliverable_namespace, agent_id, deliverable_value)
        except Exception as e:
            logger.error(f"存储 deliverable 失败: {e}")
            return False

        if agent_id not in deliverable_keys:
            deliverable_keys.append(agent_id)
        return True

    async def _apply_todo_updates(
        self,
        *,
        todo_data: dict | None,
        updates: list,
    ) -> dict | None:
        """应用 Todo 更新，返回更新后的快照"""
        if not todo_data or not updates:
            return None

        try:
            return await apply_todo_updates(
                current_todo=todo_data,
                updates=updates,
            )
        except Exception as exc:
            logger.warning(f"Todo 更新失败: {exc}")
            return None

    async def _apply_todo_plan(
        self,
        *,
        session_id: str,
        todo_data: dict | None,
        ops: list,
    ) -> dict | None:
        """应用 Todo 规划操作，返回更新后的快照"""
        if not ops:
            return None

        try:
            return await apply_todo_plan(
                session_id=session_id,
                current_todo=todo_data,
                ops=ops,
            )
        except Exception as exc:
            logger.warning(f"Todo 规划失败: {exc}")
            return None

    def _share_messages(
        self,
        ctx_builder: ContextBuilder,
        messages: list,
        *,
        is_completed: bool,
    ) -> None:
        """共享消息（completed 时移除最后一条结构化输出）"""
        if not messages:
            return

        messages_to_share = messages
        if is_completed and messages_to_share and isinstance(messages_to_share[-1], AIMessage):
            messages_to_share = messages_to_share[:-1]
        if messages_to_share:
            ctx_builder.add_messages(messages_to_share)

    def create_agent_node(self, agent_id: str):
        """
        创建 Agent 节点

        Args:
            agent_id: Agent ID

        Returns:
            节点函数
        """
        async def agent_node(state) -> Command:
            # 获取 store（通过 get_store() 获取编译时传入的 store）
            store = get_store()

            # 从 messages 获取输入
            messages = state.get("messages", [])
            query = ""

            assigned_task = state.get("assigned_task")
            if assigned_task:
                query = str(assigned_task)
            # ReAct 模式：优先使用 react_controller 写入的 TASK 指令作为 query
            elif state.get("plan"):
                for msg in reversed(messages):
                    if (
                        isinstance(msg, AIMessage)
                        and getattr(msg, "name", None) == "react_controller"
                        and _extract_text(msg.content).startswith("【TASK ")
                    ):
                        query = _extract_text(msg.content)
                        break

            # 非 ReAct 或未找到 TASK：回退到最后一条用户输入
            if not query:
                for msg in reversed(messages):
                    if isinstance(msg, HumanMessage):
                        query = _extract_text(msg.content)
                        break

            # 获取执行器
            executor = self._get_executor(agent_id)

            spec = self._spec_by_id.get(agent_id)
            run_state = dict(state)
            knowledge_tools = self._build_knowledge_tools(
                spec=spec,
                session_id=state.get("session_id", ""),
            )
            knowledge_context = await self._build_knowledge_context(
                spec=spec,
                query=query,
                session_id=state.get("session_id", ""),
            )
            if knowledge_context:
                run_state["knowledge_context"] = knowledge_context
            else:
                run_state.pop("knowledge_context", None)

            todo_context = await self._build_todo_context(
                state=state,
            )
            if todo_context:
                run_state["todo_context"] = todo_context
            else:
                run_state.pop("todo_context", None)

            # 执行（store 通过 get_store() 自动获取，无需传递）
            result = await executor.execute(
                query=query,
                state=run_state,
                additional_tools=knowledge_tools,
            )

            # 处理 Command（委派）
            if isinstance(result, Command):
                # 检查委派目标是否在团队内
                goto = result.goto
                if goto and goto not in self._agent_ids:
                    logger.warning(
                        f"Agent {agent_id} 试图委派给 {goto}，"
                        f"但该 Agent 不在团队内，忽略委派"
                    )
                    return Command(update={"active_agent": None})
                return result

            # 处理 AgentResult
            return await self._handle_result(
                state=state,
                agent_id=agent_id,
                result=result,
                store=store,
            )

        return agent_node

    async def _handle_result(
        self,
        *,
        state,
        agent_id: str,
        result: Any,
        store: BaseStore,
    ) -> Command:
        """处理 Agent 结果"""
        namespace = state["namespace"]
        session_id = state["session_id"]

        if result is not None and hasattr(result, "status") and result.status == ExecutionStatus.FAILED:
            failure_kind = getattr(result, "failure_kind", None) or FailureKind.BUSINESS
            agent_error = AgentErrorClassifier.from_failure(
                agent_id=agent_id,
                error=getattr(result, "error", None) or "Agent 执行失败",
                failure_kind=failure_kind,
            )
            raise agent_error

        # 使用 ContextBuilder 统一管理上下文
        ctx_builder = ContextBuilder.from_state(state, compactor=self._compactor)

        # 构建更新字典
        update_dict: dict = {}
        deliverable_saved = False

        # 存储 Agent 交付物到 Store
        deliverable_keys = list(state.get("deliverable_keys") or [])
        deliverable_saved = await self._persist_deliverable(
            agent_id=agent_id,
            result=result,
            store=store,
            namespace=namespace,
            session_id=session_id,
            deliverable_keys=deliverable_keys,
            require_completed=True,
        )
        if deliverable_saved:
            update_dict["deliverable_keys"] = deliverable_keys

        # Todo 变更与进度更新（基于工具上报或审计兜底）
        todo_data = state.get("todo")
        current_todo = todo_data
        if result and result.messages:
            plan_ops = extract_todo_plan_ops(result.messages)
            if plan_ops:
                planned_todo = await self._apply_todo_plan(
                    session_id=session_id,
                    todo_data=current_todo,
                    ops=plan_ops,
                )
                if planned_todo is not None:
                    current_todo = planned_todo
                    update_dict["todo"] = planned_todo

            updates = extract_todo_updates(result.messages)
            updated_todo = await self._apply_todo_updates(
                todo_data=current_todo,
                updates=updates,
            )
            if updated_todo is not None:
                current_todo = updated_todo
                update_dict["todo"] = updated_todo

            if await self._should_clear_todo(
                current_todo=current_todo,
            ):
                update_dict["todo"] = None

        # 启用上下文共享时，把 Agent 执行过程中的 messages 添加到 ContextBuilder
        # completed 场景排除最后一条 AIMessage（结构化输出），其余场景保留全部
        if self._enable_share_context and result and result.messages:
            self._share_messages(
                ctx_builder,
                result.messages,
                is_completed=result.status == ExecutionStatus.COMPLETED,
            )

        # 更新 Agent 执行状态
        if result and hasattr(result, "status"):
            update_dict["last_agent_status"] = result.status
            update_dict["last_agent_failure_kind"] = getattr(result, "failure_kind", None)
            update_dict["last_agent_error"] = getattr(result, "error", None)

        if state.get("assigned_task") is not None:
            update_dict["assigned_task"] = None

        # active_agent 设为 None，表示当前节点执行完毕
        # 具体路由由 graphs/*.py 的条件边决定
        update_dict["active_agent"] = None

        # 刷新 Timeline 事件（通过 ContextBuilder）
        key = SessionKey(namespace=namespace, session_id=session_id)
        recorded_events = self._timeline_recorder.flush(key)
        if recorded_events:
            ctx_builder.record_events(recorded_events)

        # 写入“每步执行结果”事件（框架产生，稳定供 ReAct 反思/回放使用）
        ctx_builder.add_messages(
            [
                _build_step_result_message(
                    state=state,
                    agent_id=agent_id,
                    status=getattr(result, "status", None) if result else None,
                    failure_kind=getattr(result, "failure_kind", None) if result else None,
                    error=getattr(result, "error", None) if result else None,
                    deliverable_key=agent_id if deliverable_saved else None,
                )
            ]
        )

        # 合并 ContextBuilder 的更新
        ctx_update = ctx_builder.to_state_update()
        update_dict["messages"] = ctx_update["messages"]
        if ctx_update.get("timeline"):
            update_dict["timeline"] = ctx_update["timeline"]

        return Command(update=update_dict)

    def create_mapreduce_worker_node(self, worker_ids: list[str]):
        """
        创建 MapReduce Worker 节点

        Args:
            worker_ids: 允许执行任务的 Agent ID 列表（不含 reducer）

        Returns:
            节点函数
        """

        async def mapreduce_worker_node(state) -> Command:
            """执行单个 Map 任务"""
            task_data = state.get("mapreduce_task")
            if not task_data:
                raise AgentError(
                    "MapReduce Worker 未找到任务数据",
                    agent_id="mapreduce_worker",
                    category=AgentErrorCategory.PROTOCOL,
                    action=RecoveryAction.FAIL_FAST,
                    failure_kind=FailureKind.SYSTEM,
                )

            try:
                task = MapReduceTask.model_validate(task_data)
            except Exception as exc:
                raise AgentError(
                    f"MapReduce Worker 任务解析失败: {exc}",
                    agent_id="mapreduce_worker",
                    category=AgentErrorCategory.PROTOCOL,
                    action=RecoveryAction.FAIL_FAST,
                    failure_kind=FailureKind.SYSTEM,
                    original=exc,
                ) from exc

            if task.agent_id not in worker_ids:
                raise AgentError(
                    f"MapReduce Worker 任务指定了无效 Agent: {task.agent_id}",
                    agent_id=task.agent_id,
                    category=AgentErrorCategory.PROTOCOL,
                    action=RecoveryAction.FAIL_FAST,
                    failure_kind=FailureKind.SYSTEM,
                )

            executor = self._get_executor(task.agent_id)

            worker_state = dict(state)
            worker_state["messages"] = []
            spec = self._spec_by_id.get(task.agent_id)
            knowledge_tools = self._build_knowledge_tools(
                spec=spec,
                session_id=state.get("session_id", ""),
            )
            knowledge_context = await self._build_knowledge_context(
                spec=spec,
                query=task.input,
                session_id=state.get("session_id", ""),
            )
            if knowledge_context:
                worker_state["knowledge_context"] = knowledge_context
            else:
                worker_state.pop("knowledge_context", None)

            result = await executor.execute(
                query=task.input,
                state=worker_state,
                additional_tools=knowledge_tools,
            )

            if isinstance(result, Command):
                raise AgentError(
                    f"MapReduce Worker 不支持委派: {task.agent_id}",
                    agent_id=task.agent_id,
                    category=AgentErrorCategory.PROTOCOL,
                    action=RecoveryAction.FAIL_FAST,
                    failure_kind=FailureKind.SYSTEM,
                )
            if result is not None and hasattr(result, "status") and result.status == ExecutionStatus.FAILED:
                failure_kind = getattr(result, "failure_kind", None) or FailureKind.BUSINESS
                agent_error = AgentErrorClassifier.from_failure(
                    agent_id=task.agent_id,
                    error=getattr(result, "error", None) or "MapReduce Worker 执行失败",
                    failure_kind=failure_kind,
                )
                raise agent_error

            deliverable = getattr(result, "deliverable", None)
            output = (
                deliverable.model_dump(mode="json")
                if hasattr(deliverable, "model_dump")
                else deliverable
            )
            todo_updates = []
            if hasattr(result, "messages") and result.messages:
                todo_updates = [
                    update.model_dump(mode="json")
                    for update in extract_todo_updates(result.messages)
                ]
            map_result = MapReduceResult(
                task_id=task.id,
                agent_id=task.agent_id,
                description=task.description,
                input=task.input,
                status=getattr(result, "status", ExecutionStatus.FAILED),
                output=output,
                error=getattr(result, "error", None),
                failure_kind=getattr(result, "failure_kind", None),
                todo_updates=todo_updates,
            )

            update = {"mapreduce_results": [map_result.model_dump(mode="json")]}

            return Command(update=update)

        return mapreduce_worker_node

    def create_mapreduce_reducer_node(
        self,
        *,
        reducer_agent_id: str,
        reducer_llm: Any,
        reducer_schema: Any,
    ):
        """
        创建 MapReduce Reducer 节点

        Args:
            reducer_agent_id: Reducer 的 Agent ID（最后一个 Agent）
            reducer_llm: Reducer 使用的 LLM 实例
            reducer_schema: Reducer 输出 Schema

        Returns:
            节点函数
        """

        async def mapreduce_reducer_node(state) -> Command:
            """汇总 Map 结果并产出最终交付物"""
            namespace = state["namespace"]
            session_id = state["session_id"]
            store = get_store()

            tasks_data = state.get("mapreduce_tasks") or []
            results_data = state.get("mapreduce_results") or []

            plan = MapReducePlan(
                goal=state.get("mapreduce_goal") or "",
                understanding=state.get("mapreduce_understanding") or "",
                tasks=[MapReduceTask.model_validate(t) for t in tasks_data],
            )
            results = [MapReduceResult.model_validate(r) for r in results_data]

            experience_context = state.get("experience_context")
            deliverable = await reduce_map_results(
                plan=plan,
                results=results,
                llm=reducer_llm,
                output_schema=reducer_schema,
                experience_context=experience_context,
            )

            reducer_result = AgentResult.completed(
                deliverable=deliverable,
                deliverable_type=reducer_agent_id,
                messages=[],
            )

            deliverable_keys = list(state.get("deliverable_keys") or [])

            await self._persist_deliverable(
                agent_id=reducer_agent_id,
                result=reducer_result,
                store=store,
                namespace=namespace,
                session_id=session_id,
                deliverable_keys=deliverable_keys,
                require_completed=True,
            )

            update: dict[str, Any] = {
                "deliverable_keys": deliverable_keys,
                "active_agent": None,
            }

            update["last_agent_status"] = ExecutionStatus.COMPLETED
            update["last_agent_failure_kind"] = None
            update["last_agent_error"] = None

            todo_data = state.get("todo")
            if todo_data:
                updates: list[TodoUpdate] = []
                for result in results:
                    for update in result.todo_updates or []:
                        try:
                            updates.append(TodoUpdate.model_validate(update))
                        except Exception:
                            continue
                updated_todo = await self._apply_todo_updates(
                    todo_data=todo_data,
                    updates=updates,
                )
                if updated_todo:
                    update["todo"] = updated_todo

            key = SessionKey(namespace=namespace, session_id=session_id)
            recorded_events = self._timeline_recorder.flush(key)
            if recorded_events:
                ctx_builder = ContextBuilder.from_state(state, compactor=self._compactor)
                ctx_builder.record_events(recorded_events)
                timeline_update = ctx_builder.get_timeline_update()
                if timeline_update:
                    update["timeline"] = timeline_update

            return Command(update=update)

        return mapreduce_reducer_node
