"""
A2A 配置模型

定义远程 Agent 的连接配置、认证方式等。
"""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import Any


class AuthType(str, Enum):
    """认证类型"""

    NONE = "none"
    API_KEY = "api_key"
    BEARER = "bearer"
    OAUTH2 = "oauth2"


@dataclass
class AuthScheme:
    """认证方案基类"""

    type: AuthType = AuthType.NONE

    def to_headers(self) -> dict[str, str]:
        """转换为 HTTP 头"""
        return {}


@dataclass
class APIKeyAuth(AuthScheme):
    """API Key 认证"""

    type: AuthType = field(default=AuthType.API_KEY, init=False)
    api_key: str = ""
    header_name: str = "X-API-Key"

    def to_headers(self) -> dict[str, str]:
        if not self.api_key:
            return {}
        return {self.header_name: self.api_key}


@dataclass
class BearerAuth(AuthScheme):
    """Bearer Token 认证"""

    type: AuthType = field(default=AuthType.BEARER, init=False)
    token: str = ""

    def to_headers(self) -> dict[str, str]:
        if not self.token:
            return {}
        return {"Authorization": f"Bearer {self.token}"}


@dataclass
class A2AConfig:
    """
    A2A 远程 Agent 配置

    定义如何连接和调用远程 A2A Agent。

    属性：
    - endpoint: Agent 端点 URL（AgentCard 地址）
    - auth: 认证方案
    - timeout: 请求超时（秒）
    - max_turns: 最大对话轮次
    - fail_fast: 连接失败时是否立即报错
    - trust_remote_completion: 是否信任远程 Agent 的完成状态

    使用示例：
    ```python
    config = A2AConfig(
        endpoint="https://api.example.com/.well-known/agent-card.json",
        auth=APIKeyAuth(api_key="sk-xxx"),
        timeout=120,
        max_turns=10,
    )
    ```
    """

    endpoint: str
    """Agent 端点 URL"""

    auth: AuthScheme = field(default_factory=AuthScheme)
    """认证方案"""

    timeout: int = 120
    """请求超时（秒）"""

    max_turns: int = 10
    """最大对话轮次"""

    fail_fast: bool = True
    """连接失败时是否立即报错，False 则跳过该 Agent"""

    trust_remote_completion: bool = False
    """是否信任远程 Agent 的完成状态，True 则直接返回远程结果"""

    metadata: dict[str, Any] = field(default_factory=dict)
    """额外元数据"""

    def __post_init__(self):
        """校验配置"""
        if not self.endpoint:
            raise ValueError("endpoint 不能为空")

        if not self.endpoint.startswith(("http://", "https://")):
            raise ValueError(f"endpoint 必须是 HTTP(S) URL: {self.endpoint}")

        if self.timeout <= 0:
            raise ValueError(f"timeout 必须大于 0: {self.timeout}")

        if self.max_turns <= 0:
            raise ValueError(f"max_turns 必须大于 0: {self.max_turns}")
