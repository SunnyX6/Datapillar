"""Evaluation set loader."""

from __future__ import annotations

from pathlib import Path

from datapillar_oneagentic.knowledge.evaluation.schema import EvalSet


def load_eval_set(path: str | Path) -> EvalSet:
    """Load an evaluation set JSON file."""
    payload = Path(path).read_text(encoding="utf-8")
    return EvalSet.model_validate_json(payload)
