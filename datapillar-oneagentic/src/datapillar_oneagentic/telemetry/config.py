"""
遥测配置
"""

from pydantic import BaseModel, Field


class TelemetryConfig(BaseModel):
    """遥测配置"""

    enabled: bool = Field(
        default=False,
        description="是否启用遥测",
    )

    otlp_endpoint: str | None = Field(
        default=None,
        description="OTLP 端点（如 http://localhost:4318）",
    )

    service_name: str = Field(
        default="datapillar-oneagentic",
        description="服务名称",
    )

    verbose: bool = Field(
        default=False,
        description="是否输出详细日志",
    )

    log_level: str = Field(
        default="INFO",
        description="日志级别",
    )
