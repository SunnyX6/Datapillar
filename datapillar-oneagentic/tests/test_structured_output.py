from __future__ import annotations

import json

import pytest
from pydantic import BaseModel

from datapillar_oneagentic.exception import LLMError, LLMErrorCategory
from datapillar_oneagentic.utils.structured_output import (
    ModelCapabilities,
    extract_json,
    parse_args,
    parse_structured_output,
    repair_json_text,
)


class _Payload(BaseModel):
    foo: str
    count: int = 1


class _MarkdownPayload(BaseModel):
    summary: str
    count: int
    note: str | None = None


def test_extract_json() -> None:
    text = (
        "<think>ignore</think>\n"
        "```json\n"
        "{\"foo\": \"bar\", \"count\": 2}\n"
        "```"
    )
    assert extract_json(text) == '{"foo": "bar", "count": 2}'


def test_repair_json() -> None:
    repaired = repair_json_text('{"foo": "bar",}')
    assert json.loads(repaired)["foo"] == "bar"


def test_parse_structured() -> None:
    model = parse_structured_output('{"foo": "bar", "count": 2}', _Payload)
    assert model.foo == "bar"
    assert model.count == 2


def test_parse_structured2() -> None:
    text = (
        "result:\n"
        "```json\n"
        "{\"foo\": \"bar\", \"count\": 3}\n"
        "```"
    )
    model = parse_structured_output(text, _Payload)
    assert model.count == 3


def test_parse_structured3() -> None:
    model = parse_structured_output('[{"foo": "bar", "count": 4}]', _Payload)
    assert model.count == 4


def test_parse_structured4() -> None:
    text = "- summary: ok\n- count: 2\n- note: extra"
    model = parse_structured_output(text, _MarkdownPayload)
    assert model.summary == "ok"
    assert model.count == 2
    assert model.note == "extra"


def test_parse_structured5() -> None:
    text = "## summary\nHello\n\n## count\n2"
    model = parse_structured_output(text, _MarkdownPayload)
    assert model.summary == "Hello"
    assert model.count == 2


def test_parse_structured6() -> None:
    result = {
        "raw": "raw",
        "parsed": {"foo": "bar", "count": 2},
        "parsing_error": None,
    }
    model = parse_structured_output(result, _Payload, strict=True)
    assert model.foo == "bar"
    assert model.count == 2


def test_parse_structured7() -> None:
    result = {
        "raw": "raw",
        "parsed": None,
        "parsing_error": "bad",
    }
    with pytest.raises(LLMError) as exc:
        parse_structured_output(result, _Payload, strict=True)
    error = exc.value
    assert error.category == LLMErrorCategory.STRUCTURED_OUTPUT
    assert error.raw == "raw"
    assert error.parsing_error == "bad"


def test_parse_structured8() -> None:
    with pytest.raises(LLMError):
        parse_structured_output("- summary: ok", _MarkdownPayload)


def test_parse_structured9() -> None:
    with pytest.raises(LLMError):
        parse_structured_output("   ", _Payload)


def test_parse_args() -> None:
    model_from_dict = parse_args({"foo": "bar", "count": 5}, _Payload)
    model_from_text = parse_args('{"foo": "bar", "count": 6}', _Payload)
    assert model_from_dict.count == 5
    assert model_from_text.count == 6


def test_model_capabilities() -> None:
    defaults = ModelCapabilities.get_capabilities("openai")
    assert defaults["supports_function_calling"] is True

    override = ModelCapabilities.get_capabilities(
        "openai",
        {"capabilities": {"supports_function_calling": False}},
    )
    assert override["supports_function_calling"] is False

    unknown = ModelCapabilities.get_capabilities("unknown")
    assert unknown["supports_function_calling"] is False
