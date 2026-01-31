# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
OpenLineage Sink 核心组件

- AsyncEventQueue: Sink 端二次保护机制（缓冲队列）

retry、rate_limit、filter 等由 Producer 端配置
"""

from src.modules.openlineage.core.queue import AsyncEventQueue, QueueConfig, QueueStats

__all__ = [
    "AsyncEventQueue",
    "QueueConfig",
    "QueueStats",
]
