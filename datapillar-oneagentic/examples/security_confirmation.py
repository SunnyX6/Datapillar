"""
MCP and A2A security confirmation examples.

This example shows:
1. How to configure confirmation callbacks
2. Full ConfirmationRequest structure
3. CLI confirmation workflows
4. Web app confirmation (pseudo-code)
5. Risk-based confirmation policies

Run:
    uv run python examples/security_confirmation.py
"""

import asyncio
import logging
from typing import Any

from datapillar_oneagentic.security import (
    ConfirmationRequest,
    configure_security,
    reset_security_config,
    NoConfirmationCallbackError,
    UserRejectedError,
)
from datapillar_oneagentic.mcp import (
    MCPServerStdio,
    MCPToolkit,
)
from datapillar_oneagentic.a2a import (
    A2AConfig,
    create_a2a_tool,
)

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


# ============================================================================
# Part 1: Confirmation callback examples
# ============================================================================


def cli_confirmation_simple(request: ConfirmationRequest) -> bool:
    """
    Simple CLI confirmation.

    Uses to_display_string() directly.
    """
    print(request.to_display_string())
    response = input("\nConfirm execution? (y/N): ").strip().lower()
    return response == "y"


def check_risk(request: ConfirmationRequest) -> bool:
    """
    CLI confirmation with risk checks.

    - low/medium: simple confirmation
    - high: requires input 'yes'
    - critical: requires input 'YES I UNDERSTAND'
    """
    print("\n" + "=" * 60)
    print(f"DANGEROUS OPERATION CONFIRMATION - RISK LEVEL: {request.risk_level.upper()}")
    print("=" * 60)

    # Basic info.
    print(f"\nOperation type: {request.operation_type}")
    print(f"Name: {request.name}")
    print(f"Description: {request.description}")
    print(f"Source: {request.source}")

    # Parameters.
    print("\nParameters:")
    for key, value in request.parameters.items():
        value_str = str(value)
        if len(value_str) > 80:
            value_str = value_str[:80] + "..."
        print(f"  {key}: {value_str}")

    # Warnings.
    if request.warnings:
        print("\nWarnings:")
        for warning in request.warnings:
            print(f"  - {warning}")

    # Metadata (optional).
    if request.metadata:
        print("\nMetadata:")
        for key, value in request.metadata.items():
            print(f"  {key}: {value}")

    print("=" * 60)

    # Choose confirmation flow based on risk level.
    if request.risk_level == "low":
        response = input("\nPress Enter to continue, or 'n' to cancel: ").strip().lower()
        return response != "n"

    elif request.risk_level == "medium":
        response = input("\nConfirm execution? (y/N): ").strip().lower()
        return response == "y"

    elif request.risk_level == "high":
        response = input("\nHIGH RISK operation. Type 'yes' to confirm: ").strip().lower()
        return response == "yes"

    else:  # critical
        print("\nCRITICAL RISK operation.")
        print("This operation may cause irreversible impact.")
        response = input("Type 'YES I UNDERSTAND' to confirm: ").strip()
        return response == "YES I UNDERSTAND"


def auto_approve(request: ConfirmationRequest) -> bool:
    """
    Auto-approve (test environments only).

    Records all operations but approves automatically.
    """
    logger.warning(
        f"[AUTO-APPROVE] {request.operation_type}: {request.name}\n"
        f"  Risk level: {request.risk_level}\n"
        f"  Parameters: {request.parameters}\n"
        f"  Source: {request.source}"
    )
    return True


def policy_based_confirmation(request: ConfirmationRequest) -> bool:
    """
    Policy-based confirmation.

    - Allowlisted tools auto-approve
    - Trusted sources auto-approve
    - Others require manual confirmation
    """
    # Allowlisted tools (auto-approve).
    ALLOWED_TOOLS = {"read_file", "list_directory", "get_weather"}

    # Trusted sources (auto-approve).
    TRUSTED_SOURCES = {"https://internal.company.com"}

    # Check allowlist.
    if request.name in ALLOWED_TOOLS:
        logger.info(f"[POLICY] Allowlisted tool auto-approved: {request.name}")
        return True

    # Check trusted sources.
    if any(request.source.startswith(src) for src in TRUSTED_SOURCES):
        logger.info(f"[POLICY] Trusted source auto-approved: {request.source}")
        return True

    # Auto-approve low-risk operations.
    if request.risk_level == "low":
        logger.info(f"[POLICY] Low-risk auto-approved: {request.name}")
        return True

    # Manual confirmation for others.
    return check_risk(request)


# ============================================================================
# Part 2: Web app confirmation (pseudo-code)
# ============================================================================


class WebConfirmationHandler:
    """
    Web confirmation handler.

    Push confirmation requests via WebSocket and wait for user response.
    """

    def __init__(self, websocket_manager: Any, timeout: float = 60.0):
        self.websocket_manager = websocket_manager
        self.timeout = timeout
        self.pending_requests: dict[str, asyncio.Future] = {}

    def __call__(self, request: ConfirmationRequest) -> bool:
        """
        Synchronous callback interface.

        Note: real web apps may need async implementation.
        """
        import uuid

        request_id = str(uuid.uuid4())

        # Build payload for the frontend.
        payload = {
            "type": "security_confirmation",
            "request_id": request_id,
            "operation_type": request.operation_type,
            "name": request.name,
            "description": request.description,
            "parameters": request.parameters,
            "risk_level": request.risk_level,
            "warnings": request.warnings,
            "source": request.source,
            "metadata": request.metadata,
            # For frontend display.
            "display_string": request.to_display_string(),
        }

        # Push to frontend (pseudo-code).
        # self.websocket_manager.broadcast(payload)

        # Wait for user response (pseudo-code).
        # try:
        #     future = asyncio.get_event_loop().create_future()
        #     self.pending_requests[request_id] = future
        #     result = asyncio.wait_for(future, timeout=self.timeout)
        #     return result
        # except asyncio.TimeoutError:
        #     return False
        # finally:
        #     self.pending_requests.pop(request_id, None)

        logger.info(f"[WEB] Confirmation request pushed: {request_id}")
        logger.info(f"[WEB] Payload: {payload}")

        # Simulation: return True to indicate confirmation.
        return True

    def handle_user_response(self, request_id: str, confirmed: bool) -> None:
        """
        Handle frontend user response.

        Called when frontend sends confirmation result via WebSocket.
        """
        if request_id in self.pending_requests:
            self.pending_requests[request_id].set_result(confirmed)


# ============================================================================
# Part 3: Full usage demos
# ============================================================================


async def demo_mcp():
    """
    Demo MCP tool security confirmation flow.
    """
    print("\n" + "=" * 60)
    print("Demo: MCP tool security confirmation")
    print("=" * 60)

    # Configure confirmation callback.
    configure_security(
        require_confirmation=True,
        confirmation_callback=check_risk,
    )

    try:
        # Create MCP server configuration.
        # Note: filesystem server is used as an example.
        # Actual run requires: npx -y @modelcontextprotocol/server-filesystem
        servers = [
            MCPServerStdio(
                command="npx",
                args=["-y", "@modelcontextprotocol/server-filesystem", "/tmp"],
            ),
        ]

        # Create MCP tools.
        # Dangerous tools (e.g., delete_file) will trigger confirmation.
        async with MCPToolkit(servers) as toolkit:
            tools = toolkit.get_tools()

            print(f"\nLoaded {len(tools)} MCP tools")
            for tool in tools:
                print(f"  - {tool.name}: {tool.description[:50]}...")

    except Exception as e:
        logger.error(f"MCP demo failed: {e}")
        print("\nNote: MCP demo requires Node.js and npx")

    finally:
        reset_security_config()


async def demo_a2a():
    """
    Demo A2A remote call security confirmation flow.
    """
    print("\n" + "=" * 60)
    print("Demo: A2A remote call security confirmation")
    print("=" * 60)

    # Configure confirmation callback.
    configure_security(
        require_confirmation=True,
        confirmation_callback=check_risk,
    )

    try:
        # Create A2A config.
        a2a_config = A2AConfig(
            endpoint="https://api.example.com/.well-known/agent.json",
            require_confirmation=True,  # Defaults to True.
        )

        # Create A2A tool.
        a2a_tool = create_a2a_tool(a2a_config, name="call_remote_analyst")

        print(f"\nCreated A2A tool: {a2a_tool.name}")
        print(f"Description: {a2a_tool.description}")

        # Simulate call (will trigger confirmation).
        print("\nSimulating A2A tool call...")
        try:
            result = await a2a_tool.ainvoke({
                "task": "Analyze sales data for the last week",
                "context": "Focus on East China region",
            })
            print(f"Result: {result}")
        except UserRejectedError:
            print("User rejected the operation")
        except NoConfirmationCallbackError as e:
            print(f"Configuration error: {e}")
        except Exception as e:
            # A2A calls may fail due to network, etc.
            print(f"Call failed (expected): {e}")

    finally:
        reset_security_config()


async def demo_callback():
    """
    Demo error when callback is not configured.
    """
    print("\n" + "=" * 60)
    print("Demo: error handling without callback")
    print("=" * 60)

    # Require confirmation but omit callback.
    configure_security(
        require_confirmation=True,
        confirmation_callback=None,  # Intentionally omitted.
    )

    try:
        a2a_config = A2AConfig(
            endpoint="https://api.example.com/.well-known/agent.json",
        )
        a2a_tool = create_a2a_tool(a2a_config)

        # Call will raise NoConfirmationCallbackError.
        await a2a_tool.ainvoke({"task": "test task"})

    except NoConfirmationCallbackError as e:
        print("\nCorrectly caught error: NoConfirmationCallbackError")
        print(f"Error message: {e}")

    finally:
        reset_security_config()


async def demo_disable_confirmation():
    """
    Demo disabling confirmation (test only).
    """
    print("\n" + "=" * 60)
    print("Demo: disable confirmation (test only)")
    print("=" * 60)

    # Option 1: disable confirmation entirely.
    configure_security(require_confirmation=False)
    print("Security confirmation disabled (not recommended for production)")

    reset_security_config()

    # Option 2: auto-approve with logging.
    configure_security(
        require_confirmation=True,
        confirmation_callback=auto_approve,
    )
    print("Auto-approve configured (with logging)")

    reset_security_config()


# ============================================================================
# Part 4: ConfirmationRequest structure
# ============================================================================


def show_confirmation():
    """
    Show the full ConfirmationRequest structure.
    """
    print("\n" + "=" * 60)
    print("ConfirmationRequest structure")
    print("=" * 60)

    # Create a sample request.
    example_request = ConfirmationRequest(
        operation_type="mcp_tool",
        name="delete_file",
        description="Delete file at the specified path",
        parameters={
            "path": "/tmp/important_data.txt",
        },
        risk_level="high",
        warnings=[
            "This tool may perform destructive operations (delete or modify data).",
            "This operation is irreversible and may produce different results on repeat.",
        ],
        source="MCPClient(stdio://npx @mcp/server-filesystem)",
        metadata={
            "tool_title": "Delete File",
            "annotations": {
                "destructive_hint": True,
                "idempotent_hint": False,
                "open_world_hint": False,
                "read_only_hint": False,
            },
        },
    )

    print("\nField notes:")
    print(f"  operation_type: {example_request.operation_type}")
    print("    - 'mcp_tool': MCP tool call")
    print("    - 'a2a_delegate': A2A remote agent call")

    print(f"\n  name: {example_request.name}")
    print("    - Tool or agent name")

    print(f"\n  description: {example_request.description}")
    print("    - Tool or agent description")

    print(f"\n  parameters: {example_request.parameters}")
    print("    - Full call parameters")

    print(f"\n  risk_level: {example_request.risk_level}")
    print("    - 'low': low risk (read-only)")
    print("    - 'medium': medium risk (may modify data)")
    print("    - 'high': high risk (destructive)")
    print("    - 'critical': critical risk (destructive + external network)")

    print(f"\n  warnings: {example_request.warnings}")
    print("    - Risk warning list")

    print(f"\n  source: {example_request.source}")
    print("    - MCP: server address")
    print("    - A2A: Agent endpoint")

    print(f"\n  metadata: {example_request.metadata}")
    print("    - MCP: tool_title, annotations")
    print("    - A2A: endpoint, require_confirmation, fail_fast")

    print("\n" + "-" * 60)
    print("to_display_string() output:")
    print("-" * 60)
    print(example_request.to_display_string())


# ============================================================================
# Main
# ============================================================================


async def main():
    """Run all demos."""
    print("=" * 60)
    print("MCP and A2A security confirmation examples")
    print("=" * 60)

    # Show ConfirmationRequest structure.
    show_confirmation()

    # Demo missing-callback error.
    await demo_callback()

    # Demo disabling confirmation.
    await demo_disable_confirmation()

    # The following demos require actual MCP/A2A services.
    # await demo_mcp()
    # await demo_a2a()

    print("\n" + "=" * 60)
    print("Demo completed")
    print("=" * 60)


if __name__ == "__main__":
    asyncio.run(main())
