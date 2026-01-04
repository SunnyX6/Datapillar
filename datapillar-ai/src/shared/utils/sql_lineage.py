"""
SQL 血缘分析器

基于 sqlglot 解析 SQL，提取表级和列级血缘关系。
支持临时表识别和穿透。

核心逻辑：
1. 解析 SQL 提取读写表
2. 构建依赖图
3. 识别临时表（在 session 中创建且被读取）
4. 穿透临时表，建立真实血缘
5. 提取列级血缘
"""

from dataclasses import dataclass, field
from enum import Enum

import sqlglot
import structlog
from sqlglot import exp

logger = structlog.get_logger()


class TableRole(str, Enum):
    """表角色"""

    SOURCE = "source"
    TARGET = "target"
    TEMP = "temp"


@dataclass
class TableRef:
    """表引用"""

    catalog: str | None = None
    schema: str | None = None
    table: str = ""

    @property
    def full_name(self) -> str:
        """完整表名"""
        parts = [p for p in [self.catalog, self.schema, self.table] if p]
        return ".".join(parts)

    def __hash__(self) -> int:
        return hash(self.full_name)

    def __eq__(self, other: object) -> bool:
        if not isinstance(other, TableRef):
            return False
        return self.full_name == other.full_name


@dataclass
class ColumnRef:
    """列引用"""

    table: TableRef
    column: str

    @property
    def full_name(self) -> str:
        """完整列名"""
        return f"{self.table.full_name}.{self.column}"


@dataclass
class ColumnLineage:
    """列级血缘"""

    source: ColumnRef
    target: ColumnRef
    transformation: str | None = None


@dataclass
class TableLineage:
    """表级血缘"""

    source: TableRef
    target: TableRef
    sql: str | None = None


@dataclass
class LineageResult:
    """血缘分析结果"""

    sources: set[TableRef] = field(default_factory=set)
    targets: set[TableRef] = field(default_factory=set)
    temp_tables: set[TableRef] = field(default_factory=set)
    table_lineages: list[TableLineage] = field(default_factory=list)
    column_lineages: list[ColumnLineage] = field(default_factory=list)


class SQLLineageAnalyzer:
    """
    SQL 血缘分析器

    基于依赖图拓扑识别表类型：
    - 入度=0（只读不写）→ SOURCE
    - 出度=0（只写不读）→ TARGET
    - 中间节点（既读又写）→ 临时表
    """

    def __init__(self, dialect: str = "hive") -> None:
        """
        初始化分析器

        Args:
            dialect: SQL 方言（hive, spark, mysql, postgres 等）
        """
        self.dialect = dialect

    def analyze_sql(self, sql: str) -> LineageResult:
        """
        分析单条 SQL 的血缘

        Args:
            sql: SQL 语句

        Returns:
            LineageResult: 血缘结果
        """
        return self.analyze_session([sql])

    def analyze_session(self, sqls: list[str]) -> LineageResult:
        """
        分析一个 session 中的多条 SQL

        核心逻辑：
        1. 解析每条 SQL，提取读写表
        2. 构建依赖图
        3. 识别临时表
        4. 穿透临时表建立真实血缘

        Args:
            sqls: SQL 语句列表

        Returns:
            LineageResult: 血缘结果
        """
        created_tables: set[TableRef] = set()
        read_tables: set[TableRef] = set()
        written_tables: set[TableRef] = set()
        sql_dependencies: list[tuple[set[TableRef], set[TableRef], str]] = []

        for sql in sqls:
            try:
                reads, writes = self._extract_tables(sql)
                read_tables.update(reads)
                written_tables.update(writes)
                created_tables.update(writes)
                sql_dependencies.append((reads, writes, sql))
            except Exception as e:
                logger.warning("sql_parse_failed", sql=sql[:100], error=str(e))
                continue

        # 临时表 = 在 session 中创建 且 被读取
        temp_tables = created_tables & read_tables

        # source = 被读取 但 不是 session 内创建的
        sources = read_tables - created_tables

        # target = 被写入 但 不是临时表
        targets = written_tables - temp_tables

        # 构建表级血缘（穿透临时表）
        table_lineages = self._build_table_lineages(sql_dependencies, sources, targets, temp_tables)

        # 提取列级血缘
        column_lineages = self._extract_column_lineages(sqls, sources, targets)

        return LineageResult(
            sources=sources,
            targets=targets,
            temp_tables=temp_tables,
            table_lineages=table_lineages,
            column_lineages=column_lineages,
        )

    def _extract_tables(self, sql: str) -> tuple[set[TableRef], set[TableRef]]:
        """
        从 SQL 提取读取和写入的表

        Args:
            sql: SQL 语句

        Returns:
            (读取的表集合, 写入的表集合)
        """
        reads: set[TableRef] = set()
        writes: set[TableRef] = set()

        try:
            statements = sqlglot.parse(sql, dialect=self.dialect)
        except Exception as e:
            logger.warning("sql_parse_error", sql=sql[:100], error=str(e))
            return reads, writes

        for statement in statements:
            if statement is None:
                continue

            # 提取写入的表
            write_table = self._get_write_table(statement)
            if write_table:
                writes.add(write_table)

            # 提取读取的表（排除写入的表本身）
            for table in statement.find_all(exp.Table):
                table_ref = self._table_to_ref(table)
                if table_ref and table_ref not in writes:
                    reads.add(table_ref)

        return reads, writes

    def _get_write_table(self, statement: exp.Expression) -> TableRef | None:
        """获取写入的目标表"""
        # INSERT INTO
        if isinstance(statement, exp.Insert):
            table = statement.this
            if isinstance(table, exp.Table):
                return self._table_to_ref(table)

        # CREATE TABLE AS SELECT
        if isinstance(statement, exp.Create):
            table = statement.this
            if isinstance(table, exp.Table):
                return self._table_to_ref(table)

        # MERGE INTO
        if isinstance(statement, exp.Merge):
            table = statement.this
            if isinstance(table, exp.Table):
                return self._table_to_ref(table)

        return None

    def _table_to_ref(self, table: exp.Table) -> TableRef | None:
        """将 sqlglot Table 转换为 TableRef"""
        if not table.name:
            return None

        return TableRef(
            catalog=table.catalog if hasattr(table, "catalog") else None,
            schema=table.db if hasattr(table, "db") else None,
            table=table.name,
        )

    def _build_table_lineages(
        self,
        sql_dependencies: list[tuple[set[TableRef], set[TableRef], str]],
        sources: set[TableRef],
        targets: set[TableRef],
        temp_tables: set[TableRef],
    ) -> list[TableLineage]:
        """
        构建表级血缘，穿透临时表

        Args:
            sql_dependencies: [(reads, writes, sql), ...]
            sources: 源表集合
            targets: 目标表集合
            temp_tables: 临时表集合

        Returns:
            表级血缘列表
        """
        lineages: list[TableLineage] = []

        # 构建依赖图：table -> [依赖的表]
        dependencies: dict[TableRef, set[TableRef]] = {}
        for reads, writes, _sql in sql_dependencies:
            for write_table in writes:
                if write_table not in dependencies:
                    dependencies[write_table] = set()
                dependencies[write_table].update(reads)

        # 对每个目标表，追溯到真正的源表
        for target in targets:
            source_tables = self._trace_sources(target, dependencies, temp_tables)
            for source in source_tables:
                if source in sources:
                    lineages.append(TableLineage(source=source, target=target))

        return lineages

    def _trace_sources(
        self,
        table: TableRef,
        dependencies: dict[TableRef, set[TableRef]],
        temp_tables: set[TableRef],
        visited: set[TableRef] | None = None,
    ) -> set[TableRef]:
        """
        追溯表的真正源表（穿透临时表）

        Args:
            table: 当前表
            dependencies: 依赖图
            temp_tables: 临时表集合
            visited: 已访问的表（防止循环）

        Returns:
            真正的源表集合
        """
        if visited is None:
            visited = set()

        if table in visited:
            return set()
        visited.add(table)

        if table not in dependencies:
            return {table}

        sources: set[TableRef] = set()
        for dep in dependencies[table]:
            if dep in temp_tables:
                # 穿透临时表
                sources.update(self._trace_sources(dep, dependencies, temp_tables, visited))
            else:
                sources.add(dep)

        return sources

    def _extract_column_lineages(
        self,
        sqls: list[str],
        sources: set[TableRef],
        targets: set[TableRef],
    ) -> list[ColumnLineage]:
        """
        提取列级血缘

        Args:
            sqls: SQL 语句列表
            sources: 源表集合
            targets: 目标表集合

        Returns:
            列级血缘列表
        """
        column_lineages: list[ColumnLineage] = []

        # 构建 schema（简化版，只用于 lineage 分析）
        schema: dict[str, dict[str, str]] = {}
        for table in sources | targets:
            # 简化处理：假设所有列类型为 string
            # 实际使用时应从元数据获取真实 schema
            schema[table.full_name] = {}

        for sql in sqls:
            try:
                lineages = self._analyze_column_lineage(sql, schema)
                column_lineages.extend(lineages)
            except Exception as e:
                logger.debug("column_lineage_extract_failed", sql=sql[:50], error=str(e))
                continue

        return column_lineages

    def _analyze_column_lineage(
        self, sql: str, schema: dict[str, dict[str, str]]
    ) -> list[ColumnLineage]:
        """
        分析单条 SQL 的列级血缘

        Args:
            sql: SQL 语句
            schema: 表结构信息

        Returns:
            列级血缘列表
        """
        lineages: list[ColumnLineage] = []

        try:
            statements = sqlglot.parse(sql, dialect=self.dialect)
        except Exception:
            return lineages

        for statement in statements:
            if statement is None:
                continue

            # 只处理有输出的语句（SELECT, INSERT, CREATE AS SELECT）
            select = self._select_from_stmt(statement)
            if not select:
                continue

            # 获取目标表
            target_table = self._get_write_table(statement)

            # 分析每个输出列
            for _i, expr in enumerate(select.expressions):
                if isinstance(expr, exp.Alias):
                    output_name = expr.alias
                    source_expr = expr.this
                elif isinstance(expr, exp.Column):
                    output_name = expr.name
                    source_expr = expr
                else:
                    continue

                # 提取源列
                source_columns = self._extract_source_columns(source_expr)

                for source_col in source_columns:
                    if target_table:
                        target_col = ColumnRef(
                            table=target_table,
                            column=output_name,
                        )
                        lineages.append(
                            ColumnLineage(
                                source=source_col,
                                target=target_col,
                                transformation=self._get_transformation_type(source_expr),
                            )
                        )

        return lineages

    def _select_from_stmt(self, statement: exp.Expression) -> exp.Select | None:
        """从语句中获取 SELECT 子句"""
        if isinstance(statement, exp.Select):
            return statement
        if isinstance(statement, exp.Insert):
            expr = statement.expression
            if isinstance(expr, exp.Select):
                return expr
        if isinstance(statement, exp.Create):
            expr = statement.expression
            if isinstance(expr, exp.Select):
                return expr
        return None

    def _extract_source_columns(self, expr: exp.Expression) -> list[ColumnRef]:
        """从表达式中提取源列"""
        columns: list[ColumnRef] = []

        for col in expr.find_all(exp.Column):
            table_name = col.table if col.table else ""
            schema_name = None

            # 尝试解析 schema.table 格式
            if "." in table_name:
                parts = table_name.split(".", 1)
                schema_name = parts[0]
                table_name = parts[1]

            table_ref = TableRef(schema=schema_name, table=table_name)
            columns.append(ColumnRef(table=table_ref, column=col.name))

        return columns

    def _get_transformation_type(self, expr: exp.Expression) -> str | None:
        """获取转换类型"""
        if isinstance(expr, exp.Column):
            return "IDENTITY"
        if isinstance(expr, exp.AggFunc):
            return "AGGREGATION"
        if isinstance(expr, (exp.Add, exp.Sub, exp.Mul, exp.Div)):
            return "CALCULATION"
        if isinstance(expr, exp.Case):
            return "CONDITIONAL"
        if isinstance(expr, exp.Cast):
            return "CAST"
        if isinstance(expr, exp.Concat):
            return "CONCAT"
        return "TRANSFORMATION"
