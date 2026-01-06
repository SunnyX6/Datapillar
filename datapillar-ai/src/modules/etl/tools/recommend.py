"""
推荐引导工具

工具列表：
- recommend_guidance: 推荐引导（ETL 场景）

TODO: 推荐表功能后续在 search_table.py 中实现
"""

import json
import logging

from langchain_core.tools import tool
from pydantic import BaseModel, Field

logger = logging.getLogger(__name__)


class RecommendGuidanceInput(BaseModel):
    """推荐引导的参数"""

    user_query: str = Field(..., description="用户输入（自由文本）")


@tool(
    "recommend_guidance",
    args_schema=RecommendGuidanceInput,
)
async def recommend_guidance(user_query: str) -> str:
    """
    推荐引导（ETL 场景）

    说明：
    - 暂未实现，返回空结果
    - 后续推荐表功能将在 search_table.py 中实现
    """
    logger.info("recommend_guidance(query='%s') - 暂未实现", user_query)
    return json.dumps(
        {
            "status": "success",
            "user_query": user_query,
            "recommendations": [],
        },
        ensure_ascii=False,
    )


RECOMMEND_TOOLS = [
    recommend_guidance,
]
