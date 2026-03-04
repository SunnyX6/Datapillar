# @author Sunny
# @date 2026-01-27

"""
CatalogAgent - Metadata Q&A Specialist

Responsibilities:- Answer to users about data catalog/Metadata issues
- Query catalog/schema/table structure/Field/Bloodline
"""

from __future__ import annotations

from typing import TYPE_CHECKING

from datapillar_oneagentic import agent

from src.modules.etl.schemas.catalog import CatalogResultOutput
from src.modules.etl.tools.node import build_knowledge_navigation_tool
from src.modules.etl.tools.table import (
    get_lineage_sql,
    get_table_detail,
    get_table_lineage,
    list_catalogs,
    list_schemas,
    list_tables,
    search_columns,
    search_tables,
)

if TYPE_CHECKING:
    from datapillar_oneagentic import AgentContext


# ==================== Agent definition ====================

_CATALOG_TOOLS = [
    list_catalogs,
    list_schemas,
    list_tables,
    search_tables,
    search_columns,
    get_table_detail,
    get_table_lineage,
    get_lineage_sql,
]
_CATALOG_TOOL_NAMES = [tool.name for tool in _CATALOG_TOOLS]
_CATALOG_NAV_TOOL = build_knowledge_navigation_tool(_CATALOG_TOOL_NAMES)


@agent(
    id="catalog",
    name="Metadata Specialist",
    description="Answer questions about metadata(data directory,table structure,Field,Bloodline)",
    tools=[
        _CATALOG_NAV_TOOL,
        *_CATALOG_TOOLS,
    ],
    deliverable_schema=CatalogResultOutput,
    temperature=0.0,
    max_steps=5,
)
class CatalogAgent:
    """Metadata Q&A Specialist"""

    SYSTEM_PROMPT = """you are Datapillar Metadata Q&A Specialist(CatalogAgent).## your task

Answer to users about data catalog/Metadata issues:- "What are there catalog/schema/table"
- "What fields does a table have?/What is the table structure"
- "Overview of the upstream and downstream bloodlines of a certain table"

you dont do it ETL needs analysis,No workflow generated,Dont write SQL.## Workflow

1.Analyze user problems,Use the right tools in the right situations to gain knowledge
2.**Tools must be called to obtain data**,Cant answer in a vacuum
3.Answer user questions honestly based on actual data returned by the tool

## Output format

{
    "summary":"Summarize the query results in one sentence","answer":"Detailed answer content","options":[{
    "type":"catalog/schema/table","name":"Name","path":"full path","description":"Description","tools":["list_tables"],"extra":{"column":"data_type"}
    }],"ambiguities":["Questions that need clarification"],"recommendations":["Recommendedcatalogorschema","Recommended table"],"confidence":0.9
}

## Output strong constraints

1.**Only one can be output JSON object**,not allowed Markdown,code block or any extra text
2.**All fields must be included**,Missing fields are considered errors

## important constraints
1.All data must be returned from the tool,Fabrication is prohibited
2.The path returned by the tool is always complete catalog.schema.table Format,Use directly
3.If the scope of the users problem is not clear,settings confidence < 0.7 And in ambiguities Inquiry
4.**Strong output constraints must be followed,Return to complete JSON**

### path format
All paths are three-stage:`{catalog}.{schema}.{table}`

## prohibited
1.No misuse of tools
2.No random fabrications
"""

    async def run(self, ctx: AgentContext) -> CatalogResultOutput:
        """Execute query"""
        # # your task
        messages = ctx.messages().system(self.SYSTEM_PROMPT).user(ctx.query)

        # # Workflow
        messages = await ctx.invoke_tools(messages)

        # # Output format
        output = await ctx.get_structured_output(messages)

        # # Output strong constraints
        if output.confidence < 0.7 and output.ambiguities:
            ctx.interrupt({"message": "need more information", "questions": output.ambiguities})
            output = await ctx.get_structured_output(messages)

        return output
