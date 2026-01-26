"""
Agent definitions.

Core types:
- AgentSpec: agent specification (declarative config)
- @agent: decorator that binds the spec

Design principles:
- Declarative config is the contract
- Framework behavior follows the config
- Agent only implements run()
- Decorator validates strictly to prevent bad configs
"""

from __future__ import annotations

import inspect
import logging
import re
from dataclasses import dataclass, field
from typing import TYPE_CHECKING, Any

if TYPE_CHECKING:
    from pydantic import BaseModel

    from datapillar_oneagentic.a2a.config import A2AConfig
    from datapillar_oneagentic.core.config import AgentConfig, AgentRetryConfig
    from datapillar_oneagentic.knowledge import Knowledge
    from datapillar_oneagentic.mcp.config import MCPServerConfig

logger = logging.getLogger(__name__)


@dataclass
class AgentSpec:
    """
    Agent specification (declarative config).

    Defines identity, capabilities, and output contract.
    The framework uses this spec for tool injection, delegation, and result handling.

    Note: This class is internal; business code declares it via @agent.
    """

    # === Identity ===
    id: str
    """Agent unique identifier."""

    name: str
    """Agent display name."""

    # === Capabilities ===
    description: str = ""
    """One-line description of the agent's capability."""

    tools: list[Any] = field(default_factory=list)
    """Tool list (BaseTool or compatible)."""

    # === Delegation config (framework-populated) ===
    can_delegate_to: list[str] = field(default_factory=list)
    """Delegation targets (auto-set in DYNAMIC mode)."""

    # === Deliverable contract ===
    deliverable_schema: type[BaseModel] | None = None
    """Deliverable schema (Pydantic model; structured output handled by framework)."""

    # === Execution config ===
    temperature: float = 0.0
    """LLM temperature."""

    max_steps: int | None = None
    """Max steps (None uses team AgentConfig.max_steps)."""

    retry_config: "AgentRetryConfig | None" = None
    """Retry config (None uses team AgentConfig.retry)."""

    timeout_seconds: float | None = None
    """Single-run timeout (None uses team AgentConfig.timeout_seconds)."""

    tool_timeout_seconds: float | None = None
    """Tool call timeout (None uses team AgentConfig.tool_timeout_seconds)."""

    def get_max_steps(self, config: AgentConfig) -> int:
        """Return max steps."""
        return self.max_steps if self.max_steps is not None else config.max_steps

    def get_timeout_seconds(self, config: AgentConfig) -> float:
        """Return agent timeout in seconds."""
        return self.timeout_seconds if self.timeout_seconds is not None else config.timeout_seconds

    def get_retry_config(self, config: AgentConfig) -> "AgentRetryConfig":
        """Return retry config."""
        return self.retry_config if self.retry_config is not None else config.retry

    def get_tool_timeout(self, config: AgentConfig) -> float:
        """Return tool timeout in seconds."""
        return self.tool_timeout_seconds if self.tool_timeout_seconds is not None else config.tool_timeout_seconds

    # === Knowledge config ===
    knowledge: "Knowledge | None" = None
    """Knowledge config (RAG injection)."""

    # === A2A remote agents ===
    a2a_agents: list[A2AConfig] = field(default_factory=list)
    """A2A agent configs (delegation tools created by framework)."""

    # === MCP servers ===
    mcp_servers: list[MCPServerConfig] = field(default_factory=list)
    """MCP server configs (tools converted for agent use)."""

    # === Runtime (framework-populated) ===
    agent_class: type | None = None
    """Agent class reference (instantiated per run)."""


class AgentRegistry:
    """
    Agent registry (reserved).

    The framework uses @agent binding and does not use this registry in Datapillar.
    This registry is for extensions or tests.
    """

    def __init__(self) -> None:
        self._agents: dict[str, AgentSpec] = {}

    def register(self, spec: AgentSpec) -> None:
        """Register an agent."""
        if spec.id in self._agents:
            logger.warning(f"Agent {spec.id} already exists and will be overwritten")

        self._agents[spec.id] = spec
        logger.info(f"Agent registered: {spec.name} ({spec.id})")

    def get(self, agent_id: str) -> AgentSpec | None:
        """Get agent spec."""
        return self._agents.get(agent_id)

    def list_ids(self) -> list[str]:
        """List agent IDs."""
        return list(self._agents.keys())

    def list_specs(self) -> list[AgentSpec]:
        """List agent specs."""
        return list(self._agents.values())

    def count(self) -> int:
        """Return number of agents."""
        return len(self._agents)


# === ID validation ===
_ID_PATTERN = re.compile(r"^[a-z][a-z0-9_]*$")


def _validate_id(agent_id: str, class_name: str) -> None:
    """Validate agent ID format."""
    if not agent_id:
        raise ValueError(f"Agent {class_name} id must not be empty")

    if not _ID_PATTERN.match(agent_id):
        raise ValueError(
            f"Agent {class_name} id '{agent_id}' is invalid. "
            "It must start with a lowercase letter and contain only lowercase letters, numbers, and underscores."
        )


def _validate_run_method(cls: type) -> None:
    """Validate run method."""
    if not hasattr(cls, "run"):
        raise ValueError(f"Agent {cls.__name__} must implement run(self, ctx)")

    run_method = cls.run

    # Ensure it is callable.
    if not callable(run_method):
        raise ValueError(f"Agent {cls.__name__}.run must be callable")

    # Validate signature.
    sig = inspect.signature(run_method)
    params = list(sig.parameters.keys())

    # Must include self and ctx.
    if len(params) < 2:
        raise ValueError(
            f"Agent {cls.__name__}.run() signature is invalid; expected run(self, ctx: AgentContext)"
        )

    # Second argument must be ctx.
    if params[1] != "ctx":
        raise ValueError(
            f"Agent {cls.__name__}.run() second parameter must be named 'ctx', got '{params[1]}'"
        )

    # Must be async.
    if not inspect.iscoroutinefunction(run_method):
        raise ValueError(
            f"Agent {cls.__name__}.run() must be async (async def)"
        )


def _validate_deliverable_schema(schema: type | None, class_name: str) -> None:
    """Validate deliverable_schema (required)."""
    if schema is None:
        raise ValueError(
            f"Agent {class_name} must declare deliverable_schema; "
            "the framework uses structured JSON output."
        )

    # Ensure it is a Pydantic model.
    from pydantic import BaseModel

    if not (isinstance(schema, type) and issubclass(schema, BaseModel)):
        raise ValueError(
            f"Agent {class_name} deliverable_schema must be a Pydantic BaseModel subclass, got {type(schema)}"
        )


_AGENT_SPEC_ATTR = "__datapillar_spec__"


def agent(
    id: str,
    name: str,
    *,
    deliverable_schema: type,
    description: str = "",
    tools: list[Any] | None = None,
    mcp_servers: list[MCPServerConfig] | None = None,
    a2a_agents: list[A2AConfig] | None = None,
    temperature: float = 0.0,
    max_steps: int | None = None,
    retry_config: "AgentRetryConfig | None" = None,
    knowledge: "Knowledge | None" = None,
):
    """
    Agent definition decorator.

    Use @agent(...) on a class. The class must implement async run(self, ctx: AgentContext).

    Example:
    ```python
    from datapillar_oneagentic.mcp import MCPServerStdio

    @agent(
        id="analyst",
        name="Requirements Analyst",
        deliverable_schema=AnalysisOutput,
        tools=[search_tables],
        mcp_servers=[
            MCPServerStdio(
                command="npx",
                args=["-y", "@modelcontextprotocol/server-filesystem", "/tmp"],
            ),
        ],
    )
    class AnalystAgent:
        SYSTEM_PROMPT = "You are a requirements analyst."

        async def run(self, ctx: AgentContext) -> AnalysisOutput:
            messages = ctx.messages().system(self.SYSTEM_PROMPT)
            messages = await ctx.invoke_tools(messages)

            return await ctx.get_structured_output(messages)
    ```

    Args:
        id: agent ID (lowercase letter first; only lowercase letters, numbers, underscore)
        name: display name
        deliverable_schema: deliverable schema (Pydantic model, required)
        description: capability description
        tools: tool list (BaseTool or compatible)
        mcp_servers: MCP server configs (tools converted by framework)
        a2a_agents: remote A2A agent configs (cross-service)
        temperature: LLM temperature
        max_steps: max steps (None uses team AgentConfig.max_steps)
        retry_config: retry config (None uses team AgentConfig.retry)
        knowledge: knowledge config (RAG injection)

    Notes:
        - Entry agent is the first in the team's agents list
        - Delegation is inferred in DYNAMIC mode
        - Experience learning is controlled by Datapillar(enable_learning=True)
        - Deliverables are stored/retrieved by agent_id
    """

    def decorator(cls: type) -> type:
        # === Strict validation ===

        # 1. Validate ID format.
        _validate_id(id, cls.__name__)

        # 2. Validate run method.
        _validate_run_method(cls)

        # 3. Validate deliverable_schema.
        _validate_deliverable_schema(deliverable_schema, cls.__name__)

        # 4. Validate temperature range.
        if not 0.0 <= temperature <= 2.0:
            raise ValueError(
                f"Agent {cls.__name__} temperature must be within 0.0-2.0, got {temperature}"
            )

        # === Store class reference (instantiate per run) ===

        spec = AgentSpec(
            id=id,
            name=name,
            description=description,
            tools=tools or [],
            mcp_servers=mcp_servers or [],
            a2a_agents=a2a_agents or [],
            deliverable_schema=deliverable_schema,
            temperature=temperature,
            max_steps=max_steps,
            retry_config=retry_config,
            knowledge=knowledge,
            agent_class=cls,
        )

        # Bind spec to class.
        setattr(cls, _AGENT_SPEC_ATTR, spec)

        return cls

    return decorator


def get_agent_spec(agent_class: type) -> AgentSpec | None:
    """Get bound AgentSpec from an agent class."""
    return getattr(agent_class, _AGENT_SPEC_ATTR, None)
