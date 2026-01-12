"""
ETL 模块 - 智能 ETL 工作流生成

导入此模块时，Agent 和工具自动注册到框架。

使用示例：
```python
# 导入 ETL 模块（触发 Agent 和工具注册）
from src.modules.etl.agents import create_etl_team

# 创建团队
team = create_etl_team()

# 流式执行
async for event in team.stream(
    query="创建用户宽表",
    session_id="session_001",
    user_id="user_001",
):
    print(event)
```
"""

# 导入 agents 模块触发 Agent 注册
from src.modules.etl import agents as _agents  # noqa: F401

# 导出 router 供路由自动注册
from src.modules.etl.api import router

__all__ = ["router"]
