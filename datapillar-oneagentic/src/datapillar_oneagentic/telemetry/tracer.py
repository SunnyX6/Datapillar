"""
OpenTelemetry Tracer 配置

提供 tracer 初始化和管理。
"""

from __future__ import annotations

import atexit
import logging
from typing import Any

logger = logging.getLogger(__name__)

# 全局状态
_tracer_provider: Any = None
_tracer: Any = None
_enabled: bool = False


def init_telemetry(
    *,
    service_name: str = "oneagentic",
    endpoint: str | None = None,
    headers: dict[str, str] | None = None,
    export_to_console: bool = False,
) -> bool:
    """
    初始化 OpenTelemetry

    参数：
    - service_name: 服务名称
    - endpoint: OTLP 端点（如 http://localhost:4318/v1/traces）
    - headers: 额外的 HTTP 头（如认证）
    - export_to_console: 是否同时输出到控制台

    返回：
    - 是否成功初始化
    """
    global _tracer_provider, _tracer, _enabled

    try:
        from opentelemetry import trace
        from opentelemetry.sdk.resources import SERVICE_NAME, Resource
        from opentelemetry.sdk.trace import TracerProvider
        from opentelemetry.sdk.trace.export import BatchSpanProcessor
    except ImportError:
        logger.warning(
            "OpenTelemetry 未安装，遥测功能不可用。"
            "请安装：pip install datapillar-oneagentic[telemetry]"
        )
        return False

    try:
        # 创建资源
        resource = Resource.create({SERVICE_NAME: service_name})

        # 创建 TracerProvider
        _tracer_provider = TracerProvider(resource=resource)

        # 添加导出器
        if endpoint:
            try:
                from opentelemetry.exporter.otlp.proto.http.trace_exporter import (
                    OTLPSpanExporter,
                )

                otlp_exporter = OTLPSpanExporter(
                    endpoint=endpoint,
                    headers=headers or {},
                )
                _tracer_provider.add_span_processor(BatchSpanProcessor(otlp_exporter))
                logger.info(f"OpenTelemetry OTLP 导出器已配置: {endpoint}")
            except ImportError:
                logger.warning("OTLP 导出器未安装，跳过远程导出")

        if export_to_console:
            try:
                from opentelemetry.sdk.trace.export import (
                    ConsoleSpanExporter,
                    SimpleSpanProcessor,
                )

                _tracer_provider.add_span_processor(
                    SimpleSpanProcessor(ConsoleSpanExporter())
                )
                logger.info("OpenTelemetry 控制台导出器已配置")
            except ImportError:
                pass

        # 设置全局 TracerProvider
        trace.set_tracer_provider(_tracer_provider)

        # 获取 tracer
        _tracer = trace.get_tracer(service_name)
        _enabled = True

        logger.info(f"OpenTelemetry 初始化成功: {service_name}")

        # 注册退出处理
        atexit.register(shutdown_telemetry)

        return True

    except Exception as e:
        logger.error(f"OpenTelemetry 初始化失败: {e}")
        return False


def shutdown_telemetry() -> None:
    """关闭 OpenTelemetry"""
    global _tracer_provider, _tracer, _enabled

    if _tracer_provider:
        try:
            _tracer_provider.shutdown()
            logger.info("OpenTelemetry 已关闭")
        except Exception as e:
            logger.error(f"OpenTelemetry 关闭失败: {e}")

    _tracer_provider = None
    _tracer = None
    _enabled = False


def get_tracer() -> Any:
    """
    获取 Tracer

    返回：
    - OpenTelemetry Tracer 或 NoOpTracer
    """
    global _tracer

    if _tracer is not None:
        return _tracer

    # 返回 NoOp tracer
    try:
        from opentelemetry import trace
        return trace.get_tracer("oneagentic")
    except ImportError:
        return _NoOpTracer()


def is_telemetry_enabled() -> bool:
    """检查遥测是否已启用"""
    return _enabled


class _NoOpTracer:
    """空操作 Tracer（OpenTelemetry 未安装时使用）"""

    def start_as_current_span(self, name: str, **kwargs):
        """返回空上下文管理器"""
        return _NoOpSpan()

    def start_span(self, name: str, **kwargs):
        """返回空 Span"""
        return _NoOpSpan()


class _NoOpSpan:
    """空操作 Span"""

    def __enter__(self):
        return self

    def __exit__(self, *args):
        pass

    def set_attribute(self, key: str, value: Any) -> None:
        pass

    def set_attributes(self, attributes: dict) -> None:
        pass

    def add_event(self, name: str, attributes: dict | None = None) -> None:
        pass

    def record_exception(self, exception: Exception) -> None:
        pass

    def set_status(self, status: Any) -> None:
        pass

    def end(self) -> None:
        pass
