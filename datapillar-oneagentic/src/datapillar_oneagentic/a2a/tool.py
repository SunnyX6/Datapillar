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
from typing import Any

from langchain_core.tools import StructuredTool
from pydantic import BaseModel, Field

from datapillar_oneagentic.a2a.config import A2AConfig
from datapillar_oneagentic.security import (
    get_security_config,
    ConfirmationRequest,
    UserRejectedError,
    NoConfirmationCallbackError,
)

logger = logging.getLogger(__name__)


class A2ADelegateInput(BaseModel):
    """A2A 委派工具输入"""

    task: str = Field(description="要委派给远程 Agent 的任务描述")
    context: str = Field(default="", description="额外的上下文信息")


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
        """委派任务到远程 A2A Agent（带安全校验）"""
        try:
            import httpx
            from a2a.client import A2AClient
            from a2a.types import MessageSendParams, SendMessageRequest
        except ImportError:
            raise ImportError(
                "a2a-sdk 未安装。请运行: pip install a2a-sdk"
            )

        # 安全校验 - 外部 Agent 行为不可预测
        if config.require_confirmation:
            security_config = get_security_config()

            if security_config.require_confirmation:
                warnings = [
                    "此操作将调用外部 Agent，行为不可预测",
                    "任务内容将发送到远程服务器",
                    "远程 Agent 可能执行任意操作",
                ]

                # 构建确认请求
                confirmation_request = ConfirmationRequest(
                    operation_type="a2a_delegate",
                    name=f"A2A Agent ({config.endpoint})",
                    description="委派任务到远程 A2A Agent",
                    parameters={
                        "task": task,
                        "context": context,
                    },
                    risk_level="high",  # 外部 Agent 默认高风险
                    warnings=warnings,
                    source=config.endpoint,
                    metadata={
                        "endpoint": config.endpoint,
                        "require_confirmation": config.require_confirmation,
                        "fail_fast": config.fail_fast,
                    },
                )

                # 请求用户确认
                if security_config.confirmation_callback:
                    confirmed = security_config.confirmation_callback(confirmation_request)
                    if not confirmed:
                        raise UserRejectedError(f"用户拒绝调用远程 Agent: {config.endpoint}")
                else:
                    # 无确认回调 = 无法获得用户同意 = 拒绝执行
                    raise NoConfirmationCallbackError(
                        f"调用远程 Agent {config.endpoint} 需要用户确认，但未配置 confirmation_callback。\n"
                        f"请配置 configure_security(confirmation_callback=...) 或设置 require_confirmation=False"
                    )

        # 构建完整任务描述
        full_task = task
        if context:
            full_task = f"{task}\n\n上下文信息：\n{context}"

        try:
            # 构建 httpx 客户端（传递 timeout 和 auth headers）
            headers = {}
            if config.auth and config.auth.scheme:
                if config.auth.scheme == "bearer" and config.auth.credentials:
                    headers["Authorization"] = f"Bearer {config.auth.credentials}"
                elif config.auth.scheme == "api_key" and config.auth.credentials:
                    headers["X-API-Key"] = config.auth.credentials

            httpx_client = httpx.AsyncClient(
                headers=headers,
                timeout=httpx.Timeout(float(config.timeout)),
            )

            # 创建 A2A Client
            client = A2AClient(httpx_client=httpx_client, url=config.endpoint)

            # 获取 Agent Card（可选，用于日志）
            try:
                agent_card = await client.get_agent_card()
                logger.info(f"委派任务到: {agent_card.name}")
            except Exception:
                logger.info(f"委派任务到: {config.endpoint}")

            # 发送消息（带轮次限制）
            request = SendMessageRequest(
                params=MessageSendParams(message=full_task)
            )
            response = await client.send_message(request)

            # 处理响应
            if config.trust_remote_completion and response.result:
                # 信任远程完成状态，直接返回
                return str(response.result)
            if response.result:
                return str(response.result)
            if response.error:
                return f"远程 Agent 错误: {response.error}"
            return "远程 Agent 未返回结果"

        except Exception as e:
            if config.fail_fast:
                raise
            return f"A2A 调用失败: {e}"

    # 工具名称
    tool_name = name or f"delegate_to_{_endpoint_to_name(config.endpoint)}"

    # 构建描述（包含安全警告）
    description = f"委派任务到远程 A2A Agent ({config.endpoint})"
    if config.require_confirmation:
        description += "\n[⚠️ 外部 Agent，需要确认]"

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
        from a2a.client import A2AClient
    except ImportError:
        raise ImportError(
            "a2a-sdk 未安装。请运行: pip install a2a-sdk"
        )

    tools = []

    for config in configs:
        try:
            # 尝试获取 AgentCard 以确定工具名称
            client = A2AClient(url=config.endpoint)
            try:
                card = await client.get_agent_card()
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

    return tools
