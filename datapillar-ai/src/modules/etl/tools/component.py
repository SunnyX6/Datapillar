"""
组件工具

工具列表：
- list_component: 获取企业支持的所有大数据组件列表
"""

import json
import logging

from pydantic import BaseModel

from src.infrastructure.repository import Component
from src.modules.oneagentic import tool

logger = logging.getLogger(__name__)


class ListComponentInput(BaseModel):
    """获取组件列表的参数（无参数）"""


@tool("list_component", args_schema=ListComponentInput)
def list_component() -> str:
    """
    获取企业支持的所有大数据组件列表

    返回可用的 Job 组件（HIVE、SPARK_SQL、SHELL 等）。
    ArchitectAgent 必须基于这些组件来设计工作流节点。

    返回字段：
    - id: 组件数字 ID（设计 Job 时填充到 type_id）
    - code: 组件代码（如 HIVE、SPARK_SQL，设计 Job 时填充到 type）
    - name: 组件名称
    - type: 组件类型（SQL/SCRIPT/SYNC）
    - description: 组件描述
    - config_schema: 配置模板

    输入示例（JSON）：
    - {}
    """
    logger.info("list_component()")

    try:
        results = Component.list_active()

        if not results:
            return json.dumps(
                {
                    "status": "error",
                    "message": "未找到任何可用组件",
                    "components": [],
                },
                ensure_ascii=False,
            )

        components = []
        for row in results:
            # 解析 config_schema
            config_schema = row.get("config_schema")
            if isinstance(config_schema, str):
                try:
                    config_schema = json.loads(config_schema)
                except json.JSONDecodeError:
                    config_schema = {}

            components.append(
                {
                    "id": row.get("id"),
                    "code": row.get("component_code"),
                    "name": row.get("component_name"),
                    "type": row.get("component_type"),
                    "description": row.get("description"),
                    "config_schema": config_schema,
                }
            )

        return json.dumps(
            {
                "status": "success",
                "total": len(components),
                "components": components,
                "hint": "设计 Job 时，type 填组件 code，type_id 填组件 id",
            },
            ensure_ascii=False,
        )

    except Exception as e:
        logger.error(f"list_component 执行失败: {e}", exc_info=True)
        return json.dumps(
            {
                "status": "error",
                "message": f"查询失败：{str(e)}",
                "components": [],
            },
            ensure_ascii=False,
        )


COMPONENT_TOOLS = [
    list_component,
]
