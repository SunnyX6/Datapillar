"""
Experience 模块 - 经验学习系统

从执行历史中学习，提供：
- Episode: 经验片段记录
- ExperienceRetriever: 经验检索器
- ExperienceLearner: 经验学习器

存储使用 storage/learning_stores 的 VectorStore。

使用示例：
```python
from datapillar_oneagentic.experience import (
    Episode,
    ExperienceRetriever,
    ExperienceLearner,
)
from datapillar_oneagentic.storage.learning_stores import LanceVectorStore

# 初始化存储
store = LanceVectorStore(path="./data/experience")
await store.initialize()

# 检索相似经验
retriever = ExperienceRetriever(store=store)
advice = await retriever.get_advice_for_task("分析销售数据")

# 学习新经验
learner = ExperienceLearner(store=store, llm=llm)
episode = learner.start_episode(
    session_id="...",
    user_id="...",
    goal="分析销售数据",
)
# ... 执行任务 ...
await learner.complete_and_learn(episode, outcome=Outcome.SUCCESS)
```
"""

from datapillar_oneagentic.experience.episode import (
    Episode,
    EpisodeStep,
    Outcome,
    ValidationStatus,
)
from datapillar_oneagentic.experience.learner import ExperienceLearner, LearningResult
from datapillar_oneagentic.experience.policy import (
    AlwaysSavePolicy,
    CompositePolicy,
    DefaultSedimentationPolicy,
    FeedbackAwareSedimentationPolicy,
    NeverSavePolicy,
    QualityThresholdPolicy,
    SedimentationDecision,
    SedimentationPolicy,
    TaskTypePolicy,
)
from datapillar_oneagentic.experience.retriever import (
    ExperienceRetriever,
    TaskAdvice,
)

__all__ = [
    # 数据模型
    "Episode",
    "EpisodeStep",
    "Outcome",
    "ValidationStatus",
    # 检索
    "ExperienceRetriever",
    "TaskAdvice",
    # 学习
    "ExperienceLearner",
    "LearningResult",
    # 沉淀策略
    "SedimentationPolicy",
    "SedimentationDecision",
    "DefaultSedimentationPolicy",
    "FeedbackAwareSedimentationPolicy",
    "AlwaysSavePolicy",
    "NeverSavePolicy",
    "CompositePolicy",
    "TaskTypePolicy",
    "QualityThresholdPolicy",
]
