"""
安全校验器

实现 MCP 官方安全规范：
1. 基于 Tool Annotations 判断工具是否危险
2. 危险工具调用前需要用户确认（Human-in-the-loop）
3. URL 安全校验（SSRF 防护，含 DNS 解析校验）

参考：https://modelcontextprotocol.io/specification
"""

from __future__ import annotations

import ipaddress
import logging
import socket
from collections.abc import Callable
from dataclasses import dataclass, field
from typing import Any
from urllib.parse import urlparse

logger = logging.getLogger(__name__)


# ==================== 异常定义 ====================


class SecurityError(Exception):
    """安全错误基类"""

    pass


class UserRejectedError(SecurityError):
    """用户拒绝执行"""

    pass


class NoConfirmationCallbackError(SecurityError):
    """未配置确认回调但需要用户确认"""

    pass


class URLNotAllowedError(SecurityError):
    """URL 不允许错误"""

    pass


# ==================== 内网 IP 段（SSRF 防护）====================

PRIVATE_IP_RANGES = [
    ipaddress.ip_network("127.0.0.0/8"),  # localhost
    ipaddress.ip_network("10.0.0.0/8"),  # Class A private
    ipaddress.ip_network("172.16.0.0/12"),  # Class B private
    ipaddress.ip_network("192.168.0.0/16"),  # Class C private
    ipaddress.ip_network("169.254.0.0/16"),  # Link-local
    ipaddress.ip_network("::1/128"),  # IPv6 localhost
    ipaddress.ip_network("fc00::/7"),  # IPv6 private
    ipaddress.ip_network("fe80::/10"),  # IPv6 link-local
]


# ==================== 确认请求 ====================


@dataclass
class ConfirmationRequest:
    """
    危险操作确认请求

    包含使用方做出判断所需的全部信息
    """

    # 操作类型
    operation_type: str
    """操作类型: 'mcp_tool' | 'a2a_delegate'"""

    # 基本信息
    name: str
    """工具/Agent 名称"""

    description: str
    """工具/Agent 描述"""

    # 调用详情
    parameters: dict[str, Any]
    """调用参数（完整）"""

    # 风险信息
    risk_level: str
    """风险等级: 'low' | 'medium' | 'high' | 'critical'"""

    warnings: list[str]
    """风险警告列表"""

    # 来源信息
    source: str
    """来源: MCP 服务器地址 / A2A endpoint"""

    # 额外元数据
    metadata: dict[str, Any] = field(default_factory=dict)
    """
    额外元数据，可能包含：
    - mcp_server: MCP 服务器配置
    - tool_annotations: MCP 工具注解
    - a2a_config: A2A 配置
    - agent_card: A2A Agent Card 信息
    """

    def to_display_string(self) -> str:
        """生成人类可读的确认信息"""
        lines = [
            f"{'=' * 50}",
            "⚠️  危险操作确认请求",
            f"{'=' * 50}",
            "",
            f"操作类型: {self.operation_type}",
            f"名称: {self.name}",
            f"描述: {self.description}",
            f"来源: {self.source}",
            f"风险等级: {self.risk_level}",
            "",
            "调用参数:",
        ]

        for key, value in self.parameters.items():
            # 截断过长的值
            value_str = str(value)
            if len(value_str) > 100:
                value_str = value_str[:100] + "..."
            lines.append(f"  {key}: {value_str}")

        if self.warnings:
            lines.append("")
            lines.append("风险警告:")
            for w in self.warnings:
                lines.append(f"  ⚠️ {w}")

        lines.append(f"{'=' * 50}")
        return "\n".join(lines)


# ==================== 安全配置 ====================


@dataclass
class SecurityConfig:
    """
    安全配置

    属性：
    - require_confirmation: 是否需要用户确认危险操作（MCP 规范要求）
    - confirmation_callback: 用户确认回调函数
    - allow_private_urls: 是否允许访问内网 URL
    - require_https: 是否强制 HTTPS（生产环境建议开启）
    - allowed_domains: URL 域名白名单（空表示不限制）
    """

    require_confirmation: bool = True
    """是否需要用户确认危险操作"""

    confirmation_callback: Callable[[ConfirmationRequest], bool] | None = None
    """
    用户确认回调函数

    参数：
    - request: ConfirmationRequest 对象，包含完整的确认信息

    返回：
    - True: 用户确认执行
    - False: 用户拒绝执行

    示例：
    ```python
    def my_callback(request: ConfirmationRequest) -> bool:
        print(request.to_display_string())
        print(f"风险等级: {request.risk_level}")
        print(f"参数: {request.parameters}")
        return input("确认？(y/N): ").lower() == "y"
    ```
    """

    allow_private_urls: bool = False
    """是否允许访问内网 URL（SSRF 防护）"""

    require_https: bool = False
    """是否强制 HTTPS"""

    allowed_domains: list[str] = field(default_factory=list)
    """URL 域名白名单（空表示不限制）"""


# 全局安全配置
_security_config: SecurityConfig = SecurityConfig()


def get_security_config() -> SecurityConfig:
    """获取当前安全配置"""
    return _security_config


def configure_security(
    *,
    require_confirmation: bool | None = None,
    confirmation_callback: Callable[[ConfirmationRequest], bool] | None = None,
    allow_private_urls: bool | None = None,
    require_https: bool | None = None,
    allowed_domains: list[str] | None = None,
) -> None:
    """
    配置安全选项

    参数：
    - require_confirmation: 是否需要用户确认危险操作
    - confirmation_callback: 用户确认回调函数（接收 ConfirmationRequest）
    - allow_private_urls: 是否允许访问内网 URL
    - require_https: 是否强制 HTTPS
    - allowed_domains: URL 域名白名单
    """
    global _security_config

    if require_confirmation is not None:
        _security_config.require_confirmation = require_confirmation
    if confirmation_callback is not None:
        _security_config.confirmation_callback = confirmation_callback
    if allow_private_urls is not None:
        _security_config.allow_private_urls = allow_private_urls
    if require_https is not None:
        _security_config.require_https = require_https
    if allowed_domains is not None:
        _security_config.allowed_domains = allowed_domains


def reset_security_config() -> None:
    """重置安全配置为默认值（主要用于测试）"""
    global _security_config
    _security_config = SecurityConfig()


# ==================== URL 校验 ====================


def _check_ip_in_private_ranges(ip_str: str) -> bool:
    """检查 IP 地址是否在内网范围"""
    try:
        ip = ipaddress.ip_address(ip_str)
        return any(ip in network for network in PRIVATE_IP_RANGES)
    except ValueError:
        return False


def is_private_ip(hostname: str) -> bool:
    """
    检查是否是内网 IP（含 DNS 解析）

    防护 DNS rebinding 攻击：
    - 先检查字符串是否直接是 IP
    - 再进行 DNS 解析，检查解析结果
    """
    # 检查特殊主机名
    lower_hostname = hostname.lower()
    if lower_hostname in ("localhost", "localhost.localdomain", "ip6-localhost"):
        return True

    # 检查字符串是否直接是 IP
    if _check_ip_in_private_ranges(hostname):
        return True

    # DNS 解析后检查（防止 DNS rebinding）
    try:
        addr_info = socket.getaddrinfo(hostname, None, socket.AF_UNSPEC, socket.SOCK_STREAM)
        for _family, _, _, _, sockaddr in addr_info:
            ip_str = sockaddr[0]
            if _check_ip_in_private_ranges(ip_str):
                logger.warning(f"DNS 解析发现内网 IP: {hostname} -> {ip_str}")
                return True
    except socket.gaierror as e:
        # DNS 解析失败，保守起见视为可疑
        logger.warning(f"DNS 解析失败: {hostname}, 错误: {e}")
        return True

    return False


def validate_url(url: str) -> None:
    """
    校验 URL 安全性（SSRF 防护）

    参数：
    - url: 要校验的 URL

    异常：
    - URLNotAllowedError: URL 不符合安全要求
    """
    config = get_security_config()
    parsed = urlparse(url)

    # 检查协议
    if parsed.scheme not in ("http", "https"):
        raise URLNotAllowedError(f"不支持的协议: {parsed.scheme}，仅允许 HTTP(S)")

    # 强制 HTTPS
    if config.require_https and parsed.scheme != "https":
        raise URLNotAllowedError(f"安全配置要求 HTTPS: {url}")

    # 获取主机名
    hostname = parsed.hostname
    if not hostname:
        raise URLNotAllowedError(f"无效的 URL，缺少主机名: {url}")

    # 检查内网 IP
    if not config.allow_private_urls and is_private_ip(hostname):
        raise URLNotAllowedError(
            f"禁止访问内网地址: {hostname}\n"
            f"如需访问，请配置 allow_private_urls=True"
        )

    # 检查域名白名单
    if config.allowed_domains and not any(
        hostname == domain or hostname.endswith(f".{domain}")
        for domain in config.allowed_domains
    ):
        raise URLNotAllowedError(
            f"域名不在白名单中: {hostname}\n"
            f"允许的域名: {', '.join(config.allowed_domains)}"
        )

