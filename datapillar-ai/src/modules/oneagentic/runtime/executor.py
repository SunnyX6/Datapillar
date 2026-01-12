"""
Agent 执行器

负责执行单个 Agent：
1. 准备 AgentContext
2. 调用 Agent 的 run() 方法
3. 处理返回结果

设计原则：
- Executor 只负责执行，不负责调度
- AgentContext 由 Executor 构建
- 结果处理由框架统一
"""

from __future__ import annotations

import logging
from typing import Any

from langgraph.types import Command

from src.infrastructure.llm.client import call_llm
from src.modules.oneagentic.a2a import create_a2a_tool
from src.modules.oneagentic.core.agent import AgentRegistry, AgentSpec
from src.modules.oneagentic.core.context import AgentContext, DelegationSignal
from src.modules.oneagentic.core.types import AgentResult, Clarification
from src.modules.oneagentic.knowledge.store import KnowledgeStore
from src.modules.oneagentic.memory.session_memory import SessionMemory
from src.modules.oneagentic.tools.delegation import create_delegation_tools
from src.modules.oneagentic.tools.registry import resolve_tools

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
        self.delegation_tools = create_delegation_tools(
            can_delegate_to=spec.can_delegate_to,
            agent_names=self._get_agent_names(spec.can_delegate_to),
        )

        # 创建 A2A 远程委派工具
        self.a2a_tools = self._create_a2a_tools(spec)

        # 所有工具
        self.all_tools = self.business_tools + self.delegation_tools + self.a2a_tools

        # 创建 LLM
        self.llm = call_llm(temperature=spec.temperature)

        logger.info(
            f"📦 Executor 创建: {spec.name} ({spec.id}), "
            f"工具: {len(self.business_tools)}, 委派: {len(self.delegation_tools)}, A2A: {len(self.a2a_tools)}"
        )

    def _create_a2a_tools(self, spec: AgentSpec) -> list:
        """创建 A2A 远程委派工具"""
        tools = []
        for i, a2a_config in enumerate(spec.a2a_agents):
            try:
                # 从 endpoint 生成工具名称
                endpoint = a2a_config.endpoint
                name_parts = endpoint.rstrip("/").split("/")
                tool_name = f"a2a_delegate_{name_parts[-1].replace('.', '_').replace('-', '_')}"
                if len(tool_name) > 50:
                    tool_name = f"a2a_delegate_{i}"

                tool = create_a2a_tool(a2a_config, name=tool_name)
                tools.append(tool)
                logger.info(f"📡 A2A 工具创建: {tool_name} -> {endpoint}")
            except Exception as e:
                if a2a_config.fail_fast:
                    raise
                logger.warning(f"跳过 A2A 工具: {a2a_config.endpoint}, 错误: {e}")
        return tools

    def _get_agent_names(self, agent_ids: list[str]) -> dict[str, str]:
        """获取 Agent 名称映射"""
        names = {}
        for agent_id in agent_ids:
            spec = AgentRegistry.get(agent_id)
            if spec:
                names[agent_id] = spec.name
        return names

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

        if not query:
            return AgentResult.failed(
                summary="缺少用户输入",
                error="query 不能为空",
            )

        logger.info(f"📋 [{spec.name}] 开始执行: {query[:100]}...")

        try:
            # 1. 获取知识
            knowledge_prompt = self._build_knowledge_prompt(spec)

            # 2. 构建 AgentContext（使用私有字段）
            ctx = AgentContext(
                session_id=session_id,
                query=query,
                _spec=spec,
                _memory=memory,
                _knowledge_prompt=knowledge_prompt,
                _llm=self.llm,
                _tools=self.all_tools,
                _state=state or {},
            )

            # 3. 调用 Agent 的 run() 方法
            result = await spec.run_fn(ctx)

            # 4. 处理 None
            if result is None:
                return AgentResult.failed(
                    summary="Agent 返回 None",
                    error="run() 返回 None",
                )

            # 5. 处理 Clarification
            if isinstance(result, Clarification):
                logger.info(f"❓ [{spec.name}] 需要澄清: {result.message}")
                return AgentResult.needs_clarification(result)

            # 6. 处理 deliverable_schema 实例
            if spec.deliverable_schema and isinstance(result, spec.deliverable_schema):
                summary = self._extract_summary(result)
                logger.info(f"✅ [{spec.name}] 完成: {summary}")
                return AgentResult.completed(
                    summary=summary,
                    deliverable=result,
                    deliverable_type=spec.deliverable_key,
                )

            # 7. 类型错误
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
            return AgentResult.system_error(
                summary=f"系统异常: {str(e)}",
                error=str(e),
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

    def _build_knowledge_prompt(self, spec: AgentSpec) -> str:
        """
        构建知识 Prompt

        根据 spec.knowledge_domains 从 KnowledgeStore 获取知识，
        组装成可注入 Prompt 的格式。
        """
        if not spec.knowledge_domains:
            return ""

        knowledge = KnowledgeStore.get_knowledge(
            domains=spec.knowledge_domains,
            agent_id=spec.id,
            max_tokens=4000,
            include_agent_knowledge=True,
        )

        if not knowledge.get("domains"):
            return ""

        # 组装知识 Prompt
        parts = ["## 知识库"]
        for domain_id, content in knowledge["domains"].items():
            parts.append(content)

        return "\n\n".join(parts)


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
