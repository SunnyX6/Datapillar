"""Context timeline submodule - time travel."""

from __future__ import annotations

from pydantic import BaseModel, Field


class TimeTravelRequest(BaseModel):
    """Time travel request."""

    session_id: str = Field(..., description="Session ID")
    target_checkpoint_id: str = Field(..., description="Target checkpoint ID")
    create_branch: bool = Field(
        default=False,
        description="Whether to create a branch instead of overwriting",
    )
    branch_name: str | None = Field(
        default=None,
        description="Branch name",
    )


class TimeTravelResult(BaseModel):
    """Time travel result."""

    success: bool = Field(..., description="Whether the operation succeeded")
    session_id: str = Field(..., description="Session ID (new ID when branched)")
    checkpoint_id: str = Field(..., description="Current checkpoint ID")
    removed_entries: int = Field(default=0, description="Number of removed entries")
    message: str = Field(default="", description="Result message")
    is_branch: bool = Field(default=False, description="Whether this is a branch")
    branch_name: str | None = Field(default=None, description="Branch name")

    @classmethod
    def success_result(
        cls,
        session_id: str,
        checkpoint_id: str,
        removed_entries: int = 0,
        message: str = "Time travel succeeded",
        is_branch: bool = False,
        branch_name: str | None = None,
    ) -> TimeTravelResult:
        """Create a success result."""
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
        message: str = "Time travel failed",
    ) -> TimeTravelResult:
        """Create a failure result."""
        return cls(
            success=False,
            session_id=session_id,
            checkpoint_id=checkpoint_id,
            message=message,
        )
