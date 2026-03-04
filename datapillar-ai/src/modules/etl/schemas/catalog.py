# @author Sunny
# @date 2026-01-27

"""
Catalog / Metadata Q&A related schema

CatalogAgent Responsibility for Metadata Q&A:- answer"What are there catalog/schema/table/Field"and other questions
- Answer table structure,Overview of ancestry and other issues
- Dont do it ETL needs analysis,Not generated SQL
"""

from pydantic import BaseModel, Field


class OptionItem(BaseModel):
    """Structured options(Unified representation of assets at all levels)"""

    type: str = Field(
        ...,
        description="Asset type:catalog/schema/table/column/lineage/valuedomain",
    )
    name: str = Field(..., description="Name")
    path: str = Field(
        ...,
        description="full path:catalog / catalog.schema / catalog.schema.table / catalog.schema.table.column",
    )
    description: str | None = Field(default=None, description="Description")
    tools: list[str] = Field(
        default_factory=list,
        description="Available tools(Expand to the next level or view details)",
    )
    extra: dict | None = Field(
        default=None,
        description="Additional information(Such as column of data_type,lineage of upstream/downstream)",
    )


class CatalogResultOutput(BaseModel):
    """
    CatalogAgent of LLM output(used for structured output)

    design principles:- summary:One sentence summary
    - answer:Detailed text answer
    - options:Structured options(Unified representation of assets at all levels)
    - ambiguities:Questions that need clarification
    - confidence:information adequacy
    """

    summary: str = Field(..., description="One sentence summary answer")
    answer: str = Field(..., description="Detailed text answer(Chinese answers for users)")
    options: list[OptionItem] = Field(
        default_factory=list,
        description="Structured options(catalog/schema/table/column Wait)",
    )
    ambiguities: list[str] = Field(
        default_factory=list,
        description="List of questions requiring clarification(When information is insufficient)",
    )
    recommendations: list[str] = Field(
        default_factory=list,
        description="Recommended guidance(Optional)",
    )
    confidence: float = Field(
        default=1.0,
        ge=0.0,
        le=1.0,
        description="information adequacy,When clarification is needed < 0.7",
    )


class CatalogResult(BaseModel):
    """
    CatalogAgent deliverables(Deposit Blackboard)

    Contains original user input + LLM output
    """

    user_query: str = Field(..., description="original user input")
    summary: str = Field(..., description="One sentence summary")
    answer: str = Field(..., description="Detailed text answer")
    options: list[OptionItem] = Field(default_factory=list, description="Structured options")
    ambiguities: list[str] = Field(
        default_factory=list, description="Questions that need clarification"
    )
    recommendations: list[str] = Field(default_factory=list, description="Recommended guidance")
    confidence: float = Field(default=1.0, ge=0.0, le=1.0)

    @classmethod
    def from_output(cls, output: CatalogResultOutput, user_query: str) -> "CatalogResult":
        """from LLM Output the complete results of the build"""
        return cls(
            user_query=user_query,
            summary=output.summary,
            answer=output.answer,
            options=output.options,
            ambiguities=output.ambiguities,
            recommendations=output.recommendations,
            confidence=output.confidence,
        )

    def needs_clarification(self) -> bool:
        """Does the user need clarification?"""
        return self.confidence < 0.7 and len(self.ambiguities) > 0
