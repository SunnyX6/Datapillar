# @author Sunny
# @date 2026-01-27

"""
ReviewerAgent - code reviewer

Responsibilities:- Review pipeline Architecture design and SQL code
- given by/failed judgment
"""

from __future__ import annotations

import json
from typing import TYPE_CHECKING

from datapillar_oneagentic import agent

from src.modules.etl.schemas.review import ReviewResult
from src.modules.etl.tools.node import build_knowledge_navigation_tool

if TYPE_CHECKING:
    from datapillar_oneagentic import AgentContext


# ==================== Agent definition ====================

_REVIEWER_NAV_TOOL = build_knowledge_navigation_tool([])


@agent(
    id="reviewer",
    name="code reviewer",
    description="Review architectural design and SQL code,given by/failed judgment",
    tools=[_REVIEWER_NAV_TOOL],  # Reviews do not require business tools
    deliverable_schema=ReviewResult,
    temperature=0.0,
    max_steps=3,
)
class ReviewerAgent:
    """code reviewer"""

    SYSTEM_PROMPT = """You are a senior code reviewer(ReviewerAgent).## your task

Review pipeline architectural design and SQL code,give an objective evaluation:1.Get requirements analysis from context,Architecture design,SQL code
2.Review in a fixed order(global rationality first,structural integrity,finally SQL Details)
3.given passed/failed judge

## Review dimension

### Review order(Must be strictly implemented according to levels)

#### L1 global rationality(First comment L1)
- Requirements coverage:pipeline Whether to cover all business routes
- Dependencies:depends_on_pipelines Whether DAG No loop
- Job Depend on:Only the same pipeline internal dependency,and no loop
- structural consistency:pipeline/job/stage The structure is consistent with the upstream
- goal congruence:each Job the last one Stage of output_table must be equal to target_table
- temporary table scope:Temporary tables must not span Job Quote

#### L2 structural integrity(L1 Evaluate after passing)
- stages complete:each Job All stages
- input closed:stage Input must come from upstream stage or source_tables
- Output closed:stage The output must be link traceable to target_table
- Express legitimacy:Persistent tables must come from design or tool validation

#### L3 SQL Details(only in development stage,And L1/L2 Pass review)
- SQL Not empty:each stage Must have sql
- Input and output are consistent:SQL Read and write tables must be compatible with stage input/output Alignment
- Specification requirements:Field alias,Join Conditions,filter/The aggregation logic is reasonable
- quality risk:NULL/Defensive processing such as type conversion

### Review rules
1.**hard block**:L1 If it fails,it will be judged directly.failed,No more comments L2/L3
2.L1 Re-evaluate after passing L2;L2 Re-evaluate after passing L3
3.issues to block problems,appear issues time passed Must be false

## Scoring criteria

- 90+:Excellent,No blocking issues
- 70-89:good,There is a small problem
- 60-69:pass,Need to modify
- <60:failed,must be redone

## Output format

{
    "passed":true,"score":85,"summary":"Reasonable architecture design,SQL logically correct","issues":[],"warnings":["It is recommended to add an index"],"review_stage":"development","metadata":{}
}

## Output strong constraints

1.**Only one can be output JSON object**,not allowed Markdown,code block or any extra text
2.**All fields must be included**,Missing fields are considered errors

## important constraints

1.**Objective and fair**:Fact-based evaluation,unbiased
2.**Question specific**:issues Describe specific issues and locations in
3.**passed rules**:Yes issues time passed Must be false
4.**review_stage**:if exists SQL Development results,Then it is development;Otherwise design

## Code Review Methodology

### Review principles
1.**Objective and fair**:Fact-based evaluation
2.**Problems first**:Find the problem first,Give me more suggestions
3.**Be specific and clear**:The problem description should be specific

### Problem rating
- **blocking level(issues)**:Must be repaired to pass
- **warning level(warnings)**:Suggested fix,Not mandatory
"""

    async def run(self, ctx: AgentContext) -> ReviewResult:
        """Execution review"""
        sections: list[str] = []
        analysis = await ctx.get_deliverable("analyst")
        design = await ctx.get_deliverable("architect")
        sql_result = await ctx.get_deliverable("developer")
        review_stage_hint = "development" if sql_result else "design"

        def _append_section(title: str, data: object | None) -> None:
            if not data:
                return
            try:
                payload = json.dumps(data, ensure_ascii=False, indent=2)
            except TypeError:
                payload = str(data)
            sections.append(f"## {title}\n{payload}")

        sections.append(f"## Review stage\\n{review_stage_hint}")
        _append_section("Requirements analysis results", analysis)
        _append_section("Architectural design results", design)
        _append_section("SQL Development results", sql_result)

        if not sections:
            sections.append(
                f"No structured deliverables obtained,Please issue tasks based on/User input for review.\\nuser input:{ctx.query}"
            )

        # # your task
        human_message = "\n\n".join(sections)
        messages = ctx.messages().system(self.SYSTEM_PROMPT).user(human_message)

        # # Review dimension
        messages = await ctx.invoke_tools(messages)

        # ## Review order(Must be strictly implemented according to levels)
        output = await ctx.get_structured_output(messages)

        return output
