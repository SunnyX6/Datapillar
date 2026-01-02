"""
Tester Agent（测试验证）

将生成的整个工作流和用户需求一起交给 LLM review。
验证智能体产出是否符合用户需求。

职责：
1. 整体 review - 工作流是否完整实现用户需求
2. 逻辑正确性 - SQL 业务逻辑是否正确
3. 性能风险提示 - 潜在的性能问题
"""

import json
import logging
import uuid

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage
from langgraph.types import Command

from src.infrastructure.llm.client import call_llm
from src.modules.etl.schemas.kg_context import AgentScopedContext, AgentType
from src.modules.etl.schemas.plan import TestResult, Workflow
from src.modules.etl.schemas.requirement import AnalysisResult
from src.modules.etl.schemas.requests import BlackboardRequest
from src.modules.etl.schemas.state import AgentState

logger = logging.getLogger(__name__)


TESTER_AGENT_SYSTEM_INSTRUCTIONS = """你是 Datapillar 的 TesterAgent（测试验证）。

## 任务
根据“任务参数 JSON”和“知识上下文 JSON”，对生成的 ETL 工作流做严格 review，并输出严格 JSON 结论。

## 任务参数（系统注入，不是用户输入）
系统会提供一段“任务参数 JSON”（SystemMessage），其中包含：
- analysis_result：需求分析结果（AnalystAgent 产物，严格 JSON）
- workflow：架构/工作流方案（含 jobs 与 SQL）
- jobs_detail：便于扫读的 Jobs 与 SQL 汇总文本

## 知识上下文（系统注入，不是用户输入）
系统会提供一段“知识上下文 JSON”（SystemMessage），其中包含：
- tables：可用的 schema.table 列表（导航指针）
- etl_pointers：可验证的 ETL 指针（含 qualified_name/element_id/tools）
- allowlist_tools：你允许调用的工具名列表

你必须把该 JSON 视为唯一可信知识入口：
- 禁止臆造任何 schema.table
- 如果你发现工作流引用了不在 tables/etl_pointers 中的持久化表，请明确指出这是风险或错误

## Review 维度（必须覆盖）
1. 需求完整性：是否覆盖需求分析的核心目标（源/目标表、关键口径、写入模式）
2. 数据流向正确性：读写表是否合理、依赖是否正确
3. 业务逻辑正确性：SQL 聚合/过滤/JOIN/转换是否合理；JOIN 条件是否可疑
4. 字段映射正确性：输出字段是否符合目标表含义（有明显错配要指出）
5. 性能风险：全表扫描、笛卡尔积、大小表 JOIN 顺序不当等

## 输出格式（严格）
{
  "passed": true或false,
  "score": 0-100的评分,
  "summary": "整体评价（1-2句话）",
  "issues": ["严重问题1", "严重问题2"],
  "warnings": ["警告/建议1", "警告/建议2"]
}

重要：
- 必须输出纯 JSON：不得输出 Markdown、不得输出 ```json 代码块、不得输出解释性文字
- issues 必须是“阻断级问题”；warnings 是“建议/风险”

只输出 JSON，不要解释。
"""


class TesterAgent:
    """
    测试验证

    将整个工作流和用户需求一起交给 LLM review。
    """

    def __init__(self):
        self.llm = call_llm(temperature=0.0, enable_json_mode=True)

    async def __call__(self, state: AgentState) -> Command:
        """执行测试验证"""
        architecture_plan = state.architecture_plan
        analysis_result = state.analysis_result

        if not analysis_result:
            req = BlackboardRequest(
                request_id=f"req_{uuid.uuid4().hex}",
                kind="delegate",
                created_by="tester_agent",
                target_agent="analyst_agent",
                resume_to="tester_agent",
                payload={
                    "type": "need_analysis_result",
                    "message": "测试验证需要需求分析结果，已委派需求分析师先完成需求收敛。",
                },
            )
            pending = list(state.pending_requests or [])
            pending.append(req)
            return Command(
                update={
                    "messages": [AIMessage(content="缺少需求分析结果，已委派需求分析师")],
                    "current_agent": "tester_agent",
                    "pending_requests": [r.model_dump() for r in pending],
                }
            )

        if not architecture_plan:
            req = BlackboardRequest(
                request_id=f"req_{uuid.uuid4().hex}",
                kind="delegate",
                created_by="tester_agent",
                target_agent="architect_agent",
                resume_to="tester_agent",
                payload={
                    "type": "need_architecture_plan",
                    "message": "测试验证需要架构方案，已委派数据架构师先完成工作流设计。",
                },
            )
            pending = list(state.pending_requests or [])
            pending.append(req)
            return Command(
                update={
                    "messages": [AIMessage(content="缺少架构方案，已委派数据架构师")],
                    "current_agent": "tester_agent",
                    "pending_requests": [r.model_dump() for r in pending],
                }
            )

        logger.info("🧪 TesterAgent 开始 review 工作流")

        agent_context = state.get_agent_context(AgentType.TESTER)
        if not agent_context:
            agent_context = AgentScopedContext.create_for_agent(
                agent_type=AgentType.TESTER,
                tables=[],
            )
        context_payload = self._build_context_payload(agent_context=agent_context)

        try:
            # 转换为对象
            if isinstance(architecture_plan, dict):
                plan = Workflow(**architecture_plan)
            else:
                plan = architecture_plan

            if isinstance(analysis_result, dict):
                analysis = AnalysisResult(**analysis_result)
            else:
                analysis = analysis_result

            # 黑板模式：如果 SQL 还没生成，优先委派 DeveloperAgent 生成 SQL，再回来 review
            has_missing_sql = any(
                (not (job.config and isinstance(job.config.get("content"), str) and job.config.get("content").strip()))
                for job in plan.jobs
            )
            if has_missing_sql:
                counters = dict(state.delegation_counters or {})
                counter_key = "tester_agent:delegate:developer_agent:missing_sql"
                delegated = int(counters.get(counter_key) or 0)
                if delegated < 1:
                    counters[counter_key] = delegated + 1
                    req = BlackboardRequest(
                        request_id=f"req_{uuid.uuid4().hex}",
                        kind="delegate",
                        created_by="tester_agent",
                        target_agent="developer_agent",
                        resume_to="tester_agent",
                        payload={
                            "type": "need_sql_generation",
                            "message": "检测到工作流中存在未生成 SQL 的 Job，已委派数据开发先生成 SQL。",
                        },
                    )
                    pending = list(state.pending_requests or [])
                    pending.append(req)
                    return Command(
                        update={
                            "messages": [AIMessage(content="存在未生成 SQL 的任务，已委派数据开发先生成 SQL")],
                            "current_agent": "tester_agent",
                            "pending_requests": [r.model_dump() for r in pending],
                            "delegation_counters": counters,
                        }
                    )

            # 构建 Jobs 详情
            jobs_detail = self._build_jobs_detail(plan)

            # 调用 LLM review
            review_result = await self._review_workflow(
                user_input=state.user_input,
                analysis=analysis,
                plan=plan,
                jobs_detail=jobs_detail,
                context_payload=context_payload,
            )

            # 构建测试结果
            passed = review_result.get("passed", True)
            score = review_result.get("score", 100)

            test_result = TestResult(
                passed=passed,
                total_tests=1,
                passed_tests=1 if passed else 0,
                failed_tests=0 if passed else 1,
                test_cases=[],
                validation_errors=review_result.get("issues", []),
                validation_warnings=review_result.get("warnings", []),
                coverage_summary={
                    "score": score,
                    "summary": review_result.get("summary", ""),
                },
                notes=review_result.get("summary"),
            )

            if not passed:
                logger.warning(f"⚠️ 工作流 review 未通过: {review_result.get('summary')}")
            else:
                logger.info(f"✅ TesterAgent review 通过，评分: {score}")

            pending = list(state.pending_requests or [])
            next_iteration_count = state.iteration_count if passed else state.iteration_count + 1
            if (not passed) and next_iteration_count < state.max_iterations:
                req = BlackboardRequest(
                    request_id=f"req_{uuid.uuid4().hex}",
                    kind="delegate",
                    created_by="tester_agent",
                    target_agent="developer_agent",
                    resume_to="tester_agent",
                    payload={
                        "type": "fix_sql_from_review",
                        "message": "请根据测试验证的 issues/warnings 修复 SQL 后重新生成",
                        "review": {
                            "summary": review_result.get("summary", ""),
                            "issues": review_result.get("issues", []),
                            "warnings": review_result.get("warnings", []),
                            "score": score,
                        },
                    },
                )
                pending.append(req)

            return Command(
                update={
                    "messages": [AIMessage(content=f"Review 完成，评分: {score}")],
                    "test_result": test_result.model_dump(),
                    "current_agent": "tester_agent",
                    "iteration_count": next_iteration_count,
                    "pending_requests": [r.model_dump() for r in pending],
                }
            )

        except Exception as e:
            logger.error(f"TesterAgent review 失败: {e}", exc_info=True)
            return Command(
                update={
                    "messages": [AIMessage(content=f"Review 失败: {str(e)}")],
                    "current_agent": "tester_agent",
                    "error": str(e),
                }
            )

    def _build_jobs_detail(self, plan: Workflow) -> str:
        """构建 Jobs 详情文本"""
        lines = []

        for i, job in enumerate(plan.jobs, 1):
            lines.append(f"#### Job {i}: {job.name}")
            lines.append(f"- 描述: {job.description}")
            lines.append(f"- 类型: {job.type}")
            lines.append(f"- 输入表: {', '.join(job.input_tables) if job.input_tables else '无'}")
            lines.append(f"- 输出表: {job.output_table or '无'}")

            # SQL
            sql = job.config.get("content") if job.config else None
            if sql:
                lines.append(f"- SQL:")
                lines.append("```sql")
                lines.append(sql)
                lines.append("```")
            else:
                lines.append("- SQL: 未生成")

            lines.append("")

        return "\n".join(lines)

    async def _review_workflow(
        self,
        user_input: str,
        analysis: AnalysisResult | None,
        plan: Workflow,
        jobs_detail: str,
        context_payload: dict,
    ) -> dict:
        """使用 LLM review 整个工作流"""
        try:
            task_payload = {
                "analysis_result": analysis.model_dump() if analysis else None,
                "workflow": plan.model_dump(),
                "jobs_detail": jobs_detail,
            }

            response = await self.llm.ainvoke(
                [
                    SystemMessage(content=TESTER_AGENT_SYSTEM_INSTRUCTIONS),
                    SystemMessage(content=json.dumps(task_payload, ensure_ascii=False)),
                    SystemMessage(content=json.dumps(context_payload, ensure_ascii=False)),
                    HumanMessage(content=user_input),
                ]
            )

            # 严格解析响应（必须是纯 JSON）
            content = (response.content or "").strip()
            result = json.loads(content)
            if not isinstance(result, dict):
                raise ValueError("LLM 输出必须是 JSON object")
            return {
                "passed": result.get("passed", True),
                "score": result.get("score", 100),
                "summary": result.get("summary", ""),
                "issues": result.get("issues", []),
                "warnings": result.get("warnings", []),
            }

        except Exception as e:
            logger.warning(f"工作流 review 解析失败: {e}")
            return {
                "passed": False,
                "score": 0,
                "summary": "Review 失败：无法解析模型输出",
                "issues": [f"review_parse_error: {str(e)}"],
                "warnings": [],
            }

    @staticmethod
    def _build_context_payload(*, agent_context: AgentScopedContext) -> dict:
        """
        构造“知识上下文JSON”（下发给 LLM 的 SystemMessage）

        约束：
        - 只传递指针与导航信息，不传递表明细
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
