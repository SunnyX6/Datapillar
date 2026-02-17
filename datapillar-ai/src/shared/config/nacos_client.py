# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-02-04

"""
Nacos 配置加载与服务注册

约束：
- Nacos 为唯一配置源，不做本地兜底
- 仅在启动阶段拉取配置（默认不做热更新）
"""

from __future__ import annotations

import logging
import os
import socket
from dataclasses import dataclass
from typing import Any, Awaitable, Callable

import yaml
from dynaconf import Dynaconf
from v2.nacos import (
    ClientConfigBuilder,
    ConfigParam,
    DeregisterInstanceParam,
    NacosConfigService,
    NacosNamingService,
    RegisterInstanceParam,
)

from src.shared.config.exceptions import ConfigurationError
from src.shared.config.runtime import (
    build_runtime_config,
    set_runtime_config,
    settings_to_dict,
)

logger = logging.getLogger(__name__)

ConfigChangeListener = Callable[[str, str, str, str], Awaitable[None]]


@dataclass(frozen=True)
class NacosBootstrapConfig:
    server_addr: str
    namespace: str
    username: str
    password: str
    group: str
    data_id: str
    service_name: str
    cluster_name: str
    ephemeral: bool
    heartbeat_interval: int
    watch_enabled: bool


class NacosRuntime:
    """Nacos 运行期对象：持有客户端与注册信息"""

    def __init__(
        self,
        naming_client: NacosNamingService,
        config_client: NacosConfigService,
        config: NacosBootstrapConfig,
        config_listener: ConfigChangeListener | None = None,
    ):
        self._naming_client = naming_client
        self._config_client = config_client
        self._config = config
        self._config_listener = config_listener
        self._service_ip: str | None = None
        self._service_port: int | None = None
        self._registered = False
        self._shutdown = False

    @property
    def config(self) -> NacosBootstrapConfig:
        return self._config

    async def register_service(self, port: int) -> None:
        if self._registered:
            return
        service_ip = resolve_service_ip()
        self._service_ip = service_ip
        self._service_port = port

        logger.info(
            "注册 Nacos 服务实例: service=%s, ip=%s, port=%s, group=%s, cluster=%s",
            self._config.service_name,
            service_ip,
            port,
            self._config.group,
            self._config.cluster_name,
        )

        registered = await self._naming_client.register_instance(
            RegisterInstanceParam(
                service_name=self._config.service_name,
                group_name=self._config.group,
                ip=service_ip,
                port=port,
                cluster_name=self._config.cluster_name,
                metadata={},
                weight=1.0,
                enabled=True,
                healthy=True,
                ephemeral=self._config.ephemeral,
            )
        )
        if not registered:
            raise ConfigurationError(
                f"Nacos 服务注册失败: service={self._config.service_name}, ip={service_ip}, port={port}"
            )
        self._registered = True

    async def deregister_service(self) -> None:
        if not self._registered or not self._service_ip or not self._service_port:
            return
        logger.info(
            "注销 Nacos 服务实例: service=%s, ip=%s, port=%s",
            self._config.service_name,
            self._service_ip,
            self._service_port,
        )
        try:
            removed = await self._naming_client.deregister_instance(
                DeregisterInstanceParam(
                    service_name=self._config.service_name,
                    group_name=self._config.group,
                    ip=self._service_ip,
                    port=self._service_port,
                    cluster_name=self._config.cluster_name,
                    ephemeral=self._config.ephemeral,
                )
            )
            if not removed:
                logger.warning(
                    "Nacos 服务注销返回 false: service=%s, ip=%s, port=%s",
                    self._config.service_name,
                    self._service_ip,
                    self._service_port,
                )
        finally:
            self._registered = False

    async def shutdown(self) -> None:
        if self._shutdown:
            return

        if self._config_listener is not None and self._config.watch_enabled:
            try:
                await self._config_client.remove_listener(
                    self._config.data_id,
                    self._config.group,
                    self._config_listener,
                )
            except Exception as exc:
                logger.warning("移除 Nacos 配置监听失败: %s", exc, exc_info=True)
            self._config_listener = None

        await self._naming_client.shutdown()
        await self._config_client.shutdown()
        self._shutdown = True


async def bootstrap_nacos(settings: Dynaconf) -> NacosRuntime:
    """启动期加载 Nacos 配置并初始化运行期对象"""
    config = _load_bootstrap_config()
    client_config = _build_client_config(config)
    config_client = await NacosConfigService.create_config_service(client_config)
    naming_client = await NacosNamingService.create_naming_service(client_config)

    try:
        raw_config = await load_nacos_config(config_client, config.data_id, config.group)
        apply_nacos_config(settings, raw_config)

        listener = None
        if config.watch_enabled:
            listener = await start_config_watch(
                config_client,
                config.data_id,
                config.group,
                settings,
            )

        return NacosRuntime(
            naming_client=naming_client,
            config_client=config_client,
            config=config,
            config_listener=listener,
        )
    except Exception:
        await naming_client.shutdown()
        await config_client.shutdown()
        raise


async def load_nacos_config(client: Any, data_id: str, group: str) -> dict[str, Any]:
    """从 Nacos 拉取并解析配置"""
    try:
        content = await client.get_config(ConfigParam(data_id=data_id, group=group))
    except Exception as exc:
        raise ConfigurationError(f"Nacos 配置拉取失败: dataId={data_id}, group={group}") from exc
    return parse_nacos_config_content(content, data_id, group)


def parse_nacos_config_content(
    content: str | bytes | None,
    data_id: str = "",
    group: str = "",
) -> dict[str, Any]:
    if content is None:
        raise ConfigurationError(f"Nacos 配置为空: dataId={data_id}, group={group}")
    if isinstance(content, bytes):
        content = content.decode("utf-8")
    if not str(content).strip():
        raise ConfigurationError(f"Nacos 配置为空: dataId={data_id}, group={group}")
    try:
        data = yaml.safe_load(content)
    except Exception as exc:
        raise ConfigurationError(
            f"Nacos 配置解析失败: dataId={data_id}, group={group}"
        ) from exc
    if data is None:
        raise ConfigurationError(f"Nacos 配置为空: dataId={data_id}, group={group}")
    if not isinstance(data, dict):
        raise ConfigurationError(f"Nacos 配置必须是 YAML 对象: dataId={data_id}, group={group}")
    return data


def apply_nacos_config(settings: Dynaconf, config: dict[str, Any]) -> None:
    # 严格校验：先校验完整配置，再更新 settings，避免无效配置污染运行态。
    current = settings_to_dict(settings)
    merged = {**current, **config}
    runtime_config = build_runtime_config(merged)

    settings.update(config)
    set_runtime_config(runtime_config)
    logger.info("Nacos 配置已加载: keys=%s", sorted(config.keys()))


async def start_config_watch(
    client: NacosConfigService,
    data_id: str,
    group: str,
    settings: Dynaconf,
) -> ConfigChangeListener:
    async def _listener(tenant: str, second: str, third: str, content: str) -> None:
        try:
            changed_data_id, changed_group = _resolve_change_pair(
                second=second,
                third=third,
                expected_data_id=data_id,
                expected_group=group,
            )
            data = parse_nacos_config_content(content, changed_data_id, changed_group)
            apply_nacos_config(settings, data)
            logger.info(
                "Nacos 配置监听生效: dataId=%s, group=%s, tenant=%s",
                changed_data_id,
                changed_group,
                tenant,
            )
        except Exception as exc:
            logger.error("Nacos 配置监听失败: %s", exc, exc_info=True)

    await client.add_listener(data_id, group, _listener)
    logger.info("Nacos 配置监听已开启: dataId=%s, group=%s", data_id, group)
    return _listener


def _resolve_change_pair(
    second: str,
    third: str,
    expected_data_id: str,
    expected_group: str,
) -> tuple[str, str]:
    if second == expected_data_id and third == expected_group:
        return second, third
    if second == expected_group and third == expected_data_id:
        return third, second
    if second.endswith((".yaml", ".yml", ".json", ".properties")):
        return second, third
    if third.endswith((".yaml", ".yml", ".json", ".properties")):
        return third, second
    return third, second


def resolve_service_ip() -> str:
    """优先使用显式环境变量，其次探测本机 IP"""
    for key in ("NACOS_SERVICE_IP", "POD_IP", "HOST_IP"):
        value = os.getenv(key)
        if value:
            return value

    try:
        host_ip = socket.gethostbyname(socket.gethostname())
        if host_ip and not host_ip.startswith("127."):
            return host_ip
    except Exception:
        pass

    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
            sock.connect(("8.8.8.8", 80))
            return sock.getsockname()[0]
    except Exception as exc:
        raise ConfigurationError("无法解析服务 IP，请显式配置 NACOS_SERVICE_IP") from exc


def _build_client_config(config: NacosBootstrapConfig):
    heartbeat_millis = max(config.heartbeat_interval, 1) * 1000
    log_dir = os.getenv("NACOS_LOG_DIR", "/tmp/datapillar-logs/nacos")
    cache_dir = os.getenv("NACOS_CACHE_DIR", "/tmp/datapillar-logs/nacos/cache")
    return (
        ClientConfigBuilder()
        .server_address(config.server_addr)
        .namespace_id(config.namespace)
        .username(config.username)
        .password(config.password)
        .log_level("INFO")
        .log_dir(log_dir)
        .cache_dir(cache_dir)
        .heart_beat_interval(heartbeat_millis)
        .build()
    )


def _load_bootstrap_config() -> NacosBootstrapConfig:
    server_addr = _required_env("NACOS_SERVER_ADDR")
    namespace = _required_env("NACOS_NAMESPACE")
    username = _required_env("NACOS_USERNAME")
    password = _required_env("NACOS_PASSWORD")
    group = _required_env("NACOS_GROUP")
    data_id = _required_env("NACOS_DATA_ID")
    service_name = _required_env("NACOS_SERVICE_NAME")
    cluster_name = _required_env("NACOS_CLUSTER_NAME")
    ephemeral = _required_bool_env("NACOS_EPHEMERAL")
    heartbeat_interval = _required_int_env("NACOS_HEARTBEAT_INTERVAL")
    watch_enabled = _required_bool_env("NACOS_CONFIG_WATCH")
    return NacosBootstrapConfig(
        server_addr=server_addr,
        namespace=namespace,
        username=username,
        password=password,
        group=group,
        data_id=data_id,
        service_name=service_name,
        cluster_name=cluster_name,
        ephemeral=ephemeral,
        heartbeat_interval=heartbeat_interval,
        watch_enabled=watch_enabled,
    )


def _required_env(name: str) -> str:
    value = os.getenv(name)
    if value is None or not value.strip():
        raise ConfigurationError(f"缺少 Nacos 环境变量: {name}")
    return value


def _required_bool_env(name: str) -> bool:
    value = _required_env(name).strip().lower()
    if value in {"1", "true", "yes", "y"}:
        return True
    if value in {"0", "false", "no", "n"}:
        return False
    raise ConfigurationError(f"Nacos 环境变量格式错误: {name}={value}")


def _required_int_env(name: str) -> int:
    value = _required_env(name)
    try:
        return int(value)
    except ValueError as exc:
        raise ConfigurationError(f"Nacos 环境变量格式错误: {name}={value}") from exc
