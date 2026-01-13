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
from typing import Any, TypeVar

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

    # 所有策略都失败，生成清晰的错误信息
    error_summary = "\n".join(f"  - {e}" for e in errors)

    # 提取期望字段信息
    expected_fields = []
    for name, field in schema.model_fields.items():
        field_type = field.annotation.__name__ if hasattr(field.annotation, "__name__") else str(field.annotation)
        desc = field.description or ""
        expected_fields.append(f"    - {name}: {field_type}" + (f" ({desc})" if desc else ""))

    raise ValueError(
        f"结构化输出解析失败。\n\n"
        f"期望的 JSON 字段:\n" + "\n".join(expected_fields) + "\n\n"
        f"建议: 请确保 SYSTEM_PROMPT 中明确指定了 JSON 输出格式，字段名需与上述定义一致。\n\n"
        f"解析尝试:\n{error_summary}\n\n"
        f"原始文本: {text[:300]}"
    )


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


