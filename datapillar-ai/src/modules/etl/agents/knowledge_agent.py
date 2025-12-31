"""
Knowledge Agent（知识检索专家）

职责：
1. 加载全局知识图谱上下文（GlobalKGContext）
2. 根据用户查询，使用工具发现相关表
3. 为每个 Agent 准备专属上下文（AgentScopedContext）

设计原则：
- 全局上下文只做导航，不存储细节
- 细节由各 Agent 通过工具按需查询
- 工具也是上下文的一部分
"""

import asyncio
import json
import logging

from langchain_core.messages import AIMessage, HumanMessage, ToolMessage
from langgraph.types import Command

from src.infrastructure.llm.client import call_llm
from src.infrastructure.repository import ComponentRepository, KnowledgeRepository
from src.modules.etl.schemas.kg_context import (
    AgentScopedContext,
    AgentType,
    CatalogNav,
    ComponentNav,
    GlobalKGContext,
    LineageEdge,
    SchemaNav,
    TableNav,
)
from src.modules.etl.schemas.state import AgentState
from src.modules.etl.tools.agent_tools import SEARCH_TOOLS

logger = logging.getLogger(__name__)

# 系统提示词（只用于发现相关表）
KNOWLEDGE_DISCOVERY_PROMPT = """你是数仓知识检索专家。

## 任务
根据用户需求，从知识库中发现相关的表。

## 全局上下文
{global_context_summary}

## 可用工具
- search_assets: 语义搜索数据资产，查找相关表

## 用户需求
{user_query}

## 要求
1. 分析用户需求，理解需要哪些表
2. 使用 search_assets 工具搜索相关表
3. 返回发现的表名列表

只需要发现相关表，不需要获取详情。详情由后续 Agent 通过工具获取。
"""


class KnowledgeAgent:
    """
    知识检索专家

    职责：
    1. 加载全局知识图谱上下文
    2. 根据用户查询发现相关表
    3. 为每个 Agent 准备专属上下文（指针 + 工具）
    """

    def __init__(self):
        self.llm = call_llm(temperature=0.0)
        self.llm_with_tools = self.llm.bind_tools(SEARCH_TOOLS)
        self.max_tool_calls = 3

    async def __call__(self, state: AgentState) -> Command:
        """执行知识检索"""
        user_query = state.user_input
        if not user_query:
            return Command(
                update={
                    "messages": [AIMessage(content="缺少用户输入，无法检索知识")],
                    "current_agent": "knowledge_agent",
                    "error": "缺少用户输入",
                }
            )

        logger.info(f"🔍 KnowledgeAgent 开始检索: {user_query}")

        try:
            # 1. 加载全局知识图谱上下文
            global_kg_context = await self._load_global_kg_context()

            logger.info(
                f"📊 全局上下文加载完成: "
                f"{len(global_kg_context.catalogs)} Catalogs, "
                f"{len(global_kg_context.get_all_tables())} Tables, "
                f"{len(global_kg_context.lineage_graph)} 血缘边, "
                f"{len(global_kg_context.components)} 组件"
            )

            # 2. 使用工具发现相关表
            discovered_tables = await self._discover_tables(user_query, global_kg_context)

            logger.info(f"🔎 发现 {len(discovered_tables)} 个相关表: {discovered_tables}")

            # 3. 为每个 Agent 准备专属上下文
            agent_contexts = self._create_agent_contexts(
                user_query=user_query,
                discovered_tables=discovered_tables,
            )

            logger.info(
                f"✅ KnowledgeAgent 完成: "
                f"发现 {len(discovered_tables)} 表, "
                f"创建 {len(agent_contexts)} 个 Agent 上下文"
            )

            return Command(
                update={
                    "messages": [
                        AIMessage(
                            content=f"知识检索完成，发现 {len(discovered_tables)} 个相关表"
                        )
                    ],
                    "global_kg_context": global_kg_context.model_dump(),
                    "agent_contexts": {k: v.model_dump() for k, v in agent_contexts.items()},
                    "current_agent": "knowledge_agent",
                }
            )

        except Exception as e:
            logger.error(f"KnowledgeAgent 检索失败: {e}", exc_info=True)
            return Command(
                update={
                    "messages": [AIMessage(content=f"知识检索失败: {str(e)}")],
                    "current_agent": "knowledge_agent",
                    "error": str(e),
                }
            )

    async def _load_global_kg_context(self) -> GlobalKGContext:
        """
        加载全局知识图谱上下文

        Returns:
            GlobalKGContext 对象
        """
        # 并发加载数据
        catalog_data, lineage_data = await asyncio.gather(
            KnowledgeRepository.load_catalog_hierarchy(),
            KnowledgeRepository.load_table_lineage(),
        )

        # 转换 Catalog 层级结构
        catalogs = []
        for cat_dict in catalog_data:
            schemas = []
            for sch_dict in cat_dict.get("schemas", []):
                tables = [
                    TableNav(
                        name=t.get("name", ""),
                        schema_name=t.get("schema_name", ""),
                        catalog=t.get("catalog", ""),
                        tags=t.get("tags") or [],
                        description=t.get("description"),
                        column_count=t.get("column_count", 0),
                    )
                    for t in sch_dict.get("tables", [])
                ]
                schemas.append(
                    SchemaNav(
                        name=sch_dict.get("name", ""),
                        catalog=sch_dict.get("catalog", ""),
                        description=sch_dict.get("description"),
                        tables=tables,
                    )
                )
            catalogs.append(
                CatalogNav(
                    name=cat_dict.get("name", ""),
                    metalake=cat_dict.get("metalake", ""),
                    schemas=schemas,
                )
            )

        # 转换血缘边
        lineage_graph = [
            LineageEdge(
                source_table=edge.get("source_table", ""),
                target_table=edge.get("target_table", ""),
                sql_id=edge.get("sql_id"),
            )
            for edge in lineage_data
        ]

        # 加载组件列表
        components = self._load_components()

        return GlobalKGContext(
            catalogs=catalogs,
            lineage_graph=lineage_graph,
            components=components,
        )

    def _load_components(self) -> list[ComponentNav]:
        """加载组件列表"""
        try:
            results = ComponentRepository.list_active_components()
            return [
                ComponentNav(
                    id=row.get("id"),
                    code=row.get("component_code", ""),
                    name=row.get("component_name", ""),
                    type=row.get("component_type", ""),
                )
                for row in results
            ]
        except Exception as e:
            logger.error(f"加载组件列表失败: {e}")
            return []

    def _summarize_global_context(self, global_kg_context: GlobalKGContext) -> str:
        """生成全局上下文摘要"""
        lines = ["### 数据资产概览\n"]

        # Catalog/Schema/Table 层级
        for catalog in global_kg_context.catalogs:
            lines.append(f"**Catalog: {catalog.name}** (metalake: {catalog.metalake})")
            for schema in catalog.schemas:
                lines.append(f"  - Schema: {schema.name}")
                for table in schema.tables[:10]:
                    tags_str = ", ".join(table.tags) if table.tags else "无标签"
                    lines.append(
                        f"    - {table.name} ({table.column_count}列) [{tags_str}]"
                    )
                if len(schema.tables) > 10:
                    lines.append(f"    - ...共 {len(schema.tables)} 张表")

        # 血缘关系
        if global_kg_context.lineage_graph:
            lines.append("\n### 表级血缘关系\n")
            for edge in global_kg_context.lineage_graph[:20]:
                lines.append(f"- {edge.source_table} → {edge.target_table}")
            if len(global_kg_context.lineage_graph) > 20:
                lines.append(f"- ...共 {len(global_kg_context.lineage_graph)} 条血缘")

        # 可用组件
        if global_kg_context.components:
            lines.append("\n### 可用组件\n")
            for comp in global_kg_context.components:
                lines.append(f"- {comp.code}: {comp.name} ({comp.type})")

        return "\n".join(lines)

    async def _discover_tables(
        self, user_query: str, global_kg_context: GlobalKGContext
    ) -> list[str]:
        """
        使用工具发现相关表

        Returns:
            发现的表名列表
        """
        discovered_tables: list[str] = []

        # 生成全局上下文摘要
        context_summary = self._summarize_global_context(global_kg_context)

        # 初始消息
        messages = [
            HumanMessage(
                content=KNOWLEDGE_DISCOVERY_PROMPT.format(
                    global_context_summary=context_summary,
                    user_query=user_query,
                )
            )
        ]

        tool_call_count = 0

        while tool_call_count < self.max_tool_calls:
            # 调用 LLM
            response = await self.llm_with_tools.ainvoke(messages)
            messages.append(response)

            # 检查是否有工具调用
            if not response.tool_calls:
                logger.info("LLM 决定停止工具调用")
                break

            # 执行工具调用
            for tool_call in response.tool_calls:
                tool_call_count += 1
                tool_name = tool_call["name"]
                tool_args = tool_call["args"]
                tool_id = tool_call["id"]

                logger.info(
                    f"🔧 调用工具 [{tool_call_count}/{self.max_tool_calls}]: "
                    f"{tool_name}({tool_args})"
                )

                # 执行工具
                tool_result = await self._execute_tool(tool_name, tool_args)

                # 解析结果，收集表名
                self._collect_tables(tool_result, discovered_tables)

                # 添加工具结果到消息列表
                messages.append(
                    ToolMessage(
                        content=tool_result,
                        tool_call_id=tool_id,
                    )
                )

                if tool_call_count >= self.max_tool_calls:
                    logger.info(f"已达到最大工具调用次数 {self.max_tool_calls}")
                    break

        return discovered_tables

    async def _execute_tool(self, tool_name: str, tool_args: dict) -> str:
        """执行单个工具调用"""
        from src.modules.etl.tools.agent_tools import search_assets

        try:
            if tool_name == "search_assets":
                return await search_assets.ainvoke(tool_args)
            else:
                return json.dumps(
                    {"status": "error", "message": f"未知工具: {tool_name}"}
                )
        except Exception as e:
            logger.error(f"工具 {tool_name} 执行失败: {e}")
            return json.dumps({"status": "error", "message": str(e)})

    def _collect_tables(self, tool_result: str, discovered_tables: list[str]) -> None:
        """从工具结果中收集表名"""
        try:
            data = json.loads(tool_result)
            if data.get("status") == "error":
                logger.warning(f"工具返回错误: {data.get('message')}")
                return

            # search_assets 返回的表
            tables = data.get("tables", [])
            for t in tables:
                table_name = t.get("table_name")
                if table_name and table_name not in discovered_tables:
                    discovered_tables.append(table_name)

        except json.JSONDecodeError:
            logger.error(f"工具返回无效 JSON: {tool_result}")

    def _create_agent_contexts(
        self,
        user_query: str,
        discovered_tables: list[str],
    ) -> dict[str, AgentScopedContext]:
        """
        为每个 Agent 创建专属上下文

        每个 Agent 的上下文包含：
        - 可访问的表名列表（指针）
        - 可用的工具列表
        - 用户需求

        详情由各 Agent 自己通过工具获取。
        """
        contexts = {}

        # AnalystAgent - 需求分析
        contexts[AgentType.ANALYST] = AgentScopedContext.create_for_agent(
            agent_type=AgentType.ANALYST,
            tables=discovered_tables,
            user_query=user_query,
        )

        # ArchitectAgent - 架构设计
        contexts[AgentType.ARCHITECT] = AgentScopedContext.create_for_agent(
            agent_type=AgentType.ARCHITECT,
            tables=discovered_tables,
            user_query=user_query,
        )

        # DeveloperAgent - 代码开发
        contexts[AgentType.DEVELOPER] = AgentScopedContext.create_for_agent(
            agent_type=AgentType.DEVELOPER,
            tables=discovered_tables,
            user_query=user_query,
        )

        # TesterAgent - 测试验证
        contexts[AgentType.TESTER] = AgentScopedContext.create_for_agent(
            agent_type=AgentType.TESTER,
            tables=discovered_tables,
            user_query=user_query,
        )

        return contexts
