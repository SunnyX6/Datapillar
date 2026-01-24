"""
Datapillar 团队类

组织多个 Agent 协作完成复杂任务。

核心概念：
- namespace: 最高层级的数据隔离边界（必传）
- session_id: 会话标识
- SessionKey: namespace + session_id 的组合，确保全系统隔离
- 会话/交付物按 namespace 隔离；经验/知识向量库固定库名 datapillar，
  通过表内 namespace 字段隔离

使用示例：
```python
from datapillar_oneagentic import Datapillar, DatapillarConfig, Process

# 导入已定义的 Agent 类
from my_agents import AnalystAgent, ReporterAgent

# 组建团队（namespace 必传）
config = DatapillarConfig(
    llm={"api_key": "sk-xxx", "model": "gpt-4o"},
)

team = Datapillar(
    config=config,
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
from collections.abc import AsyncGenerator
from typing import Any, TYPE_CHECKING

from datapillar_oneagentic.config import DatapillarConfig
from datapillar_oneagentic.core.agent import AgentSpec, get_agent_spec
from datapillar_oneagentic.core.graphs import build_graph
from datapillar_oneagentic.core.nodes import NodeFactory
from datapillar_oneagentic.core.process import Process
from datapillar_oneagentic.core.types import SessionKey
from datapillar_oneagentic.context import ContextCollector
from datapillar_oneagentic.context.compaction import CompactPolicy, get_compactor
from datapillar_oneagentic.context.timeline.recorder import TimelineRecorder
from datapillar_oneagentic.events import EventBus
from datapillar_oneagentic.providers.llm import EmbeddingProvider, LLMProvider

if TYPE_CHECKING:
    from datapillar_oneagentic.knowledge import Knowledge

logger = logging.getLogger(__name__)


class Datapillar:
    """
    Datapillar 智能体团队

    组织多个 Agent 协作完成复杂任务。

    特性：
    - namespace 隔离：会话/交付物按 namespace 隔离，向量库通过 namespace 字段隔离
    - 多种执行模式：顺序、动态、层级、MapReduce、ReAct
    - 委派约束：只能在团队内委派
    - 记忆管理：可选启用会话记忆
    - 经验学习：可选启用经验记录
    - 资源安全：连接在每次执行时创建，执行完自动关闭
    - 团队级知识入口：Datapillar(knowledge=...) 统一挂载
    """

    @classmethod
    def _clear_registry(cls) -> None:
        """保留接口兼容（框架当前无全局注册表，无需处理）"""
        pass

    def __init__(
        self,
        *,
        config: DatapillarConfig,
        namespace: str,
        name: str,
        agents: list[type],
        process: Process = Process.SEQUENTIAL,
        enable_learning: bool = False,
        enable_share_context: bool = True,
        a2a_agents: list | None = None,
        verbose: bool = False,
        knowledge: "Knowledge | None" = None,
    ):
        """
        创建团队

        参数：
        - config: DatapillarConfig（必传，团队级配置）
        - namespace: 命名空间（必传，最高层级的数据隔离边界）
        - name: 团队名称
        - agents: Agent 类列表（必须使用 @agent 装饰器定义）
        - process: 执行模式（SEQUENTIAL/DYNAMIC/HIERARCHICAL/MAPREDUCE/REACT）
          - MAPREDUCE 模式下最后一个 Agent 仅提供输出 schema，不参与执行
        - enable_learning: 是否启用经验学习（默认 False）
        - enable_share_context: 是否启用共享 Agent 上下文（默认 True）
        - a2a_agents: 团队级别的 A2A 远程 Agent 配置列表
        - verbose: 是否输出详细日志（默认 False）
        - knowledge: 团队级知识配置（可选，作为所有 Agent 的默认知识入口）
        """
        self._config = config
        self.namespace = namespace
        self.name = name
        self.process = process
        self.enable_learning = enable_learning
        self.enable_share_context = enable_share_context
        self.a2a_agents = a2a_agents or []
        # verbose 参数应该生效：示例里会传 verbose=True 来打开调试信息。
        self.verbose = verbose or self._config.verbose

        # 校验配置
        self._config.validate_llm()

        # 应用日志级别
        if self.verbose:
            logging.getLogger("datapillar_oneagentic").setLevel(logging.DEBUG)
        else:
            logging.getLogger("datapillar_oneagentic").setLevel(
                getattr(logging, self._config.log_level.upper(), logging.INFO)
            )

        # 解析 Agent 类，获取 AgentSpec
        self._agent_specs = self._resolve_agents(agents)
        if knowledge is not None:
            from datapillar_oneagentic.knowledge import merge_knowledge

            for spec in self._agent_specs:
                spec.knowledge = merge_knowledge(knowledge, spec.knowledge)
        self._agent_ids = [spec.id for spec in self._agent_specs]
        self._agent_name_map = {spec.id: spec.name for spec in self._agent_specs}
        self._has_knowledge = any(spec.knowledge is not None for spec in self._agent_specs)

        if enable_learning:
            self._config.validate_embedding()

        # 将团队级别的 A2A 配置合并到每个 Agent
        if self.a2a_agents:
            for spec in self._agent_specs:
                spec.a2a_agents = list(spec.a2a_agents) + list(self.a2a_agents)

        # 校验
        self._validate()

        # 设置入口 Agent（第一个）
        self._entry_agent_id = self._agent_specs[0].id if self._agent_specs else None

        # 团队级事件总线与时间线
        self._event_bus = EventBus()
        self._timeline_recorder = TimelineRecorder(self._event_bus)
        self._timeline_recorder.register()

        # 团队级 LLM Provider 与上下文压缩器
        self._llm_provider = LLMProvider(self._config.llm, event_bus=self._event_bus)
        compaction_policy = CompactPolicy(
            min_keep_entries=self._config.context.compact_min_keep_entries
        )
        self._compactor = get_compactor(
            llm=self._llm_provider(),
            policy=compaction_policy,
        )

        # 创建执行器缓存（团队内）
        self._executors: dict[str, Any] = {}

        # 创建经验学习/知识相关组件
        self._learning_embedding_provider = None
        self._knowledge_embedding_provider = None
        self._learning_store = None
        self._experience_learner = None
        self._experience_retriever = None
        self._knowledge_store = None
        self._knowledge_retriever = None

        if enable_learning:
            self._learning_embedding_provider = EmbeddingProvider(self._config.embedding)
            from datapillar_oneagentic.experience import ExperienceLearner, ExperienceRetriever
            from datapillar_oneagentic.storage import create_learning_store

            self._learning_store = create_learning_store(
                namespace,
                vector_store_config=self._config.vector_store,
                embedding_config=self._config.embedding,
            )
            self._experience_learner = ExperienceLearner(
                store=self._learning_store,
                namespace=namespace,
                embedding_provider=self._learning_embedding_provider,
            )
            self._experience_retriever = ExperienceRetriever(
                store=self._learning_store,
                embedding_provider=self._learning_embedding_provider,
            )
            logger.info(f"经验学习已启用: namespace={namespace}")

        if self._has_knowledge:
            from datapillar_oneagentic.knowledge import KnowledgeRetriever
            from datapillar_oneagentic.storage import create_knowledge_store

            if not self._config.knowledge.base_config.embedding.is_configured():
                raise ValueError("知识功能需要配置 knowledge.base_config.embedding")

            self._knowledge_embedding_provider = EmbeddingProvider(
                self._config.knowledge.base_config.embedding
            )
            self._knowledge_store = create_knowledge_store(
                namespace,
                vector_store_config=self._config.knowledge.base_config.vector_store,
                embedding_config=self._config.knowledge.base_config.embedding,
            )
            self._knowledge_retriever = KnowledgeRetriever(
                store=self._knowledge_store,
                embedding_provider=self._knowledge_embedding_provider,
                config=self._config.knowledge,
            )
            logger.info(f"知识检索已启用: namespace={namespace}")

        self._context_collector = ContextCollector(
            knowledge_retriever=self._knowledge_retriever,
            experience_retriever=self._experience_retriever,
            experience_learner=self._experience_learner,
            share_agent_context=self.enable_share_context,
        )

        # 创建节点工厂
        self._node_factory = NodeFactory(
            agent_specs=self._agent_specs,
            agent_ids=self._agent_ids,
            get_executor=self._get_executor,
            timeline_recorder=self._timeline_recorder,
            knowledge_retriever=self._knowledge_retriever,
            experience_learner=self._experience_learner,
            context_collector=self._context_collector,
        )

        # 获取 LLM（ReAct 模式需要）
        self._react_llm = None
        if process == Process.REACT:
            self._react_llm = self._llm_provider()
            logger.info("ReAct 模式已启用")

        # 获取 LLM（MapReduce 模式需要）
        self._mapreduce_llm = None
        if process == Process.MAPREDUCE:
            self._mapreduce_llm = self._llm_provider()
            logger.info("MapReduce 模式已启用")

        # 构建执行图（StateGraph，还未编译）
        self._graph = build_graph(
            process=process,
            agent_specs=self._agent_specs,
            entry_agent_id=self._entry_agent_id,
            agent_ids=self._agent_ids,
            create_agent_node=self._node_factory.create_agent_node,
            create_mapreduce_worker_node=self._node_factory.create_mapreduce_worker_node,
            create_mapreduce_reducer_node=self._node_factory.create_mapreduce_reducer_node,
            llm=self._react_llm or self._mapreduce_llm,
            context_collector=self._context_collector,
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
            spec = get_agent_spec(cls)
            if spec is None:
                raise ValueError(
                    f"Agent 类 {cls.__name__} 未注册。"
                    f"请确保该类使用了 @agent 装饰器并已被导入。"
                )
            # 深拷贝，团队隔离
            specs.append(copy.deepcopy(spec))

        return specs

    def _validate(self) -> None:
        """校验配置"""
        if not self._agent_specs:
            raise ValueError("agents 不能为空")
        if self.process == Process.MAPREDUCE and len(self._agent_specs) < 2:
            raise ValueError("MAPREDUCE 模式至少需要 2 个 Agent（最后一个作为 Reducer）")

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

            # SEQUENTIAL 模式：禁止委派
            elif self.process == Process.SEQUENTIAL:
                spec.can_delegate_to = []

            # HIERARCHICAL 模式：只有 Manager 可以委派
            elif self.process == Process.HIERARCHICAL:
                if agent_id == self._entry_agent_id:
                    spec.can_delegate_to = [aid for aid in self._agent_ids if aid != agent_id]
                else:
                    spec.can_delegate_to = []

            self._executors[agent_id] = AgentExecutor(
                spec,
                agent_config=self._config.agent,
                event_bus=self._event_bus,
                compactor=self._compactor,
                llm_provider=self._llm_provider,
                agent_name_map=self._agent_name_map,
            )
        return self._executors[agent_id]

    def _build_orchestrator(self, *, checkpointer, store):
        """创建 Orchestrator（绑定当次连接）"""
        from datapillar_oneagentic.runtime.orchestrator import Orchestrator

        return Orchestrator(
            namespace=self.namespace,
            name=self.name,
            graph=self._graph,
            entry_agent_id=self._entry_agent_id,
            agent_ids=self._agent_ids,
            agent_name_map=self._agent_name_map,
            checkpointer=checkpointer,
            store=store,
            experience_learner=self._experience_learner,
            experience_retriever=self._experience_retriever,
            process=self.process,
            event_bus=self._event_bus,
        )

    async def stream(
        self,
        *,
        query: str | None = None,
        session_id: str,
        resume_value: Any | None = None,
    ) -> AsyncGenerator[dict[str, Any], None]:
        """
        流式执行

        每次执行时创建数据库连接，执行完自动关闭，确保资源不泄漏。

        参数：
        - query: 用户输入（新会话或续聊时必传）
        - session_id: 会话 ID
        - resume_value: interrupt 恢复值（用户输入）

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
        from datapillar_oneagentic.storage import create_checkpointer, create_store

        key = SessionKey(namespace=self.namespace, session_id=session_id)

        # 使用 async with 确保连接正确关闭
        async with (
            create_checkpointer(self.namespace, agent_config=self._config.agent) as checkpointer,
            create_store(self.namespace, agent_config=self._config.agent) as store,
        ):
            # 创建 Orchestrator（每次 stream 时创建，绑定当前连接）
            orchestrator = self._build_orchestrator(
                checkpointer=checkpointer,
                store=store,
            )

            async for event in orchestrator.stream(
                query=query,
                key=key,
                resume_value=resume_value,
            ):
                yield event
        # 退出 with，连接自动关闭

    async def compact_session(self, session_id: str) -> dict:
        """手动压缩会话记忆"""
        from datapillar_oneagentic.storage import create_checkpointer, create_store

        async with (
            create_checkpointer(self.namespace, agent_config=self._config.agent) as checkpointer,
            create_store(self.namespace, agent_config=self._config.agent) as store,
        ):
            orchestrator = self._build_orchestrator(
                checkpointer=checkpointer,
                store=store,
            )
            return await orchestrator.compact_session(session_id)

    async def clear_session(self, session_id: str) -> None:
        """清理会话记忆"""
        from datapillar_oneagentic.storage import create_checkpointer

        async with create_checkpointer(self.namespace, agent_config=self._config.agent) as checkpointer:
            orchestrator = self._build_orchestrator(checkpointer=checkpointer, store=None)
            await orchestrator.clear_session(session_id)

    async def clear_session_store(self, session_id: str) -> None:
        """清理会话交付物"""
        from datapillar_oneagentic.storage import create_checkpointer, create_store

        async with (
            create_checkpointer(self.namespace, agent_config=self._config.agent) as checkpointer,
            create_store(self.namespace, agent_config=self._config.agent) as store,
        ):
            orchestrator = self._build_orchestrator(
                checkpointer=checkpointer,
                store=store,
            )
            await orchestrator.clear_session_store(session_id)

    async def get_session_stats(self, session_id: str) -> dict:
        """获取会话统计信息"""
        from datapillar_oneagentic.storage import create_checkpointer, create_store

        async with (
            create_checkpointer(self.namespace, agent_config=self._config.agent) as checkpointer,
            create_store(self.namespace, agent_config=self._config.agent) as store,
        ):
            orchestrator = self._build_orchestrator(
                checkpointer=checkpointer,
                store=store,
            )
            return await orchestrator.get_session_stats(session_id)

    async def get_session_todo(self, session_id: str) -> dict:
        """获取会话 Todo 快照"""
        from datapillar_oneagentic.storage import create_checkpointer, create_store

        async with (
            create_checkpointer(self.namespace, agent_config=self._config.agent) as checkpointer,
            create_store(self.namespace, agent_config=self._config.agent) as store,
        ):
            orchestrator = self._build_orchestrator(
                checkpointer=checkpointer,
                store=store,
            )
            return await orchestrator.get_session_todo(session_id)

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

    @property
    def event_bus(self) -> EventBus:
        """获取团队事件总线"""
        return self._event_bus

    @property
    def __repr__(self) -> str:
        return (
            f"Datapillar(namespace={self.namespace!r}, "
            f"name={self.name!r}, "
            f"agents={[s.id for s in self._agent_specs]}, "
            f"process={self.process.value})"
        )
