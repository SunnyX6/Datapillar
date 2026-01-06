"""
Reviewer Agent（方案/代码评审）

职责：对工作流做 LLM review
- 设计阶段 review：评审架构师产物（Job/Stage 设计）
- 开发阶段 review：评审开发产物（SQL 代码）
"""

import json
import logging
from typing import Literal

from langchain_core.messages import HumanMessage, SystemMessage
from pydantic import BaseModel, Field, field_validator

from src.infrastructure.llm.client import call_llm
from src.modules.etl.schemas.agent_result import AgentResult
from src.modules.etl.schemas.analyst import AnalysisResult
from src.modules.etl.schemas.review import ReviewResult
from src.modules.etl.schemas.workflow import Workflow

logger = logging.getLogger(__name__)


def _try_parse_json(value: object) -> object:
    """尝试解析字符串化的 JSON"""
    if not isinstance(value, str):
        return value
    text = value.strip()
    if not text:
        return None
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        return value


class ReviewOutput(BaseModel):
    """Review 输出（LLM 生成）"""

    passed: bool = Field(..., description="是否通过 review，有阻断级问题时为 False")
    score: int = Field(..., ge=0, le=100, description="评分 0-100")
    summary: str = Field(..., description="整体评价，1-2 句话")
    issues: list[str] = Field(default_factory=list, description="阻断级问题")
    warnings: list[str] = Field(default_factory=list, description="警告/建议")

    @field_validator("issues", "warnings", mode="before")
    @classmethod
    def _parse_list_fields(cls, v: object) -> object:
        """容错：null -> 空列表，字符串化 JSON -> 解析"""
        v = _try_parse_json(v)
        if v is None:
            return []
        if isinstance(v, str):
            items = [s.strip() for s in v.split(",")]
            return [s for s in items if s]
        return v


REVIEWER_SYSTEM_PROMPT = """你是 ETL 方案/代码评审专家。

## 你的任务
用户会提供：
1. 原始需求
2. 需求分析结果（AnalysisResult）
3. 工作流方案（Workflow）

你需要 review 这个方案是否能正确实现用户需求。

## Review 维度
1. 需求覆盖：方案是否覆盖了需求分析中的所有步骤和目标
2. 数据流向：读写表是否正确、依赖关系是否合理
3. 业务逻辑：SQL 的聚合/过滤/JOIN 是否正确（如果有 SQL）
4. 性能风险：是否存在全表扫描、笛卡尔积等问题

## 评分标准
- 90+：优秀，无阻断问题
- 70-89：良好，有小问题
- 60-69：及格，需要修改
- <60：不及格，必须重做

## 输出格式（JSON）
评审完成后，直接输出以下 JSON 格式：
```json
{
  "passed": true,
  "score": 85,
  "summary": "方案整体合理，能正确实现需求",
  "issues": [],
  "warnings": ["建议增加异常处理"]
}
```

## 字段说明
- passed: 是否通过 review（有阻断级问题时为 false）
- score: 评分 0-100
- summary: 整体评价，1-2 句话
- issues: 阻断级问题列表（有 issue 时 passed 必须为 false）
- warnings: 建议/风险列表（不影响 passed）
"""


class ReviewerAgent:
    """
    方案/代码评审

    职责：review 工作流是否符合需求，只评审不决策。
    """

    def __init__(self):
        self.llm = call_llm(temperature=0.0)

    async def run(
        self,
        *,
        user_query: str,
        analysis_result: AnalysisResult,
        workflow: Workflow,
        review_stage: Literal["design", "development"],
    ) -> AgentResult:
        """
        执行 review

        参数：
        - user_query: 用户原始需求
        - analysis_result: 需求分析结果
        - workflow: 工作流方案
        - review_stage: review 阶段（design/development）
        """
        logger.info(f"📝 ReviewerAgent 开始 {review_stage} 阶段 review")

        try:
            review = await self._do_review(
                user_query=user_query,
                analysis_result=analysis_result,
                workflow=workflow,
                review_stage=review_stage,
            )

            review_result = ReviewResult(
                passed=review.passed,
                score=review.score,
                summary=review.summary,
                issues=review.issues,
                warnings=review.warnings,
                review_stage=review_stage,
            )

            if review.passed:
                logger.info(f"✅ {review_stage} review 通过，评分: {review.score}")
            else:
                logger.warning(f"⚠️ {review_stage} review 未通过，评分: {review.score}")

            return AgentResult.completed(
                summary=f"{review_stage} review {'通过' if review.passed else '未通过'}，评分: {review.score}",
                deliverable=review_result,
                deliverable_type=f"review_{review_stage}",
            )

        except Exception as e:
            logger.error(f"ReviewerAgent 执行失败: {e}", exc_info=True)
            return AgentResult.failed(
                summary=f"Review 执行失败: {str(e)}",
                error=str(e),
            )

    async def _do_review(
        self,
        user_query: str,
        analysis_result: AnalysisResult,
        workflow: Workflow,
        review_stage: Literal["design", "development"],
    ) -> ReviewOutput:
        """
        调用 LLM 执行 review：with_structured_output(json_mode) + parse_structured_output 兜底
        """
        from src.infrastructure.llm.structured_output import parse_structured_output

        stage_hint = (
            "设计阶段，关注架构设计是否合理"
            if review_stage == "design"
            else "开发阶段，关注 SQL 实现是否正确"
        )

        human_content = f"""## 用户需求
{user_query}

## 当前阶段
{stage_hint}

## 需求分析结果
{analysis_result.model_dump_json(indent=2)}

## 工作流方案
{workflow.model_dump_json(indent=2)}

请 review 以上方案是否能正确实现用户需求。"""

        messages = [
            SystemMessage(content=REVIEWER_SYSTEM_PROMPT),
            HumanMessage(content=human_content),
        ]

        # 使用 json_mode（reviewer 没有工具调用，但保持一致）
        llm_structured = self.llm.with_structured_output(
            ReviewOutput,
            method="json_mode",
            include_raw=True,
        )
        result = await llm_structured.ainvoke(messages)

        # 情况 1：直接解析成功
        if isinstance(result, ReviewOutput):
            return result

        # 情况 2：dict 格式（include_raw=True 的返回）
        if isinstance(result, dict):
            parsed = result.get("parsed")
            if isinstance(parsed, ReviewOutput):
                return parsed

            # 解析失败，尝试从 raw 中恢复
            parsing_error = result.get("parsing_error")
            raw = result.get("raw")

            if raw:
                raw_text = getattr(raw, "content", None)
                if raw_text:
                    logger.warning(
                        "with_structured_output 解析失败，尝试 parse_structured_output 兜底"
                    )
                    try:
                        return parse_structured_output(raw_text, ReviewOutput)
                    except ValueError as e:
                        logger.error(f"parse_structured_output 兜底也失败: {e}")
                        raise

            if parsing_error:
                raise parsing_error

        raise ValueError(f"无法获取结构化输出: {type(result)}")
