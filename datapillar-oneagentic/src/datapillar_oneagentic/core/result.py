"""
DatapillarResult - 执行结果

封装团队执行的结果，包括各 Agent 的交付物、耗时等。
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any


@dataclass
class DatapillarResult:
    """
    执行结果

    使用示例：
    ```python
    result = await team.kickoff(inputs={"query": "..."})

    # 获取特定 Agent 的输出（用 agent_id）
    analysis = result.get_deliverable("analyst")
    code = result.get_deliverable("coder")

    # 或直接访问
    result.deliverables["analyst"]

    # 获取最后一个 Agent 的输出
    result.final_deliverable
    ```
    """

    success: bool
    """是否成功"""

    deliverables: dict[str, Any]
    """各 Agent 交付物 {agent_id: deliverable}"""

    duration_ms: int = 0
    """总耗时"""

    error: str | None = None
    """错误信息（如果失败）"""

    def get_deliverable(self, key: str) -> Any:
        """获取指定 Agent 的交付物"""
        return self.deliverables.get(key)

    @property
    def final_deliverable(self) -> Any:
        """获取最后一个 Agent 的交付物"""
        if self.deliverables:
            return list(self.deliverables.values())[-1]
        return None
