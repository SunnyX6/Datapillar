"""
Experience 模块 - 经验学习系统

职责：
1. 自动记录执行过程
2. 使用者调用 save_experience 保存（包含 feedback）
3. 检索相似经验，自动拼接上下文

使用示例：
```python
from datapillar_oneagentic import Datapillar, DatapillarConfig

config = DatapillarConfig(
    llm={"api_key": "sk-xxx", "model": "gpt-4o"},
    embedding={"api_key": "sk-xxx", "model": "text-embedding-3-small"},
)

team = Datapillar(
    config=config,
    agents=[...],
    enable_learning=True,
)

# 执行任务（框架自动记录）
async for event in team.stream(query="分析销售数据", session_id="s001"):
    ...

# 保存经验（包含用户反馈）
await team.save_experience(
    session_id="s001",
    feedback={"stars": 5, "comment": "很好用"},
)

# 不调用 save_experience = 不保存
```

数据模型：
```python
ExperienceRecord:
    id: str               # 记录 ID
    namespace: str        # 命名空间（隔离不同团队）
    session_id: str       # 会话 ID
    goal: str             # 用户目标
    outcome: str          # 执行结果
    result_summary: str   # 结果摘要
    tools_used: list      # 使用的工具
    agents_involved: list # 参与的 Agent
    duration_ms: int      # 执行时长
    feedback: dict        # 用户反馈
    created_at: int       # 创建时间
```
"""

from datapillar_oneagentic.experience.learner import (
    ExperienceLearner,
    ExperienceRecord,
)
from datapillar_oneagentic.experience.retriever import (
    ExperienceRetriever,
)

__all__ = [
    # 数据结构
    "ExperienceRecord",
    # 学习器
    "ExperienceLearner",
    # 检索器
    "ExperienceRetriever",
]
