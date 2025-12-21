"""
提示词优化器

根据历史案例库和失败模式动态优化 Agent 提示词。
实现自我进化的核心能力。
"""

import logging
from typing import List, Dict, Any, Optional
from dataclasses import dataclass

logger = logging.getLogger(__name__)


@dataclass
class OptimizationHint:
    """优化建议"""
    category: str  # avoidance / best_practice / pattern
    content: str
    priority: int  # 1-10，越高越重要
    source: str  # failure_pattern / success_case / rule


class PromptOptimizer:
    """
    提示词优化器

    根据以下数据源动态生成优化建议：
    1. 高频失败模式 -> 生成避免策略
    2. 高复用成功案例 -> 提取最佳实践
    3. 用户反馈统计 -> 调整生成策略

    使用方式：
    1. 在 Agent 初始化时调用 get_optimization_hints()
    2. 将返回的 hints 注入到 Agent 的系统提示词
    """

    def __init__(self):
        self._cache: Dict[str, Any] = {}
        self._cache_ttl = 300  # 5 分钟缓存

    async def get_optimization_hints(
        self,
        agent_type: str,
        limit: int = 5,
    ) -> List[OptimizationHint]:
        """
        获取针对特定 Agent 的优化建议

        Args:
            agent_type: Agent 类型（developer/architect/reviewer 等）
            limit: 返回数量

        Returns:
            优化建议列表
        """
        hints = []

        # 1. 获取高频失败模式的避免策略
        failure_hints = await self._get_failure_avoidance_hints(agent_type, limit)
        hints.extend(failure_hints)

        # 2. 获取最佳实践建议
        best_practice_hints = await self._get_best_practice_hints(agent_type, limit)
        hints.extend(best_practice_hints)

        # 按优先级排序
        hints.sort(key=lambda h: h.priority, reverse=True)

        return hints[:limit]

    async def _get_failure_avoidance_hints(
        self,
        agent_type: str,
        limit: int,
    ) -> List[OptimizationHint]:
        """从失败模式中提取避免策略"""
        from src.infrastructure.repository import KnowledgeRepository

        try:
            # 获取高频失败模式
            patterns = await KnowledgeRepository.get_top_failure_patterns(limit=limit)

            hints = []
            for pattern in patterns:
                if pattern.get("avoidance_hint"):
                    # 根据出现次数计算优先级
                    occurrence = pattern.get("occurrence_count", 1)
                    priority = min(10, 5 + occurrence // 2)

                    hints.append(OptimizationHint(
                        category="avoidance",
                        content=f"[{pattern.get('failure_type', 'unknown')}] {pattern.get('avoidance_hint')}",
                        priority=priority,
                        source="failure_pattern",
                    ))

            return hints

        except Exception as e:
            logger.error(f"获取失败避免策略失败: {e}")
            return []

    async def _get_best_practice_hints(
        self,
        agent_type: str,
        limit: int,
    ) -> List[OptimizationHint]:
        """从成功案例中提取最佳实践"""
        from src.infrastructure.repository import KnowledgeRepository

        try:
            # 获取高复用的参考 SQL
            high_use_sqls = await KnowledgeRepository.search_reference_sql(
                query="",  # 不限关键词
                limit=limit,
            )

            hints = []
            for sql_info in high_use_sqls:
                use_count = sql_info.get("use_count", 0)
                confidence = sql_info.get("confidence", 0.5)

                # 只提取高复用、高置信度的案例
                if use_count >= 2 and confidence >= 0.8:
                    tags = sql_info.get("tags", [])
                    tag_str = ", ".join(tags[:3]) if tags else "通用"

                    hints.append(OptimizationHint(
                        category="best_practice",
                        content=f"[{tag_str}] 参考历史成功案例的写法（复用 {use_count} 次）",
                        priority=min(8, 4 + use_count),
                        source="success_case",
                    ))

            return hints

        except Exception as e:
            logger.error(f"获取最佳实践失败: {e}")
            return []

    def format_hints_for_prompt(
        self,
        hints: List[OptimizationHint],
        max_hints: int = 5,
    ) -> str:
        """
        将优化建议格式化为可注入提示词的文本

        Args:
            hints: 优化建议列表
            max_hints: 最大建议数量

        Returns:
            格式化的文本
        """
        if not hints:
            return ""

        lines = ["## 优化建议（基于历史学习）"]

        # 分类整理
        avoidance_hints = [h for h in hints if h.category == "avoidance"][:max_hints // 2]
        best_practice_hints = [h for h in hints if h.category == "best_practice"][:max_hints // 2]

        if avoidance_hints:
            lines.append("\n### 避免以下问题")
            for hint in avoidance_hints:
                lines.append(f"- {hint.content}")

        if best_practice_hints:
            lines.append("\n### 最佳实践")
            for hint in best_practice_hints:
                lines.append(f"- {hint.content}")

        return "\n".join(lines)

    async def get_developer_prompt_enhancement(self) -> str:
        """
        获取 DeveloperAgent 的提示词增强

        便捷方法，直接返回格式化好的文本。
        """
        hints = await self.get_optimization_hints(agent_type="developer", limit=6)
        return self.format_hints_for_prompt(hints)

    async def get_architect_prompt_enhancement(self) -> str:
        """
        获取 ArchitectAgent 的提示词增强
        """
        hints = await self.get_optimization_hints(agent_type="architect", limit=6)
        return self.format_hints_for_prompt(hints)

    async def get_learning_stats(self) -> Dict[str, Any]:
        """
        获取学习统计信息

        用于展示自我进化的效果。
        """
        from src.infrastructure.repository import KnowledgeRepository

        try:
            # 获取失败模式统计
            failure_patterns = await KnowledgeRepository.get_top_failure_patterns(limit=100)
            total_failures = sum(p.get("occurrence_count", 0) for p in failure_patterns)

            # 获取成功案例统计
            success_cases = await KnowledgeRepository.search_reference_sql(query="", limit=100)
            total_uses = sum(c.get("use_count", 0) for c in success_cases)
            high_confidence_cases = len([c for c in success_cases if c.get("confidence", 0) >= 0.9])

            return {
                "failure_patterns_count": len(failure_patterns),
                "total_failure_occurrences": total_failures,
                "success_cases_count": len(success_cases),
                "total_case_uses": total_uses,
                "high_confidence_cases": high_confidence_cases,
                "learning_effectiveness": self._calculate_effectiveness(
                    failure_patterns, success_cases
                ),
            }

        except Exception as e:
            logger.error(f"获取学习统计失败: {e}")
            return {
                "error": str(e),
            }

    def _calculate_effectiveness(
        self,
        failure_patterns: List[Dict],
        success_cases: List[Dict],
    ) -> float:
        """
        计算学习效果评分

        基于：
        - 成功案例复用率
        - 失败模式覆盖率
        - 高置信度案例占比
        """
        if not success_cases:
            return 0.0

        # 复用率：有复用的案例占比
        used_cases = len([c for c in success_cases if c.get("use_count", 0) > 0])
        reuse_rate = used_cases / len(success_cases) if success_cases else 0

        # 高置信度占比
        high_conf_cases = len([c for c in success_cases if c.get("confidence", 0) >= 0.8])
        confidence_rate = high_conf_cases / len(success_cases) if success_cases else 0

        # 综合评分
        effectiveness = (reuse_rate * 0.4 + confidence_rate * 0.6)
        return round(effectiveness, 2)


# 全局单例
_optimizer: Optional[PromptOptimizer] = None


def get_prompt_optimizer() -> PromptOptimizer:
    """获取全局 PromptOptimizer 实例"""
    global _optimizer
    if _optimizer is None:
        _optimizer = PromptOptimizer()
    return _optimizer
