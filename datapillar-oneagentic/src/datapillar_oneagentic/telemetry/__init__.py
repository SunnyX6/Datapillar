"""
OpenTelemetry 遥测模块

提供分布式追踪和指标收集功能。

特点：
- 自动追踪 Agent/Tool/LLM 调用
- 与事件总线集成
- 支持 OTLP 导出
- 可选启用（生产环境）

使用示例：
```python
from datapillar_oneagentic.telemetry import init_telemetry, get_tracer

# 初始化（应用启动时）
init_telemetry(service_name="oneagentic", endpoint="http://localhost:4318")

# 获取 tracer
tracer = get_tracer()

# 手动追踪
with tracer.start_as_current_span("my_operation"):
    do_something()
```
"""

from datapillar_oneagentic.telemetry.tracer import (
    init_telemetry,
    shutdown_telemetry,
    get_tracer,
    is_telemetry_enabled,
)
from datapillar_oneagentic.telemetry.instrumentation import (
    instrument_events,
    trace_agent,
    trace_tool,
)

__all__ = [
    # 初始化
    "init_telemetry",
    "shutdown_telemetry",
    "get_tracer",
    "is_telemetry_enabled",
    # 自动埋点
    "instrument_events",
    "trace_agent",
    "trace_tool",
]
