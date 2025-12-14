"""
Analyst Agent（需求分析师）

核心理念：从业务角度拆分需求，分而治之
- 用户需求 → 拆成几个业务步骤 → 每个步骤一个 Step
- 每个 Step 可以包含多个 Stage（SQL）
"""

import json
import logging
from typing import Optional

from langchain_core.messages import AIMessage, HumanMessage
from langgraph.types import Command

from src.agent.etl_agents.schemas.state import AgentState
from src.agent.etl_agents.schemas.requirement import AnalysisResult, Step, Stage
from src.integrations.llm import call_llm

logger = logging.getLogger(__name__)


ANALYST_AGENT_PROMPT = """你是资深数据需求分析师，负责将用户的 ETL 需求拆分为业务步骤。

## 核心理念

**从业务角度拆分，分而治之。**

## 两层拆分

### 第一层：拆分业务步骤（Step）
- 核心问题：完成这个需求需要几个业务步骤？
- 每个 Step 是一个独立的业务步骤，对应前端一个节点

### 第二层：拆分 Stage 任务（Stage）
- 核心问题：实现这个业务步骤需要几个 Stage？
- 每个 Stage 是一个任务，产出一个表

## Step 拆分原则

什么时候拆分多个 Step：
- 需求涉及多个独立的业务步骤
- 某个中间结果会被多次使用
- 有多个输出目标

什么时候只需要一个 Step：
- 单表简单查询

## Stage 拆分原则

什么时候一个 Step 需要多个 Stage：
- 需要先过滤/清洗数据，再做聚合
- 需要先聚合，再关联其他表
- 逻辑复杂，拆成多步更清晰
- 中间结果需要被同 Step 内多次使用

什么时候一个 Step 只需要一个 Stage：
- 逻辑简单，一个 SQL 就能搞定

## 示例

### 简单需求
```
需求：查询订单金额大于1000的VIP用户

Step 1: 查询高额订单VIP用户
  └─ Stage 1: 过滤查询 → 输出结果表
```

### 中等需求（注意：Step 1 有 2 个 Stage）
```
需求：计算每个用户的月度GMV

Step 1: 订单月度汇总
  └─ Stage 1: 过滤有效订单 → tmp.tmp_valid_orders
  └─ Stage 2: 按用户月聚合 → tmp.tmp_user_monthly

Step 2: 关联用户输出
  └─ Stage 1: 关联用户维度表 → dwd.dwd_user_monthly_gmv
```

### 复杂需求（注意：Step 1 有 5 个 Stage）
```
需求：用户消费分析报表，包含消费金额、消费频次、客单价

Step 1: 计算消费指标
  └─ Stage 1: 过滤有效订单 → tmp.tmp_valid_orders
  └─ Stage 2: 计算消费金额（SUM） → tmp.tmp_amount
  └─ Stage 3: 计算消费频次（COUNT） → tmp.tmp_freq
  └─ Stage 4: 计算客单价（金额/频次） → tmp.tmp_avg_price
  └─ Stage 5: 合并三个指标 → tmp.tmp_user_metrics

Step 2: 关联用户信息输出
  └─ Stage 1: 关联用户维度表 → dwd.dwd_user_consume_report
```

## 知识上下文

### 相关表
{tables_info}

### JOIN 关系
{join_info}

## 用户需求
{user_query}

## 输出要求

请按以下 JSON 格式输出：

```json
{{
  "user_query": "用户原始输入",
  "summary": "一句话概括需求",
  "steps": [
    {{
      "step_id": "step_1",
      "step_name": "订单月度汇总",
      "description": "清洗订单数据并按用户月聚合",
      "stages": [
        {{
          "stage_id": 1,
          "name": "过滤有效订单",
          "description": "过滤状态为已完成的订单",
          "input_tables": ["ods.ods_order"],
          "output_table": "tmp.tmp_valid_orders",
          "is_temp_table": true
        }},
        {{
          "stage_id": 2,
          "name": "按用户月聚合",
          "description": "按用户ID和月份聚合订单金额",
          "input_tables": ["tmp.tmp_valid_orders"],
          "output_table": "tmp.tmp_user_monthly",
          "is_temp_table": true
        }}
      ],
      "depends_on": [],
      "output_table": "tmp.tmp_user_monthly",
      "suggested_component": "hive"
    }},
    {{
      "step_id": "step_2",
      "step_name": "关联用户输出",
      "description": "关联用户信息并输出到目标表",
      "stages": [
        {{
          "stage_id": 1,
          "name": "关联用户维度表",
          "description": "将月度汇总数据与用户维度表关联",
          "input_tables": ["tmp.tmp_user_monthly", "dim.dim_user"],
          "output_table": "dwd.dwd_user_monthly_gmv",
          "is_temp_table": false
        }}
      ],
      "depends_on": ["step_1"],
      "output_table": "dwd.dwd_user_monthly_gmv",
      "suggested_component": "hive"
    }}
  ],
  "final_target": {{
    "table_name": "dwd.dwd_user_monthly_gmv",
    "write_mode": "overwrite",
    "partition_by": ["dt"]
  }},
  "ambiguities": [],
  "confidence": 0.85
}}
```

## 注意事项
1. 不要偷懒！认真分析每个 Step 需要几个 Stage
2. 复杂逻辑必须拆分多个 Stage，不要把所有逻辑塞进一个 Stage
3. 临时表命名规范：tmp.tmp_xxx
4. 只输出 JSON，不要解释
"""


class AnalystAgent:
    """
    需求分析师

    职责：
    1. 从业务角度将需求拆分为 Step（业务步骤）
    2. 每个 Step 可以包含多个 Stage
    3. 分而治之，为后续 SQL 生成做好铺垫
    """

    def __init__(self):
        self.llm = call_llm(temperature=0.0)

    async def __call__(self, state: AgentState) -> Command:
        """执行需求分析"""
        user_query = state.user_input
        knowledge_context = state.knowledge_context

        if not user_query:
            return Command(
                update={
                    "messages": [AIMessage(content="缺少用户输入，无法分析需求")],
                    "current_agent": "analyst_agent",
                    "error": "缺少用户输入",
                }
            )

        logger.info(f"📋 AnalystAgent 开始分析需求: {user_query}")

        try:
            context_info = self._format_context(knowledge_context)

            prompt = ANALYST_AGENT_PROMPT.format(
                tables_info=context_info["tables"],
                join_info=context_info["joins"],
                user_query=user_query,
            )

            response = await self.llm.ainvoke([HumanMessage(content=prompt)])
            result_dict = self._parse_response(response.content)

            analysis_result = self._build_analysis_result(result_dict, user_query)
            analysis_result = self._validate_and_enrich(analysis_result, knowledge_context)

            plan_summary = analysis_result.get_execution_plan_summary()
            logger.info(f"✅ AnalystAgent 完成分析:\n{plan_summary}")

            if analysis_result.needs_clarification():
                questions = [a.question for a in analysis_result.ambiguities]
                return Command(
                    update={
                        "messages": [AIMessage(content="需求分析完成，有以下问题需要澄清")],
                        "analysis_result": analysis_result.model_dump(),
                        "current_agent": "analyst_agent",
                        "needs_clarification": True,
                        "clarification_questions": questions,
                    }
                )

            return Command(
                update={
                    "messages": [AIMessage(content=f"需求分析完成: {analysis_result.summary}")],
                    "analysis_result": analysis_result.model_dump(),
                    "current_agent": "analyst_agent",
                }
            )

        except Exception as e:
            logger.error(f"AnalystAgent 分析失败: {e}", exc_info=True)
            return Command(
                update={
                    "messages": [AIMessage(content=f"需求分析失败: {str(e)}")],
                    "current_agent": "analyst_agent",
                    "error": str(e),
                }
            )

    def _parse_response(self, content: str) -> dict:
        """解析 LLM 响应"""
        import re

        json_match = re.search(r'```json\s*([\s\S]*?)\s*```', content)
        if json_match:
            return json.loads(json_match.group(1))

        json_match = re.search(r'\{[\s\S]*\}', content)
        if json_match:
            return json.loads(json_match.group())

        raise ValueError("无法解析 LLM 响应为 JSON")

    def _build_analysis_result(self, result_dict: dict, user_query: str) -> AnalysisResult:
        """构建 AnalysisResult"""
        steps = []
        for step_dict in result_dict.get("steps", []):
            stages = []
            for stage_dict in step_dict.get("stages", []):
                stage = Stage(
                    stage_id=stage_dict.get("stage_id", 1),
                    name=stage_dict.get("name", ""),
                    description=stage_dict.get("description", ""),
                    input_tables=stage_dict.get("input_tables", []),
                    output_table=stage_dict.get("output_table", ""),
                    is_temp_table=stage_dict.get("is_temp_table", True),
                )
                stages.append(stage)

            step = Step(
                step_id=step_dict.get("step_id", ""),
                step_name=step_dict.get("step_name", ""),
                description=step_dict.get("description"),
                stages=stages,
                depends_on=step_dict.get("depends_on", []),
                output_table=step_dict.get("output_table"),
                suggested_component=step_dict.get("suggested_component", "hive"),
            )
            steps.append(step)

        return AnalysisResult(
            user_query=user_query,
            summary=result_dict.get("summary", ""),
            steps=steps,
            final_target=result_dict.get("final_target"),
            ambiguities=result_dict.get("ambiguities", []),
            confidence=result_dict.get("confidence", 0.5),
        )

    def _format_context(self, context: Optional[dict]) -> dict:
        """格式化上下文信息"""
        if not context:
            return {"tables": "（无）", "joins": "（无）"}

        # 格式化表信息
        tables_lines = []
        tables_dict = context.get("tables", {})
        for name, table in tables_dict.items():
            layer = table.get("layer", "")
            key_columns = table.get("key_columns", [])
            col_names = [c.get("name", "") for c in key_columns[:10]]
            tables_lines.append(f"- {name} ({layer}): {', '.join(col_names)}")

        # 格式化 JOIN 信息
        joins_lines = []
        for join in context.get("join_hints", []):
            joins_lines.append(
                f"- {join.get('left_table')}.{join.get('left_column')} = "
                f"{join.get('right_table')}.{join.get('right_column')}"
            )

        return {
            "tables": "\n".join(tables_lines) if tables_lines else "（无）",
            "joins": "\n".join(joins_lines) if joins_lines else "（无）",
        }

    def _validate_and_enrich(
        self,
        result: AnalysisResult,
        context: Optional[dict]
    ) -> AnalysisResult:
        """验证分析结果"""
        if not context:
            return result

        tables_dict = context.get("tables", {})
        table_names = set(tables_dict.keys())

        # 验证 Step 依赖是否有效
        step_ids = {s.step_id for s in result.steps}
        for step in result.steps:
            for dep_id in step.depends:
                if dep_id not in step_ids:
                    result.ambiguities.append({
                        "question": f"Step '{step.step_id}' 依赖的 '{dep_id}' 不存在",
                        "context": "依赖关系配置错误",
                        "options": list(step_ids),
                    })

        # 验证输入表是否存在（跳过临时表）
        for step in result.steps:
            for stage in step.stages:
                for input_table in stage.input_tables:
                    if input_table.startswith("tmp."):
                        continue
                    if input_table not in table_names:
                        result.ambiguities.append({
                            "question": f"表 '{input_table}' 不存在，请确认表名是否正确",
                            "context": f"Stage '{stage.name}' 引用了不存在的表",
                            "options": list(table_names),
                        })

        return result
