"""
LangGraph 节点工厂

职责：
- 创建 Agent 节点函数（供 graphs/*.py 注册到图）
- 执行 Agent 并处理结果
- 存储 deliverable 到 Store
- 通过 StateBuilder 统一写入 Blackboard（messages、timeline、routing 等）

不负责：
- 路由决策（由 graphs/*.py 的条件边处理）
- 执行模式判断（由具体的 graph 文件处理）
"""

from __future__ import annotations

import logging
from typing import TYPE_CHECKING, Any

from langgraph.config import get_store
from langgraph.store.base import BaseStore
from langgraph.types import Command
from pydantic import BaseModel, Field

from datapillar_oneagentic.context import ContextBuilder, ContextCollector, ContextScenario
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
from datapillar_oneagentic.state import StateBuilder

if TYPE_CHECKING:
    from datapillar_oneagentic.core.agent import AgentSpec
    from datapillar_oneagentic.context.timeline.recorder import TimelineRecorder
    from datapillar_oneagentic.experience import ExperienceLearner
    from datapillar_oneagentic.knowledge import KnowledgeRetriever


logger = logging.getLogger(__name__)


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
        timeline_recorder: "TimelineRecorder",
        knowledge_retriever: "KnowledgeRetriever | None" = None,
        experience_learner: "ExperienceLearner | None" = None,
        context_collector: ContextCollector | None = None,
    ):
        """
        初始化节点工厂

        Args:
            agent_specs: Agent 规格列表
            agent_ids: Agent ID 列表
            get_executor: 获取执行器的函数
            knowledge_retriever: 知识检索器（可选）
            experience_learner: 经验学习器（可选）
        """
        self._agent_specs = agent_specs
        self._agent_ids = agent_ids
        self._spec_by_id = {spec.id: spec for spec in agent_specs}
        self._get_executor = get_executor
        self._timeline_recorder = timeline_recorder
        self._knowledge_retriever = knowledge_retriever
        self._experience_learner = experience_learner
        self._context_collector = context_collector

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

        class _KnowledgeRetrieveInput(BaseModel):
            query: str = Field(description="User query text")

        # 这是框架内部注入的工具：显式声明 args_schema，避免 LangChain 对 docstring 的严格解析导致运行期报错。
        @register_tool("knowledge_retrieve", args_schema=_KnowledgeRetrieveInput)
        async def knowledge_retrieve(query: str) -> str:
            """Retrieve knowledge and return content (call when needed)."""
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
            return knowledge_text or "No relevant knowledge found."

        return [knowledge_retrieve]

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

            sb = StateBuilder(state)
            query = sb.resolve_agent_query()

            # 获取执行器
            executor = self._get_executor(agent_id)

            spec = self._spec_by_id.get(agent_id)
            knowledge_tools = self._build_knowledge_tools(
                spec=spec,
                session_id=sb.session_id,
            )
            run_state = dict(state)
            if self._context_collector is not None:
                contexts = await self._context_collector.collect(
                    scenario=ContextScenario.AGENT,
                    state=state,
                    query=query,
                    session_id=sb.session_id,
                    spec=spec,
                    has_knowledge_tool=bool(knowledge_tools),
                )
                _apply_runtime_contexts(run_state, contexts)
            # runtime state 仅用于本次执行，messages 使用 checkpoint 记忆（已清洗）
            run_state["messages"] = sb.memory.snapshot()

            # 执行（store 通过 get_store() 自动获取，无需传递）
            result = await executor.execute(
                query=query,
                state=run_state,
                additional_tools=knowledge_tools,
            )
            compression_context = _normalize_context_value(run_state.get("compression__context"))

            # 处理 Command（委派）
            if isinstance(result, Command):
                # 检查委派目标是否在团队内
                goto = result.goto
                if goto and goto not in self._agent_ids:
                    logger.warning(
                        f"Agent {agent_id} 试图委派给 {goto}，"
                        f"但该 Agent 不在团队内，忽略委派"
                    )
                    sb.routing.clear_active()
                    return Command(update=sb.patch())
                return result

            # 处理 AgentResult
            return await self._handle_result(
                state=state,
                agent_id=agent_id,
                result=result,
                store=store,
                compression_context=compression_context,
            )

        return agent_node

    async def _handle_result(
        self,
        *,
        state,
        agent_id: str,
        result: Any,
        store: BaseStore,
        compression_context: str | None,
    ) -> Command:
        """处理 Agent 结果"""
        sb = StateBuilder(state)
        namespace = sb.namespace
        session_id = sb.session_id

        if result is not None and hasattr(result, "status") and result.status == ExecutionStatus.FAILED:
            failure_kind = getattr(result, "failure_kind", None) or FailureKind.BUSINESS
            agent_error = AgentErrorClassifier.from_failure(
                agent_id=agent_id,
                error=getattr(result, "error", None) or "Agent 执行失败",
                failure_kind=failure_kind,
            )
            raise agent_error

        # 存储 Agent 交付物到 Store
        deliverable_saved = await self._persist_deliverable(
            agent_id=agent_id,
            result=result,
            store=store,
            namespace=namespace,
            session_id=session_id,
            require_completed=True,
        )
        if deliverable_saved:
            sb.deliverables.record_saved(agent_id)

        if compression_context is not None:
            sb.compression.persist_compression(compression_context)

        # Todo 变更与进度更新（基于工具上报或审计兜底）
        current_todo = sb.todo.snapshot().todo
        if result and getattr(result, "messages", None):
            plan_ops = extract_todo_plan_ops(result.messages)
            if plan_ops:
                planned_todo = await self._apply_todo_plan(
                    session_id=session_id,
                    todo_data=current_todo,
                    ops=plan_ops,
                )
                if planned_todo is not None:
                    current_todo = planned_todo
                    sb.todo.replace(planned_todo)

            updates = extract_todo_updates(result.messages)
            updated_todo = await self._apply_todo_updates(
                todo_data=current_todo,
                updates=updates,
            )
            if updated_todo is not None:
                current_todo = updated_todo
                sb.todo.replace(updated_todo)

            if await self._should_clear_todo(
                current_todo=current_todo,
            ):
                sb.todo.clear()

        # 执行摘要
        sb.memory.append_execution_summary(
            agent_id=agent_id,
            execution_status=getattr(result, "status", None) if result else None,
            failure_kind=getattr(result, "failure_kind", None) if result else None,
            error=getattr(result, "error", None) if result else None,
            deliverable_key=agent_id if deliverable_saved else None,
        )

        # 更新 Agent 执行状态
        if result and hasattr(result, "status"):
            sb.routing.finish_agent(
                status=result.status,
                failure_kind=getattr(result, "failure_kind", None),
                error=getattr(result, "error", None),
            )
        else:
            sb.routing.clear_active()
            sb.routing.clear_task()

        # 刷新 Timeline 事件
        key = SessionKey(namespace=namespace, session_id=session_id)
        recorded_events = self._timeline_recorder.flush(key)
        if recorded_events:
            sb.timeline.record_events(recorded_events)

        return Command(update=sb.patch())

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
            sb = StateBuilder(state)
            task_data = sb.mapreduce.snapshot().current_task
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

            spec = self._spec_by_id.get(task.agent_id)
            knowledge_tools = self._build_knowledge_tools(
                spec=spec,
                session_id=sb.session_id,
            )
            worker_state = dict(state)
            if self._context_collector is not None:
                contexts = await self._context_collector.collect(
                    scenario=ContextScenario.MAPREDUCE_WORKER,
                    state=state,
                    query=task.input,
                    session_id=sb.session_id,
                    spec=spec,
                    has_knowledge_tool=bool(knowledge_tools),
                )
                _apply_runtime_contexts(worker_state, contexts)
            # MapReduce worker 不需要共享 messages
            worker_state["messages"] = []

            result = await executor.execute(
                query=task.input,
                state=worker_state,
                additional_tools=knowledge_tools,
            )
            compression_context = _normalize_context_value(worker_state.get("compression__context"))

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
            sb.mapreduce.append_results([map_result.model_dump(mode="json")])
            if compression_context is not None:
                sb.compression.persist_compression(compression_context)
            return Command(update=sb.patch())

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
            sb = StateBuilder(state)
            namespace = sb.namespace
            session_id = sb.session_id
            store = get_store()

            map_snap = sb.mapreduce.snapshot()
            tasks_data = map_snap.tasks
            results_data = map_snap.results

            plan = MapReducePlan(
                goal=map_snap.goal or "",
                understanding=map_snap.understanding or "",
                tasks=[MapReduceTask.model_validate(t) for t in tasks_data],
            )
            results = [MapReduceResult.model_validate(r) for r in results_data]

            contexts: dict[str, str] = {}
            if self._context_collector is not None and plan.goal:
                contexts = await self._context_collector.collect(
                    scenario=ContextScenario.MAPREDUCE_REDUCER,
                    state=state,
                    query=plan.goal,
                    session_id=session_id,
                    spec=None,
                )
            deliverable = await reduce_map_results(
                plan=plan,
                results=results,
                llm=reducer_llm,
                output_schema=reducer_schema,
                contexts=contexts,
            )

            reducer_result = AgentResult.completed(
                deliverable=deliverable,
                deliverable_type=reducer_agent_id,
                messages=[],
            )

            deliverable_saved = await self._persist_deliverable(
                agent_id=reducer_agent_id,
                result=reducer_result,
                store=store,
                namespace=namespace,
                session_id=session_id,
                require_completed=True,
            )
            if deliverable_saved:
                sb.deliverables.record_saved(reducer_agent_id)
            sb.routing.finish_agent(status=ExecutionStatus.COMPLETED, failure_kind=None, error=None)

            todo_data = sb.todo.snapshot().todo
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
                    sb.todo.replace(updated_todo)

            key = SessionKey(namespace=namespace, session_id=session_id)
            recorded_events = self._timeline_recorder.flush(key)
            if recorded_events:
                sb.timeline.record_events(recorded_events)
            return Command(update=sb.patch())

        return mapreduce_reducer_node


def _apply_runtime_contexts(state: dict, contexts: dict[str, str]) -> None:
    for key in list(state.keys()):
        if key.endswith("__context"):
            state.pop(key, None)
    state.pop("knowledge_context", None)
    state.pop("experience_context", None)
    state.pop("todo_context", None)
    for key, value in contexts.items():
        state[key] = value


def _normalize_context_value(value: object) -> str | None:
    if value is None:
        return None
    text = value if isinstance(value, str) else str(value)
    text = text.strip()
    return text or None
