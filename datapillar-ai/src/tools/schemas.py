"""
统一的工具返回 Schema 定义（Pydantic）
避免手写 key，便于 QueryAgent 统一解析。
"""

from typing import List, Optional, Dict, Any
from pydantic import BaseModel, Field, ConfigDict


class ColumnItem(BaseModel):
    name: str
    displayName: Optional[str] = None
    dataType: Optional[str] = None
    comment: Optional[str] = None
    isPrimaryKey: Optional[bool] = None
    isForeignKey: Optional[bool] = None
    isNullable: Optional[bool] = None
    ordinalPosition: Optional[int] = None


class TableItem(BaseModel):
    name: str
    displayName: Optional[str] = None
    description: Optional[str] = None
    rowCount: Optional[int] = None
    columns: List[Dict[str, Any]] = Field(default_factory=list)


class ComponentItem(BaseModel):
    name: str
    displayName: Optional[str] = None
    type: Optional[str] = None
    category: Optional[str] = None
    description: Optional[str] = None
    supportedOperations: Optional[List[str]] = None
    version: Optional[str] = None
    status: Optional[str] = None
    owner: Optional[str] = None
    configSchema: Any = None


class WorkflowItem(BaseModel):
    workflowId: Optional[str] = None
    name: Optional[str] = None
    description: Optional[str] = None
    taskType: Optional[str] = None
    componentsUsed: Optional[Any] = None
    tablesInvolved: Optional[Any] = None
    userQuery: Optional[str] = None
    workflowJson: Optional[Any] = None
    usageCount: Optional[int] = None
    successRate: Optional[float] = None


class NodeRef(BaseModel):
    model_config = ConfigDict(populate_by_name=True)
    name: str = Field(..., alias="name")
    type: str = Field(..., alias="type")


class ColumnMapping(BaseModel):
    model_config = ConfigDict(populate_by_name=True)
    from_field: Optional[str] = Field(None, alias="from")
    to_field: Optional[str] = Field(None, alias="to")


class RelationshipItem(BaseModel):
    model_config = ConfigDict(populate_by_name=True)
    from_node: NodeRef = Field(..., alias="from")
    to: NodeRef
    type: str
    cardinality: Optional[str] = None
    relationships: List[ColumnMapping] = Field(default_factory=list)


class MixedSearchPayload(BaseModel):
    tables: List[TableItem] = Field(default_factory=list)
    relationships: List[RelationshipItem] = Field(default_factory=list)
    components: List[ComponentItem] = Field(default_factory=list)
    workflows: List[WorkflowItem] = Field(default_factory=list)
    columns: List[ColumnItem] = Field(default_factory=list)


class TableListPayload(BaseModel):
    tables: List[TableItem] = Field(default_factory=list)
    relationships: List[RelationshipItem] = Field(default_factory=list)


class ComponentListPayload(BaseModel):
    components: List[ComponentItem] = Field(default_factory=list)


class TableDetailPayload(BaseModel):
    table: TableItem

