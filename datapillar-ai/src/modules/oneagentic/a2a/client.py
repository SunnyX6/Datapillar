"""
A2A Client - 调用远程 Agent

实现 A2A 协议的客户端，支持：
- 获取 AgentCard
- 发送任务请求
- 多轮对话
- 流式响应
"""

from __future__ import annotations

import asyncio
import logging
from collections.abc import AsyncGenerator
from dataclasses import dataclass, field
from enum import Enum
from typing import Any

import httpx

from src.modules.oneagentic.a2a.card import AgentCard
from src.modules.oneagentic.a2a.config import A2AConfig

logger = logging.getLogger(__name__)


class TaskState(str, Enum):
    """任务状态"""

    PENDING = "pending"
    WORKING = "working"
    INPUT_REQUIRED = "input_required"
    COMPLETED = "completed"
    FAILED = "failed"
    CANCELLED = "cancelled"


@dataclass
class A2AMessage:
    """A2A 消息"""

    role: str
    """角色：user 或 agent"""

    content: str
    """消息内容"""

    message_id: str = ""
    """消息 ID"""

    task_id: str | None = None
    """任务 ID"""

    context_id: str | None = None
    """上下文 ID"""

    metadata: dict[str, Any] = field(default_factory=dict)
    """元数据"""

    def to_dict(self) -> dict[str, Any]:
        """转换为字典"""
        return {
            "role": self.role,
            "content": self.content,
            "message_id": self.message_id,
            "task_id": self.task_id,
            "context_id": self.context_id,
            "metadata": self.metadata,
        }

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> A2AMessage:
        """从字典创建"""
        return cls(
            role=data.get("role", "user"),
            content=data.get("content", ""),
            message_id=data.get("message_id", ""),
            task_id=data.get("task_id"),
            context_id=data.get("context_id"),
            metadata=data.get("metadata", {}),
        )


@dataclass
class A2AResult:
    """A2A 调用结果"""

    status: TaskState
    """任务状态"""

    result: str = ""
    """结果内容"""

    error: str | None = None
    """错误信息"""

    history: list[A2AMessage] = field(default_factory=list)
    """对话历史"""

    agent_card: AgentCard | None = None
    """Agent 卡片"""

    task_id: str | None = None
    """任务 ID"""

    context_id: str | None = None
    """上下文 ID"""


class A2AClient:
    """
    A2A 协议客户端

    用于调用远程 A2A Agent。

    使用示例：
    ```python
    config = A2AConfig(
        endpoint="https://api.example.com/.well-known/agent-card.json",
        auth=APIKeyAuth(api_key="sk-xxx"),
    )

    client = A2AClient(config)

    # 获取 Agent 信息
    card = await client.fetch_agent_card()
    print(f"Agent: {card.name}")

    # 发送任务
    result = await client.send_task("帮我分析这份数据")
    print(f"Result: {result.result}")
    ```
    """

    def __init__(self, config: A2AConfig):
        """
        初始化客户端

        参数：
        - config: A2A 配置
        """
        self.config = config
        self._agent_card: AgentCard | None = None
        self._http_client: httpx.AsyncClient | None = None

    async def __aenter__(self) -> A2AClient:
        """异步上下文管理器入口"""
        await self._ensure_client()
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb) -> None:
        """异步上下文管理器出口"""
        await self.close()

    async def _ensure_client(self) -> httpx.AsyncClient:
        """确保 HTTP 客户端已初始化"""
        if self._http_client is None or self._http_client.is_closed:
            headers = self.config.auth.to_headers()
            self._http_client = httpx.AsyncClient(
                timeout=self.config.timeout,
                headers=headers,
            )
        return self._http_client

    async def close(self) -> None:
        """关闭客户端"""
        if self._http_client and not self._http_client.is_closed:
            await self._http_client.aclose()
            self._http_client = None

    def _get_base_url(self) -> str:
        """从 endpoint 获取基础 URL"""
        endpoint = self.config.endpoint
        if "/.well-known/agent-card.json" in endpoint:
            return endpoint.replace("/.well-known/agent-card.json", "")
        return endpoint.rstrip("/")

    async def fetch_agent_card(self, use_cache: bool = True) -> AgentCard:
        """
        获取远程 Agent 的 AgentCard

        参数：
        - use_cache: 是否使用缓存

        返回：
        - AgentCard 对象
        """
        if use_cache and self._agent_card is not None:
            return self._agent_card

        client = await self._ensure_client()
        endpoint = self.config.endpoint

        # 确保是 agent-card.json 端点
        if not endpoint.endswith("agent-card.json"):
            if not endpoint.endswith("/"):
                endpoint += "/"
            endpoint += ".well-known/agent-card.json"

        try:
            response = await client.get(endpoint)
            response.raise_for_status()
            data = response.json()
            self._agent_card = AgentCard.from_dict(data)
            logger.info(f"获取 AgentCard 成功: {self._agent_card.name}")
            return self._agent_card

        except httpx.HTTPStatusError as e:
            if e.response.status_code == 401:
                raise A2AAuthError(f"认证失败: {endpoint}") from e
            raise A2AConnectionError(f"获取 AgentCard 失败: {e}") from e

        except Exception as e:
            raise A2AConnectionError(f"获取 AgentCard 失败: {e}") from e

    async def send_task(
        self,
        task_description: str,
        *,
        context_id: str | None = None,
        task_id: str | None = None,
        history: list[A2AMessage] | None = None,
        metadata: dict[str, Any] | None = None,
    ) -> A2AResult:
        """
        发送任务到远程 Agent

        参数：
        - task_description: 任务描述
        - context_id: 上下文 ID（用于多轮对话）
        - task_id: 任务 ID
        - history: 对话历史
        - metadata: 额外元数据

        返回：
        - A2AResult 结果对象
        """
        client = await self._ensure_client()
        base_url = self._get_base_url()

        # 构建请求
        request_data = {
            "message": {
                "role": "user",
                "content": task_description,
            },
            "context_id": context_id,
            "task_id": task_id,
            "metadata": metadata or {},
        }

        if history:
            request_data["history"] = [m.to_dict() for m in history]

        try:
            # 发送请求到 /tasks/send 端点
            response = await client.post(
                f"{base_url}/tasks/send",
                json=request_data,
            )
            response.raise_for_status()
            data = response.json()

            # 解析响应
            status = TaskState(data.get("status", "completed"))
            result_content = data.get("result", "")
            error = data.get("error")

            # 解析历史
            history_data = data.get("history", [])
            result_history = [A2AMessage.from_dict(m) for m in history_data]

            return A2AResult(
                status=status,
                result=result_content,
                error=error,
                history=result_history,
                agent_card=self._agent_card,
                task_id=data.get("task_id"),
                context_id=data.get("context_id"),
            )

        except httpx.HTTPStatusError as e:
            if e.response.status_code == 401:
                raise A2AAuthError("认证失败") from e
            raise A2AError(f"发送任务失败: {e}") from e

        except Exception as e:
            raise A2AError(f"发送任务失败: {e}") from e

    async def send_task_streaming(
        self,
        task_description: str,
        *,
        context_id: str | None = None,
        task_id: str | None = None,
        history: list[A2AMessage] | None = None,
        metadata: dict[str, Any] | None = None,
    ) -> AsyncGenerator[dict[str, Any], None]:
        """
        流式发送任务到远程 Agent

        参数：
        - task_description: 任务描述
        - context_id: 上下文 ID
        - task_id: 任务 ID
        - history: 对话历史
        - metadata: 额外元数据

        生成：
        - SSE 事件字典
        """
        client = await self._ensure_client()
        base_url = self._get_base_url()

        request_data = {
            "message": {
                "role": "user",
                "content": task_description,
            },
            "context_id": context_id,
            "task_id": task_id,
            "metadata": metadata or {},
        }

        if history:
            request_data["history"] = [m.to_dict() for m in history]

        try:
            async with client.stream(
                "POST",
                f"{base_url}/tasks/stream",
                json=request_data,
            ) as response:
                response.raise_for_status()

                async for line in response.aiter_lines():
                    if not line:
                        continue

                    if line.startswith("data: "):
                        data_str = line[6:]
                        if data_str == "[DONE]":
                            break

                        try:
                            import json

                            data = json.loads(data_str)
                            yield data
                        except Exception:
                            continue

        except httpx.HTTPStatusError as e:
            if e.response.status_code == 401:
                raise A2AAuthError("认证失败") from e
            raise A2AError(f"流式任务失败: {e}") from e

        except Exception as e:
            raise A2AError(f"流式任务失败: {e}") from e

    async def multi_turn_conversation(
        self,
        initial_message: str,
        *,
        on_response: callable | None = None,
        on_input_required: callable | None = None,
    ) -> A2AResult:
        """
        多轮对话

        参数：
        - initial_message: 初始消息
        - on_response: 收到响应时的回调
        - on_input_required: 需要输入时的回调

        返回：
        - 最终结果
        """
        history: list[A2AMessage] = []
        context_id: str | None = None
        task_id: str | None = None
        current_message = initial_message

        for turn in range(self.config.max_turns):
            result = await self.send_task(
                current_message,
                context_id=context_id,
                task_id=task_id,
                history=history,
            )

            # 更新上下文
            context_id = result.context_id
            task_id = result.task_id
            history = result.history

            # 回调
            if on_response:
                on_response(result)

            # 检查状态
            if result.status == TaskState.COMPLETED:
                return result

            if result.status == TaskState.FAILED:
                return result

            if result.status == TaskState.INPUT_REQUIRED:
                if on_input_required:
                    next_input = on_input_required(result)
                    if next_input:
                        current_message = next_input
                        continue
                # 没有提供输入，返回当前结果
                return result

            # 其他状态，等待后重试
            await asyncio.sleep(1)

        # 超过最大轮次
        logger.warning(f"多轮对话超过最大轮次: {self.config.max_turns}")
        return A2AResult(
            status=TaskState.FAILED,
            error=f"超过最大对话轮次 ({self.config.max_turns})",
            history=history,
        )


# === 异常类 ===


class A2AError(Exception):
    """A2A 基础异常"""

    pass


class A2AConnectionError(A2AError):
    """连接错误"""

    pass


class A2AAuthError(A2AError):
    """认证错误"""

    pass


class A2ATimeoutError(A2AError):
    """超时错误"""

    pass
