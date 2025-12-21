"""
自我进化学习模块

实现反馈收集、案例沉淀、失败分析、提示词优化等学习能力。
"""

from src.modules.etl.learning.feedback import (
    Feedback,
    FeedbackRating,
    FeedbackCollector,
)
from src.modules.etl.learning.failure_analyzer import (
    FailureAnalysis,
    FailureType,
    FailureAnalyzer,
)
from src.modules.etl.learning.learning_loop import LearningLoop
from src.modules.etl.learning.prompt_optimizer import (
    PromptOptimizer,
    OptimizationHint,
    get_prompt_optimizer,
)

__all__ = [
    "Feedback",
    "FeedbackRating",
    "FeedbackCollector",
    "FailureAnalysis",
    "FailureType",
    "FailureAnalyzer",
    "LearningLoop",
    "PromptOptimizer",
    "OptimizationHint",
    "get_prompt_optimizer",
]
