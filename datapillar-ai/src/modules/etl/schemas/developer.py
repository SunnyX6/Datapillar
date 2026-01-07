"""
DeveloperAgent 结构化输出 Schema

说明：
- DeveloperAgent 需要产出可执行 SQL 脚本
- 为了统一"结构化输出"协议，使用 Pydantic Schema 让 LLM 通过 function calling 返回结果
- 支持 confidence 和 issues 字段，让 LLM 表达不确定性
"""

from pydantic import BaseModel, Field


class DeveloperSqlOutput(BaseModel):
    """LLM 输出：SQL 脚本"""

    sql: str = Field(..., description="完整 SQL 脚本（包含所有 Stage，最终写入目标表）")
    confidence: float = Field(
        default=0.8,
        ge=0.0,
        le=1.0,
        description="SQL 生成置信度，存在不确定的字段映射/JOIN 条件时应 < 0.8",
    )
    issues: list[str] = Field(
        default_factory=list,
        description="需要用户确认的问题列表，如：字段映射不明确、JOIN 条件需确认、类型转换方式待定等",
    )
