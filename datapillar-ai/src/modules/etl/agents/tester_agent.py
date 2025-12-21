"""
Tester Agent（测试验证）

验证生成的 SQL 代码的正确性。
"""

import json
import logging
from typing import Optional, List, Dict, Any

from langchain_core.messages import AIMessage, HumanMessage
from langgraph.types import Command

from src.modules.etl.schemas.state import AgentState
from src.modules.etl.schemas.plan import (
    Workflow,
    Job,
    TestResult,
    TestCase,
)
from src.modules.etl.sql_validator import SqlValidator, ValidationResult
from src.infrastructure.llm.client import call_llm

logger = logging.getLogger(__name__)

# 测试用例生成提示词
TEST_CASE_GENERATION_PROMPT = """你是资深数据测试工程师，负责为 ETL SQL 生成测试用例。

## SQL 代码
{sql}

## 节点信息
节点ID: {node_id}
节点类型: {node_type}
操作原语: {node_op}

## 表结构
{table_schemas}

## 测试用例要求
请为这段 SQL 生成测试用例，包含：

1. **正向测试**：验证正常数据流转
   - 输入数据样例
   - 预期输出结果

2. **边界测试**：验证边界条件
   - NULL 值处理
   - 空表处理
   - 极值处理

3. **异常测试**：验证异常情况
   - 数据类型不匹配
   - 违反约束条件

## 输出格式
请以 JSON 格式输出测试用例列表，每个用例包含：
- name: 测试用例名称
- description: 测试描述
- test_type: 测试类型（positive/boundary/negative）
- input_data: 输入数据描述
- expected_result: 预期结果描述
- sql_assertion: SQL 断言（可选）

只输出 JSON，不要解释。
"""


class TesterAgent:
    """
    测试验证

    职责：
    1. 验证所有节点的 SQL 语法和语义
    2. 生成测试用例
    3. 执行静态分析
    4. 报告测试结果
    """

    def __init__(self):
        self.llm = call_llm(temperature=0.0, enable_json_mode=True)
        self.validator = SqlValidator()

    async def __call__(self, state: AgentState) -> Command:
        """执行测试验证"""
        architecture_plan = state.architecture_plan
        knowledge_context = state.knowledge_context

        if not architecture_plan:
            return Command(
                update={
                    "messages": [AIMessage(content="缺少架构方案，无法测试")],
                    "current_agent": "tester_agent",
                    "error": "缺少架构方案",
                }
            )

        logger.info(f"🧪 TesterAgent 开始测试验证")

        try:
            # 将 dict 转换为 Workflow
            if isinstance(architecture_plan, dict):
                plan = Workflow(**architecture_plan)
            else:
                plan = architecture_plan

            # 执行测试
            test_results = await self._run_tests(plan, knowledge_context)

            # 统计结果
            passed_count = sum(1 for r in test_results if r.get("passed"))
            total_count = len(test_results)
            all_passed = passed_count == total_count

            # 生成测试用例（仅对有 SQL 的节点）
            test_cases = []
            for node in plan.jobs:
                sql = node.config.get("sql") if node.config else None
                if sql:
                    cases = await self._generate_test_cases(node, knowledge_context)
                    test_cases.extend(cases)

            # 构建测试结果
            test_result = TestResult(
                passed=all_passed,
                total_tests=total_count,
                passed_tests=passed_count,
                failed_tests=total_count - passed_count,
                test_cases=test_cases,
                validation_errors=self._extract_errors(test_results),
                coverage_summary={
                    "nodes_tested": total_count,
                    "nodes_passed": passed_count,
                    "coverage_rate": passed_count / total_count if total_count > 0 else 0,
                },
            )

            logger.info(
                f"✅ TesterAgent 完成测试: passed={all_passed}, "
                f"{passed_count}/{total_count} 节点通过"
            )

            return Command(
                update={
                    "messages": [AIMessage(content=f"测试完成: {passed_count}/{total_count} 通过")],
                    "test_result": test_result.model_dump(),
                    "current_agent": "tester_agent",
                    "iteration_count": state.iteration_count if all_passed else state.iteration_count + 1,
                }
            )

        except Exception as e:
            logger.error(f"TesterAgent 测试失败: {e}", exc_info=True)
            return Command(
                update={
                    "messages": [AIMessage(content=f"测试失败: {str(e)}")],
                    "current_agent": "tester_agent",
                    "error": str(e),
                }
            )

    async def _run_tests(
        self, plan: Workflow, context: Optional[dict]
    ) -> List[Dict[str, Any]]:
        """执行所有测试"""
        results = []

        context_info = self._build_context_info(context)

        for node in plan.jobs:
            result = await self._test_node(node, context_info)
            results.append(result)

        return results

    async def _test_node(
        self, node: Job, context_info: dict
    ) -> Dict[str, Any]:
        """测试单个节点"""
        result = {
            "node_id": node.id,
            "node_type": "transform",
            "passed": True,
            "errors": [],
            "warnings": [],
        }

        # 获取 SQL
        sql = node.config.get("sql") if node.config else None

        # 1. 检查 SQL 是否存在
        if not sql:
            result["warnings"].append("节点没有生成 SQL")
            return result

        # 2. 语法验证
        validation = await self.validator.validate(sql, context_info)

        if not validation.is_valid:
            result["passed"] = False
            result["errors"].extend(validation.errors)

        result["warnings"].extend(validation.warnings)

        # 3. 额外的静态分析
        static_issues = self._static_analysis(sql, node, context_info)
        if static_issues:
            result["warnings"].extend(static_issues)

        return result

    def _static_analysis(
        self, sql: str, node: Job, context_info: dict
    ) -> List[str]:
        """静态分析 SQL"""
        issues = []
        sql_upper = sql.upper()

        # 1. SELECT * 检查
        if "SELECT *" in sql_upper or "SELECT  *" in sql_upper:
            issues.append("使用了 SELECT *，建议明确列出字段")

        # 2. 笛卡尔积检查
        if "CROSS JOIN" in sql_upper:
            issues.append("使用了 CROSS JOIN，可能产生笛卡尔积")

        # 3. 无 WHERE 条件的 DELETE/UPDATE
        if ("DELETE " in sql_upper or "UPDATE " in sql_upper) and "WHERE" not in sql_upper:
            issues.append("DELETE/UPDATE 语句没有 WHERE 条件，可能影响全表")

        # 4. 硬编码值检查
        if "= 'test'" in sql.lower() or "= \"test\"" in sql.lower():
            issues.append("SQL 中存在硬编码测试值")

        # 5. 分区字段检查
        tables = context_info.get("tables", {})
        for table_name, table in tables.items():
            partition_keys = table.get("partition_keys", [])
            if partition_keys:
                # 检查是否在 WHERE 中使用了分区字段
                if table_name.lower() in sql.lower():
                    has_partition_filter = any(
                        pk.lower() in sql.lower() for pk in partition_keys
                    )
                    if not has_partition_filter:
                        issues.append(f"表 {table_name} 有分区字段 {partition_keys}，但未在 WHERE 中使用")

        # 6. 大表 JOIN 检查（基于表名模式）
        large_table_patterns = ["fact_", "dwd_", "ods_"]
        for pattern in large_table_patterns:
            if pattern in sql.lower() and "JOIN" in sql_upper:
                issues.append(f"可能涉及大表 JOIN（包含 {pattern} 表），请确认性能")
                break

        return issues

    async def _generate_test_cases(
        self, node: Job, context: Optional[dict]
    ) -> List[TestCase]:
        """为节点生成测试用例"""
        sql = node.config.get("sql") if node.config else None
        if not sql:
            return []

        try:
            context_info = self._build_context_info(context)

            prompt = TEST_CASE_GENERATION_PROMPT.format(
                sql=sql,
                node_id=node.id,
                node_type="transform",
                node_op=node.type,
                table_schemas=context_info.get("table_schemas", "（无）"),
            )

            response = await self.llm.ainvoke([HumanMessage(content=prompt)])

            # 解析响应
            content = response.content
            # 清理 markdown 代码块
            content = content.strip()
            if content.startswith("```"):
                content = content.split("```")[1]
                if content.startswith("json"):
                    content = content[4:]
            content = content.strip()

            cases_data = json.loads(content)

            # 转换为 TestCase 对象
            test_cases = []
            for case in cases_data[:5]:  # 最多5个测试用例
                test_cases.append(TestCase(
                    name=case.get("name", "未命名测试"),
                    description=case.get("description", ""),
                    test_type=case.get("test_type", "positive"),
                    node_id=node.id,
                    input_data=case.get("input_data", ""),
                    expected_result=case.get("expected_result", ""),
                    sql_assertion=case.get("sql_assertion"),
                ))

            return test_cases

        except Exception as e:
            logger.warning(f"生成测试用例失败: {e}")
            # 返回基础测试用例
            return [
                TestCase(
                    name=f"基础测试_{node.id}",
                    description="验证 SQL 能够正确执行",
                    test_type="positive",
                    node_id=node.id,
                    input_data="使用样例数据",
                    expected_result="SQL 执行成功，无错误",
                )
            ]

    def _build_context_info(self, context: Optional[dict]) -> dict:
        """构建上下文信息"""
        if not context:
            return {
                "tables": {},
                "table_schemas": "（无）",
            }

        tables = context.get("tables", {})

        # 格式化表结构
        schema_lines = []
        for name, table in tables.items():
            columns = table.get("columns", [])
            col_info = [
                f"{c.get('name')} ({c.get('data_type', 'string')})"
                for c in columns
            ]
            schema_lines.append(f"### {name}")
            schema_lines.append(f"列: {', '.join(col_info)}")
            if table.get("primary_keys"):
                schema_lines.append(f"主键: {', '.join(table['primary_keys'])}")
            if table.get("partition_keys"):
                schema_lines.append(f"分区: {', '.join(table['partition_keys'])}")
            schema_lines.append("")

        return {
            "tables": tables,
            "table_schemas": "\n".join(schema_lines) if schema_lines else "（无）",
        }

    def _extract_errors(self, test_results: List[Dict]) -> List[str]:
        """提取所有错误"""
        errors = []
        for result in test_results:
            if not result.get("passed"):
                node_id = result.get("node_id", "unknown")
                for error in result.get("errors", []):
                    errors.append(f"[{node_id}] {error}")
        return errors
