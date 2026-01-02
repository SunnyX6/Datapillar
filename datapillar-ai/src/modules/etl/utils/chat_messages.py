from __future__ import annotations

from langchain_core.messages import HumanMessage, SystemMessage


def build_system_human_messages(system_instruction: str, user_input: str) -> list:
    return [
        SystemMessage(content=system_instruction),
        HumanMessage(content=user_input),
    ]

