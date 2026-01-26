from __future__ import annotations

from typing import Any

from langchain_core.messages import (
    AIMessage,
    BaseMessage,
    ChatMessage,
    HumanMessage,
    SystemMessage,
    ToolMessage,
)

from datapillar_oneagentic.messages.models import Message, ToolCall
from datapillar_oneagentic.messages.sequence import Messages


def to_langchain(messages: Messages) -> list[BaseMessage]:
    if not isinstance(messages, Messages):
        raise TypeError("to_langchain only accepts Messages")
    result: list[BaseMessage] = []
    for msg in messages:
        result.append(_to_langchain_message(msg))
    return result


def from_langchain(value: BaseMessage | list[BaseMessage]) -> Message | Messages:
    if isinstance(value, Message):
        raise TypeError("from_langchain only accepts LangChain messages")
    if isinstance(value, list):
        if value and isinstance(value[0], Message):
            raise TypeError("from_langchain only accepts LangChain message lists")
        return Messages(_from_langchain_message(msg) for msg in value)
    return _from_langchain_message(value)


def _to_langchain_message(msg: Message) -> BaseMessage:
    additional_kwargs: dict[str, Any] = {}
    if msg.metadata.get("internal") is True:
        additional_kwargs["internal"] = True
    cache_control = msg.metadata.get("cache_control")
    if not isinstance(cache_control, dict):
        cache_control = None
    if msg.role == "system":
        content: Any = msg.content
        if cache_control:
            content = [{"type": "text", "text": msg.content, "cache_control": cache_control}]
        return SystemMessage(
            content=content,
            name=msg.name,
            id=msg.id,
            additional_kwargs=additional_kwargs,
        )
    if msg.role == "user":
        return HumanMessage(
            content=msg.content,
            name=msg.name,
            id=msg.id,
            additional_kwargs=additional_kwargs,
        )
    if msg.role == "assistant":
        tool_calls = [_tool_call_dict(tc) for tc in msg.tool_calls] if msg.tool_calls else []
        reasoning = msg.metadata.get("reasoning_content")
        if isinstance(reasoning, str) and reasoning:
            additional_kwargs["reasoning_content"] = reasoning
        return AIMessage(
            content=msg.content,
            name=msg.name,
            id=msg.id,
            tool_calls=tool_calls,
            additional_kwargs=additional_kwargs,
        )
    if msg.role == "tool":
        return ToolMessage(
            content=msg.content,
            name=msg.name,
            tool_call_id=msg.tool_call_id or "",
            id=msg.id,
        )
    raise ValueError(f"Unsupported message role: {msg.role}")


def _from_langchain_message(msg: BaseMessage) -> Message:
    role = _resolve_role(msg)
    content, meta = _normalize_content(getattr(msg, "content", None))
    metadata: dict[str, Any] = {}
    metadata.update(meta)

    additional_kwargs = getattr(msg, "additional_kwargs", {}) or {}
    if isinstance(additional_kwargs, dict):
        if additional_kwargs.get("internal") is True:
            metadata["internal"] = True
        reasoning = additional_kwargs.get("reasoning_content")
        if isinstance(reasoning, str) and reasoning:
            metadata["reasoning_content"] = reasoning
        tool_calls = additional_kwargs.get("tool_calls")
    else:
        tool_calls = None

    usage_meta = getattr(msg, "usage_metadata", None)
    if isinstance(usage_meta, dict):
        metadata["usage_metadata"] = usage_meta
    response_meta = getattr(msg, "response_metadata", None)
    if isinstance(response_meta, dict):
        metadata["response_metadata"] = response_meta

    tool_calls_data = getattr(msg, "tool_calls", None) or tool_calls or []
    tool_calls_parsed = _parse_tool_calls(tool_calls_data)

    tool_call_id = getattr(msg, "tool_call_id", None)
    if isinstance(tool_call_id, str) and tool_call_id:
        tool_call_id_value = tool_call_id
    else:
        tool_call_id_value = None

    name = getattr(msg, "name", None)
    msg_id = getattr(msg, "id", None)

    return Message(
        role=role,
        content=content,
        name=name,
        id=msg_id,
        tool_calls=tool_calls_parsed,
        tool_call_id=tool_call_id_value,
        metadata=metadata,
    )


def _resolve_role(msg: BaseMessage) -> str:
    if isinstance(msg, SystemMessage):
        return "system"
    if isinstance(msg, HumanMessage):
        return "user"
    if isinstance(msg, AIMessage):
        return "assistant"
    if isinstance(msg, ToolMessage):
        return "tool"
    if isinstance(msg, ChatMessage):
        role = getattr(msg, "role", None)
        if role in {"system", "user", "assistant", "tool"}:
            return role
        return "assistant"
    return "assistant"


def _normalize_content(content: Any) -> tuple[str, dict[str, Any]]:
    if content is None:
        return "", {}
    if isinstance(content, str):
        return content, {}
    if not isinstance(content, list):
        return str(content), {}

    text_parts: list[str] = []
    thinking_parts: list[str] = []

    for block in content:
        if isinstance(block, str):
            text_parts.append(block)
            continue
        if not isinstance(block, dict):
            continue
        block_type = block.get("type")
        if block_type == "text":
            text_parts.append(str(block.get("text", "")))
        elif block_type == "thinking":
            thinking = block.get("thinking", "")
            if thinking:
                thinking_parts.append(str(thinking))

    meta: dict[str, Any] = {}
    if thinking_parts:
        meta["reasoning_content"] = "\n".join(thinking_parts)

    return "\n".join(text_parts), meta


def _parse_tool_calls(tool_calls: Any) -> list[ToolCall]:
    if not tool_calls:
        return []
    parsed: list[ToolCall] = []
    for call in tool_calls:
        if isinstance(call, ToolCall):
            parsed.append(call)
            continue
        if isinstance(call, dict):
            call_id = str(call.get("id", "") or "")
            name = str(call.get("name", "") or "")
            args = call.get("args")
            if not isinstance(args, dict):
                args = {}
            if not name:
                continue
            parsed.append(ToolCall(id=call_id, name=name, args=args))
            continue
        call_id = getattr(call, "id", None)
        name = getattr(call, "name", None)
        args = getattr(call, "args", None)
        if not isinstance(args, dict):
            args = {}
        if name:
            parsed.append(ToolCall(id=str(call_id or ""), name=str(name), args=args))
    return parsed


def _tool_call_dict(call: ToolCall) -> dict[str, Any]:
    return {"id": call.id, "name": call.name, "args": call.args}


def is_langchain_message(value: Any) -> bool:
    return isinstance(value, BaseMessage)


def is_langchain_list(value: Any) -> bool:
    if not isinstance(value, list):
        return False
    return all(isinstance(item, BaseMessage) for item in value)


def build_ai_message(content: str, tool_calls: list[dict[str, Any]] | None = None) -> BaseMessage:
    if tool_calls is None:
        return AIMessage(content=content)
    try:
        return AIMessage(content=content, tool_calls=tool_calls)
    except TypeError:
        msg = AIMessage(content=content, additional_kwargs={"tool_calls": tool_calls})
        try:
            object.__setattr__(msg, "tool_calls", tool_calls)
        except Exception:
            setattr(msg, "tool_calls", tool_calls)
        return msg
