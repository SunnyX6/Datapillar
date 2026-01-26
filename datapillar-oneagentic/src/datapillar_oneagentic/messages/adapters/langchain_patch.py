from __future__ import annotations

from typing import Any

from langchain_core.messages import (
    AIMessage,
    AIMessageChunk,
    BaseMessage,
    BaseMessageChunk,
    ChatMessage,
    ChatMessageChunk,
    HumanMessage,
    HumanMessageChunk,
    SystemMessage,
    SystemMessageChunk,
    ToolMessage,
)


def _convert_delta_chunk(
    dct: dict[str, Any], default_class: type[BaseMessageChunk]
) -> BaseMessageChunk:
    role = dct.get("role")
    content = dct.get("content", "")
    additional_kwargs: dict[str, Any] = {}

    tool_calls = dct.get("tool_calls")
    if tool_calls is not None:
        additional_kwargs["tool_calls"] = tool_calls

    reasoning_content = dct.get("reasoning_content")
    if reasoning_content:
        additional_kwargs["reasoning_content"] = reasoning_content

    if role == "system" or default_class == SystemMessageChunk:
        return SystemMessageChunk(content=content)
    if role == "user" or default_class == HumanMessageChunk:
        return HumanMessageChunk(content=content)
    if role == "assistant" or default_class == AIMessageChunk:
        return AIMessageChunk(content=content, additional_kwargs=additional_kwargs)
    if role or default_class == ChatMessageChunk:
        return ChatMessageChunk(content=content, role=role)  # type: ignore[arg-type]
    return default_class(content=content)  # type: ignore[call-arg]


def _convert_dict_message(dct: dict[str, Any]) -> BaseMessage:
    role = dct.get("role")
    content = dct.get("content", "")

    if role == "system":
        return SystemMessage(content=content)
    if role == "user":
        return HumanMessage(content=content)
    if role == "assistant":
        additional_kwargs: dict[str, Any] = {}
        tool_calls = dct.get("tool_calls")
        if tool_calls is not None:
            additional_kwargs["tool_calls"] = tool_calls
        reasoning_content = dct.get("reasoning_content")
        if reasoning_content:
            additional_kwargs["reasoning_content"] = reasoning_content
        return AIMessage(content=content, additional_kwargs=additional_kwargs)
    if role == "tool":
        additional_kwargs = {}
        if "name" in dct:
            additional_kwargs["name"] = dct["name"]
        return ToolMessage(
            content=content,
            tool_call_id=dct.get("tool_call_id"),
            additional_kwargs=additional_kwargs,
        )
    return ChatMessage(role=role, content=content)  # type: ignore[arg-type]


def apply_zhipuai_patch() -> None:
    try:
        import langchain_community.chat_models.zhipuai as zhipuai_module

        zhipuai_module._convert_delta_to_message_chunk = _convert_delta_chunk
        zhipuai_module._convert_dict_to_message = _convert_dict_message
    except ImportError:
        pass
