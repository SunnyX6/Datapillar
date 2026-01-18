"""
ContextBuilder - 统一的上下文管理器

负责：
- messages 管理（添加、压缩）
- Timeline 记录
- 为 nodes.py 提供统一的 API

设计原则：
- 所有上下文操作都通过 ContextBuilder
- nodes.py 不直接操作 messages 或 Timeline
- 压缩由 LLM 上下文超限触发
"""

from __future__ import annotations

import json
import logging

from langchain_core.messages import AIMessage, BaseMessage, HumanMessage, SystemMessage
from pydantic import BaseModel

from datapillar_oneagentic.todo.session_todo import SessionTodoList
from datapillar_oneagentic.utils.structured_output import build_output_instructions
from datapillar_oneagentic.todo.tool import TODO_PLAN_TOOL_NAME, TODO_TOOL_NAME

from datapillar_oneagentic.context.compaction import CompactResult, Compactor
from datapillar_oneagentic.context.timeline import Timeline
from datapillar_oneagentic.knowledge.config import KnowledgeInjectConfig
from datapillar_oneagentic.knowledge.models import KnowledgeChunk

logger = logging.getLogger(__name__)

_KNOWLEDGE_TOOL_PROMPT = (
    "## 知识检索\n"
    "- 当任务需要外部知识时，必须调用 knowledge_retrieve(query) 获取检索结果。\n"
    "- 禁止编造外部知识或假设不存在的资料。"
)


class ContextBuilder:
    """
    统一的上下文管理器

    管理：
    - messages: LangGraph 的消息列表
    - timeline: 执行时间线
    - 压缩: 由 LLM 上下文超限触发
    """

    def __init__(
        self,
        *,
        session_id: str,
        messages: list[BaseMessage] | None = None,
        timeline: Timeline | None = None,
        compactor: Compactor,
    ):
        """
        初始化

        Args:
            session_id: 会话 ID
            messages: 初始消息列表
            timeline: 初始时间线
        """
        self.session_id = session_id
        self._messages = list(messages) if messages else []
        self._timeline = timeline or Timeline()
        self._compactor = compactor

    @classmethod
    def from_state(cls, state: dict, *, compactor: Compactor) -> ContextBuilder:
        """从 state 创建 ContextBuilder"""
        session_id = state.get("session_id", "")
        messages = list(state.get("messages", []))

        timeline_data = state.get("timeline")
        timeline = Timeline.from_dict(timeline_data) if timeline_data else Timeline()

        return cls(
            session_id=session_id,
            messages=messages,
            timeline=timeline,
            compactor=compactor,
        )

    # ========== LLM Messages 构建 ==========

    @staticmethod
    def build_llm_messages(
        *,
        system_prompt: str,
        query: str | None,
        state: dict,
        include_knowledge_tool_prompt: bool = False,
        output_schema: type[BaseModel] | None = None,
    ) -> list[BaseMessage]:
        messages: list[BaseMessage] = [SystemMessage(content=system_prompt)]

        if output_schema is not None:
            messages.append(SystemMessage(content=build_output_instructions(output_schema)))

        context_parts = []

        upstream_messages = state.get("messages", [])
        if upstream_messages:
            for msg in upstream_messages:
                if isinstance(msg, (HumanMessage, AIMessage)):
                    messages.append(msg)

        knowledge_context = state.get("knowledge_context")
        if knowledge_context:
            context_parts.append(knowledge_context)

        experience_context = state.get("experience_context")
        if experience_context:
            context_parts.append(experience_context)

        if context_parts:
            context_content = "\n\n".join(context_parts)
            messages.append(SystemMessage(content=context_content))

        if include_knowledge_tool_prompt:
            messages.append(SystemMessage(content=_KNOWLEDGE_TOOL_PROMPT))

        assigned_task = state.get("assigned_task")
        if assigned_task:
            messages.append(SystemMessage(content=f"## 下发任务\n{assigned_task}"))

        todo_prompt = None
        todo_context = state.get("todo_context")
        if isinstance(todo_context, str) and todo_context.strip():
            todo_prompt = todo_context.strip()
        else:
            todo_data = state.get("todo")
            if todo_data:
                try:
                    todo = SessionTodoList.model_validate(todo_data)
                except Exception as exc:
                    logger.warning(f"Todo 解析失败: {exc}")
                else:
                    todo_prompt = todo.to_prompt()

        if todo_prompt:
            todo_instruction = (
                "## Todo 管理\n"
                f"- 当任务复杂或需求变化导致需要重新拆解时，必须调用 {TODO_PLAN_TOOL_NAME} 工具调整 Todo。\n"
                f"- 若你推进了任一 Todo，请调用 {TODO_TOOL_NAME} 工具上报。\n"
                "- 最终输出必须严格遵循 deliverable schema，"
                "不要在最终输出中包含 Todo 信息。"
            )
            messages.append(SystemMessage(content=f"{todo_prompt}\n\n{todo_instruction}"))
        else:
            todo_hint = (
                "## Todo 规划\n"
                f"- 当任务复杂或需要分阶段推进时，必须调用 {TODO_PLAN_TOOL_NAME} 工具生成 Todo。\n"
                "- 用户明确要求 Todo 或数量时，必须按要求生成。\n"
                "- 最终输出必须严格遵循 deliverable schema。"
            )
            messages.append(SystemMessage(content=todo_hint))

        if query and not upstream_messages:
            messages.append(HumanMessage(content=query))

        return messages

    # ========== 知识上下文构建 ==========

    @staticmethod
    def build_knowledge_context(
        *,
        chunks: list[KnowledgeChunk],
        inject: KnowledgeInjectConfig,
    ) -> str:
        if not chunks:
            return ""

        max_chunks = inject.max_chunks
        max_chars = inject.max_tokens * 2
        format_value = (inject.format or "markdown").lower()
        if format_value not in {"markdown", "json"}:
            raise ValueError(f"不支持的知识注入格式: {format_value}")

        total_chars = 0
        selected: list[KnowledgeChunk] = []
        for chunk in chunks:
            content = chunk.content.strip()
            if not content:
                continue
            if total_chars + len(content) > max_chars:
                break
            selected.append(chunk)
            total_chars += len(content)
            if len(selected) >= max_chunks:
                break

        if not selected:
            return ""

        if format_value == "json":
            payload = {
                "title": "知识上下文",
                "chunks": [
                    {
                        "source_id": chunk.source_id,
                        "doc_id": chunk.doc_id,
                        "doc_title": chunk.doc_title or chunk.doc_id,
                        "chunk_id": chunk.chunk_id,
                        "content": chunk.content.strip(),
                    }
                    for chunk in selected
                ],
            }
            return json.dumps(payload, ensure_ascii=False)

        lines = ["## 知识上下文", ""]
        for idx, chunk in enumerate(selected, 1):
            title = chunk.doc_title or chunk.doc_id
            lines.append(f"### 片段 {idx}")
            lines.append(f"- 来源: {chunk.source_id} / {title}")
            lines.append(chunk.content.strip())
            lines.append("")

        return "\n".join(lines).strip()

    # ========== Messages 操作 ==========

    def add_messages(self, messages: list[BaseMessage]) -> None:
        """添加消息"""
        self._messages.extend(messages)

    def get_messages(self) -> list[BaseMessage]:
        """获取所有消息"""
        return self._messages

    def set_messages(self, messages: list[BaseMessage]) -> None:
        """设置消息列表（压缩后使用）"""
        self._messages = list(messages)

    # ========== Timeline 操作 ==========

    def record_event(self, event_data: dict) -> None:
        """记录事件到 Timeline"""
        self._timeline.add_entry_from_dict(event_data)

    def record_events(self, events: list[dict]) -> None:
        """批量记录事件"""
        for event_data in events:
            self._timeline.add_entry_from_dict(event_data)

    # ========== 压缩 ==========

    async def compact(self) -> CompactResult:
        """
        执行压缩（由 LLM 上下文超限触发时调用）

        Returns:
            CompactResult
        """
        try:
            compressed_messages, result = await self._compactor.compact(self._messages)
            if result.success and result.removed_count > 0:
                self._messages = compressed_messages
                logger.info(
                    f"📦 上下文压缩: {result.removed_count} 条消息 → 摘要，"
                    f"保留 {result.kept_count} 条"
                )
            return result
        except Exception as e:
            logger.warning(f"上下文压缩失败: {e}")
            return CompactResult.failed(str(e))

    # ========== 状态更新 ==========

    def to_state_update(self) -> dict:
        """
        生成 state 更新字典

        Returns:
            包含 messages 和 timeline 的更新字典
        """
        return {
            "messages": self._messages,
            "timeline": self._timeline.to_dict(),
        }

    def get_timeline_update(self) -> dict | None:
        """获取 Timeline 更新（如果有变化）"""
        if self._timeline.entries:
            return self._timeline.to_dict()
        return None
