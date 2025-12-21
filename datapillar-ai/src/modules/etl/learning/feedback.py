"""
反馈收集模块

收集用户对生成结果的反馈，用于学习和优化。
"""

import logging
from typing import Optional, Dict, Any
from enum import Enum
from pydantic import BaseModel, Field

from langgraph.types import interrupt

logger = logging.getLogger(__name__)


class FeedbackRating(str, Enum):
    """反馈评分"""
    SATISFIED = "satisfied"           # 满意，直接采纳
    UNSATISFIED = "unsatisfied"       # 不满意，需要分析原因
    NEED_MODIFICATION = "need_modification"  # 需要修改
    SKIP = "skip"                     # 跳过反馈


class Feedback(BaseModel):
    """用户反馈"""
    rating: FeedbackRating
    comment: Optional[str] = None
    modified_sql: Optional[str] = None
    modified_plan: Optional[Dict[str, Any]] = None

    @property
    def is_positive(self) -> bool:
        """是否正向反馈"""
        return self.rating in (FeedbackRating.SATISFIED, FeedbackRating.NEED_MODIFICATION)

    @property
    def has_modification(self) -> bool:
        """是否有修改"""
        return self.modified_sql is not None or self.modified_plan is not None


class FeedbackCollector:
    """
    反馈收集器

    使用 LangGraph 的 interrupt() 机制暂停工作流，
    等待用户提供反馈。
    """

    def __init__(self):
        self._feedback_history: list[Feedback] = []

    def collect_feedback(
        self,
        result_summary: str,
        sql_preview: Optional[str] = None,
    ) -> Feedback:
        """
        收集用户反馈

        使用 interrupt() 暂停工作流，等待用户输入。

        Args:
            result_summary: 结果摘要，展示给用户
            sql_preview: SQL 预览（可选）

        Returns:
            用户反馈
        """
        logger.info("等待用户反馈...")

        # 构建反馈请求
        feedback_request = {
            "type": "feedback_request",
            "message": "请对生成结果进行评价",
            "result_summary": result_summary,
            "options": [
                {"value": "satisfied", "label": "👍 满意，直接采纳"},
                {"value": "unsatisfied", "label": "👎 不满意，重新生成"},
                {"value": "need_modification", "label": "✏️ 需要修改"},
                {"value": "skip", "label": "⏭️ 跳过"},
            ],
        }

        if sql_preview:
            feedback_request["sql_preview"] = sql_preview

        # 使用 interrupt 暂停，等待用户输入
        user_response = interrupt(feedback_request)

        # 解析用户响应
        feedback = self._parse_response(user_response)

        # 记录反馈历史
        self._feedback_history.append(feedback)

        logger.info(f"收到用户反馈: {feedback.rating.value}")
        return feedback

    def _parse_response(self, response: Any) -> Feedback:
        """解析用户响应"""
        if isinstance(response, dict):
            rating_str = response.get("rating", "skip")
            try:
                rating = FeedbackRating(rating_str)
            except ValueError:
                rating = FeedbackRating.SKIP

            return Feedback(
                rating=rating,
                comment=response.get("comment"),
                modified_sql=response.get("modified_sql"),
                modified_plan=response.get("modified_plan"),
            )
        elif isinstance(response, str):
            try:
                rating = FeedbackRating(response)
            except ValueError:
                rating = FeedbackRating.SKIP
            return Feedback(rating=rating)
        else:
            return Feedback(rating=FeedbackRating.SKIP)

    def get_feedback_history(self) -> list[Feedback]:
        """获取反馈历史"""
        return self._feedback_history.copy()

    def get_positive_feedback_count(self) -> int:
        """获取正向反馈数量"""
        return sum(1 for f in self._feedback_history if f.is_positive)

    def get_negative_feedback_count(self) -> int:
        """获取负向反馈数量"""
        return sum(1 for f in self._feedback_history if not f.is_positive)

    def clear_history(self) -> None:
        """清空反馈历史"""
        self._feedback_history.clear()
