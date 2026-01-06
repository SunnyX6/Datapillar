"""
ETL 多智能体系统

智能体列表：
- KnowledgeAgent: 知识检索专家（LLM + Tools）
- AnalystAgent: 需求分析师（需求收敛 + 表存在性验证）
- ArchitectAgent: 数据架构师（Job/Stage 规划 + 组件选择）
- DeveloperAgent: 数据开发（SQL 生成 + 血缘参考）
- ReviewerAgent: 代码评审（设计/代码 review）

================================================================================
LLM 调用规范（所有 Agent 必须遵循）
================================================================================

## 两阶段调用模式

每个 Agent 的 LLM 调用分为两个阶段：

### 第一阶段：工具调用（可选，0-N 次 LLM 调用）

```python
llm_with_tools = self.llm.bind_tools([get_table_detail, ...])

for _ in range(self.max_iterations):
    response = await llm_with_tools.ainvoke(messages)

    if not response.tool_calls:
        break  # 没有工具调用，进入第二阶段

    # 执行工具调用，结果放入 ToolMessage
    messages.append(response)
    for tc in response.tool_calls:
        result = await self._execute_tool(tc["name"], tc["args"])
        messages.append(ToolMessage(content=result, tool_call_id=tc["id"]))
```

### 第二阶段：结构化输出（1 次 LLM 调用）

```python
# 使用 json_mode（不是 function_calling，避免和工具调用混淆）
llm_structured = self.llm.with_structured_output(
    schema,
    method="json_mode",
    include_raw=True,
)
result = await llm_structured.ainvoke(messages)

# 解析结果，失败时用 parse_structured_output 兜底
if isinstance(result, dict):
    parsed = result.get("parsed")
    if parsed:
        return parsed
    # 从 raw 中兜底解析
    raw_text = getattr(result.get("raw"), "content", None)
    if raw_text:
        return parse_structured_output(raw_text, schema)
```

## 关键约束

1. **method="json_mode"**：不用 function_calling，避免和工具调用混淆
2. **include_raw=True**：保留原始响应，用于兜底解析
3. **ToolMessage**：工具结果必须放入 ToolMessage，不要拼接到 content
4. **max_iterations**：工具调用阶段有最大迭代次数限制，防止无限循环

## LLM 调用次数分析

单个 Agent 的 LLM 调用次数 = 工具调用轮次 + 1（结构化输出）

典型场景：
- 无工具调用：1 次（直接输出结果）
- 1 轮工具调用：2 次（调用工具 + 输出结果）
- N 轮工具调用：N+1 次

完整流程（AnalystAgent → ArchitectAgent → DeveloperAgent → ReviewerAgent）：
- 最少：4 次（每个 Agent 1 次，无工具调用）
- 典型：8-12 次（每个 Agent 平均 2-3 次）
- 最多：受 max_iterations 限制

================================================================================
"""

from src.modules.etl.agents.analyst_agent import AnalystAgent
from src.modules.etl.agents.architect_agent import ArchitectAgent
from src.modules.etl.agents.developer_agent import DeveloperAgent
from src.modules.etl.agents.knowledge_agent import KnowledgeAgent
from src.modules.etl.agents.reviewer_agent import ReviewerAgent

__all__ = [
    "KnowledgeAgent",
    "AnalystAgent",
    "ArchitectAgent",
    "DeveloperAgent",
    "ReviewerAgent",
]
