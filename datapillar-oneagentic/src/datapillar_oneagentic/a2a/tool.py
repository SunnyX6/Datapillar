"""
A2A 委派工具

为 Agent 创建调用远程 A2A Agent 的工具。
使用官方 a2a-sdk 实现。

安全机制：
- 外部 Agent 行为不可预测，默认需要用户确认
- 可通过 A2AConfig.require_confirmation 控制
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
    """A2A 委派工具输入"""

    task: str = Field(description="Task description for the remote agent")
    context: str = Field(default="", description="Additional context")


def _check_a2a_confirmation(config: A2AConfig, task: str, context: str) -> None:
    """安全校验，不通过则抛出异常"""
    if not config.require_confirmation:
        return

    security_config = get_security_config()
    if not security_config.require_confirmation:
        return

    confirmation_request = ConfirmationRequest(
        operation_type="a2a_delegate",
        name=f"A2A Agent ({config.endpoint})",
        description="委派任务到远程 A2A Agent",
        parameters={"task": task, "context": context},
        risk_level="high",
        warnings=[
            "此操作将调用外部 Agent，行为不可预测",
            "任务内容将发送到远程服务器",
            "远程 Agent 可能执行任意操作",
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
            f"调用远程 Agent {config.endpoint} 需要用户确认，但未配置 confirmation_callback。\n"
            f"请配置 configure_security(confirmation_callback=...) 或设置 require_confirmation=False"
        )

    if not security_config.confirmation_callback(confirmation_request):
        raise UserRejectedError(f"用户拒绝调用远程 Agent: {config.endpoint}")


async def _call_a2a_remote_agent(endpoint: str, full_task: str) -> str:
    """执行 A2A 调用"""
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
    创建 A2A 委派工具

    参数：
    - config: A2A 配置
    - name: 工具名称（默认根据 endpoint 生成）

    返回：
    - LangChain StructuredTool

    安全：
    - 外部 Agent 行为不可预测，默认需要用户确认
    - 通过 A2AConfig.require_confirmation 控制

    使用示例：
    ```python
    config = A2AConfig(
        endpoint="https://api.example.com/.well-known/agent.json",
    )
    tool = create_a2a_tool(config, name="call_data_analyst")

    # Agent 可以使用这个工具调用远程 Agent
    result = await tool.ainvoke({"task": "分析销售数据"})
    ```
    """

    async def delegate_to_a2a(task: str, context: str = "") -> str:
        """Delegate a task to a remote A2A agent (with security checks)."""
        try:
            from a2a.client import ClientFactory  # noqa: F401 - 检查依赖是否安装
        except ImportError as err:
            raise ImportError("a2a-sdk 未安装。请运行: pip install a2a-sdk") from err

        _check_a2a_confirmation(config, task, context)

        full_task = f"{task}\n\nContext:\n{context}" if context else task

        try:
            return await _call_a2a_remote_agent(config.endpoint, full_task)
        except Exception as e:
            if config.fail_fast:
                raise
            return f"A2A call failed: {e}"

    # 工具名称
    tool_name = name or f"delegate_to_{_endpoint_to_name(config.endpoint)}"

    # 构建描述（包含安全警告）
    description = f"Delegate a task to a remote A2A agent ({config.endpoint})"
    if config.require_confirmation:
        description += "\n[⚠️ External agent, confirmation required]"

    return StructuredTool.from_function(
        func=delegate_to_a2a,
        coroutine=delegate_to_a2a,
        name=tool_name,
        description=description,
        args_schema=A2ADelegateInput,
    )


def _endpoint_to_name(endpoint: str) -> str:
    """将 endpoint URL 转换为工具名称"""
    from urllib.parse import urlparse

    parsed = urlparse(endpoint)
    host = parsed.hostname or "unknown"
    # 移除常见后缀和特殊字符
    name = host.replace(".", "_").replace("-", "_")
    return name


async def create_a2a_tools(configs: list[A2AConfig]) -> list[StructuredTool]:
    """
    从配置列表批量创建 A2A 工具

    参数：
    - configs: A2A 配置列表

    返回：
    - 工具列表

    使用示例：
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
            "a2a-sdk 未安装。请运行: pip install a2a-sdk"
        ) from err

    tools = []

    for config in configs:
        client = None
        try:
            # 使用 ClientFactory 连接获取 AgentCard
            client_config = ClientConfig(streaming=True)
            client = await ClientFactory.connect(config.endpoint, client_config=client_config)

            try:
                card = await client.get_card()
                tool_name = f"delegate_to_{card.name.lower().replace(' ', '_').replace('-', '_')}"
            except Exception:
                tool_name = f"delegate_to_{_endpoint_to_name(config.endpoint)}"

            tool = create_a2a_tool(config, name=tool_name)
            tools.append(tool)

            # 记录安全信息
            if config.require_confirmation:
                logger.info(f"🔗 A2A 工具创建: {tool_name} [⚠️ 需确认]")
            else:
                logger.info(f"🔗 A2A 工具创建: {tool_name}")

        except Exception as e:
            if config.fail_fast:
                raise
            logger.warning(f"跳过不可用的 A2A Agent: {config.endpoint}, 错误: {e}")
        finally:
            if client:
                await client.close()

    return tools
