"""
GLM 官方 zai-sdk 适配器（LangChain BaseChatModel）

职责：
- 使用 ZhipuAiClient 调用 chat.completions（工具调用 / JSON 结构化输出）
- 维持 LangChain BaseChatModel 习惯用法：bind_tools、with_structured_output
- 消息、工具、输出均保持与 OpenAI/Claude 适配器一致的字段约定
"""

from __future__ import annotations

import asyncio
import json
from typing import Any, Dict, List, Optional, Type, TypeVar

from langchain_core.callbacks.manager import (
    AsyncCallbackManagerForLLMRun,
    CallbackManagerForLLMRun,
)
from langchain_core.language_models.chat_models import BaseChatModel
from langchain_core.messages import (
    AIMessage,
    BaseMessage,
    HumanMessage,
    SystemMessage,
    ToolMessage,
)
from langchain_core.outputs import ChatGeneration, ChatResult
from langchain_core.output_parsers import PydanticOutputParser
from langchain_core.runnables.base import RunnableSequence
from langchain_core.tools import BaseTool
from pydantic import BaseModel as LCBaseModel, ConfigDict
from zai import ZhipuAiClient

T = TypeVar("T", bound=LCBaseModel)


def _lc_tool_to_glm(tool: BaseTool) -> Dict[str, Any]:
    """将 LangChain 工具转换为 GLM function schema。"""
    schema: Dict[str, Any] = {"type": "object", "properties": {}}
    if getattr(tool, "args_schema", None):
        try:
            schema = tool.args_schema.model_json_schema()
        except Exception:
            try:
                schema = tool.args_schema.schema()
            except Exception:
                pass
    return {
        "type": "function",
        "function": {
            "name": tool.name,
            "description": getattr(tool, "description", "") or "",
            "parameters": schema,
        },
    }


def _lc_message_to_glm(msg: BaseMessage) -> Dict[str, Any]:
    """将 LC Message 转成 GLM chat.completions 消息格式。"""
    if isinstance(msg, HumanMessage):
        return {"role": "user", "content": msg.content}
    if isinstance(msg, SystemMessage):
        return {"role": "system", "content": msg.content}
    if isinstance(msg, ToolMessage):
        return {
            "role": "tool",
            "tool_call_id": getattr(msg, "tool_call_id", None),
            "name": getattr(msg, "name", None),
            "content": msg.content,
        }
    if isinstance(msg, AIMessage):
        base: Dict[str, Any] = {"role": "assistant", "content": msg.content or ""}
        tool_calls = getattr(msg, "tool_calls", None)
        if tool_calls:
            base["tool_calls"] = [
                {
                    "id": tc.get("id") or tc.get("tool_call_id"),
                    "type": "function",
                    "function": {
                        "name": tc.get("name") or tc.get("function", {}).get("name"),
                        # 发送给 GLM 时需确保 arguments 为字符串
                        "arguments": _ensure_json_str(
                            tc.get("args")
                            or tc.get("arguments")
                            or tc.get("function", {}).get("arguments", "{}")
                        ),
                    },
                }
                for tc in tool_calls
            ]
        return base
    return {"role": getattr(msg, "role", "user"), "content": getattr(msg, "content", "")}


def _parse_tool_calls(raw_message: Any) -> List[Dict[str, Any]]:
    """规整 GLM 返回的 tool_calls，避免 None 触发验证错误。"""
    calls = None
    if isinstance(raw_message, dict):
        calls = raw_message.get("tool_calls")
    else:
        calls = getattr(raw_message, "tool_calls", None)

    if not calls:
        return []

    normalized: List[Dict[str, Any]] = []
    for tc in calls:
        if isinstance(tc, dict):
            func = tc.get("function") or {}
            func_name = func.get("name")
            func_args = func.get("arguments")
            tc_id = tc.get("id") or tc.get("tool_call_id")
            tc_type = tc.get("type") or "function"
        else:
            func_obj = getattr(tc, "function", None)
            func_name = getattr(func_obj, "name", None)
            func_args = getattr(func_obj, "arguments", None)
            tc_id = getattr(tc, "id", None) or getattr(tc, "tool_call_id", None)
            tc_type = getattr(tc, "type", None) or "function"

        # LangChain 的 AIMessage 校验要求 args 为 dict
        args_dict: Dict[str, Any] = {}
        if isinstance(func_args, dict):
            args_dict = func_args
        elif isinstance(func_args, str):
            try:
                parsed = json.loads(func_args)
                if isinstance(parsed, dict):
                    args_dict = parsed
            except Exception:
                args_dict = {}

        normalized.append(
            {
                "id": tc_id,
                "type": tc_type,
                "name": func_name,
                "args": args_dict,
            }
        )
    return normalized


def _ensure_json_str(value: Any) -> str:
    """将工具参数统一转为 JSON 字符串，符合 GLM 接口要求。"""
    if value is None:
        return "{}"
    if isinstance(value, str):
        return value
    try:
        return json.dumps(value, ensure_ascii=False)
    except TypeError:
        return str(value)


def _extract_choice_message(resp: Any) -> Any:
    """兼容 object/dict 两种返回形态，取第一个 choice.message。"""
    if isinstance(resp, dict):
        return (
            resp.get("choices", [{}])[0].get("message", {})
            if resp.get("choices")
            else {}
        )
    if hasattr(resp, "choices"):
        first = resp.choices[0]
        if isinstance(first, dict):
            return first.get("message", {})
        return getattr(first, "message", {})
    return {}


class GlmNativeChatModel(BaseChatModel):
    """GLM 官方 zai-sdk 适配器，支持 function calling 与 JSON 模式。"""

    model_config = ConfigDict(arbitrary_types_allowed=True)

    model_name: str
    api_key: str
    base_url: str
    temperature: float = 0.0
    max_tokens: Optional[int] = None
    json_mode: bool = False
    bound_tools: List[BaseTool] = []
    _client: Optional[ZhipuAiClient] = None

    def __init__(
        self,
        api_key: str,
        base_url: str,
        model: str,
        temperature: float = 0.0,
        max_tokens: Optional[int] = None,
        json_mode: bool = False,
        bound_tools: Optional[List[BaseTool]] = None,
    ):
        super().__init__(
            model_name=model,
            api_key=api_key,
            base_url=base_url,
            temperature=temperature,
            max_tokens=max_tokens,
            json_mode=json_mode,
            bound_tools=bound_tools or [],
        )
        self.model_name = model
        self.api_key = api_key
        self.base_url = base_url
        self.temperature = temperature
        self.max_tokens = max_tokens
        self.json_mode = json_mode
        self.bound_tools = bound_tools or []
        self._client = ZhipuAiClient(api_key=api_key, base_url=base_url)

    @property
    def _llm_type(self) -> str:
        return "glm_native"

    def bind_tools(self, tools: List[BaseTool]) -> "GlmNativeChatModel":
        return GlmNativeChatModel(
            api_key=self.api_key,
            base_url=self.base_url,
            model=self.model_name,
            temperature=self.temperature,
            max_tokens=self.max_tokens,
            json_mode=self.json_mode,
            bound_tools=tools,
        )

    def with_structured_output(
        self, schema: Type[T] | Type[LCBaseModel], **kwargs
    ) -> RunnableSequence:
        """返回一个可直接产出 Pydantic 对象的链路。"""
        parser = PydanticOutputParser(pydantic_object=schema)
        model = GlmNativeChatModel(
            api_key=self.api_key,
            base_url=self.base_url,
            model=self.model_name,
            temperature=self.temperature,
            max_tokens=self.max_tokens,
            json_mode=True,
            bound_tools=None,
        )
        return model | parser

    async def _agenerate(
        self,
        messages: List[BaseMessage],
        stop: Optional[List[str]] = None,
        run_manager: Optional[AsyncCallbackManagerForLLMRun] = None,
        **kwargs: Any,
    ) -> ChatResult:
        payload_messages = [_lc_message_to_glm(m) for m in messages]
        tools_payload = (
            [_lc_tool_to_glm(t) for t in (self.bound_tools or [])]
            if not self.json_mode
            else None
        )
        response_format = {"type": "json_object"} if self.json_mode else None
        thinking = {"type": "disabled"}  # 关闭 GLM 思考模式，避免思考流输出

        loop = asyncio.get_running_loop()
        resp = await loop.run_in_executor(
            None,
            lambda: self._client.chat.completions.create(
                model=self.model_name,
                messages=payload_messages,
                tools=tools_payload,
                response_format=response_format,
                thinking=thinking,
                temperature=self.temperature,
                max_tokens=self.max_tokens,
            ),
        )

        choice_msg = _extract_choice_message(resp)
        content = (
            choice_msg.get("content")
            if isinstance(choice_msg, dict)
            else getattr(choice_msg, "content", None)
        ) or ""
        tool_calls = _parse_tool_calls(choice_msg)

        # 记录原始输出（用于调试 JSON 结构化输出问题）
        if self.json_mode:
            import logging
            logging.getLogger(__name__).debug(f"[GLM JSON Mode] 原始输出: {content[:1000]}")

        ai_msg = (
            AIMessage(content=content, tool_calls=tool_calls)
            if tool_calls
            else AIMessage(content=content)
        )
        return ChatResult(generations=[ChatGeneration(message=ai_msg)])

    def _generate(
        self,
        messages: List[BaseMessage],
        stop: Optional[List[str]] = None,
        run_manager: Optional[CallbackManagerForLLMRun] = None,
        **kwargs: Any,
    ) -> ChatResult:
        loop = asyncio.new_event_loop()
        try:
            return loop.run_until_complete(
                self._agenerate(messages, stop=stop, run_manager=run_manager, **kwargs)
            )
        finally:
            loop.close()


# 解决延期注解导致的 Pydantic ForwardRef 问题
GlmNativeChatModel.model_rebuild()
