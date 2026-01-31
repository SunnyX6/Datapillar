# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""
LangGraph node factory.

Responsibilities:
- Create agent node functions (registered by graphs/*.py)
- Execute agents and handle results
- Persist deliverables to the store
- Write to Blackboard via StateBuilder (messages, timeline, routing, etc.)

Out of scope:
- Routing decisions (handled by conditional edges in graphs/*.py)
- Execution mode selection (handled by specific graph files)
"""

from __future__ import annotations

import json
import logging
from typing import TYPE_CHECKING, Any

from langgraph.config import get_store
from langgraph.store.base import BaseStore
from langgraph.types import Command

from datapillar_oneagentic.context import ContextCollector, ContextScenario
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
from datapillar_oneagentic.todo.tool import extract_todo_plan, extract_todo_updates
from datapillar_oneagentic.exception import RecoveryAction
from datapillar_oneagentic.state import StateBuilder
from datapillar_oneagentic.messages.adapters.langchain import to_langchain
from datapillar_oneagentic.messages import Messages

if TYPE_CHECKING:
    from datapillar_oneagentic.core.agent import AgentSpec
    from datapillar_oneagentic.context.timeline.recorder import TimelineRecorder
    from datapillar_oneagentic.knowledge import KnowledgeConfig, KnowledgeService


logger = logging.getLogger(__name__)


class NodeFactory:
    """
    Node factory.

    Builds LangGraph node functions that wrap agent execution and result handling.
    """

    def __init__(
        self,
        *,
        agent_specs: list[AgentSpec],
        agent_ids: list[str],
        get_executor,
        timeline_recorder: "TimelineRecorder",
        namespace: str | None = None,
        knowledge_config_map: dict[str, "KnowledgeConfig"] | None = None,
        context_collector: ContextCollector | None = None,
        llm_provider=None,
    ):
        """
        Initialize the node factory.

        Args:
            agent_specs: list of agent specs
            agent_ids: list of agent IDs
            get_executor: executor factory
            namespace: team namespace
            knowledge_config_map: knowledge tool bindings by agent_id
        """
        self._agent_specs = agent_specs
        self._agent_ids = agent_ids
        self._spec_by_id = {spec.id: spec for spec in agent_specs}
        self._get_executor = get_executor
        self._timeline_recorder = timeline_recorder
        self._namespace = namespace
        self._knowledge_config_map = knowledge_config_map or {}
        self._knowledge_service_cache: dict[str, KnowledgeService] = {}
        self._context_collector = context_collector
        self._llm_provider = llm_provider

    def _resolve_knowledge_config(self, spec: AgentSpec | None) -> "KnowledgeConfig | None":
        if spec is None:
            return None
        return self._knowledge_config_map.get(spec.id)

    def _get_knowledge_service(self, *, config: "KnowledgeConfig") -> "KnowledgeService":
        if config is None:
            raise ValueError("Knowledge config is required for knowledge retrieval.")

        key = json.dumps(
            config.model_dump(mode="json", exclude_none=True),
            sort_keys=True,
            ensure_ascii=False,
        )
        cached = self._knowledge_service_cache.get(key)
        if cached is not None:
            return cached

        from datapillar_oneagentic.knowledge import KnowledgeService

        service = KnowledgeService(config=config)
        self._knowledge_service_cache[key] = service
        return service

    def _build_knowledge_tools(
        self,
        *,
        spec: AgentSpec | None,
        session_id: str,
    ) -> list[Any]:
        knowledge_binding = self._resolve_knowledge_config(spec)
        if knowledge_binding is None:
            return []
        existing_names = {getattr(t, "name", "") for t in (spec.tools or [])}
        if "knowledge_retrieve" in existing_names:
            logger.warning(
                "Tool name conflict: knowledge_retrieve already exists; skip injection: %s",
                spec.id,
            )
            return []

        from datapillar_oneagentic.tools.knowledge import create_knowledge_retrieve_tool

        default_config = knowledge_binding.model_copy(deep=True)
        if not default_config.namespaces:
            raise ValueError("KnowledgeConfig.namespaces is required for knowledge tool binding.")
        tool = create_knowledge_retrieve_tool(
            knowledge_config=default_config,
            get_service=self._get_knowledge_service,
            llm_provider=self._llm_provider,
        )
        tool.bound_namespaces = list(default_config.namespaces or [])
        return [tool]

    async def _should_clear_todo(
        self,
        *,
        current_todo: dict | None,
    ) -> bool:
        if current_todo:
            try:
                todo_list = SessionTodoList.model_validate(current_todo)
            except Exception as exc:
                logger.warning(f"Todo parse failed: {exc}")
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
        """Persist deliverable to the store and return success status."""
        if not result:
            return False
        if require_completed:
            if not hasattr(result, "status") or result.status != ExecutionStatus.COMPLETED:
                return False

        deliverable = getattr(result, "deliverable", None)
        if not deliverable:
            return False
        if store is None:
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
            logger.error(f"Failed to persist deliverable: {e}")
            return False

        return True

    async def _apply_todo_updates(
        self,
        *,
        todo_data: dict | None,
        updates: list,
    ) -> dict | None:
        """Apply todo updates and return the updated snapshot."""
        if not todo_data or not updates:
            return None

        try:
            return await apply_todo_updates(
                current_todo=todo_data,
                updates=updates,
            )
        except Exception as exc:
            logger.warning(f"Todo update failed: {exc}")
            return None

    async def _apply_todo_plan(
        self,
        *,
        session_id: str,
        todo_data: dict | None,
        ops: list,
    ) -> dict | None:
        """Apply todo planning operations and return the updated snapshot."""
        if not ops:
            return None

        try:
            return await apply_todo_plan(
                session_id=session_id,
                current_todo=todo_data,
                ops=ops,
            )
        except Exception as exc:
            logger.warning(f"Todo plan failed: {exc}")
            return None

    def create_agent_node(self, agent_id: str):
        """
        Create an agent node.

        Args:
            agent_id: agent ID

        Returns:
            Node function.
        """
        async def agent_node(state) -> Command:
            # Get store injected at compile time via get_store().
            store = get_store()

            sb = StateBuilder(state)
            query = sb.resolve_agent_query()

            # Get executor.
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
            # Runtime state is only for this run; messages are stored via checkpoint memory.
            run_state["messages"] = to_langchain(sb.memory.snapshot())

            # Execute (store is retrieved via get_store()).
            result = await executor.execute(
                query=query,
                state=run_state,
                additional_tools=knowledge_tools,
            )
            compression_context = _normalize_context_value(run_state.get("compression_context"))

            # Handle Command (delegation).
            if isinstance(result, Command):
                # Ensure the delegation target is within the team.
                goto = result.goto
                if goto and goto not in self._agent_ids:
                    logger.warning(
                        f"Agent {agent_id} attempted to delegate to {goto}, "
                        "but the target is not in the team; delegation ignored"
                    )
                    sb.routing.clear_active()
                    return Command(update=sb.patch())
                return result

            # Handle AgentResult.
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
        """Handle agent results."""
        sb = StateBuilder(state)
        namespace = sb.namespace
        session_id = sb.session_id

        if result is not None and hasattr(result, "status") and result.status == ExecutionStatus.FAILED:
            failure_kind = getattr(result, "failure_kind", None) or FailureKind.BUSINESS
            agent_error = AgentErrorClassifier.from_failure(
                agent_id=agent_id,
                error=getattr(result, "error", None) or "Agent execution failed",
                failure_kind=failure_kind,
            )
            raise agent_error

        # Persist agent deliverables to the store.
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

        # Todo updates and progress reporting (tool updates or audit fallback).
        current_todo = sb.todo.snapshot().todo
        if result and getattr(result, "messages", None):
            plan_ops = extract_todo_plan(result.messages)
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

        # Execution summary.
        sb.memory.append_execution_summary(
            agent_id=agent_id,
            execution_status=getattr(result, "status", None) if result else None,
            failure_kind=getattr(result, "failure_kind", None) if result else None,
            error=getattr(result, "error", None) if result else None,
            deliverable_key=agent_id if deliverable_saved else None,
        )

        # Update agent execution status.
        if result and hasattr(result, "status"):
            sb.routing.finish_agent(
                status=result.status,
                failure_kind=getattr(result, "failure_kind", None),
                error=getattr(result, "error", None),
            )
        else:
            sb.routing.clear_active()
            sb.routing.clear_task()

        # Flush timeline events.
        key = SessionKey(namespace=namespace, session_id=session_id)
        recorded_events = self._timeline_recorder.flush(key)
        if recorded_events:
            sb.timeline.record_events(recorded_events)

        return Command(update=sb.patch())

    def create_mapreduce_worker(self, worker_ids: list[str]):
        """
        Create a MapReduce worker node.

        Args:
            worker_ids: list of worker agent IDs (excluding reducer)

        Returns:
            Node function.
        """

        async def mapreduce_worker_node(state) -> Command:
            """Execute a single Map task."""
            sb = StateBuilder(state)
            task_data = sb.mapreduce.snapshot().current_task
            if not task_data:
                raise AgentError(
                    "MapReduce worker did not find task data",
                    agent_id="mapreduce_worker",
                    category=AgentErrorCategory.PROTOCOL,
                    action=RecoveryAction.FAIL_FAST,
                    failure_kind=FailureKind.SYSTEM,
                )

            try:
                task = MapReduceTask.model_validate(task_data)
            except Exception as exc:
                raise AgentError(
                    f"MapReduce worker task parsing failed: {exc}",
                    agent_id="mapreduce_worker",
                    category=AgentErrorCategory.PROTOCOL,
                    action=RecoveryAction.FAIL_FAST,
                    failure_kind=FailureKind.SYSTEM,
                    original=exc,
                ) from exc

            if task.agent_id not in worker_ids:
                raise AgentError(
                    f"MapReduce worker task assigned invalid agent: {task.agent_id}",
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
            # MapReduce worker does not share messages.
            worker_state["messages"] = []

            result = await executor.execute(
                query=task.input,
                state=worker_state,
                additional_tools=knowledge_tools,
            )
            compression_context = _normalize_context_value(worker_state.get("compression_context"))

            if isinstance(result, Command):
                raise AgentError(
                    f"MapReduce worker does not support delegation: {task.agent_id}",
                    agent_id=task.agent_id,
                    category=AgentErrorCategory.PROTOCOL,
                    action=RecoveryAction.FAIL_FAST,
                    failure_kind=FailureKind.SYSTEM,
                )
            if result is not None and hasattr(result, "status") and result.status == ExecutionStatus.FAILED:
                failure_kind = getattr(result, "failure_kind", None) or FailureKind.BUSINESS
                agent_error = AgentErrorClassifier.from_failure(
                    agent_id=task.agent_id,
                    error=getattr(result, "error", None) or "MapReduce worker execution failed",
                    failure_kind=failure_kind,
                )
                raise agent_error
            if result is not None and hasattr(result, "status") and result.status == ExecutionStatus.ABORTED:
                sb.routing.finish_agent(
                    status=ExecutionStatus.ABORTED,
                    failure_kind=None,
                    error=getattr(result, "error", None),
                )
                return Command(update=sb.patch())

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

    def create_mapreduce_reducer(
        self,
        *,
        reducer_agent_id: str,
        reducer_llm: Any,
        reducer_schema: Any,
    ):
        """
        Create a MapReduce reducer node.

        Args:
            reducer_agent_id: reducer agent ID (last agent)
            reducer_llm: LLM instance for reducer
            reducer_schema: reducer output schema

        Returns:
            Node function.
        """

        async def mapreduce_reducer_node(state) -> Command:
            """Aggregate Map results and produce final deliverable."""
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
                messages=Messages(),
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
        if key.endswith("_context"):
            state.pop(key, None)
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
