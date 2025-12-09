import json
from typing import Dict, Any, List, Optional
import logging

logger = logging.getLogger(__name__)
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
from langchain_core.messages import AIMessage, ToolMessage, SystemMessage
from langgraph.graph import StateGraph, END
from langgraph.prebuilt import ToolNode
from langgraph.types import interrupt, Command

from src.agent.state import OrchestratorState
from src.agent.schemas import RequirementOutput, PlanOutput
from src.integrations.llm import call_llm
from src.agent.tools import search_assets, get_table_lineage, list_component

# ============ 常量定义 ============
PLANNER_TOOLS = [search_assets, get_table_lineage, list_component]

class PlannerAgent:
    def __init__(self):
        # 1. 准备 Schema 字符串 (为了注入 Prompt)
        # 转义花括号 {{ }} 以避免 PromptTemplate 格式化报错
        self.req_schema_str = json.dumps(RequirementOutput.model_json_schema(), ensure_ascii=False, indent=2).replace("{", "{{").replace("}", "}}")
        self.plan_schema_str = json.dumps(PlanOutput.model_json_schema(), ensure_ascii=False, indent=2).replace("{", "{{").replace("}", "}}")

        # 2. 初始化 LLM
        self.llm = call_llm(temperature=0.0)
        self.llm_with_tools = self.llm.bind_tools(PLANNER_TOOLS)
        
        # 3. 绑定结构化输出 (用于最终生成)
        # 注意：这里只负责"强制格式"，Prompt 里的 Schema 负责"业务指导"
        self.llm_req_output = self.llm.with_structured_output(RequirementOutput)
        self.llm_plan_output = self.llm.with_structured_output(PlanOutput)

        # 4. 定义 Prompt 模板 (包含 Schema)
        self._init_prompts()

    def _init_prompts(self):
        # Phase 1: 需求分析 Prompt
        self.prompt_req = ChatPromptTemplate.from_messages([
            ("system", f"""
            你是 Data AI Builder 的数仓架构师 SunnyX6。

            ## 任务
            理解用户的 ETL 需求，调用工具获取表详情（列、血缘关系）。

            ## 目标输出格式
            在收集完信息后，你需要填充以下 JSON 结构（RequirementOutput）：
            ---------------------------------------------------
            {self.req_schema_str}
            ---------------------------------------------------

            ## 工作流程
            1. 思考需要填充上述 JSON 中的哪些字段（如 source_table, target_table）。
            2. 调用 `search_assets` 识别用户提到的表。
            3. 调用 `get_table_lineage` 获取表的详细列信息。
            4. 只有当信息收集完整后，停止工具调用。
            """),
            MessagesPlaceholder("messages"),
        ])

        # Phase 3: 计划生成 Prompt
        self.prompt_plan = ChatPromptTemplate.from_messages([
            ("system", f"""
            你是 Data AI Builder 的数仓架构师 SunnyX6。

            ## 任务
            基于用户确认的配置，生成 ETL 执行计划。

            ## 目标输出格式
            你需要输出符合以下 Schema 的执行计划（PlanOutput）：
            ---------------------------------------------------
            {self.plan_schema_str}
            ---------------------------------------------------

            ## 工作流程
            1. 分析用户的确认配置。
            2. 调用 `list_component` 获取可用组件的 `config_schema`。
            3. 按照组件的 schema 填充节点的 `config` 字段。
            4. 生成最终 JSON。
            """),
            ("user", "用户已确认配置数据：\n{user_conf_str}"),
            MessagesPlaceholder("messages"),
        ])

    async def __call__(self, state: OrchestratorState) -> Command:
        """
        核心调度逻辑
        """
        messages = state.messages

        # === Phase 1: 需求分析 (ReAct) ===
        if not state.requirement:
            return await self._phase_analyze_requirement(messages)

        # === Phase 3: 已完成 Phase 2，直接生成计划 ===
        # 检查是否已经进入 Phase 3（通过检查最后一条消息）
        last_message = messages[-1] if messages else None
        if isinstance(last_message, ToolMessage) and last_message.name == "list_component":
            logger.info("🔄 [Phase 3] 继续生成计划（从工具返回）")
            # 从历史消息中找到用户确认数据（SystemMessage）
            user_confirmation = {}
            for msg in reversed(messages):
                if isinstance(msg, SystemMessage) and "用户确认" in msg.content:
                    try:
                        user_confirmation = json.loads(msg.content.split("用户确认数据：")[1])
                        break
                    except (IndexError, json.JSONDecodeError):
                        pass
            return await self._phase_generate_plan(messages, user_confirmation)

        # === Phase 2: 人机协同 (Interrupt) ===
        # 提取推荐配置
        recommended_data = self._extract_lineage_data(messages, state.requirement)

        logger.info(f"⏸️ [Phase 2] 触发人机确认，推送到前端的数据条数: {len(recommended_data.get('column_mappings', []))}")

        # 🔥 中断点：等待用户确认
        # 如果是 Resume，这里直接返回用户修改后的数据
        user_confirmation = interrupt(recommended_data)

        logger.info("✅ [Phase 2] 收到用户确认数据，继续规划")

        # === Phase 3: 生成计划 (ReAct) ===
        # 在消息中添加用户确认数据（供后续使用）
        confirmation_msg = SystemMessage(content=f"用户确认数据：{json.dumps(user_confirmation, ensure_ascii=False)}")

        return await self._phase_generate_plan([*messages, confirmation_msg], user_confirmation)

    async def _phase_analyze_requirement(self, messages: List) -> Command:
        """阶段1：调用工具 -> 填充 Requirement Schema"""
        # 1. 思考与工具调用
        chain = self.prompt_req | self.llm_with_tools
        result = await chain.ainvoke({"messages": messages})

        # 决定：继续调工具 OR 结束
        if result.tool_calls:
            logger.info(f"🔍 [Phase 1] LLM 决定调用工具: {len(result.tool_calls)}")
            return Command(update={"messages": [result]}, goto="planner_tools")
        
        # 2. 信息足够，生成最终对象
        logger.info("✅ [Phase 1] 信息收集完毕，生成 RequirementOutput")
        
        # 使用结构化输出模型进行最后一次生成，确保格式绝对正确
        chain_output = self.prompt_req | self.llm_req_output
        requirement_obj = await chain_output.ainvoke({"messages": messages})
        
        return Command(
            update={
                "messages": [AIMessage(content=f"需求分析完成: {requirement_obj.summary}")],
                "requirement": requirement_obj.model_dump()
            },
            # 自旋跳转到自己，下一轮会自动进入 Phase 2
            goto="planner_llm"
        )

    async def _phase_generate_plan(self, messages: List, user_conf: Dict) -> Command:
        """阶段3：调用 list_component -> 填充 Plan Schema"""
        input_vars = {
            "messages": messages,
            "user_conf_str": json.dumps(user_conf, ensure_ascii=False)
        }

        # 1. 思考与工具调用 (主要是 list_component)
        chain = self.prompt_plan | self.llm_with_tools
        result = await chain.ainvoke(input_vars)

        if result.tool_calls:
            logger.info(f"🔍 [Phase 3] 查询组件库...")
            return Command(update={"messages": [result]}, goto="planner_tools")

        # 2. 生成最终计划
        logger.info("✅ [Phase 3] 生成最终执行计划")
        chain_output = self.prompt_plan | self.llm_plan_output
        plan_obj = await chain_output.ainvoke(input_vars)

        return Command(
            update={
                "messages": [AIMessage(content=f"已生成计划: {plan_obj.workflowName}")],
                "plan": plan_obj.model_dump(),
                "current_agent": "planner_agent",
                "is_found": True # 标记任务结束
            },
            goto=END
        )

    def _extract_lineage_data(self, messages: List, requirement: Dict) -> Dict:
        """从历史 ToolMessage 提取血缘数据，生成推荐配置给前端"""

        # 遍历消息历史，查找 get_table_lineage 工具的返回结果
        source_table_info = None
        target_table_info = None
        lineage_info = None

        logger.info(f"🔍 开始提取血缘数据，消息总数: {len(messages)}")

        for msg in reversed(messages):
            if isinstance(msg, ToolMessage):
                tool_name = msg.name
                logger.info(f"  发现工具消息: {tool_name}")

                # 解析工具返回的内容
                try:
                    tool_result = json.loads(msg.content) if isinstance(msg.content, str) else msg.content
                    logger.info(f"    内容类型: {type(tool_result)}, 键: {tool_result.keys() if isinstance(tool_result, dict) else 'N/A'}")
                except (json.JSONDecodeError, TypeError) as e:
                    logger.warning(f"    解析工具结果失败: {e}")
                    continue

                # 从 get_table_lineage 提取数据
                if tool_name == "get_table_lineage":
                    # 双表模式（source + target）
                    if "source_table" in tool_result and "target_table" in tool_result:
                        lineage_info = tool_result
                        logger.info(f"找到双表血缘数据: {lineage_info.get('source_table', {}).get('name')} -> {lineage_info.get('target_table', {}).get('name')}")
                    # 单表模式
                    elif "table" in tool_result and not target_table_info:
                        table_data = tool_result.get("table", {})
                        table_name = table_data.get("name", "")
                        # 判断是 source 还是 target（简单规则：ODS/DWD/DWS 视为target，其他视为source）
                        if any(prefix in table_name.lower() for prefix in ["ods", "dwd", "dws"]):
                            target_table_info = tool_result
                            logger.info(f"找到目标表信息: {table_name}")
                        elif not source_table_info:
                            source_table_info = tool_result
                            logger.info(f"找到源表信息: {table_name}")

        # 构建推荐数据
        recommended_data = {}

        # 优先使用双表血缘数据
        if lineage_info:
            # 提取源表和目标表信息
            source_table = lineage_info.get("source_table", {})
            target_table = lineage_info.get("target_table", {})
            source_cols = source_table.get("columns", [])
            target_cols = target_table.get("columns", [])

            # 提取列映射
            column_lineage = lineage_info.get("column_lineage", [])
            column_mappings = []

            # 使用已有的 column_lineage
            if column_lineage:
                for mapping in column_lineage:
                    column_mappings.append({
                        "source_column": mapping.get("source_column"),
                        "target_column": mapping.get("target_column"),
                        "transformation_type": mapping.get("transformation_type", "direct")
                    })
            else:
                # 简单映射：按名称匹配
                for src_col in source_cols:
                    src_name = src_col.get("name", "")
                    # 查找同名目标列
                    for tgt_col in target_cols:
                        tgt_name = tgt_col.get("name", "")
                        if src_name == tgt_name or src_name.replace("_", "") == tgt_name.replace("_", ""):
                            column_mappings.append({
                                "source_column": src_name,
                                "target_column": tgt_name,
                                "transformation_type": "direct"
                            })
                            break

            recommended_data = {
                "source_table": source_table.get("name"),
                "target_table": target_table.get("name"),
                "column_mappings": column_mappings
            }

        # 否则，使用单表信息
        elif source_table_info and target_table_info:
            # 简单名称匹配
            source_table_data = source_table_info.get("table", {})
            target_table_data = target_table_info.get("table", {})
            source_cols = source_table_data.get("columns", [])
            target_cols = target_table_data.get("columns", [])

            column_mappings = []
            for src_col in source_cols:
                src_name = src_col.get("name", "")
                for tgt_col in target_cols:
                    tgt_name = tgt_col.get("name", "")
                    if src_name == tgt_name or src_name.replace("_", "") == tgt_name.replace("_", ""):
                        column_mappings.append({
                            "source_column": src_name,
                            "target_column": tgt_name,
                            "transformation_type": "direct"
                        })
                        break

            recommended_data = {
                "source_table": source_table_data.get("name"),
                "target_table": target_table_data.get("name"),
                "column_mappings": column_mappings
            }

        logger.info(f"提取的推荐数据: source={recommended_data.get('source_table')}, "
                   f"target={recommended_data.get('target_table')}, "
                   f"mappings={len(recommended_data.get('column_mappings', []))}")

        return recommended_data

# ============ 子图构建 ============

def build_planner_subgraph():
    builder = StateGraph(OrchestratorState)
    
    # 节点
    builder.add_node("planner_llm", PlannerAgent())
    builder.add_node("planner_tools", ToolNode(PLANNER_TOOLS))
    
    # 边
    builder.set_entry_point("planner_llm")
    builder.add_edge("planner_tools", "planner_llm")
    
    return builder.compile()