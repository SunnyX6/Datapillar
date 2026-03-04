# @author Sunny
# @date 2026-01-27

"""
SQL ancestry analyzer

Based on sqlglot parse SQL,Extract table-level and column-level blood relationships.Support temporary table identification and penetration.core logic:1.parse SQL Extract read-write table
2.Build dependency graph
3.Identify temporary tables(in session Created and read in)
4.Penetrate temporary table,Establish true bloodline
5.Extract rank lineage
"""

import logging
from dataclasses import dataclass, field
from enum import Enum

import sqlglot
from sqlglot import exp

logger = logging.getLogger(__name__)


class TableRole(str, Enum):
    """table role"""

    SOURCE = "source"
    TARGET = "target"
    TEMP = "temp"


@dataclass
class TableRef:
    """table reference"""

    catalog: str | None = None
    schema: str | None = None
    table: str = ""

    @property
    def full_name(self) -> str:
        """Full table name"""
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
    """column reference"""

    table: TableRef
    column: str

    @property
    def full_name(self) -> str:
        """Full column name"""
        return f"{self.table.full_name}.{self.column}"


@dataclass
class ColumnLineage:
    """Ranked descent"""

    source: ColumnRef
    target: ColumnRef
    transformation: str | None = None


@dataclass
class TableLineage:
    """Superficial blood"""

    source: TableRef
    target: TableRef
    sql: str | None = None


@dataclass
class LineageResult:
    """Bloodline analysis results"""

    sources: set[TableRef] = field(default_factory=set)
    targets: set[TableRef] = field(default_factory=set)
    temp_tables: set[TableRef] = field(default_factory=set)
    table_lineages: list[TableLineage] = field(default_factory=list)
    column_lineages: list[ColumnLineage] = field(default_factory=list)


class SQLLineageAnalyzer:
    """
    SQL ancestry analyzer

    Identify table types based on dependency graph topology:- degree=0(Read only but not write)→ SOURCE
    - out degree=0(Only write but not read)→ TARGET
    - intermediate node(both read and write)→ temporary table
    """

    def __init__(self, dialect: str = "hive") -> None:
        """
        Initialize analyzer

        Args:dialect:SQL dialect(hive,spark,mysql,postgres Wait)
        """
        self.dialect = dialect

    def analyze_sql(self, sql: str) -> LineageResult:
        """
        Analyze a single line SQL bloodline

        Args:sql:SQL statement

        Returns:LineageResult:Bloodline results
        """
        return self.analyze_session([sql])

    def analyze_session(self, sqls: list[str]) -> LineageResult:
        """
        Analyze a session multiple of SQL

        core logic:1.Parse each SQL,Extract read-write table
        2.Build dependency graph
        3.Identify temporary tables
        4.Penetrate temporary table to establish real blood relationship

        Args:sqls:SQL statement list

        Returns:LineageResult:Bloodline results
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
                logger.warning(
                    "sql_parse_failed",
                    extra={"data": {"sql": sql[:100], "error": str(e)}},
                )
                continue

        # temporary table = in session Created in And be read
        temp_tables = created_tables & read_tables

        # source = be read But No session created within
        sources = read_tables - created_tables

        # target = is written But Not a temporary table
        targets = written_tables - temp_tables

        # Construct table-level lineage(Penetrate temporary table)
        table_lineages = self._build_table_lineages(sql_dependencies, sources, targets, temp_tables)

        # Extract rank lineage
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
        from SQL Extract read and written tables

        Args:sql:SQL statement

        Returns:(Read table collection,The set of tables written to)
        """
        reads: set[TableRef] = set()
        writes: set[TableRef] = set()

        try:
            statements = sqlglot.parse(sql, dialect=self.dialect)
        except Exception as e:
            logger.warning(
                "sql_parse_error",
                extra={"data": {"sql": sql[:100], "error": str(e)}},
            )
            return reads, writes

        for statement in statements:
            if statement is None:
                continue

            # Extract the written table
            write_table = self._get_write_table(statement)
            if write_table:
                writes.add(write_table)

            # Extract the read table(Exclude writing to the table itself)
            for table in statement.find_all(exp.Table):
                table_ref = self._table_to_ref(table)
                if table_ref and table_ref not in writes:
                    reads.add(table_ref)

        return reads, writes

    def _get_write_table(self, statement: exp.Expression) -> TableRef | None:
        """Get the target table to write to"""
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
        """will sqlglot Table Convert to TableRef"""
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
        Construct table-level lineage,Penetrate temporary table

        Args:sql_dependencies:[(reads,writes,sql),...]
        sources:Source table collection
        targets:target table collection
        temp_tables:temporary table collection

        Returns:List of table-level ancestry
        """
        lineages: list[TableLineage] = []

        # Build dependency graph:table -> [Dependent tables]
        dependencies: dict[TableRef, set[TableRef]] = {}
        for reads, writes, _sql in sql_dependencies:
            for write_table in writes:
                if write_table not in dependencies:
                    dependencies[write_table] = set()
                dependencies[write_table].update(reads)

        # for each target table,Trace back to the true source table
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
        Trace back to the real source table of the table(Penetrate temporary table)

        Args:table:current table
        dependencies:dependency graph
        temp_tables:temporary table collection
        visited:visited table(Prevent loops)

        Returns:Real source table collection
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
                # Penetrate temporary table
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
        Extract rank lineage

        Args:sqls:SQL statement list
        sources:Source table collection
        targets:target table collection

        Returns:Ranked lineage list
        """
        column_lineages: list[ColumnLineage] = []

        # Build schema(Simplified version,only for lineage analysis)
        schema: dict[str, dict[str, str]] = {}
        for table in sources | targets:
            # Simplified processing:Assume all column types are string
            # In actual use,the true value should be obtained from metadata schema
            schema[table.full_name] = {}

        for sql in sqls:
            try:
                lineages = self._analyze_column_lineage(sql, schema)
                column_lineages.extend(lineages)
            except Exception as e:
                logger.debug(
                    "column_lineage_extract_failed",
                    extra={"data": {"sql": sql[:50], "error": str(e)}},
                )
                continue

        return column_lineages

    def _analyze_column_lineage(
        self, sql: str, schema: dict[str, dict[str, str]]
    ) -> list[ColumnLineage]:
        """
        Analyze a single line SQL s pedigree

        Args:sql:SQL statement
        schema:Table structure information

        Returns:Ranked lineage list
        """
        lineages: list[ColumnLineage] = []

        try:
            statements = sqlglot.parse(sql, dialect=self.dialect)
        except Exception:
            return lineages

        for statement in statements:
            if statement is None:
                continue

            # Only processes statements with output(SELECT,INSERT,CREATE AS SELECT)
            select = self._select_from_stmt(statement)
            if not select:
                continue

            # Get target table
            target_table = self._get_write_table(statement)

            # Analyze each output column
            for _i, expr in enumerate(select.expressions):
                if isinstance(expr, exp.Alias):
                    output_name = expr.alias
                    source_expr = expr.this
                elif isinstance(expr, exp.Column):
                    output_name = expr.name
                    source_expr = expr
                else:
                    continue

                # Extract source column
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
        """Get from statement SELECT clause"""
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
        """Extract source columns from expression"""
        columns: list[ColumnRef] = []

        for col in expr.find_all(exp.Column):
            table_name = col.table if col.table else ""
            schema_name = None

            # try to parse schema.table Format
            if "." in table_name:
                parts = table_name.split(".", 1)
                schema_name = parts[0]
                table_name = parts[1]

            table_ref = TableRef(schema=schema_name, table=table_name)
            columns.append(ColumnRef(table=table_ref, column=col.name))

        return columns

    def _get_transformation_type(self, expr: exp.Expression) -> str | None:
        """Get conversion type"""
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
