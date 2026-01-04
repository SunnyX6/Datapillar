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

from src.infrastructure.llm.client import call_llm
from src.modules.etl.agents.knowledge_agent import AgentType, get_agent_tools
from src.modules.etl.agents.prompt_messages import build_llm_messages
from src.modules.etl.schemas.agent_result import AgentResult
from src.modules.etl.schemas.plan import TestResult, Workflow
from src.modules.etl.schemas.requirement import AnalysisResult

logger = logging.getLogger(__name__)


TESTER_AGENT_SYSTEM_INSTRUCTIONS = """你是 Datapillar 的 TesterAgent（测试验证）。

## 任务
根据"任务参数 JSON"和"知识上下文 JSON"，对生成的 ETL 工作流做严格 review，并输出严格 JSON 结论。

## 任务参数（系统注入，不是用户输入）
系统会提供一段"任务参数 JSON"（SystemMessage），其中包含：
- analysis_result：需求分析结果（AnalystAgent 产物，严格 JSON）
- workflow：架构/工作流方案（含 jobs 与 SQL）
- jobs_detail：便于扫读的 Jobs 与 SQL 汇总文本

## 知识上下文（系统注入，不是用户输入）
系统会提供一段"知识上下文 JSON"（SystemMessage），其中包含：
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
- issues 必须是"阻断级问题"；warnings 是"建议/风险"

只输出 JSON，不要解释。
"""


class TesterAgent:
    """
    测试验证

    将整个工作流和用户需求一起交给 LLM review。
    """

    __test__ = False

    def __init__(self):
        self.llm = call_llm(temperature=0.0, enable_json_mode=True)
        self.allowlist = get_agent_tools(AgentType.TESTER)

    async def run(
        self,
        *,
        user_query: str,
        analysis_result: AnalysisResult,
        workflow: Workflow,
    ) -> AgentResult:
        """
        执行测试验证

        参数：
        - user_query: 用户输入
        - analysis_result: 需求分析结果
        - workflow: 工作流

        返回：
        - AgentResult: 执行结果
        """
        logger.info("🧪 TesterAgent 开始 review 工作流")

        try:
            # 检查是否所有 Job 都已生成 SQL
            has_missing_sql = any(
                (
                    not (
                        job.config
                        and isinstance((content := job.config.get("content")), str)
                        and content.strip()
                    )
                )
                for job in workflow.jobs
            )
            if has_missing_sql:
                return AgentResult.needs_delegation(
                    summary="存在未生成 SQL 的 Job，需要先生成 SQL",
                    target_agent="developer_agent",
                    reason="missing_sql",
                    payload={
                        "message": "检测到工作流中存在未生成 SQL 的 Job，已委派数据开发先生成 SQL。",
                    },
                )

            jobs_detail = self._build_jobs_detail(workflow)

            review_result = await self._review_workflow(
                user_query=user_query,
                analysis=analysis_result,
                workflow=workflow,
                jobs_detail=jobs_detail,
            )

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
                return AgentResult.completed(
                    summary=f"测试未通过，评分: {score}",
                    deliverable=test_result,
                    deliverable_type="test",
                )

            logger.info(f"✅ TesterAgent review 通过，评分: {score}")

            return AgentResult.completed(
                summary=f"测试通过，评分: {score}",
                deliverable=test_result,
                deliverable_type="test",
            )

        except Exception as e:
            logger.error(f"TesterAgent review 失败: {e}", exc_info=True)
            return AgentResult.failed(
                summary=f"Review 失败: {str(e)}",
                error=str(e),
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
            sql = job.config.get("content") if job.config else None
            if sql:
                lines.append("- SQL:")
                lines.append("```sql")
                lines.append(sql)
                lines.append("```")
            else:
                lines.append("- SQL: 未生成")
            lines.append("")
        return "\n".join(lines)

    async def _review_workflow(
        self,
        user_query: str,
        analysis: AnalysisResult,
        workflow: Workflow,
        jobs_detail: str,
    ) -> dict:
        """使用 LLM review 整个工作流"""
        try:
            task_payload = {
                "analysis_result": analysis.model_dump(),
                "workflow": workflow.model_dump(),
                "jobs_detail": jobs_detail,
            }

            response = await self.llm.ainvoke(
                build_llm_messages(
                    system_instructions=TESTER_AGENT_SYSTEM_INSTRUCTIONS,
                    agent_id="tester_agent",
                    user_query=user_query,
                    task_payload=task_payload,
                )
            )

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
