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

    根据 provider 自动推断默认能力，也支持从 config_json 覆盖。
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
        config_json: dict[str, Any] | None = None,
    ) -> dict[str, bool]:
        """
        获取模型能力

        优先从 config_json 读取，否则使用 provider 默认值。
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

        if config_json and "capabilities" in config_json:
            overrides = config_json["capabilities"]
            return {**defaults, **overrides}

        return defaults

    @classmethod
    def supports_function_calling(
        cls,
        provider: str,
        config_json: dict[str, Any] | None = None,
    ) -> bool:
        """检查模型是否支持 function calling"""
        caps = cls.get_capabilities(provider, config_json)
        return caps.get("supports_function_calling", False)

    @classmethod
    def supports_structured_output(
        cls,
        provider: str,
        config_json: dict[str, Any] | None = None,
    ) -> bool:
        """检查模型是否支持 structured output"""
        caps = cls.get_capabilities(provider, config_json)
        return caps.get("supports_structured_output", False)


# ==================== JSON 修复工具 ====================


def repair_json(text: str) -> str:
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
    repaired = repair_json(text)
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
        repaired_extracted = repair_json(extracted)
        if repaired_extracted != extracted:
            try:
                return adapter.validate_json(repaired_extracted)
            except ValidationError as e:
                errors.append(f"提取+修复后解析失败: {e}")

    # 策略 4：处理数组包裹（有些模型会返回 [{}] 而不是 {}）
    # 优先使用修复后的文本，其次是提取的文本，最后是原始修复的文本
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

    # 所有策略都失败
    error_summary = "\n".join(f"  - {e}" for e in errors)
    raise ValueError(
        f"Structured output 解析失败，尝试了所有策略:\n{error_summary}\n原始文本: {text[:500]}"
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


# ==================== LLM 响应处理 ====================


class StructuredOutputResult:
    """
    结构化输出结果

    包含解析结果和元信息，便于调试和错误处理。
    """

    def __init__(
        self,
        *,
        parsed: BaseModel | None = None,
        raw_text: str | None = None,
        parsing_error: Exception | None = None,
        repair_applied: bool = False,
    ):
        self.parsed = parsed
        self.raw_text = raw_text
        self.parsing_error = parsing_error
        self.repair_applied = repair_applied

    @property
    def success(self) -> bool:
        return self.parsed is not None and self.parsing_error is None

    def get_or_raise(self) -> BaseModel:
        """获取解析结果，失败时抛出异常"""
        if self.parsing_error:
            raise self.parsing_error
        if self.parsed is None:
            raise ValueError("解析结果为空")
        return self.parsed


def _try_parse_raw(raw: Any, schema: type[T]) -> tuple[T | None, str | None]:
    """尝试从 raw 消息中解析结构化输出，返回 (parsed, raw_text)"""
    raw_text = _extract_text(raw)
    if not raw_text:
        return None, None
    try:
        return parse_structured_output(raw_text, schema), raw_text
    except ValueError:
        return None, raw_text


def _handle_dict(
    response: dict[str, Any],
    schema: type[T],
) -> tuple[T | None, str | None, Exception | None]:
    """处理 dict 格式响应，返回 (parsed, raw_text, error)"""
    # 已解析成功
    parsed = response.get("parsed")
    if isinstance(parsed, schema):
        return parsed, None, None

    # 有解析错误，尝试从 raw 恢复
    parsing_error = response.get("parsing_error")
    raw = response.get("raw")

    if parsing_error and raw:
        result, raw_text = _try_parse_raw(raw, schema)
        if result:
            return result, raw_text, None
        return None, raw_text, parsing_error

    if parsing_error:
        return None, None, parsing_error

    # parsed=None 且无错误，尝试从 raw 提取
    if raw:
        result, raw_text = _try_parse_raw(raw, schema)
        if result:
            return result, raw_text, None

    return None, None, None


def parse_llm_response(
    response: Any,
    schema: type[T],
    *,
    include_raw: bool = False,
) -> T | StructuredOutputResult:
    """
    解析 LLM 响应为结构化输出

    支持多种响应格式：
    - 直接是 Pydantic 对象
    - dict 格式（include_raw=True 时）
    - AIMessage 的 content
    - AIMessage 的 tool_calls
    """
    try:
        # 情况 1：已经是目标类型
        if isinstance(response, schema):
            return StructuredOutputResult(parsed=response) if include_raw else response

        # 情况 2：dict 格式
        if isinstance(response, dict):
            parsed, raw_text, error = _handle_dict(response, schema)
            if parsed:
                return _wrap_result(parsed, raw_text, include_raw)
            if error:
                if include_raw:
                    return StructuredOutputResult(raw_text=raw_text, parsing_error=error)
                raise error

        # 情况 3：AIMessage 类型
        parsed, raw_text = _try_parse_raw(response, schema)
        if parsed:
            return _wrap_result(parsed, raw_text, include_raw)

        raise ValueError(f"无法从响应中提取结构化输出: {type(response)}")

    except Exception as e:
        if include_raw:
            return StructuredOutputResult(parsing_error=e)
        raise


def _wrap_result(
    parsed: T,
    raw_text: str | None,
    include_raw: bool,
) -> T | StructuredOutputResult:
    """包装解析结果"""
    if include_raw:
        return StructuredOutputResult(parsed=parsed, raw_text=raw_text, repair_applied=True)
    return parsed


def _extract_text(message: Any) -> str | None:
    """从消息中提取文本内容"""
    # 尝试从 content 提取
    content = getattr(message, "content", None)
    if isinstance(content, str) and content.strip():
        return content

    # 尝试从 tool_calls 提取
    tool_calls = getattr(message, "tool_calls", None)
    if tool_calls and isinstance(tool_calls, list) and tool_calls:
        first_call = tool_calls[0]
        args = (
            first_call.get("args")
            if isinstance(first_call, dict)
            else getattr(first_call, "args", None)
        )
        if isinstance(args, dict):
            return json.dumps(args, ensure_ascii=False)
        if isinstance(args, str):
            return args

    # 尝试从 additional_kwargs.tool_calls 提取
    additional_kwargs = getattr(message, "additional_kwargs", {})
    if isinstance(additional_kwargs, dict):
        tool_calls = additional_kwargs.get("tool_calls", [])
        if tool_calls and isinstance(tool_calls, list):
            first_call = tool_calls[0]
            func = first_call.get("function", {}) if isinstance(first_call, dict) else {}
            args = func.get("arguments", "")
            if args:
                return args

    return None
