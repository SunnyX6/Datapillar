"""
AnalystAgent - 需求分析师（入口 Agent）

职责：
1. 入口接待：友好回应闲聊/问候
2. 智能分发：元数据查询 → catalog_agent
3. 需求分析：ETL 需求自己处理 → architect_agent
"""

from __future__ import annotations

from typing import TYPE_CHECKING

from pydantic import BaseModel, Field, field_validator

from datapillar_oneagentic import agent
from src.modules.etl.schemas.analyst import _try_parse_json
from src.modules.etl.tools.table import get_table_detail, search_tables

if TYPE_CHECKING:
    from datapillar_oneagentic import AgentContext


# ==================== 输出 Schema ====================


class StepOutput(BaseModel):
    """业务步骤"""

    step_id: str = Field(..., description="步骤唯一标识")
    step_name: str = Field(..., description="步骤名称")
    description: str = Field(..., description="这一步做什么")
    input_tables: list[str] = Field(
        default_factory=list, description="输入表列表（catalog.schema.table）"
    )
    output_table: str = Field("", description="输出表（catalog.schema.table）")
    depends_on: list[str] = Field(default_factory=list, description="依赖的上游步骤 ID")


class FinalTarget(BaseModel):
    """最终数据目标"""

    table_name: str = Field(..., description="目标表名")
    write_mode: str = Field("overwrite", description="写入模式：overwrite/append/upsert")
    description: str = Field("", description="描述")


class AnalysisOutput(BaseModel):
    """需求分析输出"""

    summary: str = Field(..., description="一句话概括用户需求")
    confidence: float = Field(..., ge=0, le=1, description="需求明确程度 (0-1)")
    steps: list[StepOutput] = Field(default_factory=list, description="业务步骤列表")
    final_target: FinalTarget | None = Field(None, description="最终数据目标")
    ambiguities: list[str] = Field(default_factory=list, description="需要澄清的问题")
    recommendations: list[str] = Field(default_factory=list, description="推荐引导")

    @field_validator("final_target", mode="before")
    @classmethod
    def _parse_final_target(cls, v: object) -> object:
        return _try_parse_json(v)


# ==================== Agent 定义 ====================


@agent(
    id="analyst",
    name="需求分析师",
    description="入口接待、智能分发、ETL 需求分析",
    tools=[search_tables, get_table_detail],
    deliverable_schema=AnalysisOutput,
    temperature=0.0,
    max_steps=5,
)
class AnalystAgent:
    """需求分析师（入口 Agent）"""

    SYSTEM_PROMPT = """你是 Datapillar 的智能助手，同时也是需求分析师。

## 你的职责
1. 处理用户闲聊，并给出友好提示
2. **智能分发**：识别用户意图，将请求交给合适的专家
   - 元数据查询（查表、查字段、查血缘）→ 委派给元数据专员
   - ETL 需求（创建表、数据加工）→ 完成分析后委派给数据架构师

3. **ETL 需求分析**：将 ETL 需求拆分为可执行的业务步骤

## 工作流程

1. 判断用户意图
2. 元数据查询 → 委派给元数据专员
3. 当处理etl需求时，需要调用相关工具获取知识 → 拆分步骤 → 委派给数据架构师
4. 信息不足 → 输出 JSON 并在 ambiguities 中列出问题

## 输出格式（始终使用此 JSON 格式）

{
  "summary": "一句话概括（闲聊时填回复内容，ETL时填需求概括）",
  "confidence": 0.8,
  "steps": [
    {
      "step_id": "s1",
      "step_name": "步骤名称",
      "description": "这一步做什么",
      "input_tables": ["catalog.schema.table"],
      "output_table": "catalog.schema.output",
      "depends_on": []
    }
  ],
  "final_target": {
    "table_name": "目标表名",
    "write_mode": "overwrite",
    "description": "描述"
  },
  "ambiguities": ["需要澄清的ETL问题"],
  "recommendations": ["继续细化目标表口径", "确认写入模式"]
}

## 字段说明

- **summary**: 必填，一句话概括
- **confidence**: 必填，0-1 之间
  - >= 0.7：需求明确，可以委派给架构师
  - < 0.7：信息不足，需要澄清
- **steps**: ETL 需求时填写，其他情况为空数组
- **final_target**: ETL 需求时填写，其他情况为 null
- **ambiguities**: 需要用户澄清的ETL问题列表
- **recommendations**: 向用户的推荐，不是问题澄清！

## 重要约束

1. 必须输出 JSON 格式
2. 元数据查询直接委派给元数据专员，不需要输出 JSON
3. 不允许臆造表名，必须通过工具验证
4. confidence >= 0.7 时委派给数据架构师
5. **委派时必须在任务描述中携带分析结论**（summary/steps/final_target/ambiguities）

## 需求分析方法论

### 分析原则
1. **收敛优先**：需求必须在此阶段收敛，不允许模糊需求往后传
2. **验证为先**：涉及的表必须通过工具验证存在
3. **业务聚焦**：只关心"做什么"，不关心"怎么做"

### Step 拆分原则
- 每个 Step 代表一个独立的业务逻辑单元
- Step 之间通过 depends_on 建立依赖关系
- input_tables 必须是完整路径（三段式格式）

"""

    async def run(self, ctx: AgentContext) -> AnalysisOutput:
        """执行分析"""
        # 1. 构建消息
        messages = ctx.build_messages(self.SYSTEM_PROMPT)

        # 2. 工具调用循环（委派由框架自动处理）
        messages = await ctx.invoke_tools(messages)

        # 3. 获取结构化输出
        output = await ctx.get_structured_output(messages)

        # 4. 业务判断：需要澄清？
        if output.confidence < 0.7 and output.ambiguities:
            ctx.interrupt(
                {
                    "message": "",
                    "questions": output.ambiguities,
                }
            )
            output = await ctx.get_structured_output(messages)

        return output
