"""
LangGraph 节点工厂

职责：
- 创建 Agent 节点函数（供 graphs/*.py 注册到图）
- 执行 Agent 并处理结果
- 存储 deliverable 到 Store
- 管理上下文（messages、timeline）

不负责：
- 路由决策（由 graphs/*.py 的条件边处理）
- 执行模式判断（由具体的 graph 文件处理）
"""

from __future__ import annotations

import asyncio
import logging
from typing import TYPE_CHECKING, Any

from langchain_core.messages import AIMessage, HumanMessage
from langgraph.config import get_store
from langgraph.store.base import BaseStore
from langgraph.types import Command

from datapillar_oneagentic.context import ContextBuilder
from datapillar_oneagentic.core.types import SessionKey

if TYPE_CHECKING:
    from datapillar_oneagentic.core.agent import AgentSpec


def _extract_text(content: str | list | None) -> str:
    """
    从 Message.content 提取纯文本

    LangChain 的 Message.content 类型是 Union[str, List[Union[str, Dict]]]，
    多模态消息时 content 可能是 list。此函数统一提取文本内容。
    """
    if content is None:
        return ""
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        texts = []
        for item in content:
            if isinstance(item, str):
                texts.append(item)
            elif isinstance(item, dict) and item.get("type") == "text":
                texts.append(item.get("text", ""))
        return "\n".join(texts)
    return str(content)

logger = logging.getLogger(__name__)


def _extract_latest_task_id(state: dict) -> str | None:
    """从 state.messages 中提取最近的 TASK 指令 ID（仅 ReAct 模式）"""
    messages = state.get("messages", [])
    for msg in reversed(messages):
        if (
            isinstance(msg, AIMessage)
            and getattr(msg, "name", None) == "react_controller"
        ):
            text = _extract_text(msg.content)
            if text.startswith("【TASK "):
                # 期望格式: 【TASK t1】...
                try:
                    header = text.split("】", 1)[0]  # 【TASK t1
                    return header.replace("【TASK ", "").strip()
                except Exception:
                    return None
    return None


def _build_step_result_message(
    *,
    state: dict,
    agent_id: str,
    status: str | None,
    error: str | None,
    deliverable_version: int | None,
) -> AIMessage:
    """构建框架写入的执行结果事件（用于稳定给 Reflector/回放提供证据）"""
    task_id = _extract_latest_task_id(state)

    parts = [f"【RESULT】agent={agent_id}"]
    if task_id:
        parts.append(f"task={task_id}")
    if status:
        parts.append(f"status={status}")
    if deliverable_version is not None:
        parts.append(f"deliverable={agent_id}:{deliverable_version}")
    if error:
        parts.append(f"error={error}")

    return AIMessage(content=" ".join(parts), name="datapillar")


class NodeFactory:
    """
    节点工厂

    创建 LangGraph 节点函数，封装 Agent 执行和结果处理逻辑。
    """

    def __init__(
        self,
        *,
        agent_specs: list[AgentSpec],
        agent_ids: list[str],
        get_executor,
        enable_share_context: bool = True,
    ):
        """
        初始化节点工厂

        Args:
            agent_specs: Agent 规格列表
            agent_ids: Agent ID 列表
            get_executor: 获取执行器的函数
            enable_share_context: 是否启用 Agent 间上下文共享（通过 messages 字段）
        """
        self._agent_specs = agent_specs
        self._agent_ids = agent_ids
        self._get_executor = get_executor
        self._enable_share_context = enable_share_context

    def create_agent_node(self, agent_id: str):
        """
        创建 Agent 节点

        Args:
            agent_id: Agent ID

        Returns:
            节点函数
        """
        async def agent_node(state) -> Command:
            # 获取 store（通过 get_store() 获取编译时传入的 store）
            store = get_store()

            # 从 messages 获取输入
            messages = state.get("messages", [])
            query = ""

            # ReAct 模式：优先使用 react_controller 写入的 TASK 指令作为 query
            if state.get("plan"):
                for msg in reversed(messages):
                    if (
                        isinstance(msg, AIMessage)
                        and getattr(msg, "name", None) == "react_controller"
                        and _extract_text(msg.content).startswith("【TASK ")
                    ):
                        query = _extract_text(msg.content)
                        break

            # 非 ReAct 或未找到 TASK：回退到最后一条用户输入
            if not query:
                for msg in reversed(messages):
                    if isinstance(msg, HumanMessage):
                        query = _extract_text(msg.content)
                        break

            # 获取执行器
            executor = self._get_executor(agent_id)

            # 获取经验上下文
            experience_context = state.get("experience_context")

            # 执行（store 通过 get_store() 自动获取，无需传递）
            result = await executor.execute(
                query=query,
                state=dict(state),
                experience_context=experience_context,
            )

            # 处理 Command（委派）
            if isinstance(result, Command):
                # 检查委派目标是否在团队内
                goto = result.goto
                if goto and goto not in self._agent_ids:
                    logger.warning(
                        f"Agent {agent_id} 试图委派给 {goto}，"
                        f"但该 Agent 不在团队内，忽略委派"
                    )
                    return Command(update={"active_agent": None})
                return result

            # 处理 AgentResult
            return await self._handle_result(
                state=state,
                agent_id=agent_id,
                result=result,
                store=store,
            )

        return agent_node

    async def _handle_result(
        self,
        *,
        state,
        agent_id: str,
        result: Any,
        store: BaseStore,
    ) -> Command:
        """处理 Agent 结果"""
        namespace = state["namespace"]
        session_id = state["session_id"]

        # 使用 ContextBuilder 统一管理上下文
        ctx_builder = ContextBuilder.from_state(state)

        # 构建更新字典
        update_dict: dict = {}
        deliverable_version: int | None = None

        # 存储 Agent 交付物到 Store
        if result and hasattr(result, "status") and result.status == "completed":
            key = agent_id
            deliverable_value = (
                result.deliverable.model_dump(mode="json")
                if hasattr(result.deliverable, "model_dump")
                else result.deliverable
            )

            # 使用 Store 存储 deliverable
            if store:
                latest_namespace = ("deliverables", namespace, session_id, "latest")
                versions_namespace = ("deliverables", namespace, session_id, "versions")

                deliverable_versions = dict(state.get("deliverable_versions") or {})
                version = int(deliverable_versions.get(agent_id, 0)) + 1
                deliverable_versions[agent_id] = version

                try:
                    versioned_key = f"{agent_id}:{version}"
                    await store.aput(versions_namespace, versioned_key, deliverable_value)
                    await store.aput(latest_namespace, key, deliverable_value)
                except Exception as e:
                    logger.error(f"存储 deliverable 失败: {e}")
                else:
                    # 只有写入成功才更新 versions 和 keys
                    update_dict["deliverable_versions"] = deliverable_versions
                    deliverable_version = version

                    deliverable_keys = list(state.get("deliverable_keys") or [])
                    if key not in deliverable_keys:
                        deliverable_keys.append(key)
                    update_dict["deliverable_keys"] = deliverable_keys

        # 启用上下文共享时，把 Agent 执行过程中的 messages 添加到 ContextBuilder
        # completed 场景排除最后一条 AIMessage（结构化输出），其余场景保留全部
        if self._enable_share_context and result and result.messages:
            messages_to_share = result.messages
            if result.status == "completed" and messages_to_share and isinstance(messages_to_share[-1], AIMessage):
                messages_to_share = messages_to_share[:-1]
            if messages_to_share:
                ctx_builder.add_messages(messages_to_share)

        # 更新 Agent 执行状态
        if result and hasattr(result, "status"):
            update_dict["last_agent_status"] = result.status
            update_dict["last_agent_error"] = getattr(result, "error", None)

        # active_agent 设为 None，表示当前节点执行完毕
        # 具体路由由 graphs/*.py 的条件边决定
        update_dict["active_agent"] = None

        # 刷新 Timeline 事件（通过 ContextBuilder）
        from datapillar_oneagentic.context.timeline.recorder import timeline_recorder
        key = SessionKey(namespace=namespace, session_id=session_id)
        recorded_events = timeline_recorder.flush(key)
        if recorded_events:
            ctx_builder.record_events(recorded_events)

        # 写入“每步执行结果”事件（框架产生，稳定供 ReAct 反思/回放使用）
        ctx_builder.add_messages(
            [
                _build_step_result_message(
                    state=state,
                    agent_id=agent_id,
                    status=getattr(result, "status", None) if result else None,
                    error=getattr(result, "error", None) if result else None,
                    deliverable_version=deliverable_version,
                )
            ]
        )

        # 合并 ContextBuilder 的更新
        ctx_update = ctx_builder.to_state_update()
        update_dict["messages"] = ctx_update["messages"]
        if ctx_update.get("timeline"):
            update_dict["timeline"] = ctx_update["timeline"]

        return Command(update=update_dict)

    def create_parallel_layer_node(self, agent_ids: list[str]):
        """
        创建并行执行层节点

        Args:
            agent_ids: 该层要并行执行的 Agent ID 列表

        Returns:
            节点函数
        """

        async def parallel_layer_node(state) -> Command:
            """并行执行一层的所有 Agent"""
            namespace = state["namespace"]
            session_id = state["session_id"]

            # 使用 ContextBuilder
            ctx_builder = ContextBuilder.from_state(state)

            # 获取 store
            store = get_store()

            # 从 messages 中提取最后一条用户消息作为 query
            messages = state.get("messages", [])
            query = ""
            for msg in reversed(messages):
                if isinstance(msg, HumanMessage):
                    query = _extract_text(msg.content)
                    break

            if not query:
                logger.warning("并行层未找到用户输入，跳过执行")
                return Command(update={})

            # 并行执行所有 Agent
            async def execute_agent(aid: str):
                executor = self._get_executor(aid)
                result = await executor.execute(
                    query=query,
                    state=dict(state),
                )
                return aid, result

            tasks = [execute_agent(aid) for aid in agent_ids]
            results = await asyncio.gather(*tasks, return_exceptions=True)

            # 存储 deliverables 到 Store，收集 messages
            deliverable_keys = list(state.get("deliverable_keys") or [])
            deliverable_versions = dict(state.get("deliverable_versions") or {})
            latest_namespace = ("deliverables", namespace, session_id, "latest")
            versions_namespace = ("deliverables", namespace, session_id, "versions")

            for item in results:
                if isinstance(item, Exception):
                    logger.error(f"并行执行异常: {item}")
                    continue
                aid, result = item
                result_deliverable_version: int | None = None
                if hasattr(result, "deliverable") and result.deliverable:
                    key = aid
                    deliverable_value = (
                        result.deliverable.model_dump(mode="json")
                        if hasattr(result.deliverable, "model_dump")
                        else result.deliverable
                    )
                    if store:
                        try:
                            version = int(deliverable_versions.get(aid, 0)) + 1
                            deliverable_versions[aid] = version
                            versioned_key = f"{aid}:{version}"
                            await store.aput(versions_namespace, versioned_key, deliverable_value)
                            await store.aput(latest_namespace, key, deliverable_value)
                            if key not in deliverable_keys:
                                deliverable_keys.append(key)
                            result_deliverable_version = version
                        except Exception as e:
                            logger.error(f"存储 deliverable 失败: {e}")

                # 收集 messages（通过 ContextBuilder）
                # completed 场景排除最后一条 AIMessage（结构化输出），其余场景保留全部
                if self._enable_share_context and hasattr(result, "messages") and result.messages:
                    messages_to_share = result.messages
                    if getattr(result, "status", None) == "completed" and messages_to_share and isinstance(
                        messages_to_share[-1], AIMessage
                    ):
                        messages_to_share = messages_to_share[:-1]
                    if messages_to_share:
                        ctx_builder.add_messages(messages_to_share)

                # 写入并行层每个 Agent 的执行结果事件
                ctx_builder.add_messages(
                    [
                        _build_step_result_message(
                            state=state,
                            agent_id=aid,
                            status=getattr(result, "status", None) if result else None,
                            error=getattr(result, "error", None) if result else None,
                            deliverable_version=result_deliverable_version,
                        )
                    ]
                )

            # 构建更新
            ctx_update = ctx_builder.to_state_update()
            update = {
                "deliverable_keys": deliverable_keys,
                "deliverable_versions": deliverable_versions,
                "active_agent": None,
                "messages": ctx_update["messages"],
            }
            if ctx_update.get("timeline"):
                update["timeline"] = ctx_update["timeline"]

            return Command(update=update)

        return parallel_layer_node
