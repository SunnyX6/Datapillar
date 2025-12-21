"""
学习循环模块

根据用户反馈进行学习，沉淀成功案例，分析失败原因。
"""

import logging
from typing import Optional, Dict, Any
from datetime import datetime

from src.modules.etl.learning.feedback import Feedback, FeedbackRating
from src.modules.etl.learning.failure_analyzer import FailureAnalyzer, FailureAnalysis
from src.modules.etl.memory.case_library import EtlCase, CaseLibrary

logger = logging.getLogger(__name__)


class LearningLoop:
    """
    学习循环

    根据用户反馈进行学习：
    1. 满意 → 保存成功案例到 Neo4j
    2. 不满意 → 分析失败原因，记录失败案例
    3. 需要修改 → 保存用户修改后的版本
    """

    def __init__(self, case_library: CaseLibrary):
        self.case_library = case_library
        self.failure_analyzer = FailureAnalyzer()

    async def learn_from_feedback(
        self,
        feedback: Feedback,
        user_query: str,
        sql_text: Optional[str],
        source_tables: list[str],
        target_tables: list[str],
        intent: str,
        session_id: str,
    ) -> Dict[str, Any]:
        """
        根据反馈进行学习

        Args:
            feedback: 用户反馈
            user_query: 用户原始查询
            sql_text: 生成的 SQL
            source_tables: 源表列表
            target_tables: 目标表列表
            intent: ETL 意图
            session_id: 会话 ID

        Returns:
            学习结果
        """
        case_id = f"{session_id}_{datetime.utcnow().strftime('%Y%m%d%H%M%S')}"

        if feedback.rating == FeedbackRating.SATISFIED:
            return await self._learn_from_success(
                case_id=case_id,
                user_query=user_query,
                sql_text=sql_text,
                source_tables=source_tables,
                target_tables=target_tables,
                intent=intent,
                confidence=0.9,
            )

        elif feedback.rating == FeedbackRating.UNSATISFIED:
            return await self._learn_from_failure(
                case_id=case_id,
                user_query=user_query,
                sql_text=sql_text,
                source_tables=source_tables,
                target_tables=target_tables,
                intent=intent,
                user_feedback=feedback.comment,
            )

        elif feedback.rating == FeedbackRating.NEED_MODIFICATION:
            # 使用用户修改后的 SQL
            final_sql = feedback.modified_sql or sql_text
            return await self._learn_from_success(
                case_id=case_id,
                user_query=user_query,
                sql_text=final_sql,
                source_tables=source_tables,
                target_tables=target_tables,
                intent=intent,
                confidence=0.95,  # 用户确认的置信度更高
                user_feedback=feedback.comment,
            )

        else:
            logger.info("用户跳过反馈，不进行学习")
            return {"action": "skipped", "message": "用户跳过反馈"}

    async def _learn_from_success(
        self,
        case_id: str,
        user_query: str,
        sql_text: Optional[str],
        source_tables: list[str],
        target_tables: list[str],
        intent: str,
        confidence: float,
        user_feedback: Optional[str] = None,
    ) -> Dict[str, Any]:
        """从成功案例中学习"""
        logger.info(f"学习成功案例: {case_id}")

        if not sql_text:
            logger.warning("没有 SQL 文本，跳过保存")
            return {"action": "skipped", "message": "没有 SQL 文本"}

        # 构建案例
        case = EtlCase(
            case_id=case_id,
            user_query=user_query,
            source_tables=source_tables,
            target_tables=target_tables,
            intent=intent,
            sql_text=sql_text,
            is_success=True,
            user_feedback=user_feedback,
            tags=self._generate_tags(intent, source_tables, target_tables),
        )

        # 保存到 Neo4j
        success = await self.case_library.save_case(case)

        if success:
            logger.info(f"成功案例已保存到 Neo4j: {case_id}")
            return {
                "action": "saved_success_case",
                "case_id": case_id,
                "confidence": confidence,
            }
        else:
            logger.error(f"保存成功案例失败: {case_id}")
            return {"action": "save_failed", "case_id": case_id}

    async def _learn_from_failure(
        self,
        case_id: str,
        user_query: str,
        sql_text: Optional[str],
        source_tables: list[str],
        target_tables: list[str],
        intent: str,
        user_feedback: Optional[str] = None,
    ) -> Dict[str, Any]:
        """从失败案例中学习"""
        logger.info(f"分析失败案例: {case_id}")

        # 使用智能分析（规则 + LLM）
        error_message = user_feedback or "用户反馈不满意"
        analysis = await self.failure_analyzer.smart_analyze(
            error_message=error_message,
            sql_text=sql_text,
            user_query=user_query,
            user_feedback=user_feedback,
        )

        # 持久化失败模式到 Neo4j
        from src.infrastructure.repository import KnowledgeRepository
        await KnowledgeRepository.persist_failure_pattern(
            failure_type=analysis.failure_type.value,
            pattern=analysis.error_message[:200],  # 截断
            avoidance_hint=analysis.avoidance_hint or "",
            root_cause=analysis.root_cause,
            involved_tables=analysis.involved_tables,
            involved_columns=analysis.involved_columns,
            examples=[sql_text] if sql_text else [],
        )

        # 构建失败案例
        case = EtlCase(
            case_id=case_id,
            user_query=user_query,
            source_tables=source_tables,
            target_tables=target_tables,
            intent=intent,
            sql_text=sql_text,
            is_success=False,
            error_message=analysis.root_cause or error_message,
            user_feedback=user_feedback,
            tags=self._generate_tags(intent, source_tables, target_tables) + ["failed"],
        )

        # 保存失败案例（只保存到本地，不写入 Neo4j）
        self.case_library._local_cases.append(case)

        logger.info(
            f"失败案例已记录: {case_id}, "
            f"类型: {analysis.failure_type.value}, "
            f"避免策略: {analysis.avoidance_hint}"
        )

        return {
            "action": "saved_failure_case",
            "case_id": case_id,
            "failure_analysis": {
                "type": analysis.failure_type.value,
                "root_cause": analysis.root_cause,
                "avoidance_hint": analysis.avoidance_hint,
            },
        }

    def _generate_tags(
        self,
        intent: str,
        source_tables: list[str],
        target_tables: list[str],
    ) -> list[str]:
        """生成案例标签"""
        tags = [intent]

        # 根据表数量添加标签
        if len(source_tables) > 1:
            tags.append("multi_source")
        if len(target_tables) > 1:
            tags.append("multi_target")

        # 根据意图添加标签
        intent_lower = intent.lower()
        if "join" in intent_lower:
            tags.append("join")
        if "agg" in intent_lower or "sum" in intent_lower or "count" in intent_lower:
            tags.append("aggregation")
        if "filter" in intent_lower or "where" in intent_lower:
            tags.append("filter")
        if "transform" in intent_lower:
            tags.append("transform")

        return tags

    async def get_learning_stats(self) -> Dict[str, Any]:
        """获取学习统计"""
        local_cases = self.case_library.get_local_cases()
        success_cases = [c for c in local_cases if c.is_success]
        failed_cases = [c for c in local_cases if not c.is_success]

        return {
            "total_cases": len(local_cases),
            "success_cases": len(success_cases),
            "failed_cases": len(failed_cases),
            "success_rate": len(success_cases) / max(len(local_cases), 1),
        }
