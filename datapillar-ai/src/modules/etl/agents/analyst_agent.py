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

from langchain_core.messages import ToolMessage

from src.infrastructure.llm.client import call_llm
from src.modules.etl.agents.knowledge_agent import AgentType, get_agent_tools
from src.modules.etl.agents.prompt_messages import build_llm_messages
from src.modules.etl.schemas.agent_result import AgentResult
from src.modules.etl.schemas.requirement import Ambiguity, AnalysisResult, DataTarget, Step
from src.modules.etl.tools.agent_tools import get_table_columns, recommend_guidance

logger = logging.getLogger(__name__)


def _tool_error(message: str, **extra: object) -> str:
    """构造工具错误响应"""
    payload: dict[str, object] = {"status": "error", "message": message}
    payload.update(extra)
    return json.dumps(payload, ensure_ascii=False)


ANALYST_AGENT_SYSTEM_INSTRUCTIONS = """你是 Datapillar 的 AnalystAgent（需求分析与收敛）。

## 任务
把用户需求收敛成可执行的业务步骤（Step），输出严格 JSON。

## 核心原则
1. 你只做"做什么"（业务拆解），不做"怎么做"（不写 SQL，不选组件，不画 DAG）。
2. 不允许臆造表名，如果不确定，必须提出澄清问题。

## 输出格式
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

重要：
- **必须输出纯 JSON**：不得输出 Markdown、不得输出 ```json 代码块、不得输出解释性文字
- ambiguities 中的每条 question 必须唯一，不允许同义重复
- 如果无法明确 input_tables 或 output_table，必须在 ambiguities 中提问
- confidence 反映需求的明确程度，模糊需求必须 < 0.7

只输出 JSON，不要解释。
"""


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
        self.llm_json = call_llm(temperature=0.0, enable_json_mode=True)
        self.max_tool_calls = 4
        self.allowlist = get_agent_tools(AgentType.ANALYST)

    async def run(
        self,
        *,
        user_query: str,
        knowledge_agent=None,
    ) -> AgentResult:
        """
        执行需求分析

        参数：
        - user_query: 用户输入
        - knowledge_agent: KnowledgeAgent 实例（用于按需查询指针）

        返回：
        - AgentResult: 执行结果
        """
        self._knowledge_agent = knowledge_agent

        if not user_query:
            return AgentResult.failed(
                summary="缺少用户输入",
                error="缺少用户输入，无法分析需求",
            )

        logger.info(f"📋 AnalystAgent 开始分析需求: {user_query}")

        try:
            llm_with_tools = self._bind_tools()

            result_dict = await self._analyze_with_tools(
                user_query=user_query,
                llm_with_tools=llm_with_tools,
            )

            analysis_result = self._build_analysis_result(result_dict, user_query)

            plan_summary = analysis_result.plan_summary()
            logger.info(f"✅ AnalystAgent 完成分析:\n{plan_summary}")

            if analysis_result.needs_clarification() or analysis_result.confidence < 0.7:
                questions = [a.question for a in analysis_result.ambiguities if a.question]
                if not questions:
                    return AgentResult.failed(
                        summary="需求未收敛，LLM 未生成有效澄清问题",
                        error="需求未收敛且 ambiguities 为空",
                    )
                guidance = await self._try_recommend_guidance(user_query)
                return AgentResult.needs_clarification(
                    summary="需求不够明确，需要补充关键信息",
                    message="请回答以下问题以便继续分析",
                    questions=questions,
                    guidance=guidance,
                )

            if not self._is_converged(analysis_result):
                return AgentResult.failed(
                    summary="需求未收敛：缺少 steps 或 input/output 或 final_target",
                    error="需求未收敛：输出不满足步骤/输入输出/目标表等约束",
                )

            return AgentResult.completed(
                summary=f"需求分析完成: {analysis_result.summary}",
                deliverable=analysis_result,
                deliverable_type="analysis",
            )

        except Exception as e:
            logger.error(f"AnalystAgent 分析失败: {e}", exc_info=True)
            return AgentResult.failed(
                summary=f"需求分析失败: {str(e)}",
                error=str(e),
            )

    @staticmethod
    async def _try_recommend_guidance(user_query: str) -> dict | None:
        """no-hit/需澄清场景的轻量引导数据"""
        try:
            raw = await recommend_guidance.ainvoke({"user_query": user_query})
            parsed = json.loads(raw or "")
            if isinstance(parsed, dict) and parsed.get("status") == "success":
                return parsed
            return None
        except Exception:
            return None

    async def _analyze_with_tools(
        self,
        user_query: str,
        llm_with_tools,
    ) -> dict:
        """执行带工具调用的分析"""
        messages = build_llm_messages(
            system_instructions=ANALYST_AGENT_SYSTEM_INSTRUCTIONS,
            agent_id="analyst_agent",
            user_query=user_query,
        )
        tool_call_count = 0

        while tool_call_count < self.max_tool_calls:
            response = await llm_with_tools.ainvoke(messages)
            messages.append(response)

            if not response.tool_calls:
                return self._parse_response(response.content)

            for tool_call in response.tool_calls:
                tool_call_count += 1
                tool_name = tool_call["name"]
                tool_args = tool_call["args"]
                tool_id = tool_call["id"]

                logger.info(f"🔧 AnalystAgent 调用工具: {tool_name}({tool_args})")

                tool_result = await self._execute_tool(tool_name, tool_args)

                messages.append(ToolMessage(content=tool_result, tool_call_id=tool_id))

                if tool_call_count >= self.max_tool_calls:
                    break

        response = await self.llm_json.ainvoke(messages)
        return self._parse_response(response.content)

    async def _execute_tool(self, tool_name: str, tool_args: dict) -> str:
        """执行工具调用（按需获取指针 + 权限校验）"""
        try:
            if tool_name not in self.allowlist:
                return _tool_error(f"工具不在 allowlist 中: {tool_name}")

            if not self._knowledge_agent:
                return _tool_error("无法查询指针：knowledge_agent 未注入")

            if tool_name == "get_table_columns":
                table_name = (tool_args or {}).get("table_name") or ""
                if not table_name:
                    return _tool_error("缺少 table_name 参数")

                pointers = await self._knowledge_agent.query_pointers(
                    table_name,
                    node_types=["Table"],
                    top_k=5,
                )
                pointer = self._find_matching_pointer(pointers, table_name)
                if not pointer:
                    return _tool_error("未找到指针", table_name=table_name)
                if "get_table_columns" not in (pointer.tools or []):
                    return _tool_error("指针未授权此工具", table_name=table_name)

                logger.info(f"📊 调用 get_table_columns: {pointer.qualified_name}")
                return await get_table_columns.ainvoke({"table_name": pointer.qualified_name})

            return _tool_error(f"未知工具: {tool_name}")
        except Exception as e:
            logger.error(f"工具 {tool_name} 执行失败: {e}")
            return _tool_error(str(e))

    def _find_matching_pointer(self, pointers: list, name: str):
        """从指针列表中找到匹配的指针"""
        if not pointers:
            return None
        for p in pointers:
            if p.qualified_name == name:
                return p
        for p in pointers:
            if name in (p.qualified_name or ""):
                return p
        return pointers[0] if pointers else None

    def _is_converged(self, analysis: AnalysisResult) -> bool:
        """只做结构性收敛校验"""
        if not analysis.steps:
            return False
        for step in analysis.steps:
            if not step.input_tables:
                return False
            if not step.output_table:
                return False
        if not analysis.final_target:
            return False
        return bool(analysis.final_target.table_name)

    def _bind_tools(self):
        """绑定工具到 LLM"""
        tool_registry = {
            "get_table_columns": get_table_columns,
        }
        tools = [tool_registry[name] for name in self.allowlist if name in tool_registry]
        return self.llm.bind_tools(tools)

    def _parse_response(self, content: str) -> dict:
        """严格解析 LLM 响应"""
        text = (content or "").strip()
        try:
            parsed = json.loads(text)
        except json.JSONDecodeError as e:
            raise ValueError("LLM 输出不是合法 JSON") from e
        if not isinstance(parsed, dict):
            raise ValueError("LLM 输出必须是 JSON object")
        return parsed

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

        ambiguities = []
        for amb_dict in result_dict.get("ambiguities", []):
            if isinstance(amb_dict, dict):
                ambiguities.append(
                    Ambiguity(
                        question=amb_dict.get("question", ""),
                        context=amb_dict.get("context"),
                        options=amb_dict.get("options", []),
                    )
                )
            elif isinstance(amb_dict, str):
                ambiguities.append(Ambiguity(question=amb_dict, context=None))

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
