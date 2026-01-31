# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
from __future__ import annotations

from langchain_core.messages import RemoveMessage
from langgraph.graph.message import REMOVE_ALL_MESSAGES


def remove_all_messages() -> RemoveMessage:
    return RemoveMessage(id=REMOVE_ALL_MESSAGES, content="")
