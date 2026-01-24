"""
会话级 Todo 模型

用于团队级进度跟踪，不参与路由或委派。
"""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field

from datapillar_oneagentic.core.status import ExecutionStatus, TERMINAL_STATUSES
from datapillar_oneagentic.utils.prompt_format import format_markdown
from datapillar_oneagentic.utils.time import now_ms
StepStatus = ExecutionStatus
TodoStatus = StepStatus


class TodoItem(BaseModel):
    """Todo 条目（会话级）"""

    id: str = Field(..., description="Todo ID（t1, t2...）")
    description: str = Field(..., description="任务描述")
    status: TodoStatus = Field(default=ExecutionStatus.PENDING, description="状态")
    result: str | None = Field(default=None, description="结果说明")

    def update(self, *, status: TodoStatus | None = None, result: str | None = None) -> bool:
        """更新条目，返回是否有变更"""
        changed = False
        if status and status != self.status:
            self.status = status
            changed = True
        if result is not None and result != self.result:
            self.result = result
            changed = True
        return changed


class TodoUpdate(BaseModel):
    """Todo 更新（用于上报或审计）"""

    id: str = Field(..., description="Todo ID（t1, t2...）")
    status: TodoStatus = Field(..., description="状态")
    result: str | None = Field(default=None, description="结果说明")


class TodoPlanOp(BaseModel):
    """Todo 规划操作（用于结构变更）"""

    op: Literal["add", "remove", "replace"] = Field(..., description="操作类型")
    items: list[str] = Field(default_factory=list, description="add/replace 的条目列表")
    todo_ids: list[str] = Field(default_factory=list, description="remove 的条目 ID 列表")
    goal: str | None = Field(default=None, description="可选目标")


class SessionTodoList(BaseModel):
    """
    会话级 Todo 列表

    仅用于进度跟踪，不影响执行路径。
    """

    session_id: str = Field(..., description="会话 ID")
    goal: str | None = Field(default=None, description="用户目标")
    items: list[TodoItem] = Field(default_factory=list, description="Todo 列表")
    next_item_id: int = Field(default=1, description="下一个 Todo ID")
    updated_at_ms: int = Field(default_factory=now_ms, description="更新时间")

    def set_goal(self, goal: str) -> None:
        """设置目标"""
        self.goal = goal
        self.updated_at_ms = now_ms()

    def add_item(self, description: str) -> TodoItem:
        """新增 Todo 条目"""
        item = TodoItem(
            id=f"t{self.next_item_id}",
            description=description,
        )
        self.items.append(item)
        self.next_item_id += 1
        self.updated_at_ms = now_ms()
        return item

    def get_item(self, item_id: str) -> TodoItem | None:
        """获取指定 Todo 条目"""
        for item in self.items:
            if item.id == item_id:
                return item
        return None

    def get_next_pending_item(self) -> TodoItem | None:
        """获取下一个待处理条目"""
        for item in self.items:
            if item.status == ExecutionStatus.PENDING:
                return item
        return None

    def apply_updates(self, updates: list[TodoUpdate]) -> bool:
        """批量应用更新，返回是否有变更"""
        changed = False
        for update in updates:
            item = self.get_item(update.id)
            if not item:
                continue
            if item.update(status=update.status, result=update.result):
                changed = True
        if self._prune_terminal_items():
            changed = True
        if changed:
            self.updated_at_ms = now_ms()
        return changed

    def apply_plan(self, ops: list[TodoPlanOp]) -> bool:
        """应用规划操作，返回是否有变更"""
        changed = False
        for op in ops:
            if op.goal and op.goal != self.goal:
                self.goal = op.goal
                changed = True

            if op.op == "add":
                if self._add_items(op.items):
                    changed = True
            elif op.op == "remove":
                if self._remove_items(op.todo_ids):
                    changed = True
            elif op.op == "replace":
                if self._replace_items(op.items):
                    changed = True

        if changed:
            self.updated_at_ms = now_ms()
        return changed

    def _add_items(self, items: list[str]) -> bool:
        changed = False
        for raw in items:
            text = raw.strip()
            if not text:
                continue
            self.add_item(text)
            changed = True
        return changed

    def _remove_items(self, todo_ids: list[str]) -> bool:
        if not todo_ids:
            return False
        id_set = {item_id.strip() for item_id in todo_ids if item_id.strip()}
        if not id_set:
            return False
        before = len(self.items)
        self.items = [item for item in self.items if item.id not in id_set]
        return len(self.items) != before

    def _replace_items(self, items: list[str]) -> bool:
        if not items:
            if self.items:
                self.items = []
                self.next_item_id = 1
                return True
            return False
        self.items = []
        self.next_item_id = 1
        return self._add_items(items)

    def _prune_terminal_items(self) -> bool:
        if not self.items:
            return False
        before = len(self.items)
        self.items = [item for item in self.items if item.status not in TERMINAL_STATUSES]
        return len(self.items) != before

    def is_completed(self) -> bool:
        """是否所有条目都已结束"""
        if not self.items:
            return False
        return all(item.status in TERMINAL_STATUSES for item in self.items)

    def to_prompt(self, *, include_title: bool = True) -> str:
        """生成 Todo 提示词"""
        if not self.items:
            return ""

        team_lines: list[str] = []
        if self.goal:
            team_lines.append(f"Goal: {self.goal}")

        for item in self.items:
            status_marker = f"[{item.status.value}]"
            line = f"{status_marker} [{item.id}] {item.description}"
            if item.result:
                line += f" -> {item.result}"
            team_lines.append(line)

        focus_item = self.get_next_pending_item()
        if not include_title:
            lines = [f"- {item}" for item in team_lines]
            if focus_item:
                lines.append(f"- Current Focus: [{focus_item.id}] {focus_item.description}")
            return "\n".join(lines).strip()

        sections: list[tuple[str, str | list[str]]] = [("Team Todo", team_lines)]
        if focus_item:
            sections.append(("Current Focus", f"[{focus_item.id}] {focus_item.description}"))

        return format_markdown(
            title="Todo Context",
            sections=sections,
        )
