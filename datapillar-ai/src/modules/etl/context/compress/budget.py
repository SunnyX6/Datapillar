"""
上下文预算与压缩触发（不含集成）

目标：
- 支持两类压缩触发：
  1) 用户手动触发（推荐用显式命令/按钮，避免误伤真实需求内容）
  2) 自动触发：当即将触达模型上下文窗口上限时，提前压缩（或在报错后触发重试）

约束：
- 本模块不负责“怎么压缩”（需求压缩/产出压缩由各自模块负责）
- 本模块不与 Orchestrator 集成，只提供可测试的确定性策略
"""

from __future__ import annotations

import json
import math
from collections.abc import Sequence
from enum import Enum
from typing import Any, Protocol

from langchain_core.messages import BaseMessage
from pydantic import BaseModel, Field, field_validator

from src.shared.config.settings import settings


class CompressionScope(str, Enum):
    requirement = "requirement"
    artifacts = "artifacts"
    both = "both"


class CompressionReason(str, Enum):
    manual = "manual"
    budget_soft = "budget_soft"
    budget_hard = "budget_hard"


class ContextBudget(BaseModel):
    """
    上下文预算（token 级）

    说明：
    - model_context_tokens：模型最大上下文窗口（输入+输出）
    - reserved_output_tokens：预留给模型输出的 token 数（避免输入把窗口吃满导致无法输出）
    - soft_limit_ratio：接近上限时提前压缩（避免频繁触顶）
    - hard_limit_ratio：必须压缩（否则非常容易触发 context_length_exceeded）
    """

    model_context_tokens: int = Field(..., ge=256, description="模型最大上下文窗口（token）")
    reserved_output_tokens: int = Field(default=2048, ge=0, description="预留输出 token")
    safety_margin_tokens: int = Field(default=256, ge=0, description="安全余量（token）")

    soft_limit_ratio: float = Field(default=0.85, ge=0.1, le=0.99)
    hard_limit_ratio: float = Field(default=0.95, ge=0.1, le=0.999)

    @field_validator("hard_limit_ratio")
    @classmethod
    def _hard_ge_soft(cls, v: float, info):  # type: ignore[no-untyped-def]
        data = info.data or {}
        soft = float(data.get("soft_limit_ratio") or 0.0)
        if v < soft:
            raise ValueError("hard_limit_ratio 必须 >= soft_limit_ratio")
        return v


class BudgetUsage(BaseModel):
    estimated_input_tokens: int
    estimated_total_tokens: int
    model_context_tokens: int
    reserved_output_tokens: int
    safety_margin_tokens: int

    soft_limit_tokens: int
    hard_limit_tokens: int

    remaining_tokens: int
    utilization_ratio: float


class CompressionDecision(BaseModel):
    should_compress: bool
    scope: CompressionScope
    reason: CompressionReason
    usage: BudgetUsage


class TokenEstimator(Protocol):
    def estimate_messages_tokens(self, messages: Sequence[BaseMessage]) -> int: ...


def _content_to_text(content: Any) -> str:
    if content is None:
        return ""
    if isinstance(content, str):
        return content
    try:
        return json.dumps(content, ensure_ascii=False, default=str)
    except Exception:
        return str(content)


def _estimate_heuristic(text: str) -> int:
    """
    粗粒度 token 估算（跨多模型/多供应商的保守兜底）。

    原则：
    - 只用于“是否需要触发压缩”的阈值判断，不作为计费/精确统计
    - 宁可高估（提前压缩），不要低估（触发 context_length_exceeded）
    """

    t = (text or "").strip()
    if not t:
        return 0

    total = len(t)
    ascii_count = sum(1 for ch in t if ord(ch) < 128)
    ascii_ratio = ascii_count / max(1, total)

    # 经验上：纯英文更接近 1 token ~= 4 chars；中文更接近 1 token ~= 1~2 chars。
    if ascii_ratio >= 0.95:
        return int(math.ceil(total / 4))
    if ascii_ratio >= 0.6:
        return int(math.ceil(total / 3))
    return int(math.ceil(total / 2))


class HeuristicTokenEstimator:
    """
    消息 token 估算器（启发式）

    说明：
    - 额外叠加 message_overhead_tokens，模拟 role/格式化开销
    - 默认开销取 6，保守一些，避免低估
    """

    def __init__(self, *, message_overhead_tokens: int = 6):
        self.message_overhead_tokens = max(0, int(message_overhead_tokens))

    def estimate_messages_tokens(self, messages: Sequence[BaseMessage]) -> int:
        total = 0
        for msg in list(messages or []):
            text = _content_to_text(getattr(msg, "content", ""))
            total += _estimate_heuristic(text) + self.message_overhead_tokens
        return int(total)


def get_default_budget() -> ContextBudget:
    """
    获取默认上下文预算配置（Dynaconf）。

    说明：
    - 这里的 token 数是“预算控制参数”，用于决定何时触发压缩，不要求精确。
    - 默认值偏保守，避免低估导致 context_length_exceeded。
    """

    try:
        model_context_tokens = int(settings.get("etl_llm_context_tokens", 32768))
    except Exception:
        model_context_tokens = 32768

    try:
        reserved_output_tokens = int(settings.get("etl_llm_reserved_output_tokens", 2048))
    except Exception:
        reserved_output_tokens = 2048

    try:
        safety_margin_tokens = int(settings.get("etl_llm_safety_margin_tokens", 256))
    except Exception:
        safety_margin_tokens = 256

    try:
        soft_limit_ratio = float(settings.get("etl_llm_context_soft_limit_ratio", 0.85))
    except Exception:
        soft_limit_ratio = 0.85

    try:
        hard_limit_ratio = float(settings.get("etl_llm_context_hard_limit_ratio", 0.95))
    except Exception:
        hard_limit_ratio = 0.95

    return ContextBudget(
        model_context_tokens=model_context_tokens,
        reserved_output_tokens=reserved_output_tokens,
        safety_margin_tokens=safety_margin_tokens,
        soft_limit_ratio=soft_limit_ratio,
        hard_limit_ratio=hard_limit_ratio,
    )


def parse_compress(user_input: str | None) -> CompressionScope | None:
    """
    解析用户手动压缩命令。

    约束（强烈建议）：
    - 只识别显式命令前缀（例如 /compress），避免把真实需求文本误判为压缩指令。
    - 前端按钮触发时也应走该协议（由前端生成 user_input="/compress ..." 或传结构化字段）。
    """

    raw = (user_input or "").strip()
    if not raw:
        return None

    lowered = raw.lower()
    if not (lowered.startswith("/compress") or lowered.startswith("/compact")):
        return None

    parts = lowered.split()
    if len(parts) <= 1:
        return CompressionScope.both

    arg = parts[1].strip()
    if arg in {"req", "requirement", "todo"}:
        return CompressionScope.requirement
    if arg in {"artifact", "artifacts", "evidence"}:
        return CompressionScope.artifacts
    if arg in {"both", "all"}:
        return CompressionScope.both
    return CompressionScope.both


def compute_budget_usage(
    *,
    messages: Sequence[BaseMessage],
    budget: ContextBudget,
    estimator: TokenEstimator | None = None,
) -> BudgetUsage:
    used_estimator = estimator or HeuristicTokenEstimator()
    estimated_input = int(used_estimator.estimate_messages_tokens(messages))
    estimated_total = int(
        estimated_input + budget.reserved_output_tokens + budget.safety_margin_tokens
    )

    soft_limit_tokens = int(
        math.floor(budget.model_context_tokens * float(budget.soft_limit_ratio))
    )
    hard_limit_tokens = int(
        math.floor(budget.model_context_tokens * float(budget.hard_limit_ratio))
    )
    remaining = int(budget.model_context_tokens - estimated_total)
    ratio = float(estimated_total / max(1, budget.model_context_tokens))

    return BudgetUsage(
        estimated_input_tokens=estimated_input,
        estimated_total_tokens=estimated_total,
        model_context_tokens=budget.model_context_tokens,
        reserved_output_tokens=budget.reserved_output_tokens,
        safety_margin_tokens=budget.safety_margin_tokens,
        soft_limit_tokens=soft_limit_tokens,
        hard_limit_tokens=hard_limit_tokens,
        remaining_tokens=remaining,
        utilization_ratio=ratio,
    )


def decide_compression_trigger(
    *,
    user_input: str | None,
    messages: Sequence[BaseMessage],
    budget: ContextBudget,
    estimator: TokenEstimator | None = None,
) -> CompressionDecision | None:
    """
    决定是否触发压缩（确定性）。

    优先级：
    1) 用户显式命令
    2) 达到 hard 阈值（必须压缩）
    3) 达到 soft 阈值（建议压缩）
    """

    manual_scope = parse_compress(user_input)
    usage = compute_budget_usage(messages=messages, budget=budget, estimator=estimator)
    if manual_scope is not None:
        return CompressionDecision(
            should_compress=True,
            scope=manual_scope,
            reason=CompressionReason.manual,
            usage=usage,
        )

    if usage.estimated_total_tokens >= usage.hard_limit_tokens:
        return CompressionDecision(
            should_compress=True,
            scope=CompressionScope.both,
            reason=CompressionReason.budget_hard,
            usage=usage,
        )

    if usage.estimated_total_tokens >= usage.soft_limit_tokens:
        return CompressionDecision(
            should_compress=True,
            scope=CompressionScope.both,
            reason=CompressionReason.budget_soft,
            usage=usage,
        )

    return None
