"""
Datapillar 团队类

组织多个 Agent 协作完成复杂任务。

核心概念：
- namespace: 最高层级的数据隔离边界（必传）
- session_id: 会话标识
- 所有存储（会话、经验、知识）都按 namespace 隔离

使用示例：
```python
from datapillar_oneagentic import Datapillar, Process

# 导入已定义的 Agent 类
from my_agents import AnalystAgent, ReporterAgent

# 组建团队（namespace 必传）
team = Datapillar(
    namespace="sales_app",  # 数据隔离边界
    name="分析团队",
    agents=[AnalystAgent, ReporterAgent],
    process=Process.DYNAMIC,
    enable_learning=True,
)

# 流式执行
async for event in team.stream(
    query="分析销售数据",
    session_id="session_001",
):
    print(event)

# 保存经验
await team.save_experience(session_id="session_001", feedback={"stars": 5})
```
"""

from __future__ import annotations

import copy
import logging
import time
from collections.abc import AsyncGenerator
from typing import Any

from datapillar_oneagentic.core.agent import AgentRegistry, AgentSpec
from datapillar_oneagentic.core.graphs import build_graph
from datapillar_oneagentic.core.nodes import NodeFactory
from datapillar_oneagentic.core.process import Process
from datapillar_oneagentic.core.result import DatapillarResult

logger = logging.getLogger(__name__)


def _now_ms() -> int:
    return int(time.time() * 1000)


class Datapillar:
    """
    Datapillar 智能体团队

    组织多个 Agent 协作完成复杂任务。

    特性：
    - namespace 隔离：所有数据按 namespace 隔离
    - 多种执行模式：顺序、动态、层级、并行
    - 委派约束：只能在团队内委派
    - 记忆管理：可选启用会话记忆
    - 经验学习：可选启用经验记录
    """

    @classmethod
    def _clear_registry(cls) -> None:
        """清空注册表（仅用于测试，保留接口兼容）"""
        pass

    def __init__(
        self,
        *,
        namespace: str,
        name: str,
        agents: list[type],
        process: Process = Process.SEQUENTIAL,
        enable_share_context: bool = True,
        enable_learning: bool = False,
        a2a_agents: list | None = None,
        verbose: bool = False,
    ):
        """
        创建团队

        参数：
        - namespace: 命名空间（必传，最高层级的数据隔离边界）
        - name: 团队名称
        - agents: Agent 类列表（必须使用 @agent 装饰器定义）
        - process: 执行模式（SEQUENTIAL/DYNAMIC/HIERARCHICAL/PARALLEL/REACT）
        - enable_share_context: 是否启用 Agent 间上下文共享（默认 True，通过 messages 字段共享）
        - enable_learning: 是否启用经验学习（默认 False）
        - a2a_agents: 团队级别的 A2A 远程 Agent 配置列表
        - verbose: 是否输出详细日志（默认 False）
        """
        self.namespace = namespace
        self.name = name
        self.process = process
        self.enable_share_context = enable_share_context
        self.enable_learning = enable_learning
        self.a2a_agents = a2a_agents or []
        self.verbose = verbose

        # 解析 Agent 类，获取 AgentSpec
        self._agent_specs = self._resolve_agents(agents)
        self._agent_ids = [spec.id for spec in self._agent_specs]

        # 将团队级别的 A2A 配置合并到每个 Agent
        if self.a2a_agents:
            for spec in self._agent_specs:
                spec.a2a_agents = list(spec.a2a_agents) + list(self.a2a_agents)

        # 校验
        self._validate()

        # 设置入口 Agent（第一个）
        self._entry_agent_id = self._agent_specs[0].id if self._agent_specs else None

        # 创建执行器缓存（团队内）
        self._executors: dict[str, Any] = {}

        # 创建存储实例（所有存储都用 namespace 隔离）
        from datapillar_oneagentic.storage import (
            create_checkpointer,
            create_store,
            create_learning_store,
        )

        self._checkpointer = create_checkpointer(namespace)
        self._store = create_store(namespace)

        # 创建经验学习相关组件（如果启用）
        self._learning_store = None
        self._experience_learner = None
        self._experience_retriever = None
        if enable_learning:
            from datapillar_oneagentic.experience import ExperienceLearner, ExperienceRetriever

            self._learning_store = create_learning_store(namespace)
            self._experience_learner = ExperienceLearner(
                store=self._learning_store,
                namespace=namespace,
            )
            self._experience_retriever = ExperienceRetriever(
                store=self._learning_store,
            )
            logger.info(f"经验学习已启用: namespace={namespace}")

        # 注册 Timeline 记录器
        from datapillar_oneagentic.context.timeline.recorder import timeline_recorder
        timeline_recorder.register()

        # 创建节点工厂
        self._node_factory = NodeFactory(
            agent_specs=self._agent_specs,
            agent_ids=self._agent_ids,
            get_executor=self._get_executor,
            enable_share_context=enable_share_context,
        )

        # 获取 LLM（ReAct 模式需要）
        react_llm = None
        if process == Process.REACT:
            from datapillar_oneagentic.providers.llm import call_llm
            react_llm = call_llm()
            logger.info("ReAct 模式已启用")

        # 构建执行图
        self._graph = build_graph(
            process=process,
            agent_specs=self._agent_specs,
            entry_agent_id=self._entry_agent_id,
            agent_ids=self._agent_ids,
            create_agent_node=self._node_factory.create_agent_node,
            create_parallel_layer_node=self._node_factory.create_parallel_layer_node,
            llm=react_llm,
        )

        # 创建 Orchestrator（基建层）
        from datapillar_oneagentic.runtime.orchestrator import Orchestrator

        self._orchestrator = Orchestrator(
            namespace=namespace,
            name=name,
            graph=self._graph,
            entry_agent_id=self._entry_agent_id,
            agent_ids=self._agent_ids,
            checkpointer=self._checkpointer,
            store=self._store,
            experience_learner=self._experience_learner,
            experience_retriever=self._experience_retriever,
            process=process,
        )

        logger.info(
            f"Datapillar 创建: {name} (namespace={namespace}), "
            f"成员: {[s.name for s in self._agent_specs]}, "
            f"模式: {process.value}, "
            f"入口: {self._entry_agent_id}"
        )

    def _resolve_agents(self, agent_classes: list[type]) -> list[AgentSpec]:
        """解析 Agent 类，获取 AgentSpec"""
        specs = []

        for cls in agent_classes:
            found = False
            for agent_id in AgentRegistry.list_ids():
                spec = AgentRegistry.get(agent_id)
                if spec and spec.agent_class == cls:
                    # 深拷贝，团队隔离
                    specs.append(copy.deepcopy(spec))
                    found = True
                    break

            if not found:
                raise ValueError(
                    f"Agent 类 {cls.__name__} 未注册。"
                    f"请确保该类使用了 @agent 装饰器并已被导入。"
                )

        return specs

    def _validate(self) -> None:
        """校验配置"""
        if not self._agent_specs:
            raise ValueError("agents 不能为空")

        # 校验委派约束
        for spec in self._agent_specs:
            for delegate_to in spec.can_delegate_to:
                if delegate_to not in self._agent_ids:
                    logger.warning(
                        f"Agent {spec.id} 的委派目标 {delegate_to} 不在团队内，将被忽略。"
                        f"团队成员: {self._agent_ids}"
                    )

    def _get_executor(self, agent_id: str):
        """获取执行器（带缓存）"""
        from datapillar_oneagentic.runtime.executor import AgentExecutor

        if agent_id not in self._executors:
            spec = next((s for s in self._agent_specs if s.id == agent_id), None)
            if not spec:
                raise KeyError(f"Agent {agent_id} 不在团队中")

            # DYNAMIC 模式：自动设置 can_delegate_to
            if self.process == Process.DYNAMIC:
                spec.can_delegate_to = [aid for aid in self._agent_ids if aid != agent_id]

            # HIERARCHICAL 模式：只有 Manager 可以委派
            elif self.process == Process.HIERARCHICAL:
                if agent_id == self._entry_agent_id:
                    spec.can_delegate_to = [aid for aid in self._agent_ids if aid != agent_id]
                else:
                    spec.can_delegate_to = []

            self._executors[agent_id] = AgentExecutor(spec)
        return self._executors[agent_id]

    async def stream(
        self,
        *,
        query: str | None = None,
        session_id: str,
        resume_value: Any | None = None,
    ) -> AsyncGenerator[dict[str, Any], None]:
        """
        流式执行

        委托给 Orchestrator 处理，自带：
        - 断点恢复
        - 经验记录（如果启用）
        - EventBus 事件
        - 会话管理

        参数：
        - query: 用户输入（新会话或续聊时必传）
        - session_id: 会话 ID
        - resume_value: interrupt 恢复值（用户对 clarification 的回答）

        返回：
        - SSE 事件流

        使用示例：
        ```python
        # 新会话
        async for event in team.stream(query="分析数据", session_id="s1"):
            print(event)

        # interrupt 恢复
        async for event in team.stream(session_id="s1", resume_value="确认执行"):
            print(event)
        ```
        """
        async for event in self._orchestrator.stream(
            query=query,
            session_id=session_id,
            resume_value=resume_value,
        ):
            yield event

    async def kickoff(
        self,
        *,
        inputs: dict[str, Any],
        session_id: str | None = None,
    ) -> DatapillarResult:
        """
        同步执行（收集所有结果）

        参数：
        - inputs: 输入参数（需包含 'query' 或 'requirement' 键）
        - session_id: 会话 ID（可选，不传则自动生成）

        返回：
        - DatapillarResult
        """
        import uuid

        query = inputs.get("query") or inputs.get("requirement") or str(inputs)
        session_id = session_id or f"kickoff_{uuid.uuid4().hex[:8]}"

        start_time = _now_ms()
        deliverables: dict[str, Any] = {}
        error = None

        try:
            async for event in self.stream(
                query=query,
                session_id=session_id,
            ):
                event_type = event.get("event")
                if event_type == "result":
                    deliverables = event.get("data", {}).get("deliverables", {})
                elif event_type == "error":
                    error = event.get("data", {}).get("detail")

        except Exception as e:
            error = str(e)

        duration_ms = _now_ms() - start_time

        return DatapillarResult(
            success=error is None,
            deliverables=deliverables,
            duration_ms=duration_ms,
            error=error,
        )

    async def compact_session(self, session_id: str) -> dict:
        """手动压缩会话记忆"""
        return await self._orchestrator.compact_session(session_id)

    async def delete_session(self, session_id: str) -> None:
        """删除会话"""
        await self._orchestrator.delete_session(session_id)

    async def get_session_stats(self, session_id: str) -> dict:
        """获取会话统计信息"""
        return await self._orchestrator.get_session_stats(session_id)

    async def save_experience(
        self,
        session_id: str,
        feedback: dict[str, Any] | None = None,
    ) -> bool:
        """
        保存经验到向量库

        用户觉得这次执行有价值时调用此方法保存经验。
        不调用 = 不保存。

        Args:
            session_id: 会话 ID
            feedback: 用户反馈（可选，结构由使用者定义）

        Returns:
            是否保存成功
        """
        if not self._experience_learner:
            logger.warning("经验学习未启用，无法保存经验")
            return False

        if not self._experience_learner.has_pending(session_id):
            logger.warning(f"没有待保存的记录: {session_id}")
            return False

        return await self._experience_learner.save_experience(
            session_id=session_id,
            feedback=feedback,
        )

    def __repr__(self) -> str:
        return (
            f"Datapillar(namespace={self.namespace!r}, "
            f"name={self.name!r}, "
            f"agents={[s.id for s in self._agent_specs]}, "
            f"process={self.process.value})"
        )
