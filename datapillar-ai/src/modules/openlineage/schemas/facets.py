"""
OpenLineage 标准 Facet 模型

Facet 是 OpenLineage 的元数据扩展机制，不同来源会携带不同的 facet：
- schema: 数据集的列信息
- sql: SQL 语句
- columnLineage: 列级血缘
- lifecycleStateChange: 生命周期变更（CREATE/ALTER/DROP）
- gravitino: Gravitino 自定义元数据（表描述、属性、审计信息等）
"""

from enum import Enum
from typing import Any

from pydantic import BaseModel, Field


class BaseFacet(BaseModel):
    """Facet 基类"""

    producer: str | None = Field(default=None, alias="_producer")
    schema_url: str | None = Field(default=None, alias="_schemaURL")

    model_config = {"populate_by_name": True, "extra": "allow"}


class SchemaField(BaseModel):
    """Schema Facet 中的字段定义"""

    name: str = Field(..., description="字段名称")
    type: str | None = Field(default=None, description="字段数据类型")
    description: str | None = Field(default=None, description="字段描述")

    model_config = {"extra": "allow"}


class SchemaDatasetFacet(BaseFacet):
    """
    Schema Dataset Facet

    描述数据集的列结构信息
    参考：https://openlineage.io/spec/facets/1-1-1/SchemaDatasetFacet.json
    """

    fields: list[SchemaField] = Field(default_factory=list, description="字段列表")

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "SchemaDatasetFacet":
        """从字典创建 SchemaDatasetFacet"""
        fields_data = data.get("fields", [])
        fields = [SchemaField(**f) for f in fields_data]
        return cls(
            fields=fields,
            producer=data.get("_producer"),
            schema_url=data.get("_schemaURL"),
        )


# ==================== Gravitino 自定义 Facet ====================


class TableChangeType(str, Enum):
    """表变更类型"""

    # 表级别变更
    RENAME_TABLE = "RENAME_TABLE"
    UPDATE_COMMENT = "UPDATE_COMMENT"
    SET_PROPERTY = "SET_PROPERTY"
    REMOVE_PROPERTY = "REMOVE_PROPERTY"
    ADD_INDEX = "ADD_INDEX"
    DELETE_INDEX = "DELETE_INDEX"

    # 列级别变更
    ADD_COLUMN = "ADD_COLUMN"
    DELETE_COLUMN = "DELETE_COLUMN"
    RENAME_COLUMN = "RENAME_COLUMN"
    UPDATE_COLUMN_TYPE = "UPDATE_COLUMN_TYPE"
    UPDATE_COLUMN_COMMENT = "UPDATE_COLUMN_COMMENT"
    UPDATE_COLUMN_POSITION = "UPDATE_COLUMN_POSITION"
    UPDATE_COLUMN_NULLABILITY = "UPDATE_COLUMN_NULLABILITY"
    UPDATE_COLUMN_DEFAULT_VALUE = "UPDATE_COLUMN_DEFAULT_VALUE"
    UPDATE_COLUMN_AUTO_INCREMENT = "UPDATE_COLUMN_AUTO_INCREMENT"


class TableChangeInfo(BaseModel):
    """表变更信息"""

    type: TableChangeType = Field(..., description="变更类型")

    # 表级别变更字段
    newName: str | None = Field(default=None, description="新表名（RENAME_TABLE）")
    newComment: str | None = Field(default=None, description="新注释（UPDATE_COMMENT / UPDATE_COLUMN_COMMENT）")
    propertyKey: str | None = Field(default=None, description="属性键（SET_PROPERTY / REMOVE_PROPERTY）")
    propertyValue: str | None = Field(default=None, description="属性值（SET_PROPERTY）")

    # 列级别变更字段
    columnName: str | None = Field(default=None, description="列名")
    oldColumnName: str | None = Field(default=None, description="旧列名（RENAME_COLUMN）")
    newColumnName: str | None = Field(default=None, description="新列名（RENAME_COLUMN）")
    dataType: str | None = Field(default=None, description="列数据类型（ADD_COLUMN / UPDATE_COLUMN_TYPE）")
    columnComment: str | None = Field(default=None, description="列注释（ADD_COLUMN）")
    nullable: bool | None = Field(default=None, description="是否可空")
    autoIncrement: bool | None = Field(default=None, description="是否自增")
    defaultValue: str | None = Field(default=None, description="默认值")
    position: str | None = Field(default=None, description="列位置（FIRST / AFTER xxx）")

    # 索引相关字段
    indexName: str | None = Field(default=None, description="索引名（ADD_INDEX / DELETE_INDEX）")
    indexType: str | None = Field(default=None, description="索引类型（ADD_INDEX）")
    indexColumns: list[str] | None = Field(default=None, description="索引列（ADD_INDEX）")

    model_config = {"populate_by_name": True, "extra": "allow"}


class GravitinoColumnMetadata(BaseModel):
    """Gravitino 列扩展元数据"""

    name: str = Field(..., description="列名")
    nullable: bool | None = Field(default=None, description="是否可空")
    autoIncrement: bool | None = Field(default=None, description="是否自增")
    defaultValue: str | None = Field(default=None, description="默认值表达式")

    model_config = {"populate_by_name": True, "extra": "allow"}


class GravitinoDatasetFacet(BaseFacet):
    """
    Gravitino 自定义 Dataset Facet

    传递 Gravitino 特有的元数据，包括表描述、属性、审计信息等
    """

    description: str | None = Field(default=None, description="表/Schema 的描述")
    properties: dict[str, str] | None = Field(default=None, description="扩展属性")
    partitions: str | None = Field(default=None, description="分区信息")
    distribution: str | None = Field(default=None, description="分布信息")
    sortOrders: str | None = Field(default=None, description="排序信息")
    indexes: str | None = Field(default=None, description="索引信息")
    creator: str | None = Field(default=None, description="创建者")
    createTime: str | None = Field(default=None, description="创建时间（ISO 8601）")
    lastModifier: str | None = Field(default=None, description="最后修改者")
    lastModifiedTime: str | None = Field(default=None, description="最后修改时间（ISO 8601）")
    columns: list[GravitinoColumnMetadata] | None = Field(default=None, description="列扩展元数据")
    changes: list[TableChangeInfo] | None = Field(default=None, description="表变更列表（alter_table 事件）")

    model_config = {"populate_by_name": True, "extra": "allow"}

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "GravitinoDatasetFacet":
        """从字典创建 GravitinoDatasetFacet"""
        columns_data = data.get("columns", [])
        columns = [GravitinoColumnMetadata(**c) for c in columns_data] if columns_data else None

        changes_data = data.get("changes", [])
        changes = [TableChangeInfo(**c) for c in changes_data] if changes_data else None

        return cls(
            description=data.get("description"),
            properties=data.get("properties"),
            partitions=data.get("partitions"),
            distribution=data.get("distribution"),
            sortOrders=data.get("sortOrders"),
            indexes=data.get("indexes"),
            creator=data.get("creator"),
            createTime=data.get("createTime"),
            lastModifier=data.get("lastModifier"),
            lastModifiedTime=data.get("lastModifiedTime"),
            columns=columns,
            changes=changes,
            producer=data.get("_producer"),
            schema_url=data.get("_schemaURL"),
        )

    def get_column_metadata(self, column_name: str) -> GravitinoColumnMetadata | None:
        """根据列名获取列扩展元数据"""
        if not self.columns:
            return None
        for col in self.columns:
            if col.name == column_name:
                return col
        return None


class LifecycleStateChange(str, Enum):
    """生命周期状态变更类型"""

    CREATE = "CREATE"
    ALTER = "ALTER"
    DROP = "DROP"
    TRUNCATE = "TRUNCATE"
    RENAME = "RENAME"
    OVERWRITE = "OVERWRITE"


class LifecycleStateChangeDatasetFacet(BaseFacet):
    """
    Lifecycle State Change Dataset Facet

    描述数据集的生命周期变更（CREATE/ALTER/DROP 等）
    参考：https://openlineage.io/spec/facets/1-0-1/LifecycleStateChangeDatasetFacet.json
    """

    lifecycleStateChange: LifecycleStateChange = Field(..., description="生命周期状态变更类型")

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "LifecycleStateChangeDatasetFacet":
        """从字典创建 LifecycleStateChangeDatasetFacet"""
        state = data.get("lifecycleStateChange", "")
        return cls(
            lifecycleStateChange=LifecycleStateChange(state),
            producer=data.get("_producer"),
            schema_url=data.get("_schemaURL"),
        )


class SQLJobFacet(BaseFacet):
    """
    SQL Job Facet

    描述作业执行的 SQL 语句
    参考：https://openlineage.io/spec/facets/1-1-0/SQLJobFacet.json
    """

    query: str = Field(..., description="SQL 语句")
    dialect: str | None = Field(default=None, description="SQL 方言（如 spark, hive, flink）")

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "SQLJobFacet":
        """从字典创建 SQLJobFacet"""
        return cls(
            query=data.get("query", ""),
            dialect=data.get("dialect"),
            producer=data.get("_producer"),
            schema_url=data.get("_schemaURL"),
        )


class TransformationType(str, Enum):
    """转换类型"""

    DIRECT = "DIRECT"
    INDIRECT = "INDIRECT"


class Transformation(BaseModel):
    """列血缘转换信息"""

    type: TransformationType = Field(..., description="转换类型：DIRECT 或 INDIRECT")
    subtype: str | None = Field(default=None, description="转换子类型")
    description: str | None = Field(default=None, description="转换描述")
    masking: bool | None = Field(default=None, description="是否为脱敏转换")

    model_config = {"extra": "allow"}


class InputField(BaseModel):
    """
    列血缘中的输入字段

    表示输出列依赖的某个输入列
    """

    namespace: str = Field(..., description="输入数据集命名空间")
    name: str = Field(..., description="输入数据集名称")
    field: str = Field(..., description="输入字段名称")
    transformations: list[Transformation] | None = Field(default_factory=list, description="转换信息")

    def get_full_qualified_name(self) -> str:
        """获取完整限定名：namespace/name.field"""
        return f"{self.namespace}/{self.name}.{self.field}"


class ColumnLineageField(BaseModel):
    """
    单个输出列的血缘信息

    包含该列依赖的所有输入字段和转换描述
    """

    inputFields: list[InputField] = Field(default_factory=list, description="输入字段列表")
    transformationDescription: str | None = Field(default=None, description="转换描述")
    transformationType: str | None = Field(default=None, description="转换类型：IDENTITY 或 MASKED")

    model_config = {"populate_by_name": True, "extra": "allow"}


class ColumnLineageDatasetFacet(BaseFacet):
    """
    Column Lineage Dataset Facet

    描述列级血缘关系
    参考：https://openlineage.io/spec/facets/1-2-0/ColumnLineageDatasetFacet.json
    """

    fields: dict[str, ColumnLineageField] = Field(
        default_factory=dict,
        description="输出列到输入列的映射，key 为输出列名",
    )
    dataset: list[InputField] | None = Field(
        default=None,
        description="影响整个数据集的输入字段（如 WHERE、JOIN 条件）",
    )

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "ColumnLineageDatasetFacet":
        """从字典创建 ColumnLineageDatasetFacet"""
        fields_data = data.get("fields", {})
        fields = {}
        for output_col, lineage_info in fields_data.items():
            input_fields = []
            for input_field_data in lineage_info.get("inputFields", []):
                transformations = []
                for t in input_field_data.get("transformations", []):
                    transformations.append(
                        Transformation(
                            type=TransformationType(t.get("type", "DIRECT")),
                            subtype=t.get("subtype"),
                            description=t.get("description"),
                            masking=t.get("masking"),
                        )
                    )
                input_fields.append(
                    InputField(
                        namespace=input_field_data.get("namespace", ""),
                        name=input_field_data.get("name", ""),
                        field=input_field_data.get("field", ""),
                        transformations=transformations,
                    )
                )
            fields[output_col] = ColumnLineageField(
                inputFields=input_fields,
                transformationDescription=lineage_info.get("transformationDescription"),
                transformationType=lineage_info.get("transformationType"),
            )

        dataset_fields = None
        if "dataset" in data:
            dataset_fields = [
                InputField(
                    namespace=f.get("namespace", ""),
                    name=f.get("name", ""),
                    field=f.get("field", ""),
                )
                for f in data["dataset"]
            ]

        return cls(
            fields=fields,
            dataset=dataset_fields,
            producer=data.get("_producer"),
            schema_url=data.get("_schemaURL"),
        )
