"""
DeveloperAgent 结构化输出 Schema

说明：
- DeveloperAgent 需要产出可执行 SQL 脚本
- 为了统一“结构化输出”协议，使用 Pydantic Schema 让 LLM 通过 function calling 返回结果
"""

from pydantic import BaseModel, Field


class DeveloperSqlOutput(BaseModel):
    """LLM 输出：SQL 脚本"""

    sql: str = Field(..., description="完整 SQL 脚本（包含所有 Stage，最终写入目标表）")
