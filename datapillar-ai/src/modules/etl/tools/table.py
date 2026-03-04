# @author Sunny
# @date 2026-01-27

"""
Tools related to knowledge base tables

Tool layered design:- list:List query(list_catalogs,list_schemas,list_tables),Default limit=5
- search:Semantic search(search_tables,search_columns)
- detail:Get details(get_table_detail,get_table_lineage,get_lineage_sql)

design principles:- Use the full path when entering parameters in the details tool(path),Such as "catalog.schema.table"
- All tool output parameters always contain complete path information.(catalog,schema,table)
- Search tool returns candidate list,Each candidate with full path
"""

import json
import logging

from pydantic import BaseModel, Field

from src.infrastructure.repository.knowledge import Neo4jColumnSearch, Neo4jTableSearch
from src.modules.etl.tools.registry import etl_tool
from src.shared.context import get_current_tenant_id, get_current_user_id

logger = logging.getLogger(__name__)


# ==================== Tool parameters Schema ====================


class ListCatalogsInput(BaseModel):
    """list Catalog Parameters"""

    limit: int = Field(default=5, ge=1, le=100, description="Return maximum quantity,Default 5")


class ListSchemasInput(BaseModel):
    """list Schema Parameters"""

    catalog: str = Field(..., description="Catalog Name")
    limit: int = Field(default=5, ge=1, le=100, description="Return maximum quantity,Default 5")


class ListTablesInput(BaseModel):
    """list Table Parameters"""

    catalog: str = Field(..., description="Catalog Name")
    schema_name: str = Field(..., description="Schema Name")
    keyword: str | None = Field(default=None, description="Filter by table name keyword(Optional)")
    limit: int = Field(default=5, ge=1, le=100, description="Return maximum quantity,Default 5")


class SearchTablesInput(BaseModel):
    """Search table parameters"""

    query: str = Field(..., description="Search keywords or natural language descriptions")
    top_k: int = Field(default=10, ge=1, le=50, description="Return maximum quantity")


class SearchColumnsInput(BaseModel):
    """Search column parameters"""

    query: str = Field(..., description="Search keywords or natural language descriptions")
    top_k: int = Field(default=10, ge=1, le=50, description="Return maximum quantity")


class GetTableDetailInput(BaseModel):
    """Parameters to get table details"""

    path: str = Field(..., description="full path to table:catalog.schema.table")


class GetTableLineageInput(BaseModel):
    """Get the parameters of table-level bloodline"""

    path: str = Field(..., description="full path to table:catalog.schema.table")
    direction: str = Field(
        default="both",
        description="bloodline direction:upstream(upstream),downstream(downstream),both(Two-way)",
    )


class GetLineageSqlInput(BaseModel):
    """Accurately search history based on bloodline SQL Parameters"""

    source_tables: list[str] = Field(
        ...,
        description="Source table path list,Format:catalog.schema.table",
    )
    target_table: str = Field(
        ...,
        description="target table path,Format:catalog.schema.table",
    )


# ==================== Internal helper function ====================


def _tool_error(message: str) -> str:
    """Constructor error response"""
    return json.dumps({"error": message}, ensure_ascii=False)


def _tool_success(data: dict) -> str:
    """Constructor responds successfully"""
    return json.dumps(data, ensure_ascii=False)


def _resolve_scope() -> tuple[int | None, int | None]:
    """Resolve the current requesting tenant/user context."""
    return get_current_tenant_id(), get_current_user_id()


def _parse_table_path(path: str) -> tuple[str, str, str] | None:
    """
    parse table path

    parameters:- path:full path,Format catalog.schema.table

    Return:- (catalog,schema,table) or None(Parsing failed)
    """
    if not path or not isinstance(path, str):
        return None
    parts = path.strip().split(".")
    if len(parts) != 3:
        return None
    catalog, schema, table = parts
    if not all([catalog.strip(), schema.strip(), table.strip()]):
        return None
    return catalog.strip(), schema.strip(), table.strip()


# ==================== List Tools(second floor) ====================


@etl_tool(
    "list_catalogs", tool_type="Catalog", desc="list directory", args_schema=ListCatalogsInput
)
def list_catalogs(limit: int = 5) -> str:
    """
    list Catalog list

    ⚠️ important:By default,only the previous 5 a(Collapse display),not all!- To see more,Please pass in a larger limit parameters(maximum 100)

    Output example:{
    "catalogs":[{"name":"hive_prod","description":"production environment Hive"},{"name":"mysql_prod","description":"production environment MySQL"}],"count":2
    }
    """
    logger.info(f"list_catalogs(limit={limit})")
    tenant_id, _ = _resolve_scope()
    if tenant_id is None:
        return _tool_error("Missing tenant context")

    try:
        catalogs = Neo4jTableSearch.list_catalogs(limit=limit, tenant_id=tenant_id)
        return _tool_success({"catalogs": catalogs, "count": len(catalogs)})
    except Exception as e:
        logger.error(f"list_catalogs Execution failed:{e}", exc_info=True)
        return _tool_error("Query failed")


@etl_tool(
    "list_schemas", tool_type="Schema", desc="List directory schema", args_schema=ListSchemasInput
)
def list_schemas(catalog: str, limit: int = 5) -> str:
    """
    list specified Catalog down Schema list

    ⚠️ important:By default,only the previous 5 a(Collapse display),not all!- To see more,Please pass in a larger limit parameters(maximum 100)

    Input example:{"catalog":"hive_prod"}

    Output example:{
    "catalog":"hive_prod","schemas":[{"name":"ods","path":"hive_prod.ods","description":"raw data layer"},{"name":"dwd","path":"hive_prod.dwd","description":"Detailed data layer"}],"count":2
    }
    """
    logger.info(f"list_schemas(catalog='{catalog}', limit={limit})")

    if not (isinstance(catalog, str) and catalog.strip()):
        return _tool_error("catalog cannot be empty")

    catalog = catalog.strip()
    tenant_id, _ = _resolve_scope()
    if tenant_id is None:
        return _tool_error("Missing tenant context")

    try:
        schemas = Neo4jTableSearch.list_schemas(catalog=catalog, limit=limit, tenant_id=tenant_id)
        if not schemas:
            return _tool_error("not found any Schema")

        # add full path
        for s in schemas:
            s["path"] = f"{catalog}.{s['name']}"
            s["catalog"] = catalog

        return _tool_success(
            {
                "catalog": catalog,
                "schemas": schemas,
                "count": len(schemas),
            }
        )
    except Exception as e:
        logger.error(f"list_schemas Execution failed:{e}", exc_info=True)
        return _tool_error("Query failed")


@etl_tool("list_tables", tool_type="Table", desc="list table", args_schema=ListTablesInput)
def list_tables(
    catalog: str,
    schema_name: str,
    keyword: str | None = None,
    limit: int = 5,
) -> str:
    """
    list specified Catalog.Schema down Table list

    ⚠️ important:By default,only the previous 5 a(Collapse display),not all!- To see more,Please pass in a larger limit parameters(maximum 100)
    - Optional:use keyword Parameters are filtered by table name keywords

    Input example:{"catalog":"hive_prod","schema_name":"ods"}

    Output example:{
    "catalog":"hive_prod","schema":"ods","tables":[{"name":"t_order","path":"hive_prod.ods.t_order","description":"order form"},{"name":"t_user","path":"hive_prod.ods.t_user","description":"User table"}],"count":2
    }
    """
    logger.info(
        f"list_tables(catalog='{catalog}', schema='{schema_name}', keyword='{keyword}', limit={limit})"
    )

    if not (isinstance(catalog, str) and catalog.strip()):
        return _tool_error("catalog cannot be empty")
    if not (isinstance(schema_name, str) and schema_name.strip()):
        return _tool_error("schema_name cannot be empty")

    catalog = catalog.strip()
    schema_name = schema_name.strip()
    tenant_id, _ = _resolve_scope()
    if tenant_id is None:
        return _tool_error("Missing tenant context")

    try:
        tables = Neo4jTableSearch.list_tables(
            catalog=catalog,
            schema=schema_name,
            keyword=keyword,
            limit=limit,
            tenant_id=tenant_id,
        )
        if not tables:
            hint = f"{catalog}.{schema_name}"
            if keyword and str(keyword).strip():
                hint = f"{hint} (keyword={keyword})"
            return _tool_error("No table found")

        # add full path
        for t in tables:
            t["path"] = f"{catalog}.{schema_name}.{t['name']}"
            t["catalog"] = catalog
            t["schema"] = schema_name

        return _tool_success(
            {
                "catalog": catalog,
                "schema": schema_name,
                "tables": tables,
                "count": len(tables),
            }
        )
    except Exception as e:
        logger.error(f"list_tables Execution failed:{e}", exc_info=True)
        return _tool_error("Query failed")


# ==================== Search Tools(Semantic search) ====================


@etl_tool(
    "search_tables", tool_type="Table", desc="Semantic search table", args_schema=SearchTablesInput
)
def search_tables(query: str, top_k: int = 10) -> str:
    """
    search table(Semantic search)

    Usage scenarios:- User asked"Search order-related tables","Find the user table" → Use this tool
    - Users only know business concepts,Use when you don't know the specific table name
    - Returns a list of tables sorted by relevance(bring score)

    ⚠️ Note:This is semantic search,Not an exact match.If the user knows the exact catalog/schema,should be used list_tables

    Input example:{"query":"Order"}

    Output example:{
    "query":"Order","tables":[{
    "path":"hive_prod.ods.t_order","catalog":"hive_prod","schema":"ods","table":"t_order","description":"Order master table","score":0.95
    }],"count":1
    }
    """
    logger.info(f"search_tables(query='{query}', top_k={top_k})")

    if not (isinstance(query, str) and query.strip()):
        return _tool_error("query cannot be empty")
    tenant_id, user_id = _resolve_scope()
    if tenant_id is None:
        return _tool_error("Missing tenant context")

    try:
        # Use vector search
        results = Neo4jTableSearch.search_tables(
            query=query.strip(),
            top_k=top_k,
            tenant_id=tenant_id,
            user_id=user_id,
        )

        # Filter to keep only Table Type
        tables = []
        for r in results:
            if r.get("type") == "Table":
                path = r.get("path") or ""
                parts = path.split(".")
                if len(parts) >= 3:
                    tables.append(
                        {
                            "path": path,
                            "catalog": parts[0],
                            "schema": parts[1],
                            "table": parts[2],
                            "description": r.get("description") or "",
                            "score": r.get("score", 0),
                        }
                    )

        return _tool_success(
            {
                "query": query.strip(),
                "tables": tables,
                "count": len(tables),
            }
        )
    except Exception as e:
        logger.error(f"search_tables Execution failed:{e}", exc_info=True)
        return _tool_error("Query failed")


@etl_tool(
    "search_columns",
    tool_type="Column",
    desc="Semantic search fields",
    args_schema=SearchColumnsInput,
)
def search_columns(query: str, top_k: int = 10) -> str:
    """
    search column(Semantic search)

    Usage scenarios:- User asked"Which tables have order status fields","Find the column related to the amount" → Use this tool
    - Used when users want to find fields with specific business meanings
    - Returns a list of columns sorted by relevance(bring score)

    Input example:{"query":"Order status"}

    Output example:{
    "query":"Order status","columns":[{
    "path":"hive_prod.ods.t_order.order_status","catalog":"hive_prod","schema":"ods","table":"t_order","column":"order_status","dataType":"varchar","description":"Order status","score":0.92
    }],"count":1
    }
    """
    logger.info(f"search_columns(query='{query}', top_k={top_k})")

    if not (isinstance(query, str) and query.strip()):
        return _tool_error("query cannot be empty")
    tenant_id, user_id = _resolve_scope()
    if tenant_id is None:
        return _tool_error("Missing tenant context")

    try:
        # Use vector search
        results = Neo4jColumnSearch.search_columns(
            query=query.strip(),
            top_k=top_k,
            tenant_id=tenant_id,
            user_id=user_id,
        )

        # Filter to keep only Column Type
        columns = []
        for r in results:
            if r.get("type") == "Column":
                path = r.get("path") or ""
                parts = path.split(".")
                if len(parts) >= 4:
                    columns.append(
                        {
                            "path": path,
                            "catalog": parts[0],
                            "schema": parts[1],
                            "table": parts[2],
                            "column": parts[3],
                            "dataType": r.get("dataType") or "",
                            "description": r.get("description") or "",
                            "score": r.get("score", 0),
                        }
                    )

        return _tool_success(
            {
                "query": query.strip(),
                "columns": columns,
                "count": len(columns),
            }
        )
    except Exception as e:
        logger.error(f"search_columns Execution failed:{e}", exc_info=True)
        return _tool_error("Query failed")


# ==================== Detail Tools(third floor) ====================


@etl_tool(
    "get_table_detail",
    tool_type="Table",
    desc="Get table details(Field/Description)",
    args_schema=GetTableDetailInput,
)
def get_table_detail(path: str) -> str:
    """
    Get table details(Contains columns and ranges)

    Usage scenarios:- User asked"What fields does this table have?","What is the table structure" → Use this tool
    - Verify table exists
    - Get field type,Description,Value range and other details

    ⚠️ path format:Must be full path catalog.schema.table

    Input example:{"path":"hive_prod.ods.t_order"}

    Output example:{
    "path":"hive_prod.ods.t_order","catalog":"hive_prod","schema":"ods","table":"t_order","description":"Order master table","columns":[{"name":"order_id","dataType":"bigint","description":"OrderID"},{"name":"order_status","dataType":"varchar","description":"Order status"}]
    }
    """
    logger.info(f"get_table_detail(path='{path}')")

    parsed = _parse_table_path(path)
    if not parsed:
        return _tool_error("Path format error,should be catalog.schema.table")

    catalog, schema, table = parsed
    tenant_id, user_id = _resolve_scope()
    if tenant_id is None:
        return _tool_error("Missing tenant context")

    try:
        detail = Neo4jTableSearch.get_table_detail(
            catalog,
            schema,
            table,
            tenant_id=tenant_id,
            user_id=user_id,
        )

        if not detail:
            return _tool_error("table not found")

        return _tool_success(
            {
                "path": path,
                "catalog": catalog,
                "schema": schema,
                "table": table,
                "description": detail.get("description") or "",
                "columns": detail.get("columns") or [],
            }
        )

    except Exception as e:
        logger.error(f"get_table_detail Execution failed:{e}", exc_info=True)
        return _tool_error("Query failed")


@etl_tool(
    "get_table_lineage",
    tool_type="Table",
    desc="Get table ancestry",
    args_schema=GetTableLineageInput,
)
def get_table_lineage(path: str, direction: str = "both") -> str:
    """
    Get table blood relationship

    Usage scenarios:- User asked"What is the upstream of this table?","Where does the data come from?" → direction="upstream"
    - User asked"What is downstream of this table?","Where does the data flow?" → direction="downstream"
    - User asked"blood relationship" → direction="both"

    ⚠️ path format:Must be full path catalog.schema.table
    ⚠️ direction parameters:upstream(upstream),downstream(downstream),both(Two-way,Default)

    Input example:{"path":"hive_prod.dwd.order_detail","direction":"upstream"}

    Output example:{
    "path":"hive_prod.dwd.order_detail","catalog":"hive_prod","schema":"dwd","table":"order_detail","direction":"upstream","upstream":["hive_prod.ods.t_order","hive_prod.ods.t_user"],"downstream":[]
    }
    """
    logger.info(f"get_table_lineage(path='{path}', direction='{direction}')")

    parsed = _parse_table_path(path)
    if not parsed:
        return _tool_error("Path format error,should be catalog.schema.table")

    catalog, schema, table = parsed
    tenant_id, user_id = _resolve_scope()
    if tenant_id is None:
        return _tool_error("Missing tenant context")

    try:
        lineage = Neo4jTableSearch.get_table_lineage(
            schema,
            table,
            direction,
            tenant_id=tenant_id,
            user_id=user_id,
        )

        if not lineage.get("upstream") and not lineage.get("downstream"):
            return _tool_error("No blood relationship found for table")

        return _tool_success(
            {
                "path": path,
                "catalog": catalog,
                "schema": schema,
                "table": table,
                "direction": direction,
                "upstream": lineage.get("upstream") or [],
                "downstream": lineage.get("downstream") or [],
                "edges": lineage.get("edges") or [],
            }
        )

    except Exception as e:
        logger.error(f"get_table_lineage Execution failed:{e}", exc_info=True)
        return _tool_error("Query failed")


@etl_tool(
    "get_lineage_sql",
    tool_type="Table",
    desc="Find history based on ancestry SQL",
    args_schema=GetLineageSqlInput,
)
def get_lineage_sql(source_tables: list[str], target_table: str) -> str:
    """
    Accurately search history based on blood relationships SQL

    Usage scenarios:- Need to refer to history SQL Use when writing
    - According to accurate source table → target table relationship,Find previously executed SQL
    - used for SQL Reference during development

    ⚠️ path format:All table paths must be full paths catalog.schema.table

    Input example:{
    "source_tables":["hive_prod.ods.t_order","hive_prod.ods.t_user"],"target_table":"hive_prod.dwd.order_detail"
    }

    Output example:{
    "source_tables":["hive_prod.ods.t_order","hive_prod.ods.t_user"],"target_table":"hive_prod.dwd.order_detail","sql_id":"abc123","sql_content":"INSERT INTO...","summary":"Clean order details from the order master table and user table","engine":"spark"
    }
    """
    logger.info(f"get_lineage_sql(source={source_tables}, target='{target_table}')")

    # Verify target table path
    target_parsed = _parse_table_path(target_table)
    if not target_parsed:
        return _tool_error("Target table path format is wrong,should be catalog.schema.table")

    # Extract schema.table Format(Neo4j Query usage)
    source_schema_tables = []
    for src in source_tables:
        parsed = _parse_table_path(src)
        if parsed:
            _, schema, table = parsed
            source_schema_tables.append(f"{schema}.{table}")

    if not source_schema_tables:
        return _tool_error("Source table path list is empty or malformed")

    _target_catalog, target_schema, target_table_name = target_parsed
    target_schema_table = f"{target_schema}.{target_table_name}"
    tenant_id, user_id = _resolve_scope()
    if tenant_id is None:
        return _tool_error("Missing tenant context")

    try:
        result = Neo4jTableSearch.find_lineage_sql(
            source_schema_tables,
            target_schema_table,
            tenant_id=tenant_id,
            user_id=user_id,
        )

        if not result:
            return _tool_error("No bloodline found SQL")

        return _tool_success(
            {
                "source_tables": source_tables,
                "target_table": target_table,
                "sql_id": result.get("sql_id"),
                "sql_content": result.get("content"),
                "summary": result.get("summary"),
                "engine": result.get("engine"),
            }
        )

    except Exception as e:
        logger.error(f"get_lineage_sql Execution failed:{e}", exc_info=True)
        return _tool_error("Query failed")


# ==================== Tool list ====================

# List Tools(second floor)
LIST_TOOLS = [list_catalogs, list_schemas, list_tables]

# Search Tools(Semantic search)
SEARCH_TOOLS = [search_tables, search_columns]

# Detail Tools(third floor)
DETAIL_TOOLS = [get_table_detail, get_table_lineage, get_lineage_sql]

# All table related tools
TABLE_TOOLS = LIST_TOOLS + SEARCH_TOOLS + DETAIL_TOOLS
