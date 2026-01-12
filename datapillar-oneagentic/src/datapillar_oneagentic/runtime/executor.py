"""
Agent 执行器

负责执行单个 Agent：
1. 准备 AgentContext
2. 调用 Agent 的 run() 方法
3. 处理返回结果
4. 发送执行事件
"""

from __future__ import annotations

import logging
import time
from typing import Any

from langgraph.types import Command

from datapillar_oneagentic.core.agent import AgentRegistry, AgentSpec
from datapillar_oneagentic.core.context import AgentContext, DelegationSignal
from datapillar_oneagentic.core.types import AgentResult, Clarification
from datapillar_oneagentic.events import (
    event_bus,
    AgentStartedEvent,
    AgentCompletedEvent,
    AgentFailedEvent,
)
from datapillar_oneagentic.memory.session_memory import SessionMemory
from datapillar_oneagentic.providers.llm import call_llm
from datapillar_oneagentic.tools.registry import resolve_tools

logger = logging.getLogger(__name__)


class AgentExecutor:
    """
    Agent 执行器

    负责执行单个 Agent，构建 AgentContext，处理返回结果。
    """

    def __init__(self, spec: AgentSpec):
        """
        创建执行器

        参数：
        - spec: Agent 规格
        """
        self.spec = spec

        # 解析业务工具
        self.business_tools = resolve_tools(spec.tools)

        # 创建委派工具
        self.delegation_tools = self._create_delegation_tools(spec)

        # 所有工具
        self.all_tools = self.business_tools + self.delegation_tools

        # 创建 LLM（统一使用 call_llm）
        self.llm = call_llm(temperature=spec.temperature)

        logger.info(
            f"📦 Executor 创建: {spec.name} ({spec.id}), "
            f"工具: {len(self.business_tools)}, 委派: {len(self.delegation_tools)}"
        )

    def _create_delegation_tools(self, spec: AgentSpec) -> list:
        """创建委派工具"""
        if not spec.can_delegate_to:
            return []

        from langchain_core.tools import tool as lc_tool

        tools = []
        for target_id in spec.can_delegate_to:
            target_spec = AgentRegistry.get(target_id)
            if not target_spec:
                continue

            @lc_tool(f"delegate_to_{target_id}")
            def delegate_tool(task: str, target_id: str = target_id) -> Command:
                f"""委派任务给 {target_spec.name if target_spec else target_id}

                Args:
                    task: 要委派的任务描述
                """
                return Command(
                    goto=target_id,
                    update={"task_description": task},
                )

            tools.append(delegate_tool)

        return tools

    async def execute(
        self,
        *,
        query: str,
        session_id: str,
        memory: SessionMemory | None = None,
        state: dict | None = None,
    ) -> AgentResult | Command:
        """
        执行 Agent

        参数：
        - query: 用户输入
        - session_id: 会话 ID
        - memory: 会话记忆
        - state: 共享状态

        返回：
        - AgentResult 或 Command（委派）
        """
        spec = self.spec
        start_time = time.time()

        if not query:
            return AgentResult.failed(
                summary="缺少用户输入",
                error="query 不能为空",
            )

        logger.info(f"📋 [{spec.name}] 开始执行: {query[:100]}...")

        # 发送 Agent 开始事件
        await event_bus.aemit(
            self,
            AgentStartedEvent(
                agent_id=spec.id,
                agent_name=spec.name,
                session_id=session_id,
                query=query[:200],
            ),
        )

        try:
            # 构建 AgentContext
            ctx = AgentContext(
                session_id=session_id,
                query=query,
                _spec=spec,
                _memory=memory,
                _knowledge_prompt="",
                _llm=self.llm,
                _tools=self.all_tools,
                _state=state or {},
            )

            # 调用 Agent 的 run() 方法
            result = await spec.run_fn(ctx)

            # 处理 None
            if result is None:
                await self._emit_failed_event(spec, session_id, start_time, "run() 返回 None")
                return AgentResult.failed(
                    summary="Agent 返回 None",
                    error="run() 返回 None",
                )

            # 处理 Clarification
            if isinstance(result, Clarification):
                logger.info(f"❓ [{spec.name}] 需要澄清: {result.message}")
                return AgentResult.needs_clarification(result)

            # 处理 deliverable_schema 实例
            if spec.deliverable_schema and isinstance(result, spec.deliverable_schema):
                summary = self._extract_summary(result)
                logger.info(f"✅ [{spec.name}] 完成: {summary}")

                # 发送 Agent 完成事件
                duration_ms = (time.time() - start_time) * 1000
                await event_bus.aemit(
                    self,
                    AgentCompletedEvent(
                        agent_id=spec.id,
                        agent_name=spec.name,
                        session_id=session_id,
                        result=summary,
                        duration_ms=duration_ms,
                    ),
                )

                return AgentResult.completed(
                    summary=summary,
                    deliverable=result,
                    deliverable_type=spec.deliverable_key,
                )

            # 类型错误
            raise TypeError(
                f"Agent {spec.id} 的 run() 返回类型错误: "
                f"期望 {spec.deliverable_schema.__name__ if spec.deliverable_schema else 'Any'}, "
                f"实际 {type(result).__name__}"
            )

        except DelegationSignal as signal:
            # 委派信号由框架处理
            logger.info(f"🔄 [{spec.name}] 委派给 {signal.command.goto}")
            return signal.command
        except TypeError:
            raise
        except Exception as e:
            logger.error(f"[{spec.name}] 执行失败: {e}", exc_info=True)
            await self._emit_failed_event(spec, session_id, start_time, str(e))
            return AgentResult.system_error(
                summary=f"系统异常: {str(e)}",
                error=str(e),
            )

    async def _emit_failed_event(
        self,
        spec: AgentSpec,
        session_id: str,
        start_time: float,
        error: str,
    ) -> None:
        """发送 Agent 失败事件"""
        await event_bus.aemit(
            self,
            AgentFailedEvent(
                agent_id=spec.id,
                agent_name=spec.name,
                session_id=session_id,
                error=error,
                error_type=type(error).__name__,
            ),
        )

    def _extract_summary(self, result: Any) -> str:
        """从结果中提取摘要"""
        if result is None:
            return "完成"

        # 尝试常见字段
        for field in ["summary", "answer", "message"]:
            if hasattr(result, field):
                value = getattr(result, field)
                if value:
                    return str(value)[:200]

        return "完成"


# === 执行器缓存 ===

_executor_cache: dict[str, AgentExecutor] = {}


def get_executor(agent_id: str) -> AgentExecutor:
    """获取执行器（带缓存）"""
    if agent_id not in _executor_cache:
        spec = AgentRegistry.get(agent_id)
        if not spec:
            raise KeyError(f"Agent {agent_id} 不存在")
        _executor_cache[agent_id] = AgentExecutor(spec)
    return _executor_cache[agent_id]


def clear_executor_cache() -> None:
    """清空缓存（仅测试用）"""
    _executor_cache.clear()
