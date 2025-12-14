"""
SQL 验证器：多层次验证生成的 SQL
"""

import logging
import re
from typing import Dict, List, Any, Optional, Set, Tuple
from dataclasses import dataclass

logger = logging.getLogger(__name__)


@dataclass
class ValidationResult:
    """验证结果"""
    is_valid: bool
    errors: List[str]
    warnings: List[str] = None

    def __post_init__(self):
        if self.warnings is None:
            self.warnings = []


class SqlValidator:
    """SQL 验证器：语法、语义、类型检查"""

    def __init__(self):
        # 尝试导入 sqlparse（可选依赖）
        try:
            import sqlparse
            self.sqlparse = sqlparse
            self.has_sqlparse = True
        except ImportError:
            logger.warning("sqlparse 未安装，跳过语法检查")
            self.sqlparse = None
            self.has_sqlparse = False

    async def validate(self, sql: str, context: Dict[str, Any]) -> ValidationResult:
        """
        多层次验证 SQL

        Args:
            sql: 待验证的 SQL
            context: 上下文（包含 tables/lineage/join_hints）

        Returns:
            验证结果
        """
        errors = []
        warnings = []

        # Level 1: 语法检查
        syntax_errors = self._validate_syntax(sql)
        errors.extend(syntax_errors)

        # Level 2: 表/字段存在性检查
        if not errors:  # 语法正确才继续
            existence_errors = self._validate_existence(sql, context)
            errors.extend(existence_errors)

        # Level 3: JOIN 条件类型检查
        if not errors:
            type_errors = self._validate_join_types(sql, context)
            errors.extend(type_errors)

        # Level 4: 性能警告（不算错误）
        perf_warnings = self._check_performance(sql, context)
        warnings.extend(perf_warnings)

        return ValidationResult(
            is_valid=len(errors) == 0,
            errors=errors,
            warnings=warnings,
        )

    def _validate_syntax(self, sql: str) -> List[str]:
        """
        Level 1: 语法检查（使用 sqlparse）
        """
        errors = []

        if not self.has_sqlparse:
            return errors

        try:
            # 解析 SQL
            parsed = self.sqlparse.parse(sql)
            if not parsed:
                errors.append("SQL 解析失败：无法识别的语法")
                return errors

            # 检查是否有有效的语句
            stmt = parsed[0]
            if not stmt.tokens:
                errors.append("SQL 为空或无效")

            # 检查关键字拼写（基础检查）
            sql_upper = sql.upper()
            if "SELCT" in sql_upper or "FORM" in sql_upper or "WEHRE" in sql_upper:
                errors.append("SQL 关键字拼写错误")

        except Exception as e:
            errors.append(f"SQL 语法检查失败: {str(e)}")

        return errors

    def _validate_existence(self, sql: str, context: Dict[str, Any]) -> List[str]:
        """
        Level 2: 表/字段存在性检查
        """
        errors = []

        tables = context.get("tables", {})
        if not tables:
            logger.warning("上下文中无表信息，跳过存在性检查")
            return errors

        # 提取 SQL 中使用的表名
        used_tables = self._extract_tables_from_sql(sql)

        # 提取 SQL 中使用的字段名
        used_columns = self._extract_columns_from_sql(sql)

        # 检查表是否存在
        for table in used_tables:
            # 标准化表名（去掉别名）
            table_name = self._normalize_table_ref(table)
            if table_name and table_name not in tables:
                errors.append(f"表不存在: {table_name}")

        # 检查字段是否存在
        for table_name, columns in used_columns.items():
            table_schema = tables.get(table_name)
            if not table_schema:
                continue

            valid_columns = {col["name"].lower() for col in table_schema.get("columns", [])}

            for col in columns:
                col_lower = col.lower()
                if col_lower != "*" and col_lower not in valid_columns:
                    errors.append(f"字段不存在: {table_name}.{col}")

        return errors

    def _validate_join_types(self, sql: str, context: Dict[str, Any]) -> List[str]:
        """
        Level 3: JOIN 条件类型匹配检查
        """
        errors = []

        tables = context.get("tables", {})
        if not tables:
            return errors

        # 提取 JOIN 条件
        join_conditions = self._extract_join_conditions(sql)

        for left_col, right_col in join_conditions:
            # 解析字段：table.column
            left_table, left_field = self._parse_column_ref(left_col)
            right_table, right_field = self._parse_column_ref(right_col)

            # 获取字段类型
            left_type = self._get_column_type(left_table, left_field, tables)
            right_type = self._get_column_type(right_table, right_field, tables)

            # 类型检查
            if left_type and right_type and not self._types_compatible(left_type, right_type):
                errors.append(
                    f"JOIN 类型不匹配: {left_col} ({left_type}) vs {right_col} ({right_type})"
                )

        return errors

    def _check_performance(self, sql: str, context: Dict[str, Any]) -> List[str]:
        """
        Level 4: 性能警告（不算错误）
        """
        warnings = []

        sql_upper = sql.upper()

        # 检查是否使用 SELECT *
        if "SELECT *" in sql_upper or "SELECT  *" in sql_upper:
            warnings.append("建议避免使用 SELECT *，明确指定需要的列")

        # 检查是否缺少分区条件
        tables = context.get("tables", {})
        for table_name, table_info in tables.items():
            partition_keys = table_info.get("partition_keys", [])
            if partition_keys:
                # 检查 SQL 中是否包含分区字段
                has_partition_filter = any(
                    pk.lower() in sql.lower() for pk in partition_keys
                )
                if not has_partition_filter:
                    warnings.append(
                        f"表 {table_name} 有分区字段 {partition_keys}，建议添加分区过滤条件"
                    )

        # 检查笛卡尔积
        if self._has_cartesian_product(sql):
            warnings.append("可能存在笛卡尔积 JOIN，请检查 JOIN 条件")

        return warnings

    # ========== 辅助方法：SQL 解析 ==========

    def _extract_tables_from_sql(self, sql: str) -> Set[str]:
        """从 SQL 中提取表名"""
        tables = set()

        # 简单正则提取 FROM/JOIN 后的表名
        # 匹配：FROM table_name 或 JOIN table_name
        pattern = r"(?:FROM|JOIN)\s+([a-zA-Z_][\w.]*)"
        matches = re.findall(pattern, sql, re.IGNORECASE)

        for match in matches:
            # 去掉可能的别名（table_name AS alias 或 table_name alias）
            table = match.split()[0].strip()
            tables.add(table)

        return tables

    def _extract_columns_from_sql(self, sql: str) -> Dict[str, Set[str]]:
        """
        从 SQL 中提取字段名（按表分组）

        Returns:
            {"table_name": {"col1", "col2"}}
        """
        columns = {}

        # 简单正则提取：table.column 或 alias.column
        pattern = r"([a-zA-Z_][\w]*)\\.([a-zA-Z_][\w]*)"
        matches = re.findall(pattern, sql)

        for table_ref, col in matches:
            if table_ref not in columns:
                columns[table_ref] = set()
            columns[table_ref].add(col)

        return columns

    def _extract_join_conditions(self, sql: str) -> List[Tuple[str, str]]:
        """
        提取 JOIN 条件

        Returns:
            [("t0.id", "t1.id"), ...]
        """
        conditions = []

        # 匹配：ON t0.col = t1.col
        pattern = r"ON\s+([a-zA-Z_][\w.]+)\s*=\s*([a-zA-Z_][\w.]+)"
        matches = re.findall(pattern, sql, re.IGNORECASE)

        for left, right in matches:
            conditions.append((left.strip(), right.strip()))

        return conditions

    def _parse_column_ref(self, col_ref: str) -> Tuple[Optional[str], Optional[str]]:
        """
        解析字段引用：t0.user_id → ("t0", "user_id")
        """
        if "." in col_ref:
            parts = col_ref.split(".")
            return parts[0], parts[1]
        return None, col_ref

    def _get_column_type(
        self,
        table_ref: Optional[str],
        column_name: Optional[str],
        tables: Dict[str, Any],
    ) -> Optional[str]:
        """
        获取字段类型

        Args:
            table_ref: 表引用（可能是表名或别名）
            column_name: 字段名
            tables: 表 schema 字典

        Returns:
            字段类型（如 "bigint", "string"）
        """
        if not table_ref or not column_name:
            return None

        # 尝试直接匹配表名
        table_schema = tables.get(table_ref)

        # 如果是别名，尝试模糊匹配（简化处理）
        if not table_schema:
            for table_name, schema in tables.items():
                # 假设别名 t0/t1 对应第一个/第二个表
                # 这里简化处理，实际应该解析 AS 子句
                table_schema = schema
                break

        if not table_schema:
            return None

        # 查找字段类型
        columns = table_schema.get("columns", [])
        for col in columns:
            if col["name"].lower() == column_name.lower():
                return col["data_type"]

        return None

    @staticmethod
    def _types_compatible(type1: str, type2: str) -> bool:
        """
        检查两个类型是否兼容

        简化规则：
        - 数值类型之间兼容（int/bigint/decimal）
        - 字符串类型之间兼容（string/varchar）
        - 时间类型之间兼容（timestamp/date）
        """
        type1_lower = type1.lower()
        type2_lower = type2.lower()

        # 完全相同
        if type1_lower == type2_lower:
            return True

        # 数值类型组
        numeric_types = {"int", "bigint", "tinyint", "smallint", "decimal", "double", "float"}
        if type1_lower in numeric_types and type2_lower in numeric_types:
            return True

        # 字符串类型组
        string_types = {"string", "varchar", "char", "text"}
        if type1_lower in string_types and type2_lower in string_types:
            return True

        # 时间类型组
        time_types = {"timestamp", "date", "datetime"}
        if type1_lower in time_types and type2_lower in time_types:
            return True

        return False

    @staticmethod
    def _normalize_table_ref(table: str) -> str:
        """标准化表引用（去掉别名）"""
        # FROM table_name AS alias → table_name
        if " AS " in table.upper():
            return table.split()[0]
        return table

    @staticmethod
    def _has_cartesian_product(sql: str) -> bool:
        """检查是否可能存在笛卡尔积"""
        sql_upper = sql.upper()

        # 简单检查：有多个表，但没有 JOIN 或 WHERE
        has_multiple_tables = sql_upper.count("FROM") > 0 and "," in sql
        has_no_join = "JOIN" not in sql_upper and "WHERE" not in sql_upper

        return has_multiple_tables and has_no_join
