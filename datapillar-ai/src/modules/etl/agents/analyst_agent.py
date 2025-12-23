"""
Analyst Agent（需求分析师）

职责：业务层面的需求分析与收敛
- 将用户需求拆分为业务步骤（Step）
- 基于知识库验证需求的可行性
- 需求必须在此阶段收敛清楚，不允许模糊需求往后传
- 通过工具验证表是否存在
"""

import json
import logging
import re

from langchain_core.messages import AIMessage, HumanMessage, ToolMessage
from langgraph.types import Command

from src.infrastructure.llm.client import call_llm
from src.modules.etl.schemas.kg_context import AgentScopedContext, AgentType, GlobalKGContext
from src.modules.etl.schemas.requirement import AnalysisResult, Ambiguity, DataTarget, Step
from src.modules.etl.schemas.state import AgentState
from src.modules.etl.tools.agent_tools import search_assets, get_table_columns

logger = logging.getLogger(__name__)


ANALYST_AGENT_PROMPT = """你是资深数据需求分析师。

## 任务
根据知识上下文，将用户需求收敛为明确的业务步骤。

## 核心原则
1. **需求必须收敛** - 每个 Step 必须有明确的输入表和输出表
2. **基于知识库** - 源头输入表和最终目标表必须在知识库中存在
3. **拒绝模糊需求** - 如果需求无法收敛，必须返回具体问题让用户澄清

## 知识上下文

### 可用的表（KnowledgeAgent 发现的相关表）
{discovered_tables}

### 全局表清单（导航）
{tables_summary}

### 表级血缘（已有的数据流向）
{lineage_summary}

### 可用工具
{tools_description}

## 用户需求
{user_query}

## 分析要求

0. **知识库为空检查**：
   - 如果"可用的表"和"全局表清单"都为空，说明知识库尚未初始化
   - 此时必须直接返回提示：
     - summary: "当前知识库为空，无法处理您的需求"
     - steps: []
     - ambiguities: 包含一条建议，说明需要先在 Gravitino 中创建数据表并同步元数据
     - confidence: 0

1. **验证可行性**：
   - 用户提到的**源头输入表**必须在知识库中存在
   - 用户提到的**最终目标表**必须在知识库中存在
   - 如果不确定表是否存在，使用 search_assets 或 get_table_columns 工具验证

2. **明确输入输出**：
   - 每个 Step 必须有 input_tables（从哪读）
   - 每个 Step 必须有 output_table（写到哪）

3. **需求收敛**：
   - 如果需求模糊（如"处理用户数据"），必须要求用户明确
   - 如果缺少关键信息（如目标表），必须要求用户补充
   - confidence < 0.7 时，必须有 ambiguities

## 输出格式

```json
{{
  "summary": "一句话概括需求（必须具体，不能模糊）",
  "steps": [
    {{
      "step_id": "step_1",
      "step_name": "业务步骤名称",
      "description": "这一步做什么（业务描述）",
      "input_tables": ["schema.table"],
      "output_table": "schema.table",
      "depends_on": []
    }}
  ],
  "final_target": {{
    "table_name": "最终目标表（必须明确）",
    "write_mode": "overwrite",
    "partition_by": ["dt"]
  }},
  "ambiguities": [
    {{
      "question": "需要用户澄清的具体问题",
      "context": "为什么需要澄清",
      "options": ["可能的选项1", "可能的选项2"]
    }}
  ],
  "confidence": 0.85
}}
```

重要：
- 如果知识库中没有用户提到的源头输入表或最终目标表，直接返回提示，confidence 设为 0
- 如果无法明确 input_tables 或 output_table，必须在 ambiguities 中提问
- confidence 反映需求的明确程度，模糊需求必须 < 0.7

只输出 JSON，不要解释。
"""


# 绑定的工具
ANALYST_TOOLS = [search_assets, get_table_columns]


class AnalystAgent:
    """
    需求分析师

    职责：
    1. 基于知识库收敛用户需求
    2. 通过工具验证涉及的表是否存在
    3. 需求不明确时强制要求澄清
    4. 不允许模糊需求往后传
    """

    def __init__(self):
        self.llm = call_llm(temperature=0.0)
        self.llm_with_tools = self.llm.bind_tools(ANALYST_TOOLS)
        self.max_tool_calls = 4

    async def __call__(self, state: AgentState) -> Command:
        """执行需求分析"""
        user_query = state.user_input

        if not user_query:
            return Command(
                update={
                    "messages": [AIMessage(content="缺少用户输入，无法分析需求")],
                    "current_agent": "analyst_agent",
                    "error": "缺少用户输入",
                }
            )

        logger.info(f"📋 AnalystAgent 开始分析需求: {user_query}")

        try:
            # 获取上下文
            global_kg_context = state.get_global_kg_context()
            agent_context = state.get_agent_context(AgentType.ANALYST)

            if not global_kg_context:
                global_kg_context = GlobalKGContext()

            if not agent_context:
                agent_context = AgentScopedContext.create_for_agent(
                    agent_type=AgentType.ANALYST,
                    tables=[],
                    user_query=user_query,
                )

            # 格式化上下文
            context_info = self._format_context(global_kg_context, agent_context)

            # 执行分析（带工具调用）
            result_dict = await self._analyze_with_tools(
                user_query=user_query,
                context_info=context_info,
                agent_context=agent_context,
            )

            analysis_result = self._build_analysis_result(result_dict, user_query)

            # 基于知识库验证需求是否收敛
            validation_issues = await self._validate_convergence(
                analysis_result, global_kg_context, agent_context
            )
            if validation_issues:
                for issue in validation_issues:
                    analysis_result.ambiguities.append(issue)
                analysis_result.confidence = min(analysis_result.confidence, 0.5)

            plan_summary = analysis_result.get_execution_plan_summary()
            logger.info(f"✅ AnalystAgent 完成分析:\n{plan_summary}")

            # 需求不明确时，强制要求澄清
            if analysis_result.needs_clarification() or analysis_result.confidence < 0.7:
                questions = [a.question for a in analysis_result.ambiguities]
                return Command(
                    update={
                        "messages": [AIMessage(content="需求不够明确，请补充以下信息")],
                        "analysis_result": analysis_result.model_dump(),
                        "current_agent": "analyst_agent",
                        "needs_clarification": True,
                        "clarification_questions": questions,
                    }
                )

            return Command(
                update={
                    "messages": [AIMessage(content=f"需求分析完成: {analysis_result.summary}")],
                    "analysis_result": analysis_result.model_dump(),
                    "current_agent": "analyst_agent",
                }
            )

        except Exception as e:
            logger.error(f"AnalystAgent 分析失败: {e}", exc_info=True)
            return Command(
                update={
                    "messages": [AIMessage(content=f"需求分析失败: {str(e)}")],
                    "current_agent": "analyst_agent",
                    "error": str(e),
                }
            )

    async def _analyze_with_tools(
        self,
        user_query: str,
        context_info: dict,
        agent_context: AgentScopedContext,
    ) -> dict:
        """执行带工具调用的分析"""
        prompt = ANALYST_AGENT_PROMPT.format(
            discovered_tables=", ".join(agent_context.tables) if agent_context.tables else "（无）",
            tables_summary=context_info["tables"],
            lineage_summary=context_info["lineage"],
            tools_description=agent_context.get_tools_description(),
            user_query=user_query,
        )

        messages = [HumanMessage(content=prompt)]
        tool_call_count = 0

        while tool_call_count < self.max_tool_calls:
            response = await self.llm_with_tools.ainvoke(messages)
            messages.append(response)

            # 如果没有工具调用，解析响应
            if not response.tool_calls:
                return self._parse_response(response.content)

            # 执行工具调用
            for tool_call in response.tool_calls:
                tool_call_count += 1
                tool_name = tool_call["name"]
                tool_args = tool_call["args"]
                tool_id = tool_call["id"]

                logger.info(f"🔧 AnalystAgent 调用工具: {tool_name}({tool_args})")

                tool_result = await self._execute_tool(tool_name, tool_args)

                messages.append(
                    ToolMessage(content=tool_result, tool_call_id=tool_id)
                )

                if tool_call_count >= self.max_tool_calls:
                    break

        # 达到最大工具调用次数，获取最终响应
        response = await self.llm.ainvoke(messages)
        return self._parse_response(response.content)

    async def _execute_tool(self, tool_name: str, tool_args: dict) -> str:
        """执行工具调用"""
        try:
            if tool_name == "search_assets":
                return await search_assets.ainvoke(tool_args)
            elif tool_name == "get_table_columns":
                return await get_table_columns.ainvoke(tool_args)
            else:
                return json.dumps({"status": "error", "message": f"未知工具: {tool_name}"})
        except Exception as e:
            logger.error(f"工具 {tool_name} 执行失败: {e}")
            return json.dumps({"status": "error", "message": str(e)})

    async def _validate_convergence(
        self,
        analysis: AnalysisResult,
        global_kg_context: GlobalKGContext,
        agent_context: AgentScopedContext,
    ) -> list[Ambiguity]:
        """
        验证需求是否收敛

        验证规则（业务层面）：
        1. 每个 Step 必须有明确的 input_tables 和 output_table
        2. 源头输入表（第一个 Step 的 input）必须在知识库中存在
        3. 最终目标表（final_target）必须在知识库中存在

        注意：中间步骤的表不验证，那是技术层面的事情（ArchitectAgent 负责）
        """
        issues = []

        # 获取已知表集合
        known_tables = set(global_kg_context.get_table_names())
        known_tables.update(agent_context.tables)

        # 1. 检查 Steps 是否存在
        if not analysis.steps:
            issues.append(Ambiguity(
                question="无法从需求中识别出具体的业务步骤，请明确说明要做什么",
                context="需求过于模糊，无法拆分为可执行的步骤",
                options=["请描述具体的数据处理逻辑", "请指明源表和目标表"],
            ))
            return issues

        # 2. 检查每个 Step 是否有输入输出声明
        for step in analysis.steps:
            if not step.input_tables:
                issues.append(Ambiguity(
                    question=f"步骤 '{step.step_name}' 缺少输入表，请指明从哪些表读取数据",
                    context=f"步骤描述: {step.description}",
                    options=[],
                ))
            if not step.output_table:
                issues.append(Ambiguity(
                    question=f"步骤 '{step.step_name}' 缺少输出表，请指明数据写入哪个表",
                    context=f"步骤描述: {step.description}",
                    options=[],
                ))

        # 3. 验证源头输入表（第一个 Step 的 input_tables）
        if analysis.steps and analysis.steps[0].input_tables:
            for table in analysis.steps[0].input_tables:
                if table not in known_tables:
                    exists = await self._verify_table_exists(table)
                    if not exists:
                        issues.append(Ambiguity(
                            question=f"源表 '{table}' 不在知识库中，请确认表名是否正确",
                            context="源头输入表必须在 Gravitino 中存在",
                            options=["请检查表名拼写", "请先在 Gravitino 中创建此表并同步元数据"],
                        ))

        # 4. 验证最终目标表
        if not analysis.final_target or not analysis.final_target.table_name:
            issues.append(Ambiguity(
                question="请明确最终数据要写入哪个表",
                context="缺少最终目标表信息",
                options=[],
            ))
        else:
            final_table = analysis.final_target.table_name
            if final_table not in known_tables:
                exists = await self._verify_table_exists(final_table)
                if not exists:
                    issues.append(Ambiguity(
                        question=f"最终目标表 '{final_table}' 不在知识库中",
                        context="最终目标表必须先在 Gravitino 中创建并同步元数据",
                        options=["请先创建目标表，同步元数据后再生成 ETL"],
                    ))

        return issues

    async def _verify_table_exists(self, table_name: str) -> bool:
        """通过工具验证表是否存在"""
        try:
            result = await get_table_columns.ainvoke({"table_name": table_name})
            data = json.loads(result)
            return data.get("status") == "success"
        except Exception as e:
            logger.warning(f"验证表 {table_name} 是否存在失败: {e}")
            return False

    def _parse_response(self, content: str) -> dict:
        """解析 LLM 响应"""
        logger.info(f"🔍 解析 LLM 响应 (长度: {len(content)}): {content[:1000]}...")

        json_match = re.search(r'```json\s*([\s\S]*?)\s*```', content)
        if json_match:
            return json.loads(json_match.group(1))

        json_match = re.search(r'\{[\s\S]*\}', content)
        if json_match:
            try:
                return json.loads(json_match.group())
            except json.JSONDecodeError as e:
                logger.error(f"JSON 解析失败: {e}, 匹配内容: {json_match.group()[:500]}...")

        logger.error(f"无法解析 LLM 响应为 JSON，原始内容: {content}")
        raise ValueError("无法解析 LLM 响应为 JSON")

    def _build_analysis_result(self, result_dict: dict, user_query: str) -> AnalysisResult:
        """构建 AnalysisResult"""
        steps = []
        for step_dict in result_dict.get("steps", []):
            step = Step(
                step_id=step_dict.get("step_id", ""),
                step_name=step_dict.get("step_name", ""),
                description=step_dict.get("description", ""),
                input_tables=step_dict.get("input_tables", []),
                output_table=step_dict.get("output_table"),
                depends_on=step_dict.get("depends_on", []),
            )
            steps.append(step)

        # 解析 ambiguities
        ambiguities = []
        for amb_dict in result_dict.get("ambiguities", []):
            if isinstance(amb_dict, dict):
                ambiguities.append(Ambiguity(
                    question=amb_dict.get("question", ""),
                    context=amb_dict.get("context"),
                    options=amb_dict.get("options", []),
                ))
            elif isinstance(amb_dict, str):
                ambiguities.append(Ambiguity(question=amb_dict))

        # 解析 final_target 为 DataTarget 对象
        final_target = None
        final_target_dict = result_dict.get("final_target")
        if final_target_dict and isinstance(final_target_dict, dict):
            final_target = DataTarget(
                table_name=final_target_dict.get("table_name", ""),
                write_mode=final_target_dict.get("write_mode", "overwrite"),
                partition_by=final_target_dict.get("partition_by", []),
                description=final_target_dict.get("description"),
            )

        return AnalysisResult(
            user_query=user_query,
            summary=result_dict.get("summary", ""),
            steps=steps,
            final_target=final_target,
            ambiguities=ambiguities,
            confidence=result_dict.get("confidence", 0.5),
        )

    def _format_context(
        self, global_kg_context: GlobalKGContext, agent_context: AgentScopedContext
    ) -> dict:
        """格式化上下文信息"""
        # 格式化表信息
        tables_lines = []
        for catalog in global_kg_context.catalogs:
            for schema in catalog.schemas:
                for table in schema.tables[:20]:
                    tags_str = ", ".join(table.tags) if table.tags else ""
                    tables_lines.append(
                        f"- {schema.name}.{table.name} ({table.column_count}列) [{tags_str}]"
                    )

        # 格式化血缘
        lineage_lines = []
        for edge in global_kg_context.lineage_graph[:10]:
            lineage_lines.append(f"- {edge.source_table} → {edge.target_table}")

        return {
            "tables": "\n".join(tables_lines) if tables_lines else "（无）",
            "lineage": "\n".join(lineage_lines) if lineage_lines else "（无）",
        }
