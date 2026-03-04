# @author Sunny
# @date 2026-01-27

"""
ETL module - Smart ETL Workflow generation

When importing this module,Agent and tools are automatically registered to the framework.Usage example:```python
# import ETL module(trigger Agent and tool registration)
from src.modules.etl.agents import create_etl_team

# Create a team
team = create_etl_team()

# Streaming execution
async for event in team.stream(query="Create user wide table",session_id="session_001",):print(event)
```
"""

# import agents module trigger Agent Register
from src.modules.etl import agents as _agents  # noqa:F401

# Export router For automatic registration of routes
from src.modules.etl.api import router

MODULE_SCOPE = "biz"

__all__ = ["router", "MODULE_SCOPE"]
