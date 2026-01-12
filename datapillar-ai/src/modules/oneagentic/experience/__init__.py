"""
Experience 模块 - 经验学习系统 (L5)

从执行历史中学习，提供：
- Episode: 经验片段记录
- ExperienceStore: 经验存储抽象接口
- LanceExperienceStore: LanceDB 默认实现
- ExperienceRetriever: 经验检索器
- ExperienceLearner: 经验学习器

使用示例：
```python
from src.modules.oneagentic.experience import (
    Episode,
    LanceExperienceStore,
    ExperienceRetriever,
    ExperienceLearner,
)

# 初始化存储
store = LanceExperienceStore(path="./data/experience")
await store.initialize()

# 检索相似经验
retriever = ExperienceRetriever(store=store)
advice = await retriever.get_advice_for_task("创建用户宽表")

# 学习新经验
learner = ExperienceLearner(store=store, llm=llm)
episode = learner.start_episode(
    session_id="...",
    user_id="...",
    goal="创建用户宽表",
)
# ... 执行任务 ...
await learner.complete_and_learn(episode, outcome=Outcome.SUCCESS)
```

扩展实现：
用户可以实现 ExperienceStore 接口来使用其他向量数据库：
- ChromaDB
- Milvus
- Qdrant
- pgvector
"""

from src.modules.oneagentic.experience.episode import (
    Episode,
    EpisodeStep,
    Outcome,
    ValidationStatus,
)
from src.modules.oneagentic.experience.lance_store import LanceExperienceStore
from src.modules.oneagentic.experience.learner import ExperienceLearner, LearningResult
from src.modules.oneagentic.experience.policy import (
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
from src.modules.oneagentic.experience.retriever import (
    ExperienceRetriever,
    TaskAdvice,
)
from src.modules.oneagentic.experience.store import (
    ExperienceStore,
    SearchFilter,
    SearchResult,
)

__all__ = [
    # 数据模型
    "Episode",
    "EpisodeStep",
    "Outcome",
    "ValidationStatus",
    # 存储
    "ExperienceStore",
    "SearchFilter",
    "SearchResult",
    "LanceExperienceStore",
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
