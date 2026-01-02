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
import uuid

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage, ToolMessage
from langgraph.types import Command

from src.infrastructure.llm.client import call_llm
from src.modules.etl.schemas.kg_context import AgentScopedContext, AgentType, ETLPointer
from src.modules.etl.schemas.requirement import AnalysisResult, Ambiguity, DataTarget, Step
from src.modules.etl.schemas.requests import BlackboardRequest
from src.modules.etl.schemas.state import AgentState
from src.modules.etl.tools.agent_tools import get_table_columns, recommend_guidance

logger = logging.getLogger(__name__)


ANALYST_AGENT_SYSTEM_INSTRUCTIONS = """你是 Datapillar 的 AnalystAgent（需求分析与收敛）。

## 任务
把用户需求收敛成可执行的业务步骤（Step），输出严格 JSON。

## 核心原则
1. 你只做“做什么”（业务拆解），不做“怎么做”（不写 SQL，不选组件，不画 DAG）。
2. 你必须基于 KnowledgeAgent 下发的“ETL 指针（etl_pointers / ETLPointer）”完成表级收敛。
3. 不允许臆造：你只能引用上下文中已给出的表（schema.table），否则必须提出澄清问题。

## 知识上下文（系统注入，不是用户输入）
系统会向你提供一份“知识上下文 JSON”，其中包含：
- etl_pointers：可验证的 ETL 指针（含 element_id、qualified_name、tools 等）
- allowlist_tools：该 Agent 允许调用的工具名列表

你必须把该 JSON 视为可信输入；并以它为唯一知识来源来收敛表名与工具调用。

## 工具使用规则（严格）
你只能调用 allowlist 中出现的工具；并且仅当某个 ETLPointer.tools 包含该工具名时，才允许对该节点调用该工具。

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
- 如果无法在上下文中定位用户提到的源头输入表或最终目标表，必须提出澄清（禁止臆造表名），confidence 设为 0
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
            agent_context = state.get_agent_context(AgentType.ANALYST)

            if not agent_context:
                agent_context = AgentScopedContext.create_for_agent(
                    agent_type=AgentType.ANALYST,
                    tables=[],
                )

            context_payload = self._build_context_payload(
                agent_context=agent_context,
            )

            llm_with_tools = self._bind_tools_by_allowlist(agent_context)

            # 执行分析（带工具调用）
            result_dict = await self._analyze_with_tools(
                user_query=user_query,
                context_payload=context_payload,
                agent_context=agent_context,
                llm_with_tools=llm_with_tools,
            )

            analysis_result = self._build_analysis_result(result_dict, user_query)

            plan_summary = analysis_result.get_execution_plan_summary()
            logger.info(f"✅ AnalystAgent 完成分析:\n{plan_summary}")

            allowed_tables = self._build_allowed_tables(agent_context.etl_pointers)
            unknown_tables = self._find_unknown_tables(analysis_result, allowed_tables=allowed_tables)
            if unknown_tables:
                # 黑板模式：遇到未知表，优先委派 KnowledgeAgent 刷新上下文，而不是直接报错终止
                counters = dict(state.delegation_counters or {})
                counter_key = "analyst_agent:delegate:knowledge_agent:unknown_tables"
                delegated = int(counters.get(counter_key) or 0)
                if delegated < 1:
                    counters[counter_key] = delegated + 1
                    req = BlackboardRequest(
                        request_id=f"req_{uuid.uuid4().hex}",
                        kind="delegate",
                        created_by="analyst_agent",
                        target_agent="knowledge_agent",
                        resume_to="analyst_agent",
                        payload={
                            "type": "refresh_knowledge",
                            "reason": "unknown_tables",
                            "unknown_tables": unknown_tables,
                            "message": "需求分析阶段发现未知表，已委派知识检索刷新上下文后再继续。",
                        },
                    )
                    pending = list(state.pending_requests or [])
                    pending.append(req)
                    return Command(
                        update={
                            "messages": [AIMessage(content="检测到未知表，已委派知识检索刷新上下文")],
                            "current_agent": "analyst_agent",
                            "pending_requests": [r.model_dump() for r in pending],
                            "delegation_counters": counters,
                        }
                    )
                request_id = f"req_{uuid.uuid4().hex}"
                guidance = await self._try_recommend_guidance(user_query)
                payload = {
                    "type": "clarification",
                    "message": "知识库无法定位你提到的表，请补充可验证线索（避免系统臆造）。",
                    "questions": [
                        f"请确认这些表的准确名称（推荐 schema.table）：{', '.join(unknown_tables[:12])}",
                        "如果你不确定表名：请粘贴一段现有 SQL/DDL/字段清单，或说明数据来源系统与目标表。",
                    ],
                }
                if guidance:
                    payload["guidance"] = guidance
                req = BlackboardRequest(
                    request_id=request_id,
                    kind="human",
                    created_by="analyst_agent",
                    resume_to="blackboard_router",
                    payload=payload,
                )
                pending = list(state.pending_requests or [])
                pending.append(req)
                return Command(
                    update={
                        "messages": [AIMessage(content="无法定位表指针：需要你补充上下文信息后才能继续")],
                        "current_agent": "analyst_agent",
                        "pending_requests": [r.model_dump() for r in pending],
                        "delegation_counters": counters,
                    }
                )

            # 需求不明确时，强制要求澄清（以 LLM 输出为准，不做程序“后补问题”）
            if analysis_result.needs_clarification() or analysis_result.confidence < 0.7:
                questions = [a.question for a in analysis_result.ambiguities if a.question]
                if not questions:
                    return Command(
                        update={
                            "messages": [AIMessage(content="需求未收敛，但未生成可执行的澄清问题（LLM 输出不合格）")],
                            "current_agent": "analyst_agent",
                            "error": "需求未收敛且 ambiguities 为空",
                        }
                    )
                request_id = f"req_{uuid.uuid4().hex}"
                guidance = await self._try_recommend_guidance(user_query)
                payload = {
                    "type": "clarification",
                    "message": "请回答以下问题以便继续分析",
                    "questions": questions,
                }
                if guidance:
                    payload["guidance"] = guidance
                req = BlackboardRequest(
                    request_id=request_id,
                    kind="human",
                    created_by="analyst_agent",
                    resume_to="blackboard_router",
                    payload=payload,
                )
                pending = list(state.pending_requests or [])
                pending.append(req)
                return Command(
                    update={
                        "messages": [AIMessage(content="需求不够明确，需要你补充关键信息后才能继续")],
                        "current_agent": "analyst_agent",
                        "pending_requests": [r.model_dump() for r in pending],
                    }
                )

            # 结构性收敛校验（只做校验，不做后补）
            if not self._is_converged(analysis_result):
                return Command(
                    update={
                        "messages": [AIMessage(content="需求未收敛：输出不满足步骤/输入输出/目标表等约束（LLM 输出不合格）")],
                        "analysis_result": analysis_result.model_dump(),
                        "current_agent": "analyst_agent",
                        "error": "需求未收敛：缺少 steps 或 input/output 或 final_target",
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

    @staticmethod
    async def _try_recommend_guidance(user_query: str) -> dict | None:
        """
        no-hit/需澄清场景的轻量引导数据（tag/catalog 导航）

        约束：
        - 只返回导航信息，不返回 element_id/指针
        - 失败时静默降级，不影响主链路
        """
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
        context_payload: dict,
        agent_context: AgentScopedContext,
        llm_with_tools,
    ) -> dict:
        """执行带工具调用的分析"""
        messages = [
            SystemMessage(content=ANALYST_AGENT_SYSTEM_INSTRUCTIONS),
            SystemMessage(content=json.dumps(context_payload, ensure_ascii=False)),
            HumanMessage(content=user_query),
        ]
        tool_call_count = 0

        while tool_call_count < self.max_tool_calls:
            response = await llm_with_tools.ainvoke(messages)
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

                tool_result = await self._execute_tool(
                    tool_name=tool_name,
                    tool_args=tool_args,
                    agent_context=agent_context,
                )

                messages.append(
                    ToolMessage(content=tool_result, tool_call_id=tool_id)
                )

                if tool_call_count >= self.max_tool_calls:
                    break

        # 达到最大工具调用次数，获取最终响应
        response = await self.llm_json.ainvoke(messages)
        return self._parse_response(response.content)

    @staticmethod
    def _build_allowed_table_index(node_pointers: list[ETLPointer]) -> dict[str, ETLPointer]:
        """
        从 etl_pointers 构建可调用工具的表索引（按 qualified_name）

        说明：
        - AnalystAgent 只做表级收敛，因此这里只关心 Table 指针
        - 下游只允许对“上下文已给出的表指针”调用 get_table_columns
        """
        table_index: dict[str, ETLPointer] = {}
        for p in node_pointers or []:
            if "Table" not in set(p.labels or []):
                continue
            if not p.qualified_name:
                continue
            table_index[p.qualified_name] = p
        return table_index

    async def _execute_tool(self, *, tool_name: str, tool_args: dict, agent_context: AgentScopedContext) -> str:
        """执行工具调用"""
        try:
            allowlist = set(agent_context.tools or [])
            if tool_name not in allowlist:
                return json.dumps(
                    {"status": "error", "message": f"工具不在 allowlist 中: {tool_name}"},
                    ensure_ascii=False,
                )

            if tool_name == "get_table_columns":
                table_name = (tool_args or {}).get("table_name") or ""
                table_index = self._build_allowed_table_index(agent_context.etl_pointers)
                pointer = table_index.get(table_name)
                if not pointer:
                    return json.dumps(
                        {
                            "status": "error",
                            "message": "禁止对未下发的表指针调用工具",
                            "table_name": table_name,
                        },
                        ensure_ascii=False,
                    )
                if tool_name not in set(pointer.tools or []):
                    return json.dumps(
                        {
                            "status": "error",
                            "message": "该表指针未授权此工具（ETLPointer.tools）",
                            "table_name": table_name,
                            "pointer_element_id": pointer.element_id,
                        },
                        ensure_ascii=False,
                    )
                return await get_table_columns.ainvoke({"table_name": table_name})

            return json.dumps({"status": "error", "message": f"未知工具: {tool_name}"}, ensure_ascii=False)
        except Exception as e:
            logger.error(f"工具 {tool_name} 执行失败: {e}")
            return json.dumps({"status": "error", "message": str(e)})

    def _is_converged(self, analysis: AnalysisResult) -> bool:
        """只做结构性收敛校验，不做任何“补全/追加”"""
        if not analysis.steps:
            return False
        for step in analysis.steps:
            if not step.input_tables:
                return False
            if not step.output_table:
                return False
        if not analysis.final_target:
            return False
        if not analysis.final_target.table_name:
            return False
        return True

    @staticmethod
    def _build_allowed_tables(node_pointers: list[ETLPointer]) -> set[str]:
        allowed: set[str] = set()
        for p in node_pointers or []:
            if "Table" not in set(p.labels or []):
                continue
            if p.qualified_name:
                allowed.add(p.qualified_name)
        return allowed

    @staticmethod
    def _find_unknown_tables(analysis: AnalysisResult, *, allowed_tables: set[str]) -> list[str]:
        referenced = set(analysis.get_all_tables())
        unknown: list[str] = []
        for t in referenced:
            if not t or t.startswith("temp."):
                continue
            if t not in allowed_tables:
                unknown.append(t)
        return sorted(set(unknown))

    def _bind_tools_by_allowlist(self, agent_context: AgentScopedContext):
        """
        按 allowlist 动态绑定工具，避免硬编码导致的“越权/误导”。

        说明：
        - bind_tools 决定 LLM 能否发起工具调用（能力面）
        - allowlist 决定该 Agent 是否允许调用（权限面）
        """
        allowlist = set(agent_context.tools or [])
        tool_registry = {
            "get_table_columns": get_table_columns,
        }
        tools = [tool_registry[name] for name in allowlist if name in tool_registry]
        return self.llm.bind_tools(tools)

    def _parse_response(self, content: str) -> dict:
        """严格解析 LLM 响应（必须是纯 JSON）"""
        text = (content or "").strip()
        logger.info(f"🔍 解析 LLM JSON (长度: {len(text)}): {text[:500]}...")
        try:
            parsed = json.loads(text)
        except json.JSONDecodeError as e:
            logger.error("LLM 输出不是合法 JSON: %s", e)
            raise ValueError("LLM 输出不是合法 JSON（必须输出纯 JSON）") from e
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

    @staticmethod
    def _build_context_payload(
        *,
        agent_context: AgentScopedContext,
    ) -> dict:
        """
        构造“知识上下文JSON”（下发给 LLM 的 SystemMessage）

        约束：
        - 只传递指针与导航信息，不传递表明细（列明细必须通过工具获取）
        """
        node_pointers = agent_context.etl_pointers or []
        table_pointers = [
            {
                "element_id": p.element_id,
                "qualified_name": p.qualified_name,
                "path": p.path,
                "display_name": p.display_name,
                "description": p.description,
                "tools": p.tools,
            }
            for p in node_pointers
            if "Table" in set(p.labels or []) and p.qualified_name
        ]

        return {
            "agent_type": agent_context.agent_type,
            "allowlist_tools": agent_context.tools,
            "tables": agent_context.tables,
            "table_pointers": table_pointers,
            "etl_pointers": [p.model_dump() for p in node_pointers],
            "doc_pointers": [p.model_dump() for p in (agent_context.doc_pointers or [])],
        }
