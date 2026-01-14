"""
Context Timeline 子模块 - 时间旅行
"""

from __future__ import annotations

from pydantic import BaseModel, Field


class TimeTravelRequest(BaseModel):
    """时间旅行请求"""

    session_id: str = Field(..., description="会话 ID")
    target_checkpoint_id: str = Field(..., description="目标检查点 ID")
    create_branch: bool = Field(
        default=False,
        description="是否创建分支（而不是覆盖）",
    )
    branch_name: str | None = Field(
        default=None,
        description="分支名称",
    )


class TimeTravelResult(BaseModel):
    """时间旅行结果"""

    success: bool = Field(..., description="是否成功")
    session_id: str = Field(..., description="会话 ID（分支时为新 ID）")
    checkpoint_id: str = Field(..., description="当前检查点 ID")
    removed_entries: int = Field(default=0, description="移除的事件数")
    message: str = Field(default="", description="结果消息")
    is_branch: bool = Field(default=False, description="是否为分支")
    branch_name: str | None = Field(default=None, description="分支名称")

    @classmethod
    def success_result(
        cls,
        session_id: str,
        checkpoint_id: str,
        removed_entries: int = 0,
        message: str = "时间旅行成功",
        is_branch: bool = False,
        branch_name: str | None = None,
    ) -> TimeTravelResult:
        """创建成功结果"""
        return cls(
            success=True,
            session_id=session_id,
            checkpoint_id=checkpoint_id,
            removed_entries=removed_entries,
            message=message,
            is_branch=is_branch,
            branch_name=branch_name,
        )

    @classmethod
    def failure_result(
        cls,
        session_id: str,
        checkpoint_id: str,
        message: str = "时间旅行失败",
    ) -> TimeTravelResult:
        """创建失败结果"""
        return cls(
            success=False,
            session_id=session_id,
            checkpoint_id=checkpoint_id,
            message=message,
        )
