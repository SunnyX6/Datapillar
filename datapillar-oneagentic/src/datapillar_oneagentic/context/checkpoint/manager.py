"""
Context Checkpoint 子模块 - 检查点管理

封装 Checkpointer，提供统一的检查点管理接口。
"""

from __future__ import annotations

import logging
import uuid
from typing import TYPE_CHECKING, Any

from datapillar_oneagentic.context.types import CheckpointType
from datapillar_oneagentic.core.types import SessionKey

if TYPE_CHECKING:
    pass

logger = logging.getLogger(__name__)


def _generate_checkpoint_id() -> str:
    """生成检查点 ID"""
    return f"cp_{uuid.uuid4().hex[:12]}"


class CheckpointManager:
    """
    检查点管理器

    封装 LangGraph Checkpointer，提供：
    - 统一的检查点创建接口
    - 检查点列表查询
    - 状态恢复
    """

    def __init__(
        self,
        *,
        key: SessionKey,
        checkpointer=None,
    ):
        """
        创建检查点管理器

        参数：
        - key: SessionKey（namespace + session_id 组合）
        - checkpointer: Checkpointer 实例（可选）
        """
        self._key = key
        self._checkpointer = checkpointer

    @property
    def key(self) -> SessionKey:
        """获取会话标识"""
        return self._key

    @property
    def thread_id(self) -> str:
        """获取线程 ID（用于 LangGraph）"""
        return str(self._key)

    def get_config(self, checkpoint_id: str | None = None) -> dict:
        """获取 LangGraph 配置"""
        config: dict[str, Any] = {
            "configurable": {
                "thread_id": self.thread_id,
            }
        }
        if checkpoint_id:
            config["configurable"]["checkpoint_id"] = checkpoint_id
        return config

    def generate_checkpoint_id(
        self,
        checkpoint_type: CheckpointType = CheckpointType.AUTO,
    ) -> str:
        """生成新的检查点 ID"""
        type_prefix = checkpoint_type.value[:3]
        return f"cp_{type_prefix}_{uuid.uuid4().hex[:8]}"

    async def get_state(self, compiled_graph, checkpoint_id: str | None = None) -> dict | None:
        """
        获取指定检查点的状态

        参数：
        - compiled_graph: 编译后的 LangGraph
        - checkpoint_id: 检查点 ID（可选，不传则获取最新状态）

        返回：
        - 状态字典或 None
        """
        config = self.get_config(checkpoint_id)
        try:
            state_snapshot = await compiled_graph.aget_state(config)
            if state_snapshot and state_snapshot.values:
                return dict(state_snapshot.values)
        except Exception as e:
            logger.error(f"获取状态失败: {e}")
        return None

    async def update_state(
        self,
        compiled_graph,
        updates: dict,
        checkpoint_id: str | None = None,
    ) -> str | None:
        """
        更新状态

        参数：
        - compiled_graph: 编译后的 LangGraph
        - updates: 状态更新
        - checkpoint_id: 检查点 ID（可选）

        返回：
        - 新的检查点 ID 或 None
        """
        config = self.get_config(checkpoint_id)
        try:
            result = await compiled_graph.aupdate_state(config, updates)
            if result and "configurable" in result:
                return result["configurable"].get("checkpoint_id")
        except Exception as e:
            logger.error(f"更新状态失败: {e}")
        return None

    async def list_checkpoints(self, compiled_graph, limit: int = 20) -> list[dict]:
        """
        列出检查点

        参数：
        - compiled_graph: 编译后的 LangGraph
        - limit: 最大数量

        返回：
        - 检查点列表
        """
        config = self.get_config()
        checkpoints = []
        try:
            async for state in compiled_graph.aget_state_history(config):
                checkpoints.append({
                    "checkpoint_id": state.config.get("configurable", {}).get("checkpoint_id"),
                    "created_at": state.created_at,
                    "parent_id": state.parent_config.get("configurable", {}).get("checkpoint_id")
                    if state.parent_config else None,
                })
                if len(checkpoints) >= limit:
                    break
        except Exception as e:
            logger.error(f"列出检查点失败: {e}")
        return checkpoints

    async def delete(self) -> bool:
        """删除会话的所有检查点"""
        if not self._checkpointer:
            return False
        try:
            import asyncio
            import inspect
            delete_method = getattr(self._checkpointer, "delete_thread", None)
            if delete_method is None:
                return False
            if asyncio.iscoroutinefunction(delete_method) or inspect.isasyncgenfunction(delete_method):
                await delete_method(self.thread_id)
            else:
                delete_method(self.thread_id)
            return True
        except Exception as e:
            logger.error(f"删除检查点失败: {e}")
            return False
