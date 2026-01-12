"""
Orchestrator - 编排器

负责：
1. 流式执行
2. 断点恢复
3. SSE 事件流
"""

from __future__ import annotations

import logging
import time
from collections.abc import AsyncGenerator
from typing import Any

from langchain_core.messages import HumanMessage
from langgraph.graph import StateGraph

from datapillar_oneagentic.events import event_bus, SessionStartedEvent, SessionCompletedEvent
from datapillar_oneagentic.storage import get_storage_provider
from datapillar_oneagentic.state.blackboard import create_blackboard

logger = logging.getLogger(__name__)


def _now_ms() -> int:
    return int(time.time() * 1000)


class Orchestrator:
    """
    编排器

    负责执行团队的工作流程。
    """

    def __init__(
        self,
        *,
        name: str,
        team_id: str,
        graph: StateGraph,
        entry_agent_id: str,
        agent_ids: list[str],
        checkpointer=None,
        deliverable_store=None,
        learning_store=None,
        enable_learning: bool = False,
        enable_react: bool = False,
    ):
        """
        创建编排器

        参数：
        - name: 团队名称
        - team_id: 团队 ID
        - graph: LangGraph 状态图
        - entry_agent_id: 入口 Agent ID
        - agent_ids: 所有 Agent ID 列表
        - checkpointer: Checkpointer 实例（可选）
        - deliverable_store: Store 实例（可选）
        - learning_store: VectorStore 实例（可选）
        - enable_learning: 是否启用经验学习
        - enable_react: 是否启用 ReAct 模式
        """
        self.name = name
        self.team_id = team_id
        self.graph = graph
        self.entry_agent_id = entry_agent_id
        self.agent_ids = agent_ids
        self._checkpointer = checkpointer
        self._deliverable_store = deliverable_store
        self._learning_store = learning_store
        self.enable_learning = enable_learning
        self.enable_react = enable_react

        # 编译图
        self._compiled_graph = None

    async def _ensure_compiled(self):
        """确保图已编译"""
        if self._compiled_graph is None:
            if self._checkpointer:
                saver = self._checkpointer.get_saver()
                self._compiled_graph = self.graph.compile(checkpointer=saver)
            else:
                storage_provider = get_storage_provider()
                async with storage_provider.get_checkpointer() as checkpointer:
                    self._compiled_graph = self.graph.compile(checkpointer=checkpointer)
        return self._compiled_graph

    async def stream(
        self,
        *,
        query: str,
        session_id: str,
        user_id: str,
        task_type: str = "general",
    ) -> AsyncGenerator[dict[str, Any], None]:
        """
        流式执行

        支持断点恢复：同一 session_id 的多次调用会保留之前的状态和记忆。

        参数：
        - query: 用户输入
        - session_id: 会话 ID
        - user_id: 用户 ID
        - task_type: 任务类型

        返回：
        - SSE 事件流
        """
        start_time = _now_ms()

        # 发送会话开始事件
        await event_bus.aemit(
            self,
            SessionStartedEvent(
                session_id=session_id,
                user_id=user_id,
                query=query,
            ),
        )

        config = {
            "configurable": {
                "thread_id": f"{self.team_id}:{session_id}",
            }
        }

        # 编译图
        compiled = await self._ensure_compiled()

        # 尝试获取现有状态（支持断点恢复）
        existing_state = None
        try:
            state_snapshot = await compiled.aget_state(config)
            if state_snapshot and state_snapshot.values:
                existing_state = state_snapshot.values
                logger.info(f"🔄 恢复会话状态: session_id={session_id}")
        except Exception as e:
            logger.debug(f"无现有状态，创建新会话: {e}")

        if existing_state:
            # 有现有状态：追加新消息，保留记忆
            existing_messages = existing_state.get("messages", [])
            existing_messages.append(HumanMessage(content=query))

            # 更新状态：追加消息，重新激活入口 Agent
            await compiled.aupdate_state(
                config,
                {
                    "messages": [HumanMessage(content=query)],
                    "active_agent": self.entry_agent_id,
                },
            )
            input_state = None  # 使用 None 从更新后的状态继续
        else:
            # 无现有状态：创建新的初始状态
            input_state = create_blackboard(session_id=session_id, user_id=user_id)
            input_state["messages"] = [HumanMessage(content=query)]
            input_state["active_agent"] = self.entry_agent_id

        try:
            # 发送开始事件
            yield {
                "event": "start",
                "data": {
                    "session_id": session_id,
                    "team": self.name,
                    "entry_agent": self.entry_agent_id,
                    "resumed": existing_state is not None,
                },
            }

            async for event in compiled.astream(input_state, config):
                # 处理节点输出
                for node_name, node_output in event.items():
                    if node_name == "__end__":
                        continue

                    # 发送 Agent 事件
                    yield {
                        "event": "agent",
                        "data": {
                            "agent_id": node_name,
                            "status": "completed",
                        },
                    }

            # 获取最终状态
            final_state = await compiled.aget_state(config)
            messages = final_state.values.get("messages", [])
            deliverables = final_state.values.get("deliverables", {})

            # 提取最终消息
            final_message = ""
            if messages:
                last_msg = messages[-1]
                final_message = getattr(last_msg, "content", "")

            # 发送结果事件
            yield {
                "event": "result",
                "data": {
                    "message": final_message,
                    "deliverables": deliverables,
                    "duration_ms": _now_ms() - start_time,
                },
            }

            # 发送会话完成事件
            await event_bus.aemit(
                self,
                SessionCompletedEvent(
                    session_id=session_id,
                    user_id=user_id,
                    result=deliverables,
                    duration_ms=_now_ms() - start_time,
                ),
            )

        except Exception as e:
            logger.error(f"执行失败: {e}", exc_info=True)
            yield {
                "event": "error",
                "data": {
                    "detail": str(e),
                    "duration_ms": _now_ms() - start_time,
                },
            }

    async def compact_session(self, session_id: str, user_id: str) -> dict:
        """手动压缩会话记忆"""
        # TODO: 实现压缩逻辑
        return {"status": "not_implemented"}

    async def delete_session(self, session_id: str, user_id: str) -> None:
        """删除会话"""
        storage_provider = get_storage_provider()
        thread_id = f"{self.team_id}:{session_id}"
        await storage_provider.delete_thread(thread_id)

    async def get_session_stats(self, session_id: str, user_id: str) -> dict:
        """获取会话统计"""
        # TODO: 实现统计逻辑
        return {
            "session_id": session_id,
            "user_id": user_id,
            "team_id": self.team_id,
        }
