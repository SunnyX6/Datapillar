# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
OpenLineage 队列封装（转发到 shared.utils.event_queue）
"""

from src.shared.utils.event_queue import AsyncEventQueue, QueueConfig, QueueStats

__all__ = ["AsyncEventQueue", "QueueConfig", "QueueStats"]
