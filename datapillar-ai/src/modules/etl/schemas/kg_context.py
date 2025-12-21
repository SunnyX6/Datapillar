"""
知识上下文数据结构（精简版）

KnowledgeContext 只包含轻量级的表级信息，
列级血缘、DQ 规则等重量级数据通过 Tool 按需查询。
"""

from typing import List, Dict, Any, Optional, Literal
from pydantic import BaseModel, Field


class ColumnInfo(BaseModel):
    """列信息（精简版，只保留核心字段）"""
    name: str
    data_type: str = "string"
    description: Optional[str] = None
    is_primary_key: bool = False


class TableSchema(BaseModel):
    """表结构信息（精简版）"""
    name: str
    display_name: Optional[str] = None
    description: Optional[str] = None

    # 只保留关键列（主键 + 前 10 个字段）
    key_columns: List[ColumnInfo] = Field(default_factory=list)
    column_count: int = Field(default=0, description="总列数")

    # 业务层级
    layer: Optional[Literal["SRC", "ODS", "DWD", "DWS", "ADS"]] = None
    schema_name: Optional[str] = None
    subject_name: Optional[str] = None
    catalog_name: Optional[str] = None
    domain_name: Optional[str] = None


class TableLineage(BaseModel):
    """表级血缘（轻量）"""
    source_table: str
    target_table: str
    confidence: float = Field(default=0.5, ge=0.0, le=1.0)


class JoinHint(BaseModel):
    """JOIN 关系提示"""
    left_table: str
    left_column: str
    right_table: str
    right_column: str
    join_type: Literal["INNER", "LEFT", "RIGHT", "FULL"] = "LEFT"


class BusinessContext(BaseModel):
    """业务上下文"""
    domain: Optional[str] = None
    catalog: Optional[str] = None
    subject: Optional[str] = None
    schema: Optional[str] = None
    layer: Optional[str] = None


class ReferenceCase(BaseModel):
    """历史成功案例（用于 Few-shot 学习）"""
    case_id: str
    user_query: str  # 原始用户需求（Few-shot prompt）
    sql_text: Optional[str] = None  # 成功的 SQL（Few-shot response）
    intent: str = ""  # ETL 意图
    source_tables: List[str] = Field(default_factory=list)
    target_tables: List[str] = Field(default_factory=list)
    tags: List[str] = Field(default_factory=list)


class Component(BaseModel):
    """ETL 组件（datax/hive/spark/flink 等）"""
    component_id: str
    component_name: str
    description: Optional[str] = None


class KnowledgeContext(BaseModel):
    """
    知识上下文（精简版）

    只包含轻量级的表级信息，重量级数据通过 Tool 按需查询：
    - 列级血缘 → get_column_lineage(source_table, target_table)
    - DQ 规则 → get_dq_rules(table_name)
    - 参考 SQL → search_reference_sql(query)
    """

    # 表信息（key: 表名）
    tables: Dict[str, TableSchema] = Field(default_factory=dict)

    # 表级血缘（轻量，只记录 A→B 关系）
    table_lineage: List[TableLineage] = Field(default_factory=list)

    # JOIN 关系
    join_hints: List[JoinHint] = Field(default_factory=list)

    # 业务上下文
    business_context: Optional[BusinessContext] = None

    # 历史成功案例（Few-shot 学习）
    reference_cases: List[ReferenceCase] = Field(default_factory=list)

    # 可用组件
    components: List[Component] = Field(default_factory=list)

    # 知识缺口
    gaps: List[str] = Field(default_factory=list)

    def get_table(self, name: str) -> Optional[TableSchema]:
        """获取表信息"""
        return self.tables.get(name)

    def get_source_tables(self) -> List[str]:
        """获取所有源表"""
        sources = set()
        for lineage in self.table_lineage:
            sources.add(lineage.source_table)
        for name, table in self.tables.items():
            if table.layer in ("SRC", "ODS"):
                sources.add(name)
        return list(sources)

    def get_target_tables(self) -> List[str]:
        """获取所有目标表"""
        targets = set()
        for lineage in self.table_lineage:
            targets.add(lineage.target_table)
        for name, table in self.tables.items():
            if table.layer in ("DWD", "DWS", "ADS"):
                targets.add(name)
        return list(targets)

    def get_join_for_tables(self, left: str, right: str) -> Optional[JoinHint]:
        """获取两表之间的 JOIN 关系"""
        for hint in self.join_hints:
            if (hint.left_table == left and hint.right_table == right) or \
               (hint.left_table == right and hint.right_table == left):
                return hint
        return None

    def get_table_names(self) -> List[str]:
        """获取所有表名"""
        return list(self.tables.keys())

    def get_component_ids(self) -> List[str]:
        """获取所有可用组件 ID"""
        return [c.type for c in self.components]
