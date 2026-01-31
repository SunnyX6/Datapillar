# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
OpenLineage 标准事件模型

基于 OpenLineage Spec 2.0.2，使用 Pydantic v2 实现
参考：https://openlineage.io/spec/2-0-2/OpenLineage.json
"""

from datetime import datetime
from enum import Enum
from typing import Any

from pydantic import BaseModel, Field, field_validator


class EventType(str, Enum):
    """
    OpenLineage 事件类型

    - START: 作业开始
    - RUNNING: 作业运行中
    - COMPLETE: 作业成功完成
    - ABORT: 作业被中止
    - FAIL: 作业失败
    - OTHER: 其他事件（用于补充元数据）
    """

    START = "START"
    RUNNING = "RUNNING"
    COMPLETE = "COMPLETE"
    ABORT = "ABORT"
    FAIL = "FAIL"
    OTHER = "OTHER"


class Run(BaseModel):
    """OpenLineage 运行实例"""

    runId: str = Field(..., description="运行实例的全局唯一 ID（UUID 格式）")
    facets: dict[str, Any] | None = Field(default_factory=dict, description="运行级别的 facets")


class Job(BaseModel):
    """OpenLineage 作业"""

    namespace: str = Field(
        ..., description="作业所属的命名空间", examples=["spark://cluster", "gravitino"]
    )
    name: str = Field(
        ..., description="作业名称", examples=["etl_job.orders", "gravitino.create_table"]
    )
    facets: dict[str, Any] | None = Field(default_factory=dict, description="作业级别的 facets")


class Dataset(BaseModel):
    """OpenLineage 数据集基类"""

    namespace: str = Field(
        ...,
        description="数据集所属的命名空间",
        examples=["hive://warehouse", "gravitino://metalake/catalog"],
    )
    name: str = Field(..., description="数据集名称", examples=["db.schema.table"])
    facets: dict[str, Any] | None = Field(default_factory=dict, description="数据集级别的 facets")

    def qualified_name(self) -> str:
        """获取完整限定名"""
        return f"{self.namespace}/{self.name}"


class InputDataset(Dataset):
    """OpenLineage 输入数据集"""

    inputFacets: dict[str, Any] | None = Field(
        default_factory=dict, description="输入数据集特有的 facets"
    )


class OutputDataset(Dataset):
    """OpenLineage 输出数据集"""

    outputFacets: dict[str, Any] | None = Field(
        default_factory=dict, description="输出数据集特有的 facets"
    )


class RunEvent(BaseModel):
    """
    OpenLineage RunEvent - 核心事件模型

    这是 OpenLineage 的核心事件类型，包含：
    - 事件元信息（时间、类型、生产者）
    - 运行实例信息
    - 作业信息
    - 输入/输出数据集
    """

    eventTime: datetime = Field(..., description="事件发生时间（ISO 8601 格式）")
    eventType: EventType | None = Field(default=None, description="事件类型")
    run: Run = Field(..., description="运行实例")
    job: Job = Field(..., description="作业信息")
    inputs: list[InputDataset] = Field(default_factory=list, description="输入数据集列表")
    outputs: list[OutputDataset] = Field(default_factory=list, description="输出数据集列表")
    producer: str | None = Field(default=None, description="事件生产者标识（URI）")
    schemaURL: str | None = Field(default=None, description="OpenLineage 规范版本")

    @field_validator("eventTime", mode="before")
    @classmethod
    def parse_event_time(cls, v: Any) -> datetime:
        """解析事件时间，支持多种格式"""
        if isinstance(v, datetime):
            return v
        if isinstance(v, str):
            return datetime.fromisoformat(v.replace("Z", "+00:00"))
        raise ValueError(f"无法解析事件时间: {v}")

    def get_producer_type(self) -> str:
        """
        从 producer 提取来源类型

        例如：
        - https://github.com/apache/gravitino/openlineage-listener → gravitino
        - https://github.com/OpenLineage/OpenLineage/tree/.../spark → spark
        - https://github.com/OpenLineage/OpenLineage/tree/.../flink → flink
        """
        if not self.producer:
            return "unknown"

        producer_lower = self.producer.lower()
        if "gravitino" in producer_lower:
            return "gravitino"
        if "spark" in producer_lower:
            return "spark"
        if "flink" in producer_lower:
            return "flink"
        if "hive" in producer_lower:
            return "hive"
        if "airflow" in producer_lower:
            return "airflow"
        if "dbt" in producer_lower:
            return "dbt"

        return "unknown"

    def get_all_datasets(self) -> list[Dataset]:
        """获取所有数据集（输入 + 输出）"""
        datasets: list[Dataset] = []
        datasets.extend(self.inputs)
        datasets.extend(self.outputs)
        return datasets

    def has_sql_facet(self) -> bool:
        """检查是否包含 SQL facet"""
        return self.job.facets is not None and "sql" in self.job.facets

    def get_sql(self) -> str | None:
        """获取 SQL 语句"""
        job_facets = self.job.facets
        if not job_facets or "sql" not in job_facets:
            return None
        sql_facet = job_facets.get("sql", {})
        return sql_facet.get("query") if isinstance(sql_facet, dict) else None

    def get_sql_dialect(self) -> str | None:
        """获取 SQL 方言"""
        job_facets = self.job.facets
        if not job_facets or "sql" not in job_facets:
            return None
        sql_facet = job_facets.get("sql", {})
        return sql_facet.get("dialect") if isinstance(sql_facet, dict) else None
