"""
统一的工具返回 Schema 定义（Pydantic）
避免手写 key，便于 QueryAgent 统一解析。
"""

from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class ColumnItem(BaseModel):
    name: str
    displayName: str | None = None
    dataType: str | None = None
    comment: str | None = None
    isPrimaryKey: bool | None = None
    isForeignKey: bool | None = None
    isNullable: bool | None = None
    ordinalPosition: int | None = None


class TableItem(BaseModel):
    name: str
    displayName: str | None = None
    description: str | None = None
    rowCount: int | None = None
    columns: list[dict[str, Any]] = Field(default_factory=list)


class ComponentItem(BaseModel):
    name: str
    displayName: str | None = None
    type: str | None = None
    category: str | None = None
    description: str | None = None
    supportedOperations: list[str] | None = None
    version: str | None = None
    status: str | None = None
    owner: str | None = None
    configSchema: Any = None


class WorkflowItem(BaseModel):
    workflowId: str | None = None
    name: str | None = None
    description: str | None = None
    taskType: str | None = None
    componentsUsed: Any | None = None
    tablesInvolved: Any | None = None
    userQuery: str | None = None
    workflowJson: Any | None = None
    usageCount: int | None = None
    successRate: float | None = None


class NodeRef(BaseModel):
    model_config = ConfigDict(populate_by_name=True)
    name: str = Field(..., alias="name")
    type: str = Field(..., alias="type")


class ColumnMapping(BaseModel):
    model_config = ConfigDict(populate_by_name=True)
    from_field: str | None = Field(None, alias="from")
    to_field: str | None = Field(None, alias="to")


class RelationshipItem(BaseModel):
    model_config = ConfigDict(populate_by_name=True)
    from_node: NodeRef = Field(..., alias="from")
    to: NodeRef
    type: str
    cardinality: str | None = None
    relationships: list[ColumnMapping] = Field(default_factory=list)


class MixedSearchPayload(BaseModel):
    tables: list[TableItem] = Field(default_factory=list)
    relationships: list[RelationshipItem] = Field(default_factory=list)
    components: list[ComponentItem] = Field(default_factory=list)
    workflows: list[WorkflowItem] = Field(default_factory=list)
    columns: list[ColumnItem] = Field(default_factory=list)


class TableListPayload(BaseModel):
    tables: list[TableItem] = Field(default_factory=list)
    relationships: list[RelationshipItem] = Field(default_factory=list)


class ComponentListPayload(BaseModel):
    components: list[ComponentItem] = Field(default_factory=list)


class TableDetailPayload(BaseModel):
    table: TableItem
