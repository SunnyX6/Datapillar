"""
Agent 执行上下文

AgentContext 是框架提供给业务 Agent 的接口：
- 只读信息：namespace, query, session_id
- 工作方法：build_messages, invoke_tools, get_structured_output, interrupt
- 依赖获取：get_deliverable

设计原则：
- 业务侧只能使用公开的方法和属性
- 框架内部对象私有化，防止业务侧越权
- 记忆、LLM、工具等由框架自动管理
- 委派由框架内部处理，业务侧无需关心
- Store 操作封装在框架内部，业务侧通过简洁 API 访问
"""

from __future__ import annotations

import asyncio
import logging
from dataclasses import dataclass, field
from typing import TYPE_CHECKING, Any

from langchain_core.messages import AIMessage, BaseMessage
from langgraph.prebuilt import ToolNode
from langgraph.types import Command, interrupt

from datapillar_oneagentic.context import ContextBuilder
from datapillar_oneagentic.state import StateBuilder
from datapillar_oneagentic.events import (
    EventBus,
    LLMThinkingEvent,
    ToolCalledEvent,
    ToolCompletedEvent,
    ToolFailedEvent,
)
from datapillar_oneagentic.providers.llm.llm import extract_thinking
from datapillar_oneagentic.core.types import SessionKey
from datapillar_oneagentic.utils.structured_output import parse_structured_output

if TYPE_CHECKING:
    from datapillar_oneagentic.core.agent import AgentSpec
    from datapillar_oneagentic.core.config import AgentConfig

logger = logging.getLogger(__name__)

class DelegationSignal(Exception):
    """
    委派信号（框架内部）

    当 Agent 调用委派工具时抛出，由 Executor 捕获处理。
    业务侧不需要知道这个异常的存在。
    """

    def __init__(self, command: Command):
        self.command = command
        super().__init__(f"Delegation to {command.goto}")


@dataclass
class AgentContext:
    """
    Agent 执行上下文

    业务 Agent 通过此上下文与框架交互。

    公开属性（只读）：
    - namespace: 命名空间
    - session_id: 会话 ID
    - query: 用户输入

    公开方法：
    - build_messages(system_prompt, human_message=None): 构建 LLM 消息
    - invoke_tools(messages): 执行工具调用循环
    - get_structured_output(messages): 获取结构化输出
    - interrupt(payload): 中断等待用户回复
    - get_deliverable(agent_id): 获取其他 Agent 的产出

    使用示例：
    ```python
    async def run(self, ctx: AgentContext) -> AnalysisOutput:
        # 获取上游 Agent 的产出（通过 agent_id）
        upstream_data = await ctx.get_deliverable(agent_id="data_extractor")

        # 1. 构建消息
        messages = ctx.build_messages(self.SYSTEM_PROMPT)

        # 2. 工具调用循环（委派由框架自动处理）
        messages = await ctx.invoke_tools(messages)

        # 3. 获取结构化输出
        output = await ctx.get_structured_output(messages)

        # 4. 业务判断
        if output.confidence < 0.7:
            user_reply = ctx.interrupt("需求不够明确")
            # 可根据 user_reply 补充上下文后继续

        return output
    ```
    """

    # === 公开属性（只读）===
    namespace: str
    """命名空间"""

    session_id: str
    """会话 ID"""

    query: str
    """用户输入"""

    # === 框架内部（私有化）===
    _spec: AgentSpec = field(default=None, repr=False)
    """Agent 规格（框架内部）"""

    _llm: Any = field(default=None, repr=False)
    """LLM 实例（框架内部）"""

    _tools: list[Any] = field(default_factory=list, repr=False)
    """工具列表（框架内部）"""

    _state: dict = field(default_factory=dict, repr=False)
    """共享状态（框架内部）"""

    _delegation_command: Command | None = field(default=None, repr=False)
    """委派命令（框架内部）"""

    _messages: list[BaseMessage] = field(default_factory=list, repr=False)
    _agent_config: AgentConfig | None = field(default=None, repr=False)
    _event_bus: EventBus | None = field(default=None, repr=False)
    """消息历史（框架内部）"""

    # === 公开方法 ===

    def build_messages(self, system_prompt: str, human_message: str | None = None) -> Any:
        """
        构建 LLM 消息

        自动注入：
        - 系统提示词
        - Checkpoint 记忆（messages）
        - 知识上下文
        - 经验上下文
        - 用户查询

        参数：
        - system_prompt: Agent 的系统提示词
        - human_message: 追加的人类消息（可选，仅用于当前调用）

        返回：
        - 消息对象（业务侧不需要了解具体类型，只需传递）
        """
        ctx_builder = ContextBuilder.from_state(self._state)
        messages = ctx_builder.compose_llm_messages(
            system_prompt=system_prompt,
            query=self.query,
            human_message=human_message,
        )
        self._messages = messages
        return messages

    def _has_tool(self, tool_name: str) -> bool:
        for tool in self._tools or []:
            name = getattr(tool, "name", None)
            if not name and callable(tool):
                name = getattr(tool, "__name__", "")
            if name == tool_name:
                return True
        return False

    async def invoke_tools(self, messages: Any) -> Any:
        """
        工具调用循环

        执行 LLM 调用和工具调用的循环，直到 LLM 不再调用工具。
        如果调用了委派工具，会抛出 DelegationSignal 由框架处理。

        关键说明：
        - 有工具时仅使用 bind_tools 进行工具调用循环，不强制结构化输出
        - 无工具时使用结构化输出，避免额外调用 get_structured_output
        - 工具路径最终仍需调用 get_structured_output 解析结果

        参数：
        - messages: build_messages() 返回的消息对象

        返回：
        - 更新后的消息对象

        异常：
        - DelegationSignal: 当调用委派工具时（框架内部处理）
        """
        schema = self._spec.deliverable_schema

        if not self._tools:
            # 没有工具，直接调用 LLM（带结构化输出）
            llm_structured = self._llm.with_structured_output(schema, method="function_calling")
            response = await llm_structured.ainvoke(messages)
            # 将 Pydantic 对象序列化为 JSON 字符串，包装成 AIMessage
            if hasattr(response, "model_dump_json"):
                content = response.model_dump_json()
            else:
                import json
                content = json.dumps(response) if isinstance(response, dict) else str(response)
            messages.append(AIMessage(content=content))
            self._messages = messages
            return messages

        # 创建 ToolNode
        tool_node = ToolNode(self._tools)

        # bind_tools 绑定工具
        llm_with_tools = self._llm.bind_tools(self._tools)

        max_steps = self._spec.get_max_steps(self._agent_config)
        key = SessionKey(namespace=self.namespace, session_id=self.session_id)
        for _iteration in range(1, max_steps + 1):
            # LLM 调用
            response = await llm_with_tools.ainvoke(messages)

            # 提取并发送思考内容（如果有）
            thinking_content = self._extract_thinking(response)
            if thinking_content:
                await self._emit_event(
                    LLMThinkingEvent(
                        agent_id=self._spec.id,
                        key=key,
                        thinking_content=thinking_content,
                    )
                )

            if not response.tool_calls:
                # 没有工具调用，结束
                messages.append(response)
                break

            messages.append(response)

            # 记录工具调用信息（用于后续发送完成/失败事件）
            tool_calls_info = []
            for tc in response.tool_calls:
                tool_name = tc.get("name") if isinstance(tc, dict) else getattr(tc, "name", None)
                tool_args = tc.get("args", {}) if isinstance(tc, dict) else getattr(tc, "args", {})
                tool_call_id = tc.get("id", "") if isinstance(tc, dict) else getattr(tc, "id", "")

                if not tool_name:
                    continue

                logger.info(f"🔧 [{self._spec.name}] 调用工具: {tool_name}")
                tool_calls_info.append({
                    "name": tool_name,
                    "args": tool_args if isinstance(tool_args, dict) else {},
                    "id": tool_call_id or "",
                })
                await self._emit_event(
                    ToolCalledEvent(
                        agent_id=self._spec.id,
                        key=key,
                        tool_name=tool_name,
                        tool_call_id=tool_call_id or "",
                        tool_input=tool_args if isinstance(tool_args, dict) else {},
                    )
                )

            # 执行工具（带超时控制）
            import time
            tool_start_time = time.time()
            current_state = dict(self._state)
            current_state["messages"] = list(messages)
            tool_error = None
            tool_timeout = self._spec.get_tool_timeout_seconds(self._agent_config)
            try:
                result = await asyncio.wait_for(
                    tool_node.ainvoke(current_state),
                    timeout=tool_timeout,
                )
            except asyncio.TimeoutError:
                tool_error = f"工具调用超时（{tool_timeout}秒）"
                logger.error(f"⏰ [{self._spec.name}] {tool_error}")
                for tc_info in tool_calls_info:
                    await self._emit_event(
                        ToolFailedEvent(
                            agent_id=self._spec.id,
                            key=key,
                            tool_name=tc_info["name"],
                            tool_call_id=tc_info["id"],
                            error=tool_error,
                        )
                    )
                raise TimeoutError(tool_error)
            except Exception as e:
                tool_error = str(e)
                # 发送所有工具的失败事件
                for tc_info in tool_calls_info:
                    await self._emit_event(
                        ToolFailedEvent(
                            agent_id=self._spec.id,
                            key=key,
                            tool_name=tc_info["name"],
                            tool_call_id=tc_info["id"],
                            error=tool_error,
                        )
                    )
                raise
            tool_duration_ms = (time.time() - tool_start_time) * 1000

            # 解析工具结果：分离 Command 和普通消息
            delegation_command = None
            new_messages = []

            if isinstance(result, dict):
                new_messages = result.get("messages", [])
            elif isinstance(result, list):
                for item in result:
                    if isinstance(item, Command):
                        # 只取第一个 Command（多个委派不合理）
                        if delegation_command is None:
                            delegation_command = item
                        else:
                            logger.warning(f"🔄 [{self._spec.name}] 忽略多余的委派命令")
                    else:
                        new_messages.append(item)

            # 处理委派命令
            if delegation_command is not None:
                self._delegation_command = delegation_command
                logger.info(f"🔄 [{self._spec.name}] 委派给 {self._delegation_command.goto}")
                self._messages = messages
                # 抛出委派信号，由框架处理
                raise DelegationSignal(self._delegation_command)

            # 发送工具完成事件（从 ToolMessage 中提取结果）
            tool_outputs = {}
            for msg in new_messages:
                if hasattr(msg, "tool_call_id") and hasattr(msg, "content"):
                    tool_outputs[msg.tool_call_id] = msg.content

            for tc_info in tool_calls_info:
                tool_output = tool_outputs.get(tc_info["id"], "")
                await self._emit_event(
                    ToolCompletedEvent(
                        agent_id=self._spec.id,
                        key=key,
                        tool_name=tc_info["name"],
                        tool_call_id=tc_info["id"],
                        tool_output=tool_output,
                        duration_ms=tool_duration_ms / len(tool_calls_info) if tool_calls_info else 0,
                    )
                )

            messages.extend(new_messages)

        self._messages = messages
        return messages

    async def get_structured_output(self, messages: Any) -> Any:
        """
        获取结构化输出

        参数：
        - messages: invoke_tools() 返回的消息对象

        返回：
        - deliverable_schema 实例
        """
        schema = self._spec.deliverable_schema
        # 直接调用 LLM 生成结构化输出，避免共享上下文中的非输出消息干扰解析
        llm_structured = self._llm.with_structured_output(
            schema,
            method="function_calling",
            include_raw=True,
        )
        result = await llm_structured.ainvoke(messages)
        return parse_structured_output(result, schema, strict=False)

    async def _emit_event(self, event: Any) -> None:
        """安全发送事件（允许 event_bus 为空）"""
        if self._event_bus is None:
            return
        await self._event_bus.emit(self, event)

    def _extract_thinking(self, response: AIMessage) -> str | None:
        """
        从 LLM 响应中提取思考内容

        支持多种模型的思考格式：
        - GLM: additional_kwargs.reasoning_content
        - Claude: content 中的 thinking blocks
        - DeepSeek: additional_kwargs.reasoning_content
        """
        if not isinstance(response, AIMessage):
            return None
        return extract_thinking(response)

    def interrupt(self, payload: Any | None = None) -> Any:
        """
        中断并等待用户回复

        payload 是可序列化的提示信息（可选）。
        恢复后返回用户输入，并自动写入上下文消息。
        """
        resume_value = interrupt(payload)
        self._append_user_reply(resume_value)
        return resume_value

    def _append_user_reply(self, resume_value: Any) -> None:
        """将用户回复追加为 HumanMessage（统一结构）"""
        sb = StateBuilder(self._state)
        sb.append_user_reply_inplace(resume_value)
        # interrupt 恢复后，后续调用应基于最新 checkpoint 记忆
        self._messages = sb.memory.snapshot()

    async def get_deliverable(self, agent_id: str) -> Any | None:
        """
        获取其他 Agent 的产出

        通过 agent_id 获取上游 Agent 产出的交付物。
        常用于有依赖关系的 Agent 之间传递数据。

        参数：
        - agent_id: 上游 Agent 的 ID

        返回：
        - 交付物内容（dict），如果不存在则返回 None

        使用示例：
        ```python
        async def run(self, ctx: AgentContext) -> ReportOutput:
            # 获取数据分析 Agent 的产出
            analysis = await ctx.get_deliverable(agent_id="analyst")
            if not analysis:
                user_reply = ctx.interrupt("缺少分析数据")
                # 可根据 user_reply 获取数据后继续

            # 使用分析结果生成报告
            ...
        ```
        """
        from langgraph.config import get_store

        store = get_store()
        if not store:
            logger.warning("Store 未配置，无法获取 deliverable")
            return None

        store_namespace = ("deliverables", self.namespace, self.session_id)

        try:
            item = await store.aget(store_namespace, agent_id)
            if item:
                return item.value
            return None
        except Exception as e:
            logger.error(f"获取 deliverable 失败: {e}")
            return None
