"""
ContextBuilder - LLM 上下文构建器（V2）

职责：
- ContextCollector：从运行态收集 __context 分层块
- ContextComposer：纯函数渲染 messages（不读写 state）
- ContextBuilder：对外门面 + checkpoint/interrupt 工具

硬约束：
- 只有 __context 结尾的块允许注入
- 只在 Composer 内统一拼接顺序
"""

from __future__ import annotations

import json
import logging
from enum import Enum
from typing import Any, Mapping, TYPE_CHECKING

from langchain_core.messages import BaseMessage, HumanMessage, SystemMessage

from datapillar_oneagentic.context.checkpoint import CheckpointManager
from datapillar_oneagentic.core.types import SessionKey
from datapillar_oneagentic.knowledge.config import KnowledgeInjectConfig
from datapillar_oneagentic.knowledge.models import KnowledgeChunk
from datapillar_oneagentic.todo.session_todo import SessionTodoList
from datapillar_oneagentic.todo.tool import TODO_PLAN_TOOL_NAME, TODO_TOOL_NAME
from datapillar_oneagentic.utils.prompt_format import format_markdown

if TYPE_CHECKING:
    from datapillar_oneagentic.core.agent import AgentSpec
    from datapillar_oneagentic.experience import ExperienceLearner, ExperienceRetriever
    from datapillar_oneagentic.knowledge import KnowledgeRetriever

logger = logging.getLogger(__name__)

FRAMEWORK_CONTEXT_KEY = "framework__context"
EXPERIENCE_CONTEXT_KEY = "experience__context"
KNOWLEDGE_CONTEXT_KEY = "knowledge__context"
TODO_CONTEXT_KEY = "todo__context"
COMPRESSION_CONTEXT_KEY = "compression__context"

CONTEXT_ORDER = (
    FRAMEWORK_CONTEXT_KEY,
    EXPERIENCE_CONTEXT_KEY,
    KNOWLEDGE_CONTEXT_KEY,
    TODO_CONTEXT_KEY,
    COMPRESSION_CONTEXT_KEY,
)

class ContextScenario(str, Enum):
    AGENT = "agent"
    MAPREDUCE_WORKER = "mapreduce_worker"
    MAPREDUCE_PLANNER = "mapreduce_planner"
    MAPREDUCE_REDUCER = "mapreduce_reducer"


class ContextComposer:
    """纯函数：将系统提示词 + __context 块 + checkpoint_messages 组装为 messages。"""

    @staticmethod
    def compose_agent_messages(
        *,
        system_prompt: str,
        query: str | None,
        contexts: Mapping[str, str],
        checkpoint_messages: list[BaseMessage],
        human_message: str | None = None,
    ) -> list[BaseMessage]:
        messages: list[BaseMessage] = [SystemMessage(content=system_prompt)]
        ContextComposer._append_context_messages(messages, contexts)
        if checkpoint_messages:
            messages.extend(checkpoint_messages)
        if human_message:
            messages.append(HumanMessage(content=human_message, additional_kwargs={"internal": True}))
        if query and not checkpoint_messages:
            messages.append(HumanMessage(content=query))
        return messages

    @staticmethod
    def compose_simple_messages(
        *,
        system_prompt: str,
        human_content: str,
        contexts: Mapping[str, str] | None = None,
    ) -> list[BaseMessage]:
        messages: list[BaseMessage] = [SystemMessage(content=system_prompt)]
        if contexts:
            ContextComposer._append_context_messages(messages, contexts)
        messages.append(HumanMessage(content=human_content))
        return messages

    @staticmethod
    def _append_context_messages(messages: list[BaseMessage], contexts: Mapping[str, str]) -> None:
        if not contexts:
            return
        ordered_keys = list(CONTEXT_ORDER)
        extra_keys = sorted(
            key for key in contexts.keys() if key not in CONTEXT_ORDER and key.endswith("__context")
        )
        for key in ordered_keys + extra_keys:
            value = contexts.get(key)
            if not isinstance(value, str):
                continue
            text = value.strip()
            if text:
                messages.append(SystemMessage(content=text))


class ContextCollector:
    """运行态收集 __context 分层块（不做渲染）。"""

    def __init__(
        self,
        *,
        knowledge_retriever: "KnowledgeRetriever | None" = None,
        experience_retriever: "ExperienceRetriever | None" = None,
        experience_learner: "ExperienceLearner | None" = None,
        share_agent_context: bool = True,
    ) -> None:
        self._knowledge_retriever = knowledge_retriever
        self._experience_retriever = experience_retriever
        self._experience_learner = experience_learner
        self._share_agent_context = share_agent_context

    async def collect(
        self,
        *,
        scenario: ContextScenario,
        state: Mapping[str, Any],
        query: str,
        session_id: str,
        spec: "AgentSpec | None" = None,
        has_knowledge_tool: bool = False,
        force_knowledge_system: bool = False,
    ) -> dict[str, str]:
        allowed = self._resolve_allowed_contexts(scenario=scenario)
        if not allowed:
            return {}

        contexts: dict[str, str] = {}

        todo_prompt = None
        if TODO_CONTEXT_KEY in allowed:
            todo_prompt = self._build_todo_prompt(
                todo_data=_as_dict(state.get("todo")),
                assigned_task=_as_str(state.get("assigned_task")),
            )
            if todo_prompt:
                contexts[TODO_CONTEXT_KEY] = todo_prompt

        if FRAMEWORK_CONTEXT_KEY in allowed:
            framework_context = self._build_framework_context(
                has_knowledge_tool=has_knowledge_tool,
                include_todo_instruction=TODO_CONTEXT_KEY in allowed,
                has_todo_prompt=bool(todo_prompt),
            )
            if framework_context:
                contexts[FRAMEWORK_CONTEXT_KEY] = framework_context

        if COMPRESSION_CONTEXT_KEY in allowed:
            compression_value = _as_str(state.get(COMPRESSION_CONTEXT_KEY))
            if compression_value:
                contexts[COMPRESSION_CONTEXT_KEY] = compression_value

        if EXPERIENCE_CONTEXT_KEY in allowed:
            experience_context = await self._build_experience_context(query=query)
            if experience_context:
                contexts[EXPERIENCE_CONTEXT_KEY] = experience_context

        if KNOWLEDGE_CONTEXT_KEY in allowed:
            knowledge_context = await self._build_knowledge_context(
                spec=spec,
                query=query,
                session_id=session_id,
                force_system=force_knowledge_system,
            )
            if knowledge_context:
                contexts[KNOWLEDGE_CONTEXT_KEY] = knowledge_context

        return contexts

    def _resolve_allowed_contexts(self, *, scenario: ContextScenario) -> set[str]:
        if scenario == ContextScenario.MAPREDUCE_WORKER:
            return {FRAMEWORK_CONTEXT_KEY, KNOWLEDGE_CONTEXT_KEY}
        if scenario in {ContextScenario.MAPREDUCE_PLANNER, ContextScenario.MAPREDUCE_REDUCER}:
            allowed = {FRAMEWORK_CONTEXT_KEY}
            if self._share_agent_context:
                allowed |= {EXPERIENCE_CONTEXT_KEY, KNOWLEDGE_CONTEXT_KEY}
            return allowed
        if not self._share_agent_context:
            return set()
        return {
            FRAMEWORK_CONTEXT_KEY,
            EXPERIENCE_CONTEXT_KEY,
            KNOWLEDGE_CONTEXT_KEY,
            TODO_CONTEXT_KEY,
            COMPRESSION_CONTEXT_KEY,
        }

    def _build_framework_context(
        self,
        *,
        has_knowledge_tool: bool,
        include_todo_instruction: bool,
        has_todo_prompt: bool,
    ) -> str:
        sections: list[tuple[str, list[str] | str]] = []
        if has_knowledge_tool:
            sections.append(
                (
                    "Knowledge Retrieval",
                    [
                        "When external knowledge is required, call `knowledge_retrieve(query)`.",
                        "Do not fabricate sources.",
                    ],
                )
            )
        if include_todo_instruction:
            todo_items = self._build_todo_instruction(has_todo_prompt=has_todo_prompt)
            if todo_items:
                sections.append(("Todo Management", todo_items))
        if not sections:
            return ""
        return format_markdown(title="Framework Context", sections=sections)

    def _build_todo_instruction(self, *, has_todo_prompt: bool) -> list[str]:
        if has_todo_prompt:
            return [
                f"When the task becomes complex or requirements change, call {TODO_PLAN_TOOL_NAME} to update Todo.",
                f"When any Todo item is progressed, call {TODO_TOOL_NAME} to report it.",
                "The final output must strictly follow the deliverable schema; do not include Todo details.",
            ]
        return [
            f"When the task is complex or multi-stage, call {TODO_PLAN_TOOL_NAME} to create Todo.",
            "If the user explicitly asks for a Todo or item count, follow that request.",
            "The final output must strictly follow the deliverable schema.",
        ]

    def _build_todo_prompt(self, *, todo_data: dict | None, assigned_task: str | None) -> str | None:
        if todo_data:
            try:
                todo = SessionTodoList.model_validate(todo_data)
            except Exception as exc:
                logger.warning("Todo 解析失败: %s", exc)
            else:
                prompt = todo.to_prompt()
                if prompt:
                    return prompt
        if assigned_task:
            return format_markdown(
                title="Todo Context",
                sections=[("Assigned Task", assigned_task)],
            )
        return None

    async def _build_experience_context(self, *, query: str) -> str | None:
        if not self._experience_retriever or not query:
            return None
        try:
            context = await self._experience_retriever.build_context(query)
        except Exception as exc:
            logger.warning("经验检索失败: %s", exc)
            return None
        if context:
            logger.info("📚 检索到相似经验，已注入上下文")
        return context or None

    async def _build_knowledge_context(
        self,
        *,
        spec: "AgentSpec | None",
        query: str,
        session_id: str,
        force_system: bool,
    ) -> str | None:
        if not spec or not spec.knowledge or self._knowledge_retriever is None:
            return None
        inject = self._knowledge_retriever.resolve_inject_config(spec.knowledge)
        inject_mode = (inject.mode or "tool").lower()
        if inject_mode == "tool" and not force_system:
            return None
        try:
            if inject_mode == "tool" and force_system:
                logger.info("MapReduce reducer 不支持 tool 注入，已强制使用 system: %s", spec.id)
            result = await self._knowledge_retriever.retrieve(
                query=query,
                knowledge=spec.knowledge,
            )
            if result.refs and self._experience_learner is not None:
                refs = [ref.to_dict() for ref in result.refs]
                self._experience_learner.record_knowledge(session_id, refs)
            knowledge_text = ContextBuilder.build_knowledge_context(
                chunks=[chunk for chunk, _ in result.hits],
                inject=inject,
            )
            return knowledge_text or None
        except ValueError:
            raise
        except Exception as exc:
            logger.warning("知识检索失败: %s", exc)
            return None


class ContextBuilder:
    """基于 state 构造单次 LLM 调用 messages。"""

    def __init__(self, *, state: dict[str, Any]):
        self._state = state

    @classmethod
    def from_state(cls, state: dict[str, Any]) -> "ContextBuilder":
        return cls(state=state)

    def compose_llm_messages(
        self,
        *,
        system_prompt: str,
        query: str | None,
        human_message: str | None = None,
    ) -> list[BaseMessage]:
        return self.build_llm_messages(
            system_prompt=system_prompt,
            query=query,
            state=self._state,
            human_message=human_message,
        )

    @staticmethod
    def build_llm_messages(
        *,
        system_prompt: str,
        query: str | None,
        state: dict[str, Any],
        human_message: str | None = None,
    ) -> list[BaseMessage]:
        checkpoint_messages = _as_messages(state.get("messages"))
        contexts = ContextBuilder.extract_context_blocks(state)
        return ContextComposer.compose_agent_messages(
            system_prompt=system_prompt,
            query=query,
            contexts=contexts,
            checkpoint_messages=checkpoint_messages,
            human_message=human_message,
        )

    @staticmethod
    def extract_context_blocks(state: Mapping[str, Any]) -> dict[str, str]:
        contexts: dict[str, str] = {}
        for key, value in state.items():
            if not key.endswith("__context"):
                continue
            if not isinstance(value, str):
                continue
            text = value.strip()
            if text:
                contexts[key] = text
        return contexts

    # ========== Checkpoint（编排器使用） ==========

    @staticmethod
    def create_checkpoint_manager(
        *,
        key: SessionKey,
        checkpointer: object | None,
    ) -> CheckpointManager:
        return CheckpointManager(key=key, checkpointer=checkpointer)

    @staticmethod
    async def delete_checkpoints(*, checkpoint_manager: CheckpointManager) -> bool:
        return await checkpoint_manager.delete()

    @staticmethod
    def extract_agent_id_from_interrupt(interrupt_obj: object) -> str | None:
        """从 interrupt 对象中解析节点名"""
        namespaces = getattr(interrupt_obj, "ns", None)
        if isinstance(namespaces, list) and namespaces:
            first = namespaces[0]
            if isinstance(first, str):
                return first.split(":", 1)[0]
        return None

    @classmethod
    def extract_interrupts(cls, snapshot: object | None) -> list[dict]:
        """解析 snapshot 中的中断信息"""
        interrupts_info: list[dict] = []
        if not snapshot:
            return interrupts_info
        tasks = getattr(snapshot, "tasks", None) or []
        for task in tasks:
            task_interrupts = getattr(task, "interrupts", None) or []
            if not task_interrupts:
                continue
            payloads = [getattr(item, "value", None) for item in task_interrupts]
            payload = payloads[0] if len(payloads) == 1 else payloads
            interrupt_obj = task_interrupts[0]
            agent_id = (
                getattr(task, "name", None)
                or getattr(task, "node", None)
                or cls.extract_agent_id_from_interrupt(interrupt_obj)
                or "unknown"
            )
            interrupts_info.append(
                {
                    "agent_id": agent_id,
                    "payload": payload,
                }
            )
        return interrupts_info

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
                "title": "Knowledge Context",
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

        lines: list[str] = []
        for idx, chunk in enumerate(selected, 1):
            title = chunk.doc_title or chunk.doc_id
            lines.append(f"### Chunk {idx}")
            lines.append(f"- Source: {chunk.source_id} / {title}")
            lines.append(chunk.content.strip())
            lines.append("")
        body = "\n".join(lines).strip()
        return format_markdown(
            title="Knowledge Context",
            sections=[("Chunks", body)],
        )

    # ========== 各类系统消息模板 ==========

    @staticmethod
    def build_react_planner_messages(*, system_prompt: str, goal: str) -> list[BaseMessage]:
        return ContextComposer.compose_simple_messages(
            system_prompt=system_prompt,
            human_content=format_markdown(title=None, sections=[("User Goal", goal)]),
        )

    @staticmethod
    def build_react_replan_messages(*, system_prompt: str, context: str) -> list[BaseMessage]:
        return ContextComposer.compose_simple_messages(
            system_prompt=system_prompt,
            human_content=context,
        )

    @staticmethod
    def build_react_reflector_messages(*, system_prompt: str, context: str) -> list[BaseMessage]:
        return ContextComposer.compose_simple_messages(
            system_prompt=system_prompt,
            human_content=context,
        )

    @staticmethod
    def build_mapreduce_planner_messages(
        *,
        system_prompt: str,
        goal: str,
        contexts: Mapping[str, str] | None = None,
    ) -> list[BaseMessage]:
        return ContextComposer.compose_simple_messages(
            system_prompt=system_prompt,
            human_content=format_markdown(title=None, sections=[("User Goal", goal)]),
            contexts=contexts or {},
        )

    @staticmethod
    def build_mapreduce_reducer_messages(
        *,
        system_prompt: str,
        content: str,
        contexts: Mapping[str, str] | None = None,
    ) -> list[BaseMessage]:
        return ContextComposer.compose_simple_messages(
            system_prompt=system_prompt,
            human_content=content,
            contexts=contexts or {},
        )

    @staticmethod
    def build_todo_audit_messages(*, system_prompt: str, context: str) -> list[BaseMessage]:
        return ContextComposer.compose_simple_messages(
            system_prompt=system_prompt,
            human_content=context,
        )

    @staticmethod
    def build_compactor_messages(*, system_prompt: str, prompt: str) -> list[BaseMessage]:
        return ContextComposer.compose_simple_messages(
            system_prompt=system_prompt,
            human_content=prompt,
        )


def _as_dict(value: Any) -> dict | None:
    return dict(value) if isinstance(value, dict) else None


def _as_str(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def _as_messages(value: Any) -> list[BaseMessage]:
    if isinstance(value, list):
        return [item for item in value if isinstance(item, BaseMessage)]
    return []
