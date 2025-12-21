"""
Knowledge Agent（知识检索专家）

通过 LLM + Tools 的方式从 Neo4j 知识库检索信息。
LLM 自主决定调用哪些工具，支持多轮检索。
"""

import json
import logging
from typing import List, Dict, Any, Optional

from langchain_core.messages import AIMessage, HumanMessage, ToolMessage
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
from langgraph.types import Command

from src.modules.etl.schemas.state import AgentState
from src.modules.etl.schemas.kg_context import (
    KnowledgeContext,
    TableSchema,
    ColumnInfo,
    TableLineage,
    JoinHint,
    BusinessContext,
    ReferenceCase,
    Component,
)
from src.modules.etl.memory import MemoryManager
from src.infrastructure.llm.client import call_llm
from src.modules.etl.tools.agent_tools import (
    search_assets,
    get_table_lineage,
    kg_join_hints,
    kg_quality_rules,
    search_reference_cases,
    list_component,
)

logger = logging.getLogger(__name__)

# 知识检索工具列表
KNOWLEDGE_TOOLS = [
    search_assets,
    get_table_lineage,
    kg_join_hints,
    kg_quality_rules,
    search_reference_cases,
    list_component,
]

# 系统提示词
KNOWLEDGE_AGENT_PROMPT = """你是数仓知识检索专家，负责从知识库中检索用户所需的表结构、血缘关系、JOIN 关系等信息。

## 你的职责
1. 分析用户查询，理解用户想要处理哪些数据
2. 调用合适的工具检索相关知识
3. 根据检索结果决定是否需要补充查询
4. 检索历史成功案例，为后续 SQL 生成提供参考
5. 获取可用组件列表，为后续架构设计提供约束
6. 整理检索结果，标注知识缺口

## 可用工具
- search_assets: 搜索相关表（向量+全文检索），返回表名、列信息、业务上下文
- get_table_lineage: 获取表的详细信息和血缘关系
- kg_join_hints: 获取表的 JOIN 关系（左表、右表、关联字段）
- kg_quality_rules: 获取表的数据质量规则
- search_reference_cases: 检索历史成功的 ETL 案例，获取可复用的 SQL 模板
- list_component: 【必须调用】获取企业支持的大数据组件列表（datax/hive/spark/flink等）

## 检索策略
1. 首先调用 search_assets 找到与用户查询相关的表
2. 如果找到多个表，调用 kg_join_hints 获取它们的 JOIN 关系
3. 如果用户提到了源表和目标表，调用 get_table_lineage 获取血缘
4. 如果涉及数据质量，调用 kg_quality_rules 获取 DQ 规则
5. 调用 search_reference_cases 检索历史成功案例
6. 【必须】调用 list_component 获取可用组件列表

## 注意事项
- 每次调用工具后，分析结果是否足够
- 如果信息不足，继续调用其他工具补充
- 最多调用 6 次工具
- 如果无法找到相关表，明确告知用户
- list_component 必须调用，否则后续架构设计无法进行

## 当前用户查询
{user_query}
"""


class KnowledgeAgent:
    """
    知识检索专家（Tool-based）

    使用 LLM + Tools 的方式检索知识：
    1. LLM 分析用户查询，决定检索策略
    2. LLM 调用工具执行检索
    3. LLM 根据结果决定是否需要补充查询
    4. 整合结果构建 KnowledgeContext
    """

    def __init__(self, memory: MemoryManager):
        self.memory = memory
        self.llm = call_llm(temperature=0.0)
        self.llm_with_tools = self.llm.bind_tools(KNOWLEDGE_TOOLS)
        self.max_tool_calls = 6

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
            # 执行 Tool-based 检索
            tool_results = await self._execute_tool_loop(user_query)

            # 解析工具调用结果，构建 KnowledgeContext
            context = self._build_context_from_results(user_query, tool_results)

            # 缓存到 Memory
            if context.tables:
                self.memory.cache_tables(context.tables)

            logger.info(
                f"✅ KnowledgeAgent 完成检索: {len(context.tables)} 表, "
                f"{len(context.join_hints)} JOIN, {len(context.gaps)} 缺口"
            )

            return Command(
                update={
                    "messages": [AIMessage(content=f"知识检索完成，找到 {len(context.tables)} 个相关表")],
                    "knowledge_context": context.model_dump(),
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

    async def _execute_tool_loop(self, user_query: str) -> Dict[str, Any]:
        """
        执行 Tool 调用循环

        LLM 自主决定调用哪些工具，最多调用 max_tool_calls 次。

        Returns:
            {"search_results": [...], "lineage_results": [...], "join_results": [...], "dq_results": [...], "case_results": [...]}
        """
        results = {
            "search_results": [],
            "lineage_results": [],
            "join_results": [],
            "dq_results": [],
            "case_results": [],
            "component_results": [],
        }

        # 初始消息
        messages = [
            HumanMessage(content=KNOWLEDGE_AGENT_PROMPT.format(user_query=user_query))
        ]

        tool_call_count = 0

        while tool_call_count < self.max_tool_calls:
            # 调用 LLM，让它决定下一步行动
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

                logger.info(f"🔧 调用工具 [{tool_call_count}/{self.max_tool_calls}]: {tool_name}({tool_args})")

                # 执行工具
                tool_result = await self._execute_tool(tool_name, tool_args)

                # 记录结果
                self._record_tool_result(results, tool_name, tool_result)

                # 添加工具结果到消息列表
                messages.append(ToolMessage(
                    content=tool_result,
                    tool_call_id=tool_id,
                ))

                if tool_call_count >= self.max_tool_calls:
                    logger.info(f"已达到最大工具调用次数 {self.max_tool_calls}")
                    break

        return results

    async def _execute_tool(self, tool_name: str, tool_args: Dict[str, Any]) -> str:
        """执行单个工具调用"""
        try:
            if tool_name == "search_assets":
                return await search_assets.ainvoke(tool_args)
            elif tool_name == "get_table_lineage":
                return await get_table_lineage.ainvoke(tool_args)
            elif tool_name == "kg_join_hints":
                return await kg_join_hints.ainvoke(tool_args)
            elif tool_name == "kg_quality_rules":
                return await kg_quality_rules.ainvoke(tool_args)
            elif tool_name == "search_reference_cases":
                return await search_reference_cases.ainvoke(tool_args)
            elif tool_name == "list_component":
                return list_component.invoke(tool_args)
            else:
                return json.dumps({"status": "error", "message": f"未知工具: {tool_name}"})
        except Exception as e:
            logger.error(f"工具 {tool_name} 执行失败: {e}")
            return json.dumps({"status": "error", "message": str(e)})

    def _record_tool_result(self, results: Dict, tool_name: str, tool_result: str) -> None:
        """记录工具调用结果"""
        try:
            data = json.loads(tool_result)
            if data.get("status") == "error":
                logger.warning(f"工具 {tool_name} 返回错误: {data.get('message')}")
                return

            if tool_name == "search_assets":
                results["search_results"].extend(data.get("tables", []))
            elif tool_name == "get_table_lineage":
                results["lineage_results"].append(data)
            elif tool_name == "kg_join_hints":
                results["join_results"].extend(data.get("join_keys", []))
            elif tool_name == "kg_quality_rules":
                results["dq_results"].extend(data.get("dq_rules", []))
            elif tool_name == "search_reference_cases":
                results["case_results"].extend(data.get("cases", []))
            elif tool_name == "list_component":
                results["component_results"].extend(data.get("components", []))

        except json.JSONDecodeError:
            logger.error(f"工具 {tool_name} 返回无效 JSON: {tool_result}")

    def _build_context_from_results(self, user_query: str, results: Dict) -> KnowledgeContext:
        """从工具调用结果构建 KnowledgeContext（精简版）"""

        # 1️⃣ 构建表信息（精简版）
        tables: Dict[str, TableSchema] = {}

        for table_data in results["search_results"]:
            table_name = table_data.get("table_name")
            if not table_name:
                continue

            # 提取列信息
            all_columns = table_data.get("columns", [])

            # 分离主键列和普通列
            pk_columns = [c for c in all_columns if c.get("isPrimaryKey")]
            other_columns = [c for c in all_columns if not c.get("isPrimaryKey")]

            # 只保留主键 + 前 10 个普通列
            selected_columns = pk_columns + other_columns[:10]

            key_columns = [
                ColumnInfo(
                    name=c.get("name", ""),
                    data_type=c.get("dataType", "string"),
                    description=c.get("description"),
                    is_primary_key=c.get("isPrimaryKey", False),
                )
                for c in selected_columns
                if c.get("name")
            ]

            # 提取业务上下文
            biz_ctx = table_data.get("business_context", {})

            tables[table_name] = TableSchema(
                name=table_name,
                display_name=table_data.get("table_display_name"),
                description=table_data.get("description"),
                key_columns=key_columns,
                column_count=len(all_columns),
                layer=biz_ctx.get("layer"),
                schema_name=biz_ctx.get("schema"),
                subject_name=biz_ctx.get("subject"),
                catalog_name=biz_ctx.get("catalog"),
                domain_name=biz_ctx.get("domain"),
            )

        # 2️⃣ 构建表级血缘（只保留表级，不保留列级）
        table_lineage: List[TableLineage] = []

        for lineage_data in results["lineage_results"]:
            source_table = lineage_data.get("source_table", {}).get("name")
            target_table = lineage_data.get("target_table", {}).get("name")

            if source_table and target_table:
                table_lineage.append(TableLineage(
                    source_table=source_table,
                    target_table=target_table,
                    confidence=0.8 if lineage_data.get("has_lineage") else 0.5,
                ))

        # 从 search_results 中提取表级血缘
        for table_data in results["search_results"]:
            source = table_data.get("table_name")
            for target in table_data.get("downstream_lineage", []):
                if source and target:
                    # 避免重复
                    if not any(l.source_table == source and l.target_table == target for l in table_lineage):
                        table_lineage.append(TableLineage(
                            source_table=source,
                            target_table=target,
                            confidence=0.7,
                        ))

        # 3️⃣ 构建 JOIN 信息
        join_hints: List[JoinHint] = []
        for join_data in results["join_results"]:
            if join_data.get("left_table") and join_data.get("right_table"):
                join_hints.append(JoinHint(
                    left_table=join_data["left_table"],
                    left_column=join_data.get("left_column", ""),
                    right_table=join_data["right_table"],
                    right_column=join_data.get("right_column", ""),
                    join_type=join_data.get("join_type", "LEFT"),
                ))

        # 4️⃣ 提取业务上下文
        business_context = None
        if tables:
            first_table = list(tables.values())[0]
            business_context = BusinessContext(
                domain=first_table.domain_name,
                catalog=first_table.catalog_name,
                subject=first_table.subject_name,
                schema=first_table.schema_name,
                layer=first_table.layer,
            )

        # 5️⃣ 识别知识缺口
        gaps = self._identify_gaps(tables, join_hints, table_lineage)

        # 6️⃣ 构建历史参考案例
        reference_cases: List[ReferenceCase] = []
        for case_data in results.get("case_results", []):
            if case_data.get("case_id"):
                reference_cases.append(ReferenceCase(
                    case_id=case_data["case_id"],
                    user_query=case_data.get("user_query", ""),
                    sql_text=case_data.get("sql_text"),
                    intent=case_data.get("intent", ""),
                    source_tables=case_data.get("source_tables", []),
                    target_tables=case_data.get("target_tables", []),
                    tags=case_data.get("tags", []),
                ))

        if reference_cases:
            logger.info(f"📚 找到 {len(reference_cases)} 个历史参考案例")

        # 7️⃣ 构建组件列表
        components: List[Component] = []
        for comp_data in results.get("component_results", []):
            if comp_data.get("component_id"):
                components.append(Component(
                    component_id=comp_data["component_id"],
                    component_name=comp_data.get("component_name", ""),
                    description=comp_data.get("description"),
                ))

        if components:
            logger.info(f"📦 找到 {len(components)} 个可用组件")

        return KnowledgeContext(
            tables=tables,
            table_lineage=table_lineage,
            join_hints=join_hints,
            business_context=business_context,
            reference_cases=reference_cases,
            components=components,
            gaps=gaps,
        )

    def _identify_gaps(
        self,
        tables: Dict[str, TableSchema],
        join_hints: List[JoinHint],
        table_lineage: List[TableLineage],
    ) -> List[str]:
        """识别知识缺口"""
        gaps = []

        if not tables:
            gaps.append("未找到与查询相关的表，请确认表名或描述")
            return gaps

        # 推断源表和目标表
        source_tables = [name for name, t in tables.items() if t.layer in ("SRC", "ODS")]
        target_tables = [name for name, t in tables.items() if t.layer in ("DWD", "DWS", "ADS")]

        if not source_tables:
            gaps.append("未识别到明确的源表，请确认数据来源")

        if not target_tables:
            gaps.append("未识别到明确的目标表，请确认数据目标")

        # 多表场景检查 JOIN
        if len(tables) > 1 and not join_hints:
            gaps.append("多表场景但未找到 JOIN 关系，请确认表关联方式")

        # 检查主键（通过 key_columns 中的 is_primary_key 判断）
        tables_without_pk = [
            name for name, t in tables.items()
            if not any(col.is_primary_key for col in t.key_columns)
        ]
        if tables_without_pk and len(tables_without_pk) < len(tables):
            gaps.append(f"部分表缺少主键信息: {', '.join(tables_without_pk[:3])}")

        return gaps
