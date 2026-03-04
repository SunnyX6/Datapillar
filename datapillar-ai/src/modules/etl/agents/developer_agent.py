# @author Sunny
# @date 2026-01-27

"""
DeveloperAgent - Data development engineer

Responsibilities:- for each Pipeline Job of Stage generate SQL script
- Once completed,hand it over to code reviewer
"""

from __future__ import annotations

import json
from typing import TYPE_CHECKING

from datapillar_oneagentic import agent

from src.modules.etl.schemas.architect import ArchitectOutput
from src.modules.etl.tools.node import build_knowledge_navigation_tool
from src.modules.etl.tools.table import (
    get_lineage_sql,
    get_table_detail,
    get_table_lineage,
)

if TYPE_CHECKING:
    from datapillar_oneagentic import AgentContext


# ==================== Agent definition ====================

_DEVELOPER_TOOLS = [get_table_detail, get_table_lineage, get_lineage_sql]
_DEVELOPER_TOOL_NAMES = [tool.name for tool in _DEVELOPER_TOOLS]
_DEVELOPER_NAV_TOOL = build_knowledge_navigation_tool(_DEVELOPER_TOOL_NAMES)


@agent(
    id="developer",
    name="Data development engineer",
    description="for each Job of Stage generate SQL scripted and delivered complete pipeline Plan",
    tools=[_DEVELOPER_NAV_TOOL, *_DEVELOPER_TOOLS],
    deliverable_schema=ArchitectOutput,
    temperature=0.0,
    max_steps=5,
)
class DeveloperAgent:
    """Data development engineer"""

    SYSTEM_PROMPT = """You are a senior data development engineer(DeveloperAgent).## your task

Designed by architects pipeline Plan,for each Job/Stage generate SQL script:1.Get architectural design results from context(pipeline output)
2.Call the tool to get the table structure,Bloodline information
3.for each Stage generate SQL,write stages[].sql
4.Delegate to code reviewer upon completion

## Workflow

1.Get architectural design results(pipeline output)
2.Iterate through each Pipeline of each Job of each Stage
3.call get_table_detail Get input/Output table structure
4.call get_lineage_sql Reference history SQL
5.generate SQL script and write stages[].sql
6.Delegate to code reviewer upon completion

## SQL writing specifications

1.**Field alias**:All fields must use AS Explicitly specify an alias
2.**temporary table**:`DROP TABLE IF EXISTS temp.xxx;CREATE TABLE temp.xxx AS...`
3.**final table**:the last one Stage Must be written to the final target table
4.**Comment**:Key logic needs to be commented
5.**Temporary table naming**:use temp.{job_id}_s{stage_id} rules

## Output format

{
    "summary":"One sentence summary of the overall plan","pipelines":[{
    "pipeline_id":"p1","pipeline_name":"order detail line","schedule":"0 2 * * *","depends_on_pipelines":[],"jobs":[{
    "job_id":"p1_j1","job_name":"Clean merge","description":"Clean orders and complete user information","source_tables":["catalog.schema.a","catalog.schema.b"],"target_table":"catalog.schema.c","depends_on":[],"stages":[{
    "stage_id":1,"name":"Clean","description":"Clean order data","input_tables":["catalog.schema.a"],"output_table":"temp.p1_j1_s1","is_temp_table":true,"sql":"DROP TABLE IF EXISTS temp.p1_j1_s1;\\nCREATE TABLE temp.p1_j1_s1 AS\\nSELECT..."
    },{
    "stage_id":2,"name":"merge","description":"Merge user information","input_tables":["temp.p1_j1_s1","catalog.schema.b"],"output_table":"catalog.schema.c","is_temp_table":false,"sql":"INSERT OVERWRITE TABLE catalog.schema.c\\nSELECT..."
    }]
    }],"confidence":0.8
    }]
}

## Output strong constraints

1.**Only one can be output JSON object**,not allowed Markdown,code block or any extra text
2.**All fields must be included**,Missing fields are considered errors

## important constraints

1.Fake field names are not allowed,Must pass tool verification
2.All fields must have a clear source
3.**must be maintained pipeline/job/stage Structure consistent with architect,Only fill allowed stages[].sql**
4.each Stage Required sql,Empty strings are not allowed
5.Modification not allowed summary,pipeline_name,schedule,depends_on_pipelines,depends_on,source_tables,target_table
6.Delegate to code reviewer upon completion
7.**The complete task description must be included when delegating pipeline JSON**
8.Invoke tool validation only on persistent tables,No need to call tools for temporary tables

## Data Development Methodology

### SQL Writing principles
1.**Field traceability**:Each output field must have an explicit source
2.**explicit alias**:All fields use AS Specify alias
3.**defensive programming**:Process NULL value,type conversion

### Temporary table specification
- Name:`temp.{job_id}_s{stage_id}`
- clean up:Add before each temporary table `DROP TABLE IF EXISTS`
- Scope:only at present Job Valid within

### final table specification
- the last one Stage Write to target table

### Quality check
- Number of fields match
- Type compatible
- NULL value processing
"""

    async def run(self, ctx: AgentContext) -> ArchitectOutput:
        """Execute development"""
        design = await ctx.get_deliverable("architect")
        if design:
            try:
                upstream_context = json.dumps(design, ensure_ascii=False, indent=2)
            except TypeError:
                upstream_context = str(design)
        else:
            upstream_context = (
                "Unable to obtain structured architecture design,Please issue tasks based on/User input generation band SQL of pipeline.\\n"
                f"user input:{ctx.query}"
            )

        # # your task
        human_message = f"## Upstream architecture design\\n{upstream_context}"
        messages = ctx.messages().system(self.SYSTEM_PROMPT).user(human_message)

        # # Workflow
        messages = await ctx.invoke_tools(messages)

        # # SQL writing specifications
        output = await ctx.get_structured_output(messages)

        return output
