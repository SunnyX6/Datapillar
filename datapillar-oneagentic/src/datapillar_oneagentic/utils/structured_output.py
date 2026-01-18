"""
结构化输出解析器（多模型兼容）

渐进式降级策略：
1. 严格 JSON 解析
2. json_repair 修复畸形 JSON
3. 处理特殊格式（如思维链标签、数组包裹）
4. Pydantic validator 容错

支持的能力声明：
- supports_function_calling: 是否支持 function calling
- supports_structured_output: 是否支持 structured output
"""

import json
import logging
import re
from typing import Annotated, Any, TypeVar, get_args, get_origin

import json_repair
from pydantic import BaseModel, TypeAdapter, ValidationError

logger = logging.getLogger(__name__)

T = TypeVar("T", bound=BaseModel)


# ==================== 模型能力声明 ====================


class ModelCapabilities:
    """
    模型能力声明

    根据 provider 自动推断默认能力，也支持从 config 覆盖。
    """

    # 默认能力配置（根据 provider 推断）
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
            "supports_tool_choice": True,  # 通过 ChatOpenAI 兼容层
        },
        "deepseek": {
            "supports_function_calling": True,
            "supports_structured_output": True,
            "supports_tool_choice": True,
        },
        "openrouter": {
            # OpenRouter 取决于后端模型，默认保守估计
            "supports_function_calling": True,
            "supports_structured_output": True,
            "supports_tool_choice": False,
        },
        "ollama": {
            # Ollama 取决于本地模型，默认保守估计
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
        获取模型能力

        优先从 config 读取，否则使用 provider 默认值。
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
        """检查模型是否支持 function calling"""
        caps = cls.get_capabilities(provider, config)
        return caps.get("supports_function_calling", False)

    @classmethod
    def supports_structured_output(
        cls,
        provider: str,
        config: dict[str, Any] | None = None,
    ) -> bool:
        """检查模型是否支持 structured output"""
        caps = cls.get_capabilities(provider, config)
        return caps.get("supports_structured_output", False)


# ==================== JSON 修复工具 ====================


def repair_json_text(text: str) -> str:
    """
    修复畸形 JSON

    使用 json_repair 库处理常见问题：
    - 缺失引号
    - 尾随逗号
    - 单引号
    - 注释
    """
    if not text or not text.strip():
        return text

    try:
        # 先尝试直接解析，如果成功就不需要修复
        json.loads(text)
        return text
    except json.JSONDecodeError:
        pass

    try:
        # 使用 json_repair 修复
        repaired = json_repair.repair_json(text, return_objects=False)
        return repaired if isinstance(repaired, str) else text
    except Exception as e:
        logger.warning(f"json_repair 修复失败: {e}")
        return text


def extract_json(text: str) -> str:
    """
    从文本中提取 JSON

    处理常见场景：
    - Markdown 代码块包裹
    - 前后有额外文字
    - 思维链标签（如 <think>...</think>）
    """
    if not text:
        return text

    # 移除思维链标签（Deepseek-R1 等模型）
    text = re.sub(r"<think>.*?</think>", "", text, flags=re.DOTALL)
    text = re.sub(r"<thinking>.*?</thinking>", "", text, flags=re.DOTALL)

    # 尝试提取 Markdown 代码块中的 JSON
    json_block_match = re.search(r"```(?:json)?\s*([\s\S]*?)\s*```", text)
    if json_block_match:
        return json_block_match.group(1).strip()

    # 尝试找到 JSON 对象边界
    # 找第一个 { 和最后一个 }
    first_brace = text.find("{")
    last_brace = text.rfind("}")
    if first_brace != -1 and last_brace != -1 and last_brace > first_brace:
        return text[first_brace : last_brace + 1]

    # 找第一个 [ 和最后一个 ]
    first_bracket = text.find("[")
    last_bracket = text.rfind("]")
    if first_bracket != -1 and last_bracket != -1 and last_bracket > first_bracket:
        return text[first_bracket : last_bracket + 1]

    return text.strip()


# ==================== 渐进式解析器 ====================


def parse_structured_output(
    text: str,
    schema: type[T],
    *,
    strict: bool = False,
) -> T:
    """
    渐进式解析 structured output

    策略（从严格到宽松）：
    1. 直接用 Pydantic TypeAdapter 解析
    2. 用 json_repair 修复后解析
    3. 从文本中提取 JSON 后解析
    4. 处理数组包裹（取第一个 dict 元素）
    5. Markdown 兜底解析（按字段名匹配）
    6. 单字段文本兜底（将 Markdown/纯文本封装为结构化输出）

    Args:
        text: LLM 返回的原始文本
        schema: Pydantic 模型类
        strict: 是否使用严格模式（失败直接抛异常）

    Returns:
        解析后的 Pydantic 模型实例

    Raises:
        ValueError: 所有策略都失败时
    """
    if not text or not text.strip():
        raise ValueError("输入文本为空")

    errors: list[str] = []
    adapter = TypeAdapter(schema)

    # 策略 1：直接解析
    try:
        return adapter.validate_json(text)
    except ValidationError as e:
        errors.append(f"直接解析失败: {e}")
        if strict:
            raise ValueError(f"Structured output 解析失败: {e}") from e

    # 策略 2：json_repair 修复
    repaired = repair_json_text(text)
    if repaired != text:
        try:
            return adapter.validate_json(repaired)
        except ValidationError as e:
            errors.append(f"修复后解析失败: {e}")

    # 策略 3：提取 JSON 后解析
    extracted = extract_json(text)
    repaired_extracted = None
    if extracted != text:
        try:
            return adapter.validate_json(extracted)
        except ValidationError as e:
            errors.append(f"提取后解析失败: {e}")

        # 对提取的内容再修复一次
        repaired_extracted = repair_json_text(extracted)
        if repaired_extracted != extracted:
            try:
                return adapter.validate_json(repaired_extracted)
            except ValidationError as e:
                errors.append(f"提取+修复后解析失败: {e}")

    # 策略 4：处理数组包裹（有些模型会返回 [{}] 而不是 {}）
    final_text = repaired_extracted or extracted or repaired
    try:
        parsed = json.loads(final_text)
        if isinstance(parsed, list) and parsed:
            # 取第一个 dict 元素
            first_dict = next((item for item in parsed if isinstance(item, dict)), None)
            if first_dict:
                return schema.model_validate(first_dict)
    except (json.JSONDecodeError, ValidationError) as e:
        errors.append(f"数组解包失败: {e}")

    # 策略 5：Markdown 兜底解析（按字段名匹配）
    markdown_parsed, markdown_error = _try_parse_markdown_fields(text, schema)
    if markdown_parsed is not None:
        logger.warning("Structured output 使用 Markdown 兜底解析，可能存在 JSON 格式不一致。")
        return markdown_parsed
    if markdown_error:
        errors.append(markdown_error)

    # 策略 6：单字段文本兜底（仅对单字符串字段）
    coerced, coercion_error = _try_coerce_single_text_field(text, schema)
    if coerced is not None:
        logger.warning("Structured output 使用单字段文本兜底解析，可能存在 JSON 格式不一致。")
        return coerced
    if coercion_error:
        errors.append(coercion_error)

    # 所有策略都失败，生成清晰的错误信息
    error_summary = "\n".join(f"  - {e}" for e in errors)

    # 提取期望字段信息
    expected_fields = []
    for name, field in schema.model_fields.items():
        field_type = field.annotation.__name__ if hasattr(field.annotation, "__name__") else str(field.annotation)
        desc = field.description or ""
        expected_fields.append(f"    - {name}: {field_type}" + (f" ({desc})" if desc else ""))

    raise ValueError(
        "结构化输出解析失败。\n\n"
        "期望的 JSON 字段:\n" + "\n".join(expected_fields) + "\n\n"
        f"建议: 请确保 SYSTEM_PROMPT 中明确指定了 JSON 输出格式，字段名需与上述定义一致。\n\n"
        f"解析尝试:\n{error_summary}\n\n"
        f"原始文本: {text[:300]}"
    )


def _try_coerce_single_text_field(text: str, schema: type[T]) -> tuple[T | None, str | None]:
    fields = list(schema.model_fields.items())
    if len(fields) != 1:
        return None, None
    field_name, field = fields[0]
    if not _is_string_annotation(field.annotation):
        return None, None
    try:
        return schema.model_validate({field_name: text.strip()}), None
    except ValidationError as exc:
        return None, f"文本兜底解析失败: {exc}"


def _try_parse_markdown_fields(text: str, schema: type[T]) -> tuple[T | None, str | None]:
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
                parsed_value = json.loads(value)
            except json.JSONDecodeError as exc:
                return None, f"Markdown 兜底解析失败: 字段 {field_name} 不是合法 JSON 值: {exc}"
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
        return None, f"Markdown 兜底解析失败: 缺少必填字段: {', '.join(missing_fields)}"

    try:
        return schema.model_validate(result), None
    except ValidationError as exc:
        return None, f"Markdown 兜底解析失败: {exc}"


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
            kv = _extract_key_value_line(stripped)
            if kv:
                candidates.append(kv)

        i += 1

    return candidates


def _extract_key_value_line(line: str) -> tuple[str, str] | None:
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
    解析 tool call 的 arguments

    处理场景：
    - args 已经是 dict（正常情况）
    - args 是 JSON 字符串（需要解析）
    - args 是畸形 JSON（需要修复）

    Args:
        args: tool call 的 arguments（可能是 dict 或 str）
        schema: Pydantic 模型类

    Returns:
        解析后的 Pydantic 模型实例
    """
    if isinstance(args, dict):
        return schema.model_validate(args)

    if isinstance(args, str):
        return parse_structured_output(args, schema)

    raise ValueError(f"无法解析 tool call arguments: {type(args)}")


def build_output_instructions(schema: type[BaseModel]) -> str:
    """
    构建统一的结构化输出约束提示词（JSON-only）

    目标：
    - 强制输出 JSON
    - 禁止 Markdown/代码块/额外说明
    """
    return (
        "## 重要\n"
        "必须输出严格 JSON（单个对象），不得输出 Markdown、代码块或解释性文字。\n"
        "允许调用工具，但最终输出必须是 JSON。\n"
        "必须严格遵循交付物结构定义。\n"
        "## 禁止\n"
        "禁止输出非 JSON 内容，禁止添加未定义字段。"
    )
