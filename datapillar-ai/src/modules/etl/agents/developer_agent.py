"""
Developer Agent（数据开发）

职责：为每个 Stage 生成 SQL
- 每个 Job 包含多个 Stage（来自 AnalystAgent 的拆分）
- 为每个 Stage 生成 SQL
- 将所有 Stage 的 SQL 组合为完整脚本
"""

import json
import logging
from typing import Optional, List, Dict, Any

from langchain_core.messages import AIMessage, HumanMessage
from langgraph.types import Command

from src.modules.etl.schemas.state import AgentState
from src.modules.etl.schemas.plan import Workflow, Job
from src.infrastructure.repository import ComponentRepository
from src.infrastructure.llm.client import call_llm

logger = logging.getLogger(__name__)


STAGE_SQL_PROMPT = """你是资深数据开发工程师，请为以下 Stage 生成 SQL。

## Stage 信息
- 名称: {stage_name}
- 描述: {stage_description}
- 输入表: {input_tables}
- 输出表: {output_table}
- 是否临时表: {is_temp_table}

## 相关表结构
{table_schemas}

## JOIN 关系
{join_hints}

## 生成要求

### 如果是临时表 (is_temp_table=true)
```sql
CREATE TEMPORARY TABLE {output_table} AS
SELECT ...
FROM ...
WHERE ...;
```

### 如果是最终表 (is_temp_table=false)
```sql
INSERT OVERWRITE TABLE {output_table} PARTITION(dt='${{bizdate}}')
SELECT ...
FROM ...
WHERE ...;
```

## 注意事项
1. 根据 Stage 描述生成完整的 SQL
2. 字段名必须与表结构一致
3. 只输出 SQL，不要解释

请输出 SQL：
"""


class DeveloperAgent:
    """
    数据开发

    职责：
    1. 读取每个 Job 中的 stages 信息
    2. 为每个 Stage 生成 SQL
    3. 将所有 Stage SQL 组合为完整脚本
    """

    def __init__(self):
        self.llm = call_llm(temperature=0.0)
        self.max_retries = 2

    async def __call__(self, state: AgentState) -> Command:
        """执行配置生成"""
        architecture_plan = state.architecture_plan
        knowledge_context = state.knowledge_context

        if not architecture_plan:
            return Command(
                update={
                    "messages": [AIMessage(content="缺少架构方案，无法生成配置")],
                    "current_agent": "developer_agent",
                    "error": "缺少架构方案",
                }
            )

        logger.info("💻 DeveloperAgent 开始生成 SQL")

        # 转换为 Workflow
        if isinstance(architecture_plan, dict):
            plan = Workflow(**architecture_plan)
        else:
            plan = architecture_plan

        # 缓存组件信息
        component_cache: Dict[str, Dict] = {}

        all_errors: List[str] = []
        generated_count = 0

        try:
            # 按拓扑顺序处理节点
            sorted_nodes = plan.topological_sort()

            for node in sorted_nodes:
                # 获取组件配置
                if node.type not in component_cache:
                    component = ComponentRepository.get_component_by_id(node.type)
                    if component:
                        if isinstance(component.get("config_schema"), str):
                            component["config_schema"] = json.loads(component["config_schema"])
                        component_cache[node.type] = component
                    else:
                        all_errors.append(f"节点 {node.id} 的组件 {node.type} 不存在")
                        continue

                component = component_cache[node.type]

                # 获取 stages
                stages = node.config.get("stages", [])
                if not stages:
                    all_errors.append(f"节点 {node.id} 没有 stages 信息")
                    continue

                # 为每个 Stage 生成 SQL
                sql_script, success, errors = await self._generate_sql_script(
                    node=node,
                    stages=stages,
                    component=component,
                    knowledge_context=knowledge_context,
                )

                # 更新节点配置
                node.config = {
                    "sql": sql_script,
                    "stages": stages,  # 保留 stages 信息
                }
                node.config_generated = success

                if success:
                    generated_count += 1
                    logger.info(f"✅ 节点 {node.id} SQL 脚本生成成功 ({len(stages)} 个 Stage)")
                else:
                    all_errors.extend(errors)
                    logger.warning(f"❌ 节点 {node.id} SQL 生成失败: {errors}")

            # 更新 plan
            plan_dict = plan.model_dump()

            logger.info(
                f"✅ DeveloperAgent 完成: {generated_count}/{len(sorted_nodes)} 成功"
            )

            if all_errors:
                return Command(
                    update={
                        "messages": [AIMessage(content=f"SQL 生成完成，但有 {len(all_errors)} 个问题")],
                        "architecture_plan": plan_dict,
                        "current_agent": "developer_agent",
                        "error": "\n".join(all_errors[:5]),
                    }
                )

            return Command(
                update={
                    "messages": [AIMessage(content=f"SQL 生成完成: {generated_count} 个节点")],
                    "architecture_plan": plan_dict,
                    "current_agent": "developer_agent",
                }
            )

        except Exception as e:
            logger.error(f"DeveloperAgent 生成失败: {e}", exc_info=True)
            return Command(
                update={
                    "messages": [AIMessage(content=f"SQL 生成失败: {str(e)}")],
                    "current_agent": "developer_agent",
                    "error": str(e),
                }
            )

    async def _generate_sql_script(
        self,
        node: Job,
        stages: List[Dict],
        component: Dict[str, Any],
        knowledge_context: Optional[dict],
    ) -> tuple[str, bool, List[str]]:
        """为所有 Stage 生成 SQL 并组合"""
        sql_parts = []
        errors = []

        for stage in stages:
            stage_sql, success, stage_errors = await self._generate_stage_sql(
                stage=stage,
                knowledge_context=knowledge_context,
            )

            if success:
                sql_parts.append(f"-- Stage {stage.get('stage_id')}: {stage.get('name')}")
                sql_parts.append(stage_sql)
                sql_parts.append("")
            else:
                errors.extend(stage_errors)

        if errors:
            return "\n".join(sql_parts), False, errors

        return "\n".join(sql_parts), True, []

    async def _generate_stage_sql(
        self,
        stage: Dict,
        knowledge_context: Optional[dict],
    ) -> tuple[str, bool, List[str]]:
        """为单个 Stage 生成 SQL"""
        table_schemas = self._format_table_schemas(stage.get("input_tables", []), knowledge_context)
        join_hints = self._format_join_hints(knowledge_context)

        for attempt in range(self.max_retries):
            try:
                prompt = STAGE_SQL_PROMPT.format(
                    stage_name=stage.get("name", ""),
                    stage_description=stage.get("description", ""),
                    input_tables=", ".join(stage.get("input_tables", [])),
                    output_table=stage.get("output_table", ""),
                    is_temp_table=stage.get("is_temp_table", True),
                    table_schemas=table_schemas,
                    join_hints=join_hints,
                )

                response = await self.llm.ainvoke([HumanMessage(content=prompt)])
                sql = self._clean_sql(response.content)

                if not sql or len(sql) < 20:
                    continue

                if not any(kw in sql.upper() for kw in ["SELECT", "INSERT", "CREATE"]):
                    continue

                return sql, True, []

            except Exception as e:
                logger.error(f"Stage {stage.get('name')} SQL 生成失败: {e}")

        return "", False, [f"Stage {stage.get('name')} SQL 生成失败"]

    def _format_table_schemas(self, input_tables: List[str], context: Optional[dict]) -> str:
        """格式化表结构信息"""
        if not context:
            return "（无）"

        tables = context.get("tables", {})
        lines = []

        for table_name in input_tables:
            # 跳过临时表
            if table_name.startswith("tmp."):
                continue

            if table_name in tables:
                table = tables[table_name]
                key_columns = table.get("key_columns", [])
                col_info = [f"{c.get('name')} ({c.get('data_type', 'string')})" for c in key_columns]
                lines.append(f"### {table_name}")
                lines.append(f"字段: {', '.join(col_info)}")
                lines.append("")

        return "\n".join(lines) if lines else "（无）"

    def _format_join_hints(self, context: Optional[dict]) -> str:
        """格式化 JOIN 关系"""
        if not context:
            return "（无）"

        join_hints = context.get("join_hints", [])
        if not join_hints:
            return "（无）"

        lines = []
        for j in join_hints:
            lines.append(
                f"- {j.get('left_table')}.{j.get('left_column')} = "
                f"{j.get('right_table')}.{j.get('right_column')}"
            )

        return "\n".join(lines)

    @staticmethod
    def _clean_sql(content: str) -> str:
        """清理 SQL（去掉 markdown 代码块）"""
        import re

        # 提取 SQL 代码块
        sql_match = re.search(r'```sql\s*([\s\S]*?)\s*```', content)
        if sql_match:
            return sql_match.group(1).strip()

        # 提取普通代码块
        code_match = re.search(r'```\s*([\s\S]*?)\s*```', content)
        if code_match:
            return code_match.group(1).strip()

        return content.strip()
