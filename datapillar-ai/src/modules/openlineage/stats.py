"""
统计和监控

汇总所有组件的统计信息，提供监控指标
"""

from dataclasses import dataclass, field
from datetime import datetime
from typing import Any

import structlog

logger = structlog.get_logger()


@dataclass
class ComponentStats:
    """单个组件统计"""

    name: str
    enabled: bool = True
    stats: dict[str, Any] = field(default_factory=dict)


@dataclass
class SinkMetrics:
    """
    Sink 完整监控指标

    汇总所有组件的统计信息
    """

    state: str = "stopped"
    start_time: datetime | None = None

    events_received: int = 0
    events_accepted: int = 0
    events_rejected: int = 0
    events_processed: int = 0
    events_failed: int = 0

    rate_limiter: ComponentStats | None = None
    event_filter: ComponentStats | None = None
    queue: ComponentStats | None = None
    retry_handler: ComponentStats | None = None
    metadata_writer: ComponentStats | None = None
    lineage_writer: ComponentStats | None = None
    metric_writer: ComponentStats | None = None

    def to_dict(self) -> dict[str, Any]:
        """转换为字典"""
        uptime = 0.0
        if self.start_time:
            uptime = (datetime.utcnow() - self.start_time).total_seconds()

        result = {
            "state": self.state,
            "uptime_seconds": round(uptime, 2),
            "events": {
                "received": self.events_received,
                "accepted": self.events_accepted,
                "rejected": self.events_rejected,
                "processed": self.events_processed,
                "failed": self.events_failed,
                "success_rate": self._calc_success_rate(),
            },
            "components": {},
        }

        for component_name in [
            "rate_limiter",
            "event_filter",
            "queue",
            "retry_handler",
            "metadata_writer",
            "lineage_writer",
            "metric_writer",
        ]:
            component = getattr(self, component_name)
            if component:
                result["components"][component_name] = {
                    "enabled": component.enabled,
                    "stats": component.stats,
                }

        return result

    def _calc_success_rate(self) -> float:
        """计算成功率"""
        if self.events_processed + self.events_failed == 0:
            return 0.0
        return self.events_processed / (self.events_processed + self.events_failed)


class StatsCollector:
    """
    统计收集器

    负责收集和汇总所有组件的统计信息
    """

    def __init__(self) -> None:
        self._metrics = SinkMetrics()

    def update_state(self, state: str) -> None:
        """更新状态"""
        self._metrics.state = state
        if state == "running" and self._metrics.start_time is None:
            self._metrics.start_time = datetime.utcnow()

    def record_event_received(self) -> None:
        """记录接收事件"""
        self._metrics.events_received += 1

    def record_event_accepted(self) -> None:
        """记录接受事件"""
        self._metrics.events_accepted += 1

    def record_event_rejected(self, reason: str) -> None:
        """记录拒绝事件"""
        self._metrics.events_rejected += 1
        logger.debug("event_rejected", reason=reason)

    def record_event_processed(self) -> None:
        """记录处理完成"""
        self._metrics.events_processed += 1

    def record_event_failed(self, error: str) -> None:
        """记录处理失败"""
        self._metrics.events_failed += 1
        logger.warning("event_failed", error=error)

    def update_component_stats(
        self,
        component_name: str,
        enabled: bool,
        stats: dict[str, Any],
    ) -> None:
        """更新组件统计"""
        component_stats = ComponentStats(
            name=component_name,
            enabled=enabled,
            stats=stats,
        )
        setattr(self._metrics, component_name, component_stats)

    def get_metrics(self) -> SinkMetrics:
        """获取完整指标"""
        return self._metrics

    def get_metrics_dict(self) -> dict[str, Any]:
        """获取指标字典"""
        return self._metrics.to_dict()

    def reset(self) -> None:
        """重置统计"""
        self._metrics = SinkMetrics()
