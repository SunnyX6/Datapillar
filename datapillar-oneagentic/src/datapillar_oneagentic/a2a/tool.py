"""
A2A delegation tools.

Creates tools that allow agents to call remote A2A agents.
Uses the official a2a-sdk implementation.

Security:
- External agent behavior is unpredictable; confirmation required by default
- Controlled via A2AConfig.require_confirmation
"""

from __future__ import annotations

import logging

from langchain_core.tools import StructuredTool
from pydantic import BaseModel, Field

from datapillar_oneagentic.a2a.config import A2AConfig
from datapillar_oneagentic.security import (
    ConfirmationRequest,
    NoConfirmationCallbackError,
    UserRejectedError,
    get_security_config,
)

logger = logging.getLogger(__name__)


class A2ADelegateInput(BaseModel):
    """A2A delegation tool input."""

    task: str = Field(description="Task description for the remote agent")
    context: str = Field(default="", description="Additional context")


def _check_a2a_confirmation(config: A2AConfig, task: str, context: str) -> None:
    """Security check; raise if not confirmed."""
    if not config.require_confirmation:
        return

    security_config = get_security_config()
    if not security_config.require_confirmation:
        return

    confirmation_request = ConfirmationRequest(
        operation_type="a2a_delegate",
        name=f"A2A Agent ({config.endpoint})",
        description="Delegate a task to a remote A2A agent",
        parameters={"task": task, "context": context},
        risk_level="high",
        warnings=[
            "This operation calls an external agent with unpredictable behavior.",
            "Task content will be sent to a remote server.",
            "The remote agent may perform arbitrary actions.",
        ],
        source=config.endpoint,
        metadata={
            "endpoint": config.endpoint,
            "require_confirmation": config.require_confirmation,
            "fail_fast": config.fail_fast,
        },
    )

    if not security_config.confirmation_callback:
        raise NoConfirmationCallbackError(
            f"Remote agent {config.endpoint} requires confirmation, but confirmation_callback is not configured.\n"
            "Configure configure_security(confirmation_callback=...) or set require_confirmation=False"
        )

    if not security_config.confirmation_callback(confirmation_request):
        raise UserRejectedError(f"User rejected remote agent call: {config.endpoint}")


async def _call_a2a_agent(endpoint: str, full_task: str) -> str:
    """Execute A2A call."""
    from a2a.client import ClientConfig, ClientFactory, create_text_message_object
    from a2a.types import TaskState

    client = await ClientFactory.connect(endpoint, client_config=ClientConfig(streaming=True))
    try:
        message = create_text_message_object(content=full_task)

        async for event in client.send_message(message):
            if not isinstance(event, tuple):
                continue

            task_obj, _ = event
            state = task_obj.status.state

            if state == TaskState.failed:
                return f"Remote agent error: {task_obj.status.message}"

            if state == TaskState.completed:
                msg = task_obj.status.message
                if msg and msg.parts:
                    return msg.parts[0].root.text
                break

        return "Remote agent returned no result"
    finally:
        await client.close()


def create_a2a_tool(config: A2AConfig, name: str | None = None) -> StructuredTool:
    """
    Create an A2A delegation tool.

    Args:
    - config: A2A configuration
    - name: Tool name (defaults to endpoint-derived)

    Returns:
    - LangChain StructuredTool

    Security:
    - External agent behavior is unpredictable; confirmation required by default
    - Controlled via A2AConfig.require_confirmation

    Example:
    ```python
    config = A2AConfig(
        endpoint="https://api.example.com/.well-known/agent.json",
    )
    tool = create_a2a_tool(config, name="call_data_analyst")

    # Agents can use this tool to call remote agents
    result = await tool.ainvoke({"task": "Analyze sales data"})
    ```
    """

    async def delegate_to_a2a(task: str, context: str = "") -> str:
        """Delegate a task to a remote A2A agent (with security checks)."""
        try:
            from a2a.client import ClientFactory  # noqa: F401 - dependency check
        except ImportError as err:
            raise ImportError("a2a-sdk is not installed. Run: pip install a2a-sdk") from err

        _check_a2a_confirmation(config, task, context)

        full_task = f"{task}\n\nContext:\n{context}" if context else task

        try:
            return await _call_a2a_agent(config.endpoint, full_task)
        except Exception as e:
            if config.fail_fast:
                raise
            return f"A2A call failed: {e}"

    # Tool name
    tool_name = name or f"delegate_to_{_endpoint_to_name(config.endpoint)}"

    # Build description (include security notice)
    description = f"Delegate a task to a remote A2A agent ({config.endpoint})"
    if config.require_confirmation:
        description += "\n[External agent, confirmation required]"

    return StructuredTool.from_function(
        func=delegate_to_a2a,
        coroutine=delegate_to_a2a,
        name=tool_name,
        description=description,
        args_schema=A2ADelegateInput,
    )


def _endpoint_to_name(endpoint: str) -> str:
    """Convert endpoint URL to tool name."""
    from urllib.parse import urlparse

    parsed = urlparse(endpoint)
    host = parsed.hostname or "unknown"
    # Strip common suffixes and special characters
    name = host.replace(".", "_").replace("-", "_")
    return name


async def create_a2a_tools(configs: list[A2AConfig]) -> list[StructuredTool]:
    """
    Create A2A tools from configs.

    Args:
    - configs: A2A config list

    Returns:
    - Tool list

    Example:
    ```python
    configs = [
        A2AConfig(endpoint="https://agent1.example.com/.well-known/agent.json"),
        A2AConfig(endpoint="https://agent2.example.com/.well-known/agent.json"),
    ]

    tools = await create_a2a_tools(configs)
    ```
    """
    try:
        from a2a.client import ClientConfig, ClientFactory
    except ImportError as err:
        raise ImportError(
            "a2a-sdk is not installed. Run: pip install a2a-sdk"
        ) from err

    tools = []

    for config in configs:
        client = None
        try:
            # Use ClientFactory to fetch AgentCard
            client_config = ClientConfig(streaming=True)
            client = await ClientFactory.connect(config.endpoint, client_config=client_config)

            try:
                card = await client.get_card()
                tool_name = f"delegate_to_{card.name.lower().replace(' ', '_').replace('-', '_')}"
            except Exception:
                tool_name = f"delegate_to_{_endpoint_to_name(config.endpoint)}"

            tool = create_a2a_tool(config, name=tool_name)
            tools.append(tool)

            # Log security info
            if config.require_confirmation:
                logger.info(f"A2A tool created: {tool_name} [confirmation required]")
            else:
                logger.info(f"A2A tool created: {tool_name}")

        except Exception as e:
            if config.fail_fast:
                raise
            logger.warning(f"Skipping unavailable A2A agent: {config.endpoint}, error={e}")
        finally:
            if client:
                await client.close()

    return tools
