"""
Datapillar 团队类

组织多个 Agent 协作完成复杂任务。

核心职责：
1. 团队定义：组织 Agent 集合
2. 入口管理：自动将第一个 Agent 作为入口
3. 执行模式：支持顺序/动态
4. 委派约束：DYNAMIC 模式下自动设置委派目标

内部组合 Orchestrator 提供基建能力：
- 状态持久化（Checkpoint）
- 断点恢复
- 经验学习
- 事件发送（EventBus）
- 会话管理

使用示例：
```python
from src.modules.oneagentic import Datapillar, Process

# 导入已定义的 Agent 类
from src.modules.etl.agents import AnalystAgent, ArchitectAgent

# 组建团队
team = Datapillar(
    name="ETL 团队",
    agents=[AnalystAgent, ArchitectAgent],
    process=Process.DYNAMIC,
)

# 流式执行（自带断点恢复、经验学习、EventBus）
async for event in team.stream(
    query="创建用户宽表",
    session_id="session_001",
    user_id="user_001",
):
    print(event)

# 会话管理
await team.compact_session(session_id, user_id)
await team.delete_session(session_id, user_id)
```
"""

from __future__ import annotations

import hashlib
import logging
import time
from collections.abc import AsyncGenerator
from dataclasses import dataclass, field
from typing import TYPE_CHECKING, Any

from langchain_core.messages import AIMessage, HumanMessage
from langgraph.graph import END, StateGraph
from langgraph.types import Command

from src.modules.oneagentic.core.agent import AgentRegistry, AgentSpec
from src.modules.oneagentic.core.process import Process
from src.modules.oneagentic.memory.session_memory import SessionMemory
from src.modules.oneagentic.runtime.executor import AgentExecutor
from src.modules.oneagentic.state.blackboard import Blackboard

if TYPE_CHECKING:
    pass

logger = logging.getLogger(__name__)


def _now_ms() -> int:
    return int(time.time() * 1000)


@dataclass
class DatapillarResult:
    """执行结果"""

    success: bool
    """是否成功"""

    output: Any
    """最终输出"""

    summary: str
    """结果摘要"""

    agent_outputs: dict[str, Any] = field(default_factory=dict)
    """各 Agent 输出 {agent_id: output}"""

    duration_ms: int = 0
    """总耗时"""

    error: str | None = None
    """错误信息（如果失败）"""


class Datapillar:
    """
    Datapillar 智能体团队

    组织多个 Agent 协作完成复杂任务。

    特性：
    - 团队隔离：每个实例有独立的 Agent 集合和 team_id
    - 多种执行模式：顺序、动态、层级、并行
    - 委派约束：只能在团队内委派
    - 记忆管理：可选启用会话记忆
    - 经验学习：可选启用经验沉淀
    """

    # 类级别注册表（进程内共享，用于检测重复团队名称）
    _active_teams: dict[str, Datapillar] = {}

    @staticmethod
    def _generate_team_id(name: str) -> str:
        """基于团队名称生成稳定的 team_id"""
        return f"team_{hashlib.sha256(name.encode()).hexdigest()[:12]}"

    @classmethod
    def _clear_registry(cls) -> None:
        """清空注册表（仅用于测试）"""
        cls._active_teams.clear()

    @classmethod
    def get_team(cls, name: str) -> Datapillar | None:
        """获取已注册的团队实例"""
        return cls._active_teams.get(name)

    def __init__(
        self,
        *,
        name: str,
        agents: list[type],
        process: Process = Process.SEQUENTIAL,
        manager_llm: str | None = None,
        memory: bool = True,
        enable_learning: bool = False,
        enable_react: bool = False,
        verbose: bool = False,
    ):
        """
        创建 Datapillar 团队

        参数：
        - name: 团队名称
        - agents: Agent 类列表（必须使用 @agent 装饰器定义）
        - process: 执行模式（SEQUENTIAL/DYNAMIC）
        - manager_llm: 层级模式下的 Manager LLM（HIERARCHICAL 模式必填）
        - memory: 是否启用对话记忆（默认 True）
        - enable_learning: 是否启用经验学习（默认 False）
        - enable_react: 是否启用 ReAct 模式（默认 False）
        - verbose: 是否输出详细日志（默认 False）
        """
        # 检查团队名称是否重复
        if name in Datapillar._active_teams:
            raise ValueError(f"团队名称 '{name}' 已被使用，请使用不同的名称")

        self.name = name
        self.team_id = self._generate_team_id(name)
        self.process = process
        self.manager_llm = manager_llm
        self.memory = memory
        self.enable_learning = enable_learning
        self.enable_react = enable_react
        self.verbose = verbose

        # 注册到类级别字典
        Datapillar._active_teams[name] = self

        # 解析 Agent 类，获取 AgentSpec
        self._agent_specs = self._resolve_agents(agents)
        self._agent_ids = [spec.id for spec in self._agent_specs]

        # 校验
        self._validate()

        # 设置入口 Agent（第一个）
        self._entry_agent_id = self._agent_specs[0].id if self._agent_specs else None

        # 创建执行器缓存（团队内）
        self._executors: dict[str, AgentExecutor] = {}

        # 构建执行图
        self._graph = self._build_graph()

        # 创建 Orchestrator（基建层）
        from src.modules.oneagentic.runtime.orchestrator import Orchestrator

        self._orchestrator = Orchestrator(
            name=name,
            team_id=self.team_id,
            graph=self._graph,
            entry_agent_id=self._entry_agent_id,
            agent_ids=self._agent_ids,
            enable_learning=enable_learning,
            enable_react=enable_react,
        )

        logger.info(
            f"Datapillar 团队创建: {name} ({self.team_id}), "
            f"成员: {[s.name for s in self._agent_specs]}, "
            f"模式: {process.value}, "
            f"入口: {self._entry_agent_id}"
        )

    def _resolve_agents(self, agent_classes: list[type]) -> list[AgentSpec]:
        """
        解析 Agent 类，获取 AgentSpec

        从全局 AgentRegistry 获取已注册的 AgentSpec。
        """
        specs = []

        for cls in agent_classes:
            # 获取类名对应的 agent_id
            # 约定：@agent 装饰器的 id 参数是唯一标识
            # 需要遍历 Registry 找到对应的 spec
            found = False
            for agent_id in AgentRegistry.list_ids():
                spec = AgentRegistry.get(agent_id)
                if spec and spec.run_fn and spec.run_fn.__self__.__class__ == cls:
                    specs.append(spec)
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

        # 层级模式需要 manager_llm
        if self.process == Process.HIERARCHICAL and not self.manager_llm:
            raise ValueError("HIERARCHICAL 模式需要指定 manager_llm")

        # 校验委派约束：can_delegate_to 必须在团队内
        for spec in self._agent_specs:
            for delegate_to in spec.can_delegate_to:
                if delegate_to not in self._agent_ids:
                    logger.warning(
                        f"Agent {spec.id} 的委派目标 {delegate_to} 不在团队内，"
                        f"将被忽略。团队成员: {self._agent_ids}"
                    )

    def _get_executor(self, agent_id: str) -> AgentExecutor:
        """获取执行器（带缓存）"""
        if agent_id not in self._executors:
            spec = AgentRegistry.get(agent_id)
            if not spec:
                raise KeyError(f"Agent {agent_id} 不存在")

            # DYNAMIC 模式：自动设置 can_delegate_to 为团队内其他成员
            if self.process == Process.DYNAMIC:
                spec.can_delegate_to = [aid for aid in self._agent_ids if aid != agent_id]

            self._executors[agent_id] = AgentExecutor(spec)
        return self._executors[agent_id]

    def _build_graph(self) -> StateGraph:
        """构建执行图"""
        if self.process == Process.SEQUENTIAL:
            return self._build_sequential_graph()
        elif self.process == Process.DYNAMIC:
            return self._build_dynamic_graph()
        elif self.process == Process.HIERARCHICAL:
            return self._build_hierarchical_graph()
        elif self.process == Process.PARALLEL:
            return self._build_parallel_graph()
        else:
            raise ValueError(f"不支持的执行模式: {self.process}")

    def _build_sequential_graph(self) -> StateGraph:
        """
        构建顺序执行图

        Agent 1 → Agent 2 → Agent 3 → ... → END
        """
        graph = StateGraph(Blackboard)

        # 为每个 Agent 创建节点
        for spec in self._agent_specs:
            node_fn = self._create_agent_node(spec.id)
            graph.add_node(spec.id, node_fn)

        # 设置入口
        graph.set_entry_point(self._entry_agent_id)

        # 顺序连接
        for i, spec in enumerate(self._agent_specs[:-1]):
            next_spec = self._agent_specs[i + 1]
            graph.add_edge(spec.id, next_spec.id)

        # 最后一个 Agent 连接到 END
        if self._agent_specs:
            graph.add_edge(self._agent_specs[-1].id, END)

        return graph

    def _build_dynamic_graph(self) -> StateGraph:
        """
        构建动态执行图

        Agent 自主决定是否委派，通过 Command(goto=...) 跳转。
        """
        graph = StateGraph(Blackboard)

        # 为每个 Agent 创建节点，声明可跳转的目标
        for spec in self._agent_specs:
            node_fn = self._create_agent_node(spec.id)
            # destinations 告诉 LangGraph 这个节点可以通过 Command(goto=...) 跳转到哪些目标
            other_agents = tuple(aid for aid in self._agent_ids if aid != spec.id)
            graph.add_node(spec.id, node_fn, destinations=other_agents)

        # 条件入口：根据 active_agent 路由
        route_map = {agent_id: agent_id for agent_id in self._agent_ids}
        route_map["end"] = END
        graph.set_conditional_entry_point(self._route_by_active_agent, route_map)

        # 每个 Agent 执行后路由（作为 fallback，当没有 Command(goto=...) 时）
        for spec in self._agent_specs:
            graph.add_conditional_edges(
                spec.id,
                self._route_after_agent,
                {agent_id: agent_id for agent_id in self._agent_ids} | {"end": END},
            )

        return graph

    def _build_hierarchical_graph(self) -> StateGraph:
        """
        构建层级执行图

        Manager 协调分配任务。
        """
        # TODO: 实现层级模式
        raise NotImplementedError("HIERARCHICAL 模式尚未实现")

    def _build_parallel_graph(self) -> StateGraph:
        """
        构建并行执行图

        分析依赖，并行执行无依赖任务。
        """
        # TODO: 实现并行模式
        raise NotImplementedError("PARALLEL 模式尚未实现")

    def _route_by_active_agent(self, state: Blackboard) -> str:
        """根据 active_agent 路由"""
        active = state.get("active_agent")
        if active and active in self._agent_ids:
            return active
        return "end"

    def _route_after_agent(self, state: Blackboard) -> str:
        """Agent 执行后的路由"""
        active = state.get("active_agent")
        if active and active in self._agent_ids:
            return active
        return "end"

    def _create_agent_node(self, agent_id: str):
        """创建 Agent 节点"""

        async def agent_node(state: Blackboard) -> Command:
            session_id = state.get("session_id", "")
            user_id = state.get("user_id", "")

            # 从 messages 获取用户输入
            messages = state.get("messages", [])
            query = ""
            for msg in reversed(messages):
                if isinstance(msg, HumanMessage):
                    query = msg.content
                    break

            # 获取执行器
            executor = self._get_executor(agent_id)

            # 加载记忆
            memory = None
            if self.memory:
                memory_data = state.get("memory")
                if memory_data:
                    memory = SessionMemory.model_validate(memory_data)
                else:
                    memory = SessionMemory(session_id=session_id, user_id=user_id)

            # 执行
            result = await executor.execute(
                query=query,
                session_id=session_id,
                memory=memory,
                state=dict(state),
            )

            # 处理 Command（委派）
            if isinstance(result, Command):
                # 检查委派目标是否在团队内
                goto = result.goto
                if goto and goto not in self._agent_ids:
                    logger.warning(
                        f"Agent {agent_id} 试图委派给 {goto}，" f"但该 Agent 不在团队内，忽略委派"
                    )
                    return Command(update={"active_agent": None})
                return result

            # 处理 AgentResult
            return self._handle_result(
                state=state,
                agent_id=agent_id,
                result=result,
                memory=memory,
            )

        return agent_node

    def _handle_result(
        self,
        *,
        state: Blackboard,
        agent_id: str,
        result: Any,
        memory: SessionMemory | None,
    ) -> Command:
        """处理 Agent 结果"""

        update_dict: dict = {}

        # 更新记忆
        if memory and result:
            if hasattr(result, "status"):
                if result.status == "completed":
                    memory.add_agent_handover(
                        from_agent=agent_id,
                        to_agent="system",
                        summary=f"完成: {result.summary or ''}",
                    )
                elif result.status == "failed":
                    memory.add_agent_handover(
                        from_agent=agent_id,
                        to_agent="system",
                        summary=f"失败: {result.error or ''}",
                    )

            update_dict["memory"] = memory.model_dump(mode="json")

        # 决定下一步
        next_agent = self._decide_next(agent_id, result)
        update_dict["active_agent"] = next_agent

        # 顺序模式：自动设置下一个 Agent
        if self.process == Process.SEQUENTIAL:
            current_idx = next(
                (i for i, s in enumerate(self._agent_specs) if s.id == agent_id),
                -1,
            )
            if current_idx >= 0 and current_idx < len(self._agent_specs) - 1:
                next_agent = self._agent_specs[current_idx + 1].id
                update_dict["active_agent"] = next_agent

        # 流程结束时添加最终消息
        if update_dict.get("active_agent") is None and result:
            if hasattr(result, "status") and result.status == "completed":
                final_message = AIMessage(
                    content=result.summary or "完成",
                    additional_kwargs={
                        "deliverable": (
                            result.deliverable.model_dump(mode="json")
                            if hasattr(result.deliverable, "model_dump")
                            else result.deliverable
                        ),
                        "deliverable_type": result.deliverable_type,
                    },
                )
                update_dict["messages"] = [final_message]

        return Command(update=update_dict)

    def _decide_next(self, agent_id: str, result: Any) -> str | None:
        """决定下一个 Agent（动态模式）"""
        if self.process != Process.DYNAMIC:
            return None

        # 失败或需要澄清：暂停
        if hasattr(result, "status") and result.status != "completed":
            return None

        # 动态模式下，委派由 Agent 自己决定（通过 Command）
        return None

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

        委托给 Orchestrator 处理，自带：
        - 断点恢复
        - 经验学习
        - EventBus 事件
        - 会话管理

        参数：
        - query: 用户输入
        - session_id: 会话 ID
        - user_id: 用户 ID
        - task_type: 任务类型（用于经验学习分类）

        返回：
        - SSE 事件流
        """
        async for event in self._orchestrator.stream(
            query=query,
            session_id=session_id,
            user_id=user_id,
            task_type=task_type,
        ):
            yield event

    def _get_agent_name(self, agent_id: str) -> str:
        """获取 Agent 名称"""
        spec = AgentRegistry.get(agent_id)
        return spec.name if spec else agent_id

    async def kickoff(
        self,
        *,
        inputs: dict[str, Any],
        session_id: str | None = None,
        user_id: str | None = None,
    ) -> DatapillarResult:
        """
        同步执行（收集所有结果）

        参数：
        - inputs: 输入参数（需包含 'query' 或 'requirement' 键）
        - session_id: 会话 ID（可选）
        - user_id: 用户 ID（可选）

        返回：
        - DatapillarResult
        """
        import uuid

        query = inputs.get("query") or inputs.get("requirement") or str(inputs)
        session_id = session_id or f"kickoff_{uuid.uuid4().hex[:8]}"
        user_id = user_id or "system"

        start_time = _now_ms()
        final_output = None
        final_summary = ""
        error = None

        try:
            async for event in self.stream(
                query=query,
                session_id=session_id,
                user_id=user_id,
            ):
                event_type = event.get("event")
                if event_type == "result":
                    final_output = event.get("data", {}).get("deliverable")
                    final_summary = event.get("data", {}).get("message", "")
                elif event_type == "error":
                    error = event.get("data", {}).get("detail")

        except Exception as e:
            error = str(e)

        duration_ms = _now_ms() - start_time

        return DatapillarResult(
            success=error is None,
            output=final_output,
            summary=final_summary,
            duration_ms=duration_ms,
            error=error,
        )

    async def compact_session(self, session_id: str, user_id: str) -> dict:
        """
        手动压缩会话记忆

        类似 Claude Code 的 /compact 命令。

        参数：
        - session_id: 会话 ID
        - user_id: 用户 ID

        返回：
        - 压缩结果：
            - success: 是否成功
            - removed_count: 移除的条目数
            - tokens_saved: 节省的 token 数
            - message: 结果消息
        """
        return await self._orchestrator.compact_session(session_id, user_id)

    async def delete_session(self, session_id: str, user_id: str) -> None:
        """
        删除会话

        清理 Checkpoint 和 DeliverableStore 中的数据。

        参数：
        - session_id: 会话 ID
        - user_id: 用户 ID
        """
        await self._orchestrator.delete_session(session_id, user_id)

    async def get_session_stats(self, session_id: str, user_id: str) -> dict:
        """
        获取会话统计信息

        参数：
        - session_id: 会话 ID
        - user_id: 用户 ID

        返回：
        - 统计信息
        """
        return await self._orchestrator.get_session_stats(session_id, user_id)

    def __repr__(self) -> str:
        return (
            f"Datapillar(name={self.name!r}, "
            f"agents={[s.id for s in self._agent_specs]}, "
            f"process={self.process.value})"
        )
