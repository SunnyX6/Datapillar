"""
Agent 执行上下文

AgentContext 是框架提供给业务 Agent 的接口：
- 只读信息：query, session_id
- 工作方法：build_messages, invoke_tools, get_output, clarify

设计原则：
- 业务侧只能使用公开的方法和属性
- 框架内部对象私有化，防止业务侧越权
- 记忆、LLM、工具等由框架自动管理
- 委派由框架内部处理，业务侧无需关心
"""

from __future__ import annotations

import logging
from dataclasses import dataclass, field
from typing import TYPE_CHECKING, Any

from langchain_core.messages import BaseMessage, HumanMessage, SystemMessage
from langgraph.prebuilt import ToolNode
from langgraph.types import Command

from src.modules.oneagentic.core.types import Clarification

if TYPE_CHECKING:
    from src.modules.oneagentic.core.agent import AgentSpec
    from src.modules.oneagentic.memory.session_memory import SessionMemory

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
    - session_id: 会话 ID
    - query: 用户输入

    公开方法：
    - build_messages(system_prompt): 构建 LLM 消息
    - invoke_tools(messages): 执行工具调用循环
    - get_output(messages): 获取结构化输出
    - clarify(message, questions): 请求用户澄清

    使用示例：
    ```python
    async def run(self, ctx: AgentContext) -> AnalysisOutput | Clarification:
        # 1. 构建消息
        messages = ctx.build_messages(self.SYSTEM_PROMPT)

        # 2. 工具调用循环（委派由框架自动处理）
        messages = await ctx.invoke_tools(messages)

        # 3. 获取结构化输出
        output = await ctx.get_output(messages)

        # 4. 业务判断
        if output.confidence < 0.7:
            return ctx.clarify("需求不够明确", output.ambiguities)

        return output
    ```
    """

    # === 公开属性（只读）===
    session_id: str
    """会话 ID"""

    query: str
    """用户输入"""

    # === 框架内部（私有化）===
    _spec: AgentSpec = field(default=None, repr=False)
    """Agent 规格（框架内部）"""

    _memory: SessionMemory | None = field(default=None, repr=False)
    """会话记忆（框架自动管理）"""

    _knowledge_prompt: str = field(default="", repr=False)
    """知识上下文（框架自动注入）"""

    _llm: Any = field(default=None, repr=False)
    """LLM 实例（框架内部）"""

    _tools: list[Any] = field(default_factory=list, repr=False)
    """工具列表（框架内部）"""

    _state: dict = field(default_factory=dict, repr=False)
    """共享状态（框架内部）"""

    _delegation_command: Command | None = field(default=None, repr=False)
    """委派命令（框架内部）"""

    _messages: list[BaseMessage] = field(default_factory=list, repr=False)
    """消息历史（框架内部）"""

    # === 公开方法 ===

    def build_messages(self, system_prompt: str) -> Any:
        """
        构建 LLM 消息

        自动注入：
        - 系统提示词
        - 记忆上下文（对话历史）
        - 知识上下文
        - 用户查询

        参数：
        - system_prompt: Agent 的系统提示词

        返回：
        - 消息对象（业务侧不需要了解具体类型，只需传递）
        """
        messages: list[BaseMessage] = [SystemMessage(content=system_prompt)]

        # 注入上下文
        context_parts = []

        # 会话记忆（框架自动管理）
        if self._memory:
            memory_prompt = self._memory.to_prompt()
            if memory_prompt:
                context_parts.append(memory_prompt)

        # 知识上下文（框架自动注入）
        if self._knowledge_prompt:
            context_parts.append(self._knowledge_prompt)

        if context_parts:
            context_content = "\n\n".join(context_parts)
            messages.append(SystemMessage(content=context_content))

        # 用户查询
        if self.query:
            messages.append(HumanMessage(content=self.query))

        self._messages = messages
        return messages

    async def invoke_tools(self, messages: Any) -> Any:
        """
        工具调用循环

        执行 LLM 调用和工具调用的循环，直到 LLM 不再调用工具。
        如果调用了委派工具，会抛出 DelegationSignal 由框架处理。

        参数：
        - messages: build_messages() 返回的消息对象

        返回：
        - 更新后的消息对象

        异常：
        - DelegationSignal: 当调用委派工具时（框架内部处理）
        """
        if not self._tools:
            # 没有工具，直接调用 LLM
            response = await self._llm.ainvoke(messages)
            messages.append(response)
            self._messages = messages
            return messages

        # 创建 ToolNode
        tool_node = ToolNode(self._tools)
        llm_with_tools = self._llm.bind_tools(self._tools)

        # 准备状态
        current_state = self._state.copy()

        for iteration in range(1, self._spec.max_iterations + 1):
            # LLM 调用
            response = await llm_with_tools.ainvoke(messages)

            if not response.tool_calls:
                # 没有工具调用，结束
                messages.append(response)
                break

            messages.append(response)

            # 日志
            for tc in response.tool_calls:
                logger.info(f"🔧 [{self._spec.name}] 调用工具: {tc['name']}")

            # 执行工具
            current_state["messages"] = messages
            result = await tool_node.ainvoke(current_state)

            # 检查是否是委派命令
            if isinstance(result, list) and result and isinstance(result[0], Command):
                self._delegation_command = result[0]
                logger.info(f"🔄 [{self._spec.name}] 委派给 {self._delegation_command.goto}")
                self._messages = messages
                # 抛出委派信号，由框架处理
                raise DelegationSignal(self._delegation_command)

            # 普通工具结果
            if isinstance(result, dict):
                new_messages = result.get("messages", [])
            else:
                new_messages = result if isinstance(result, list) else []

            messages.extend(new_messages)

        self._messages = messages
        return messages

    async def get_output(self, messages: Any) -> Any:
        """
        获取结构化输出

        根据 Agent 声明的 deliverable_schema 生成结构化输出。
        使用项目统一的 parse_structured_output 机制解析。

        参数：
        - messages: invoke_tools() 返回的消息对象

        返回：
        - deliverable_schema 实例
        """
        from src.infrastructure.llm.structured_output import parse_structured_output

        if not self._spec.deliverable_schema:
            # 没有 schema，返回最后一条消息内容
            if messages:
                last_msg = messages[-1]
                if hasattr(last_msg, "content"):
                    return last_msg.content
            return None

        # 使用 with_structured_output（json_mode 方法）
        llm_structured = self._llm.with_structured_output(
            self._spec.deliverable_schema,
            method="json_mode",
            include_raw=True,
        )
        result = await llm_structured.ainvoke(messages)

        # 1. 直接是目标类型
        if isinstance(result, self._spec.deliverable_schema):
            return result

        # 2. dict 格式（include_raw=True 的返回）
        if isinstance(result, dict):
            # 优先使用已解析的结果
            parsed = result.get("parsed")
            if isinstance(parsed, self._spec.deliverable_schema):
                return parsed

            # 从 raw 提取文本，用 parse_structured_output 解析
            raw = result.get("raw")
            if raw:
                content = getattr(raw, "content", None)
                if content:
                    return parse_structured_output(content, self._spec.deliverable_schema)

                # 尝试从 tool_calls 提取
                tool_calls = getattr(raw, "tool_calls", None)
                if tool_calls and isinstance(tool_calls, list) and tool_calls:

                    args = (
                        tool_calls[0].get("args")
                        if isinstance(tool_calls[0], dict)
                        else getattr(tool_calls[0], "args", None)
                    )
                    if isinstance(args, dict):
                        return self._spec.deliverable_schema.model_validate(args)
                    if isinstance(args, str):
                        return parse_structured_output(args, self._spec.deliverable_schema)

        raise ValueError(f"无法获取结构化输出: {type(result)}")

    def clarify(
        self, message: str, questions: list[str], options: list[dict] | None = None
    ) -> Clarification:
        """
        请求用户澄清

        当业务判断需要更多信息时使用。
        框架会暂停流程，等待用户回复。

        参数：
        - message: 提示信息
        - questions: 需要回答的问题列表
        - options: 可选项（可选）

        返回：
        - Clarification 对象
        """
        return Clarification(
            message=message,
            questions=questions,
            options=options or [],
        )
