"""
Structured output parser (multi-model compatible).

Progressive fallback strategy:
1. Strict JSON parsing
2. json_repair for malformed JSON
3. Handle special formats (thinking tags, array-wrapped payloads)
4. Pydantic validator tolerance

Supported capability flags:
- supports_function_calling: whether function calling is supported
- supports_structured_output: whether structured output is supported
"""

import json
import logging
import re
from typing import Annotated, Any, TypeVar, get_args, get_origin

import json_repair
from pydantic import BaseModel, TypeAdapter, ValidationError

from datapillar_oneagentic.exception.base import RecoveryAction
from datapillar_oneagentic.exception.llm.categories import LLMErrorCategory
from datapillar_oneagentic.exception.llm.errors import LLMError

logger = logging.getLogger(__name__)

T = TypeVar("T", bound=BaseModel)


# ==================== Model capability flags ====================


class ModelCapabilities:
    """
    Model capability flags.

    Defaults are inferred by provider, with optional overrides from config.
    """

    # Default capability map (inferred by provider).
    DEFAULT_CAPABILITIES: dict[str, dict[str, bool]] = {
        "openai": {
            "supports_function_calling": True,
            "supports_structured_output": True,
            "supports_tool_choice": True,
        },
        "claude": {
            "supports_function_calling": True,
            "supports_structured_output": True,
            "supports_tool_choice": True,
        },
        "anthropic": {
            "supports_function_calling": True,
            "supports_structured_output": True,
            "supports_tool_choice": True,
        },
        "glm": {
            "supports_function_calling": True,
            "supports_structured_output": True,
            "supports_tool_choice": False,  
        },
        "deepseek": {
            "supports_function_calling": True,
            "supports_structured_output": True,
            "supports_tool_choice": True,
        },
        "openrouter": {
            # OpenRouter depends on the upstream model, default to conservative values.
            "supports_function_calling": True,
            "supports_structured_output": True,
            "supports_tool_choice": False,
        },
        "ollama": {
            # Ollama depends on local models, default to conservative values.
            "supports_function_calling": False,
            "supports_structured_output": False,
            "supports_tool_choice": False,
        },
    }

    @classmethod
    def get_capabilities(
        cls,
        provider: str,
        config: dict[str, Any] | None = None,
    ) -> dict[str, bool]:
        """
        Resolve model capabilities.

        Prefer config overrides, otherwise fall back to provider defaults.
        """
        provider_lower = provider.lower()
        defaults = cls.DEFAULT_CAPABILITIES.get(
            provider_lower,
            {
                "supports_function_calling": False,
                "supports_structured_output": False,
                "supports_tool_choice": False,
            },
        )

        if config and "capabilities" in config:
            overrides = config["capabilities"]
            return {**defaults, **overrides}

        return defaults

    @classmethod
    def supports_function_calling(
        cls,
        provider: str,
        config: dict[str, Any] | None = None,
    ) -> bool:
        """Return whether function calling is supported."""
        caps = cls.get_capabilities(provider, config)
        return caps.get("supports_function_calling", False)

    @classmethod
    def supports_structured_output(
        cls,
        provider: str,
        config: dict[str, Any] | None = None,
    ) -> bool:
        """Return whether structured output is supported."""
        caps = cls.get_capabilities(provider, config)
        return caps.get("supports_structured_output", False)


# ==================== JSON repair tools ====================


def repair_json_text(text: str) -> str:
    """
    Repair malformed JSON.

    Uses json_repair for common issues:
    - Missing quotes
    - Trailing commas
    - Single quotes
    - Comments
    """
    if not text or not text.strip():
        return text

    try:
        # Try plain parsing first to avoid unnecessary repair.
        json.loads(text)
        return text
    except json.JSONDecodeError:
        pass

    try:
        # Repair via json_repair.
        repaired = json_repair.repair_json(text, return_objects=False)
        return repaired if isinstance(repaired, str) else text
    except Exception as e:
        logger.warning(f"json_repair failed: {e}")
        return text


def extract_json(text: str) -> str:
    """
    Extract JSON from text.

    Common scenarios handled:
    - Markdown code blocks
    - Extra text before/after JSON
    - Thinking tags (e.g., <think>...</think>)
    """
    if not text:
        return text

    # Strip thinking tags (e.g., Deepseek-R1 style).
    text = re.sub(r"<think>.*?</think>", "", text, flags=re.DOTALL)
    text = re.sub(r"<thinking>.*?</thinking>", "", text, flags=re.DOTALL)

    # Extract JSON from Markdown code blocks.
    json_block_match = re.search(r"```(?:json)?\s*([\s\S]*?)\s*```", text)
    if json_block_match:
        return json_block_match.group(1).strip()

    # Find JSON object boundaries.
    # First "{" and last "}".
    first_brace = text.find("{")
    last_brace = text.rfind("}")
    if first_brace != -1 and last_brace != -1 and last_brace > first_brace:
        return text[first_brace : last_brace + 1]

    # First "[" and last "]".
    first_bracket = text.find("[")
    last_bracket = text.rfind("]")
    if first_bracket != -1 and last_bracket != -1 and last_bracket > first_bracket:
        return text[first_bracket : last_bracket + 1]

    return text.strip()


# ==================== Progressive parser ====================

def _maybe_parse_jsonish(text: str) -> object:
    stripped = text.strip()
    if not stripped:
        return text
    lowered = stripped.lower()
    if lowered in {"null", "true", "false"}:
        try:
            return json.loads(lowered)
        except json.JSONDecodeError:
            return text
    if (stripped.startswith("{") and stripped.endswith("}")) or (
        stripped.startswith("[") and stripped.endswith("]")
    ):
        try:
            return json.loads(stripped)
        except json.JSONDecodeError:
            return text
    return text

def normalize_jsonish(value: object) -> object:
    if isinstance(value, dict):
        return {key: normalize_jsonish(val) for key, val in value.items()}
    if isinstance(value, list):
        return [normalize_jsonish(item) for item in value]
    if isinstance(value, str):
        return _maybe_parse_jsonish(value)
    return value


def _build_expected_fields(schema: type[BaseModel]) -> str:
    fields = []
    for name, field in schema.model_fields.items():
        field_type = field.annotation.__name__ if hasattr(field.annotation, "__name__") else str(field.annotation)
        desc = field.description or ""
        fields.append(f"- {name}: {field_type}" + (f" ({desc})" if desc else ""))
    return "\n".join(fields)


def _build_error_message(schema: type[BaseModel], *, detail: str | None = None) -> str:
    lines = [
        "Structured output parsing failed.",
        "Possible causes: schema fields do not match the prompt output, or the model did not emit strict JSON.",
    ]
    if detail:
        lines.append(f"Error detail: {detail}")
    lines.append("Expected JSON fields:")
    lines.append(_build_expected_fields(schema))
    lines.append(
        "Suggestion: ensure the SYSTEM_PROMPT explicitly requires JSON output and field names match the schema."
    )
    return "\n".join(lines)


def _extract_raw_text(raw: Any) -> str | None:
    if raw is None:
        return None
    if isinstance(raw, str):
        return raw
    content = getattr(raw, "content", None)
    if isinstance(content, str):
        return content
    return None


def _log_structured_failure(raw: Any, parsing_error: Any) -> None:
    logger.error(
        "LLM structured output parsing failed raw=%r parsing_error=%r",
        raw,
        parsing_error,
    )


def parse_structured_output(
    text: Any,
    schema: type[T],
    *,
    strict: bool = False,
) -> T:
    """
    Progressively parse structured output.

    Strategy (strict to permissive):
    1. Parse with Pydantic TypeAdapter
    2. Repair malformed JSON via json_repair
    3. Extract JSON from text and parse
    4. Unwrap list payloads (take first dict)
    5. Markdown fallback (match by field name)
    6. Single-field text fallback (wrap Markdown/plain text)

    Args:
        text: LLM response (text, structured payload, or parsed model)
        schema: Pydantic model class
        strict: whether to fail fast without fallback

    Returns:
        Parsed Pydantic model instance.

    Raises:
        LLMError: raised on parsing failures.
    """
    if isinstance(text, schema):
        return text

    if isinstance(text, dict):
        parsed = text.get("parsed")
        parsing_error = text.get("parsing_error")
        raw = text.get("raw")
        if {"raw", "parsed", "parsing_error"} <= text.keys():
            raw_text = _extract_raw_text(raw)
            if isinstance(parsed, schema):
                return parsed
            if isinstance(parsed, dict):
                try:
                    return schema.model_validate(normalize_jsonish(parsed))
                except ValidationError as e:
                    if not strict and raw_text:
                        try:
                            return parse_structured_output(raw_text, schema, strict=False)
                        except LLMError as fallback_error:
                            detail = f"Schema validation failed: {e}; fallback parsing failed: {fallback_error}"
                            _log_structured_failure(raw, parsing_error)
                            raise LLMError(
                                _build_error_message(schema, detail=detail),
                                category=LLMErrorCategory.STRUCTURED_OUTPUT,
                                action=RecoveryAction.FAIL_FAST,
                                original=e,
                                raw=raw,
                                parsing_error=parsing_error,
                            ) from fallback_error
                    _log_structured_failure(raw, parsing_error)
                    raise LLMError(
                        _build_error_message(schema, detail=f"Schema validation failed: {e}"),
                        category=LLMErrorCategory.STRUCTURED_OUTPUT,
                        action=RecoveryAction.FAIL_FAST,
                        original=e,
                        raw=raw,
                        parsing_error=parsing_error,
                    ) from e
            if parsing_error is not None or parsed is None:
                detail = "Parser did not return a valid parsed value"
                if parsing_error:
                    detail = f"Parser error: {parsing_error}"
                if not strict and raw_text:
                    try:
                        return parse_structured_output(raw_text, schema, strict=False)
                    except LLMError as fallback_error:
                        detail = f"{detail}; fallback parsing failed: {fallback_error}"
                _log_structured_failure(raw, parsing_error)
                raise LLMError(
                    _build_error_message(schema, detail=detail),
                    category=LLMErrorCategory.STRUCTURED_OUTPUT,
                    action=RecoveryAction.FAIL_FAST,
                    original=parsing_error if isinstance(parsing_error, Exception) else None,
                    raw=raw,
                    parsing_error=parsing_error,
                )
            _log_structured_failure(raw, parsing_error)
            raise LLMError(
                _build_error_message(schema, detail="Parser did not return a valid parsed value"),
                category=LLMErrorCategory.STRUCTURED_OUTPUT,
                action=RecoveryAction.FAIL_FAST,
                raw=raw,
                parsing_error=parsing_error,
            )

        try:
            return schema.model_validate(normalize_jsonish(text))
        except ValidationError as e:
            raise LLMError(
                _build_error_message(schema, detail=f"Schema validation failed: {e}"),
                category=LLMErrorCategory.STRUCTURED_OUTPUT,
                action=RecoveryAction.FAIL_FAST,
                original=e,
                raw=text,
            ) from e

    if not isinstance(text, str):
        raise LLMError(
            _build_error_message(schema, detail=f"Unsupported output type: {type(text)}"),
            category=LLMErrorCategory.STRUCTURED_OUTPUT,
            action=RecoveryAction.FAIL_FAST,
            raw=text,
        )

    if not text or not text.strip():
        raise LLMError(
            _build_error_message(schema, detail="Output text is empty"),
            category=LLMErrorCategory.STRUCTURED_OUTPUT,
            action=RecoveryAction.FAIL_FAST,
            raw=text,
        )

    errors: list[str] = []
    adapter = TypeAdapter(schema)

    # Strategy 1: direct JSON parsing.
    try:
        return adapter.validate_json(text)
    except ValidationError as e:
        errors.append(f"Direct parsing failed: {e}")
        if strict:
            raise LLMError(
                _build_error_message(schema, detail=f"Schema validation failed: {e}"),
                category=LLMErrorCategory.STRUCTURED_OUTPUT,
                action=RecoveryAction.FAIL_FAST,
                original=e,
                raw=text,
            ) from e

    # Strategy 2: repair malformed JSON via json_repair.
    repaired = repair_json_text(text)
    if repaired != text:
        try:
            return adapter.validate_json(repaired)
        except ValidationError as e:
            errors.append(f"Parsing failed after repair: {e}")

    # Strategy 3: extract JSON and parse.
    extracted = extract_json(text)
    repaired_extracted = None
    if extracted != text:
        try:
            return adapter.validate_json(extracted)
        except ValidationError as e:
            errors.append(f"Parsing failed after extraction: {e}")

        # Repair the extracted JSON once more.
        repaired_extracted = repair_json_text(extracted)
        if repaired_extracted != extracted:
            try:
                return adapter.validate_json(repaired_extracted)
            except ValidationError as e:
                errors.append(f"Parsing failed after extraction + repair: {e}")

    # Strategy 4: unwrap list payloads (some models return [{}] instead of {}).
    final_text = repaired_extracted or extracted or repaired
    try:
        parsed = json.loads(final_text)
        if isinstance(parsed, dict):
            return schema.model_validate(normalize_jsonish(parsed))
        if isinstance(parsed, list) and parsed:
            # Take the first dict element.
            first_dict = next((item for item in parsed if isinstance(item, dict)), None)
            if first_dict:
                return schema.model_validate(normalize_jsonish(first_dict))
    except (json.JSONDecodeError, ValidationError) as e:
        errors.append(f"List unwrap failed: {e}")

    # Strategy 5: Markdown fallback (match by field name).
    markdown_parsed, markdown_error = _parse_markdown_fields(text, schema)
    if markdown_parsed is not None:
        logger.warning(
            "Structured output fallback to Markdown parsing; JSON format may be inconsistent."
        )
        return markdown_parsed
    if markdown_error:
        errors.append(markdown_error)

    # Strategy 6: single-field text fallback (only for single-string schemas).
    coerced, coercion_error = _coerce_text_field(text, schema)
    if coerced is not None:
        logger.warning(
            "Structured output fallback to single-field text; JSON format may be inconsistent."
        )
        return coerced
    if coercion_error:
        errors.append(coercion_error)

    error_summary = "; ".join(errors)
    raise LLMError(
        _build_error_message(schema, detail=f"All parsing attempts failed: {error_summary}"),
        category=LLMErrorCategory.STRUCTURED_OUTPUT,
        action=RecoveryAction.FAIL_FAST,
        raw=text,
    )


def _coerce_text_field(text: str, schema: type[T]) -> tuple[T | None, str | None]:
    fields = list(schema.model_fields.items())
    if len(fields) != 1:
        return None, None
    field_name, field = fields[0]
    if not _is_string_annotation(field.annotation):
        return None, None
    try:
        return schema.model_validate({field_name: text.strip()}), None
    except ValidationError as exc:
        return None, f"Text fallback parsing failed: {exc}"


def _parse_markdown_fields(text: str, schema: type[T]) -> tuple[T | None, str | None]:
    candidates = _extract_markdown_candidates(text)
    if not candidates:
        return None, None

    field_map = {_normalize_field_name(name): name for name in schema.model_fields.keys()}
    result: dict[str, Any] = {}
    matched_fields: set[str] = set()

    for key, value in candidates:
        normalized_key = _normalize_field_name(key)
        field_name = field_map.get(normalized_key)
        if not field_name:
            continue
        field = schema.model_fields[field_name]
        if _is_string_annotation(field.annotation):
            parsed_value = value.strip()
        else:
            try:
                parsed_value = normalize_jsonish(json.loads(value))
            except json.JSONDecodeError as exc:
                return None, f"Markdown fallback failed: field {field_name} is not valid JSON: {exc}"
        result[field_name] = parsed_value
        matched_fields.add(field_name)

    if not matched_fields:
        return None, None

    missing_fields = [
        name
        for name, field in schema.model_fields.items()
        if field.is_required() and name not in result
    ]
    if missing_fields:
        return None, f"Markdown fallback failed: missing required fields: {', '.join(missing_fields)}"

    try:
        return schema.model_validate(result), None
    except ValidationError as exc:
        return None, f"Markdown fallback failed: {exc}"


def _extract_markdown_candidates(text: str) -> list[tuple[str, str]]:
    lines = text.splitlines()
    candidates: list[tuple[str, str]] = []
    in_code_block = False
    i = 0

    while i < len(lines):
        line = lines[i]
        stripped = line.strip()

        if stripped.startswith("```"):
            in_code_block = not in_code_block
            i += 1
            continue

        if not in_code_block and _is_heading_line(stripped):
            key = stripped[3:].strip()
            body_lines: list[str] = []
            j = i + 1
            block_in_code = False
            while j < len(lines):
                next_line = lines[j]
                next_stripped = next_line.strip()
                if next_stripped.startswith("```"):
                    block_in_code = not block_in_code
                    j += 1
                    continue
                if not block_in_code and _is_heading_line(next_stripped):
                    break
                if not block_in_code:
                    body_lines.append(next_line)
                j += 1
            value = "\n".join(body_lines).strip()
            if key and value:
                candidates.append((key, value))
            i = j
            continue

        if not in_code_block:
            kv = _extract_key_value(stripped)
            if kv:
                candidates.append(kv)

        i += 1

    return candidates


def _extract_key_value(line: str) -> tuple[str, str] | None:
    if not line:
        return None
    if line.startswith("#"):
        return None
    for prefix in ("- ", "* ", "• "):
        if line.startswith(prefix):
            line = line[len(prefix) :].strip()
            break
    if ":" not in line:
        return None
    key, value = line.split(":", 1)
    key = key.strip()
    value = value.strip()
    if not key or not value:
        return None
    return key, value


def _is_heading_line(line: str) -> bool:
    return line.startswith("## ") and len(line) > 3


def _normalize_field_name(name: str) -> str:
    name = name.strip()
    name = _strip_markdown_wrappers(name)
    return name.strip().lower()


def _strip_markdown_wrappers(text: str) -> str:
    for wrapper in ("**", "__", "`", "*"):
        if text.startswith(wrapper) and text.endswith(wrapper) and len(text) > len(wrapper) * 2:
            text = text[len(wrapper) : -len(wrapper)].strip()
    return text


def _is_string_annotation(annotation: Any) -> bool:
    origin = get_origin(annotation)
    if origin is Annotated:
        args = get_args(annotation)
        if not args:
            return False
        return _is_string_annotation(args[0])
    if annotation is str:
        return True
    args = get_args(annotation)
    if not args:
        return False
    return any(_is_string_annotation(arg) for arg in args if arg is not type(None))


def parse_args(
    args: str | dict[str, Any],
    schema: type[T],
) -> T:
    """
    Parse tool call arguments.

    Scenarios:
    - args is already a dict
    - args is a JSON string
    - args is malformed JSON

    Args:
        args: tool call arguments (dict or str)
        schema: Pydantic model class

    Returns:
        Parsed Pydantic model instance.
    """
    if isinstance(args, dict):
        return schema.model_validate(normalize_jsonish(args))

    if isinstance(args, str):
        return parse_structured_output(args, schema)

    raise LLMError(
        _build_error_message(schema, detail=f"Unsupported tool arguments type: {type(args)}"),
        category=LLMErrorCategory.INVALID_INPUT,
        action=RecoveryAction.FAIL_FAST,
        raw=args,
    )


def build_output_instructions(schema: type[BaseModel]) -> str:
    """
    Build a unified JSON-only output instruction block.

    Goals:
    - Enforce strict JSON output
    - Forbid Markdown/code blocks/explanations
    """
    return (
        "## Important\n"
        "Output strict JSON (single object). Do not output Markdown, code blocks, or explanatory text.\n"
        "Tool calls are allowed, but the final output must be JSON.\n"
        "Strictly follow the delivery schema.\n"
        "## Forbidden\n"
        "Do not output non-JSON content or add undefined fields."
    )
