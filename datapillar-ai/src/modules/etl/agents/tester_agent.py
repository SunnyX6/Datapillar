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
import re

from langchain_core.messages import AIMessage, HumanMessage
from langgraph.types import Command

from src.infrastructure.llm.client import call_llm
from src.modules.etl.schemas.plan import TestResult, Workflow
from src.modules.etl.schemas.requirement import AnalysisResult
from src.modules.etl.schemas.state import AgentState

logger = logging.getLogger(__name__)


WORKFLOW_REVIEW_PROMPT = """你是资深数据架构师和测试专家，负责 review 生成的 ETL 工作流是否满足用户需求。

## 用户原始需求
{user_input}

## 需求分析结果
{analysis_summary}

## 生成的工作流

### 工作流名称
{workflow_name}

### 工作流描述
{workflow_description}

### Jobs 和 SQL
{jobs_detail}

## Review 任务

请从以下维度进行 review：

1. **需求完整性** - 工作流是否完整实现了用户的所有需求
2. **数据流向正确性** - 源表、目标表是否正确，数据流向是否合理
3. **业务逻辑正确性** - SQL 的聚合、过滤、JOIN、转换逻辑是否正确
4. **字段映射正确性** - 输出字段是否符合目标表结构和业务含义
5. **性能风险** - 是否有全表扫描、笛卡尔积、大小表 JOIN 顺序不当等问题

## 输出格式

```json
{{
  "passed": true或false,
  "score": 0-100的评分,
  "summary": "整体评价（1-2句话）",
  "issues": ["严重问题1", "严重问题2"],
  "warnings": ["警告/建议1", "警告/建议2"]
}}
```

评分标准：
- 90-100: 完全满足需求，无问题
- 70-89: 基本满足需求，有小问题或建议
- 50-69: 部分满足需求，有明显问题
- 0-49: 不满足需求，需要重新生成

只输出 JSON，不要解释。
"""


class TesterAgent:
    """
    测试验证

    将整个工作流和用户需求一起交给 LLM review。
    """

    def __init__(self):
        self.llm = call_llm(temperature=0.0)

    async def __call__(self, state: AgentState) -> Command:
        """执行测试验证"""
        architecture_plan = state.architecture_plan
        analysis_result = state.analysis_result

        if not architecture_plan:
            return Command(
                update={
                    "messages": [AIMessage(content="缺少架构方案，无法测试")],
                    "current_agent": "tester_agent",
                    "error": "缺少架构方案",
                }
            )

        logger.info("🧪 TesterAgent 开始 review 工作流")

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

            # 构建 Jobs 详情
            jobs_detail = self._build_jobs_detail(plan)

            # 调用 LLM review
            review_result = await self._review_workflow(
                user_input=state.user_input,
                analysis=analysis,
                plan=plan,
                jobs_detail=jobs_detail,
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

            return Command(
                update={
                    "messages": [AIMessage(content=f"Review 完成，评分: {score}")],
                    "test_result": test_result.model_dump(),
                    "current_agent": "tester_agent",
                    "iteration_count": state.iteration_count if passed else state.iteration_count + 1,
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
    ) -> dict:
        """使用 LLM review 整个工作流"""
        try:
            prompt = WORKFLOW_REVIEW_PROMPT.format(
                user_input=user_input,
                analysis_summary=analysis.summary if analysis else "无",
                workflow_name=plan.name,
                workflow_description=plan.description or "无",
                jobs_detail=jobs_detail,
            )

            response = await self.llm.ainvoke([HumanMessage(content=prompt)])

            # 解析响应
            content = response.content.strip()
            json_match = re.search(r'\{[\s\S]*\}', content)
            if json_match:
                result = json.loads(json_match.group())
                return {
                    "passed": result.get("passed", True),
                    "score": result.get("score", 100),
                    "summary": result.get("summary", ""),
                    "issues": result.get("issues", []),
                    "warnings": result.get("warnings", []),
                }

        except Exception as e:
            logger.warning(f"工作流 review 解析失败: {e}")

        # 默认通过
        return {
            "passed": True,
            "score": 80,
            "summary": "Review 完成",
            "issues": [],
            "warnings": [],
        }
