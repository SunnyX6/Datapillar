from __future__ import annotations

import logging
from pathlib import Path

import yaml
from datapillar_oneagentic.log.context import ContextFilter


def _load_logging_config() -> dict:
    root_dir = Path(__file__).resolve().parents[1]
    config_path = root_dir / "logging.yaml"
    return yaml.safe_load(config_path.read_text(encoding="utf-8"))


def test_logging_formatter_renders_context_fields() -> None:
    config = _load_logging_config()
    formatter_config = config["formatters"]["standard"]
    formatter = logging.Formatter(
        fmt=formatter_config["format"],
        datefmt=formatter_config["datefmt"],
    )
    record = logging.LogRecord(
        name="tests.logging",
        level=logging.INFO,
        pathname=__file__,
        lineno=1,
        msg="hello",
        args=(),
        exc_info=None,
    )
    ContextFilter().filter(record)
    output = formatter.format(record)
    assert output.endswith("hello")
    assert "event=" not in output
    assert "namespace=" not in output


def test_logging_formatter_includes_context_values() -> None:
    config = _load_logging_config()
    formatter_config = config["formatters"]["standard"]
    formatter = logging.Formatter(
        fmt=formatter_config["format"],
        datefmt=formatter_config["datefmt"],
    )
    record = logging.LogRecord(
        name="tests.logging",
        level=logging.INFO,
        pathname=__file__,
        lineno=1,
        msg="hello",
        args=(),
        exc_info=None,
    )
    record.event = "team.created"
    record.namespace = "etl_team"
    record.data = {"agents": 3}
    ContextFilter().filter(record)
    output = formatter.format(record)
    assert " | event=team.created" in output
    assert "namespace=etl_team" in output
    assert "data={\"agents\": 3}" in output
