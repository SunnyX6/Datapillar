# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""
Datapillar team class.

Organizes multiple agents to complete complex tasks.

Core concepts:
- namespace: top-level isolation boundary (required)
- session_id: session identifier
- SessionKey: namespace + session_id for full isolation
- Sessions/deliverables are isolated by namespace; experience/knowledge vector stores
  use a fixed database and are isolated by a namespace column (knowledge namespace is from KnowledgeConfig)

Example:
```python
from datapillar_oneagentic import Datapillar, DatapillarConfig, Process

# Import defined Agent classes
from my_agents import AnalystAgent, ReporterAgent

# Build a team (namespace is required)
config = DatapillarConfig(
    llm={"api_key": "sk-xxx", "model": "gpt-4o"},
)

team = Datapillar(
    config=config,
    namespace="sales_app",  # data isolation boundary
    name="Analysis Team",
    agents=[AnalystAgent, ReporterAgent],
    process=Process.DYNAMIC,
    enable_learning=True,
)

# Streamed execution
async for event in team.stream(
    query="Analyze sales data",
    session_id="session_001",
):
    print(event)

# Save experience
await team.save_experience(session_id="session_001", feedback={"stars": 5})
```
"""

from __future__ import annotations

import copy
import logging
from collections.abc import AsyncGenerator
from typing import Any, TYPE_CHECKING

from datapillar_oneagentic.config import DatapillarConfig
from datapillar_oneagentic.core.agent import AgentSpec, get_agent_spec
from datapillar_oneagentic.core.graphs import build_graph
from datapillar_oneagentic.core.nodes import NodeFactory
from datapillar_oneagentic.core.process import Process
from datapillar_oneagentic.core.types import SessionKey
from datapillar_oneagentic.context import ContextCollector
from datapillar_oneagentic.context.compaction import CompactPolicy, get_compactor
from datapillar_oneagentic.context.timeline.recorder import TimelineRecorder
from datapillar_oneagentic.events import EventBus
from datapillar_oneagentic.log import bind_log_context, setup_logging
from datapillar_oneagentic.providers.llm import EmbeddingProvider, LLMProvider

if TYPE_CHECKING:
    from datapillar_oneagentic.knowledge import KnowledgeConfig

logger = logging.getLogger(__name__)


class Datapillar:
    """
    Datapillar agent team.

    Organizes multiple agents to complete complex tasks.

    Features:
    - Namespace isolation: sessions/deliverables are isolated by namespace
    - Multiple execution modes: sequential, dynamic, hierarchical, MapReduce, ReAct
    - Delegation constraints: delegation only within the team
    - Memory management: optional session memory
    - Experience learning: optional experience recording
    - Resource safety: connections created per run and closed afterward
    - Knowledge tool binding: Datapillar(knowledge=...) binds tools for the team
    """

    @classmethod
    def _clear_registry(cls) -> None:
        """Compatibility hook (no global registry in the framework)."""
        pass

    def __init__(
        self,
        *,
        config: DatapillarConfig,
        namespace: str,
        name: str,
        agents: list[type],
        process: Process = Process.SEQUENTIAL,
        enable_learning: bool = False,
        enable_share_context: bool = True,
        a2a_agents: list | None = None,
        verbose: bool = False,
        knowledge: "KnowledgeConfig | None" = None,
    ):
        """
        Create a team.

        Args:
            config: DatapillarConfig (required, team-level config)
            namespace: namespace (required, top-level isolation boundary)
            name: team name
            agents: list of Agent classes (must use @agent decorator)
            process: execution mode (SEQUENTIAL/DYNAMIC/HIERARCHICAL/MAPREDUCE/REACT)
                - In MAPREDUCE, the last agent only provides output schema
            enable_learning: enable experience learning (default False)
            enable_share_context: enable shared agent context (default True)
            a2a_agents: team-level A2A remote agent configs
            verbose: enable verbose logging (default False)
            knowledge: knowledge tool binding (store + retrieve defaults)
        """
        self._config = config
        self.namespace = namespace
        self.name = name
        self.process = process
        self.enable_learning = enable_learning
        self.enable_share_context = enable_share_context
        self.a2a_agents = a2a_agents or []
        # Ensure verbose flag is respected (examples rely on verbose=True for debug logs).
        self.verbose = verbose or self._config.verbose

        # Validate config.
        self._config.validate_llm()

        # Initialize logging
        if self.verbose:
            setup_logging(logging.DEBUG)
        else:
            setup_logging(getattr(logging, self._config.log_level.upper(), logging.INFO))

        # Resolve agent classes into AgentSpec.
        self._agent_specs = self._resolve_agents(agents)
        self._agent_ids = [spec.id for spec in self._agent_specs]
        self._agent_name_map = {spec.id: spec.name for spec in self._agent_specs}

        # Resolve knowledge tool bindings (team + agent).
        self._knowledge_config = knowledge
        self._knowledge_config_map: dict[str, KnowledgeConfig] = {}
        for spec in self._agent_specs:
            if spec.knowledge is not None and self._knowledge_config is not None:
                if spec.knowledge.model_dump(mode="json", exclude_none=True) != (
                    self._knowledge_config.model_dump(mode="json", exclude_none=True)
                ):
                    raise ValueError(
                        f"Agent {spec.id} has a different knowledge config than the team binding."
                    )
            bound = spec.knowledge or self._knowledge_config
            if bound is not None:
                self._knowledge_config_map[spec.id] = bound

        if enable_learning:
            self._config.validate_embedding()
        if self._knowledge_config_map:
            self._validate_knowledge_configs()

        # Merge team-level A2A config into each agent.
        if self.a2a_agents:
            for spec in self._agent_specs:
                spec.a2a_agents = list(spec.a2a_agents) + list(self.a2a_agents)

        # Validate.
        self._validate()

        # Set entry agent (first).
        self._entry_agent_id = self._agent_specs[0].id if self._agent_specs else None

        # Team-level event bus and timeline.
        self._event_bus = EventBus()
        self._timeline_recorder = TimelineRecorder(self._event_bus)
        self._timeline_recorder.register()

        # Team-level LLM provider and context compactor.
        self._llm_provider = LLMProvider(self._config.llm, event_bus=self._event_bus)
        compaction_policy = CompactPolicy(
            min_keep_entries=self._config.context.compact_min_keep_entries
        )
        self._compactor = get_compactor(
            llm=self._llm_provider(),
            policy=compaction_policy,
        )

        # Executor cache (per team).
        self._executors: dict[str, Any] = {}

        # Experience learning components.
        self._learning_embedding_provider = None
        self._learning_store = None
        self._experience_learner = None
        self._experience_retriever = None

        if enable_learning:
            self._learning_embedding_provider = EmbeddingProvider(self._config.embedding)
            from datapillar_oneagentic.experience import ExperienceLearner, ExperienceRetriever
            from datapillar_oneagentic.storage import create_learning_store

            self._learning_store = create_learning_store(
                namespace,
                vector_store_config=self._config.learning.vector_store,
                embedding_config=self._config.embedding,
            )
            self._experience_learner = ExperienceLearner(
                store=self._learning_store,
                namespace=namespace,
                embedding_provider=self._learning_embedding_provider,
            )
            self._experience_retriever = ExperienceRetriever(
                store=self._learning_store,
                embedding_provider=self._learning_embedding_provider,
            )
            logger.info(
                "Experience learning enabled",
                extra={"event": "experience.enabled", "namespace": namespace},
            )

        if self._knowledge_config_map:
            logger.info(
                "Knowledge retrieval enabled",
                extra={"event": "knowledge.enabled", "namespace": namespace},
            )

        self._context_collector = ContextCollector(
            experience_retriever=self._experience_retriever,
            experience_learner=self._experience_learner,
            share_agent_context=self.enable_share_context,
        )

        # Create node factory.
        self._node_factory = NodeFactory(
            agent_specs=self._agent_specs,
            agent_ids=self._agent_ids,
            get_executor=self._get_executor,
            timeline_recorder=self._timeline_recorder,
            namespace=self.namespace,
            knowledge_config_map=self._knowledge_config_map,
            context_collector=self._context_collector,
            llm_provider=self._llm_provider,
        )

        # Create LLM for ReAct mode.
        self._react_llm = None
        if process == Process.REACT:
            self._react_llm = self._llm_provider()
            logger.info("ReAct mode enabled", extra={"event": "process.react.enabled"})

        # Create LLM for MapReduce mode.
        self._mapreduce_llm = None
        if process == Process.MAPREDUCE:
            self._mapreduce_llm = self._llm_provider()
            logger.info("MapReduce mode enabled", extra={"event": "process.mapreduce.enabled"})

        # Build execution graph (StateGraph, not compiled yet).
        self._graph = build_graph(
            process=process,
            agent_specs=self._agent_specs,
            entry_agent_id=self._entry_agent_id,
            agent_ids=self._agent_ids,
            create_agent_node=self._node_factory.create_agent_node,
            create_mapreduce_worker=self._node_factory.create_mapreduce_worker,
            create_mapreduce_reducer=self._node_factory.create_mapreduce_reducer,
            llm=self._react_llm or self._mapreduce_llm,
            context_collector=self._context_collector,
        )

        logger.info(
            "Datapillar created",
            extra={
                "event": "team.created",
                "namespace": namespace,
                "data": {
                    "name": name,
                    "agents": [s.name for s in self._agent_specs],
                    "process": process.value,
                    "entry_agent_id": self._entry_agent_id,
                },
            },
        )

    def _resolve_agents(self, agent_classes: list[type]) -> list[AgentSpec]:
        """Resolve Agent classes into AgentSpec."""
        specs = []

        for cls in agent_classes:
            spec = get_agent_spec(cls)
            if spec is None:
                raise ValueError(
                    f"Agent class {cls.__name__} is not registered. "
                    "Ensure it uses the @agent decorator and is imported."
                )
            # Deep copy for team isolation.
            specs.append(copy.deepcopy(spec))

        return specs

    def _validate_knowledge_configs(self) -> None:
        """Validate knowledge configs for tool bindings."""
        for agent_id, config in self._knowledge_config_map.items():
            if not config.embedding.is_configured():
                raise ValueError(
                    f"Knowledge embedding is not configured for agent {agent_id}."
                )
            if not config.embedding.dimension:
                raise ValueError(
                    f"Knowledge embedding dimension is required for agent {agent_id}."
                )

    def _validate(self) -> None:
        """Validate configuration."""
        if not self._agent_specs:
            raise ValueError("agents must not be empty")
        if self.process == Process.MAPREDUCE and len(self._agent_specs) < 2:
            raise ValueError("MAPREDUCE requires at least 2 agents (last agent is reducer)")

        # Validate delegation constraints.
        for spec in self._agent_specs:
            for delegate_to in spec.can_delegate_to:
                if delegate_to not in self._agent_ids:
                    logger.warning(
                        "Delegate target not in team; ignoring",
                        extra={
                            "event": "agent.delegate.ignored",
                            "agent_id": spec.id,
                            "data": {
                                "delegate_to": delegate_to,
                                "team_agents": self._agent_ids,
                            },
                        },
                    )

    def _get_executor(self, agent_id: str):
        """Get executor (with cache)."""
        from datapillar_oneagentic.runtime.executor import AgentExecutor

        if agent_id not in self._executors:
            spec = next((s for s in self._agent_specs if s.id == agent_id), None)
            if not spec:
                raise KeyError(f"Agent {agent_id} is not in the team")

            # DYNAMIC: auto-populate can_delegate_to.
            if self.process == Process.DYNAMIC:
                spec.can_delegate_to = [aid for aid in self._agent_ids if aid != agent_id]

            # SEQUENTIAL: no delegation.
            elif self.process == Process.SEQUENTIAL:
                spec.can_delegate_to = []

            # HIERARCHICAL: only Manager can delegate.
            elif self.process == Process.HIERARCHICAL:
                if agent_id == self._entry_agent_id:
                    spec.can_delegate_to = [aid for aid in self._agent_ids if aid != agent_id]
                else:
                    spec.can_delegate_to = []

            self._executors[agent_id] = AgentExecutor(
                spec,
                agent_config=self._config.agent,
                event_bus=self._event_bus,
                compactor=self._compactor,
                llm_provider=self._llm_provider,
                agent_name_map=self._agent_name_map,
            )
        return self._executors[agent_id]

    def _build_orchestrator(self, *, checkpointer, store):
        """Create orchestrator (bound to the current connection)."""
        from datapillar_oneagentic.runtime.orchestrator import Orchestrator

        return Orchestrator(
            namespace=self.namespace,
            name=self.name,
            graph=self._graph,
            entry_agent_id=self._entry_agent_id,
            agent_ids=self._agent_ids,
            agent_name_map=self._agent_name_map,
            checkpointer=checkpointer,
            store=store,
            experience_learner=self._experience_learner,
            experience_retriever=self._experience_retriever,
            process=self.process,
            event_bus=self._event_bus,
        )

    async def stream(
        self,
        *,
        query: str | None = None,
        session_id: str,
        resume_value: Any | None = None,
    ) -> AsyncGenerator[dict[str, Any], None]:
        """
        Streamed execution.

        Database connections are created per run and closed automatically.

        Args:
            query: user input (required for new or resumed chat)
            session_id: session ID
            resume_value: interrupt resume value (user input)

        Returns:
            SSE event stream.

        Example:
        ```python
        # New session
        async for event in team.stream(query="Analyze data", session_id="s1"):
            print(event)

        # Resume from interrupt
        async for event in team.stream(session_id="s1", resume_value="Proceed"):
            print(event)
        ```
        """
        from datapillar_oneagentic.storage import create_checkpointer, create_store

        key = SessionKey(namespace=self.namespace, session_id=session_id)

        # Use async with to ensure connections are closed.
        async with (
            create_checkpointer(self.namespace, agent_config=self._config.agent) as checkpointer,
            create_store(self.namespace, agent_config=self._config.agent) as store,
        ):
            # Create orchestrator per stream call and bind it to current connections.
            orchestrator = self._build_orchestrator(
                checkpointer=checkpointer,
                store=store,
            )
            with bind_log_context(namespace=self.namespace, session_id=key.session_id):
                async for event in orchestrator.stream(
                    query=query,
                    key=key,
                    resume_value=resume_value,
                ):
                    yield event
        # Connections close on context exit.

    async def compact_session(self, session_id: str) -> dict:
        """Manually compact session memory."""
        from datapillar_oneagentic.storage import create_checkpointer, create_store

        async with (
            create_checkpointer(self.namespace, agent_config=self._config.agent) as checkpointer,
            create_store(self.namespace, agent_config=self._config.agent) as store,
        ):
            orchestrator = self._build_orchestrator(
                checkpointer=checkpointer,
                store=store,
            )
            return await orchestrator.compact_session(session_id)

    async def clear_session(self, session_id: str) -> None:
        """Clear session memory."""
        from datapillar_oneagentic.storage import create_checkpointer

        async with create_checkpointer(self.namespace, agent_config=self._config.agent) as checkpointer:
            orchestrator = self._build_orchestrator(checkpointer=checkpointer, store=None)
            await orchestrator.clear_session(session_id)

    async def clear_session_store(self, session_id: str) -> None:
        """Clear session deliverables."""
        from datapillar_oneagentic.storage import create_checkpointer, create_store

        async with (
            create_checkpointer(self.namespace, agent_config=self._config.agent) as checkpointer,
            create_store(self.namespace, agent_config=self._config.agent) as store,
        ):
            orchestrator = self._build_orchestrator(
                checkpointer=checkpointer,
                store=store,
            )
            await orchestrator.clear_session_store(session_id)

    async def get_session_stats(self, session_id: str) -> dict:
        """Get session stats."""
        from datapillar_oneagentic.storage import create_checkpointer, create_store

        async with (
            create_checkpointer(self.namespace, agent_config=self._config.agent) as checkpointer,
            create_store(self.namespace, agent_config=self._config.agent) as store,
        ):
            orchestrator = self._build_orchestrator(
                checkpointer=checkpointer,
                store=store,
            )
            return await orchestrator.get_session_stats(session_id)

    async def get_session_todo(self, session_id: str) -> dict:
        """Get session todo snapshot."""
        from datapillar_oneagentic.storage import create_checkpointer, create_store

        async with (
            create_checkpointer(self.namespace, agent_config=self._config.agent) as checkpointer,
            create_store(self.namespace, agent_config=self._config.agent) as store,
        ):
            orchestrator = self._build_orchestrator(
                checkpointer=checkpointer,
                store=store,
            )
            return await orchestrator.get_session_todo(session_id)

    async def save_experience(
        self,
        session_id: str,
        feedback: dict[str, Any] | None = None,
    ) -> bool:
        """
        Save an experience to the vector store.

        Call this when the execution is valuable. If not called, nothing is saved.

        Args:
            session_id: session ID
            feedback: user feedback (optional, caller-defined structure)

        Returns:
            True if saved successfully.
        """
        if not self._experience_learner:
            logger.warning("Experience learning is disabled; cannot save experience")
            return False

        if not self._experience_learner.has_pending(session_id):
            logger.warning(f"No pending record to save: {session_id}")
            return False

        return await self._experience_learner.save_experience(
            session_id=session_id,
            feedback=feedback,
        )

    @property
    def event_bus(self) -> EventBus:
        """Return the team event bus."""
        return self._event_bus

    @property
    def __repr__(self) -> str:
        return (
            f"Datapillar(namespace={self.namespace!r}, "
            f"name={self.name!r}, "
            f"agents={[s.id for s in self._agent_specs]}, "
            f"process={self.process.value})"
        )
