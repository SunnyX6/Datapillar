# @author Sunny
# @date 2026-01-27

"""
Component tools

Tool list:- list_component:Get a list of all enterprise-supported big data components
"""

import json
import logging

from pydantic import BaseModel

from src.infrastructure.repository import Component
from src.modules.etl.tools.registry import etl_tool
from src.shared.context import get_current_tenant_id

logger = logging.getLogger(__name__)


class ListComponentInput(BaseModel):
    """Get the parameters of the component list(no parameters)"""


@etl_tool(
    "list_component",
    tool_type="Component",
    desc="List available components",
    args_schema=ListComponentInput,
)
def list_component() -> str:
    """
    Get a list of all enterprise-supported big data components

    Return available Job components(HIVE,SPARK_SQL,SHELL Wait).ArchitectAgent Workflow nodes must be designed based on these components.Return fields:- id:Component numbers ID(design Job filled into type_id)
    - code:component code(Such as HIVE,SPARK_SQL,design Job filled into type)
    - name:Component name
    - type:Component type(SQL/SCRIPT/SYNC)
    - description:Component description
    - config_schema:Configuration template

    Input example(JSON):- {}
    """
    logger.info("list_component()")
    tenant_id = get_current_tenant_id()
    if tenant_id is None:
        return json.dumps(
            {
                "error": "Missing tenant context",
                "components": [],
            },
            ensure_ascii=False,
        )

    try:
        results = Component.list_active(tenant_id=tenant_id)

        if not results:
            return json.dumps(
                {
                    "error": "No available components found",
                    "components": [],
                },
                ensure_ascii=False,
            )

        components = []
        for row in results:
            # parse config_schema
            config_schema = row.get("config_schema")
            if config_schema is None:
                config_schema = row.get("job_params")
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
                "total": len(components),
                "components": components,
                "hint": "design Job time,type Fill in components code,type_id Fill in components id",
            },
            ensure_ascii=False,
        )

    except Exception as e:
        logger.error(f"list_component Execution failed:{e}", exc_info=True)
        return json.dumps(
            {
                "error": "Query failed",
                "components": [],
            },
            ensure_ascii=False,
        )


COMPONENT_TOOLS = [
    list_component,
]
