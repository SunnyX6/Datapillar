"""
LLM Usage 追踪单元测试

测试模块：datapillar_oneagentic.providers.llm.usage_tracker
"""

from decimal import Decimal

import pytest
from langchain_core.messages import AIMessage, HumanMessage, SystemMessage

from datapillar_oneagentic.providers.llm.usage_tracker import (
    ModelPricingUsd,
    NormalizedTokenUsage,
    UsageCostUsd,
    estimate_cost_usd,
    estimate_usage,
    extract_usage,
    parse_pricing,
)


class TestNormalizedTokenUsage:
    """NormalizedTokenUsage 数据类测试"""

    def test_create_basic_usage(self):
        """测试创建基本 usage"""
        usage = NormalizedTokenUsage(
            prompt_tokens=100,
            completion_tokens=50,
            total_tokens=150,
            estimated=False,
        )

        assert usage.prompt_tokens == 100
        assert usage.completion_tokens == 50
        assert usage.total_tokens == 150
        assert usage.estimated is False

    def test_create_usage_with_cache(self):
        """测试创建带缓存的 usage"""
        usage = NormalizedTokenUsage(
            prompt_tokens=100,
            completion_tokens=50,
            total_tokens=150,
            estimated=False,
            cached_tokens=30,
        )

        assert usage.cached_tokens == 30

    def test_create_usage_with_anthropic_cache(self):
        """测试创建带 Anthropic 缓存的 usage"""
        usage = NormalizedTokenUsage(
            prompt_tokens=100,
            completion_tokens=50,
            total_tokens=150,
            estimated=False,
            cache_creation_tokens=20,
            cache_read_tokens=10,
        )

        assert usage.cache_creation_tokens == 20
        assert usage.cache_read_tokens == 10

    def test_create_usage_with_reasoning(self):
        """测试创建带推理 token 的 usage"""
        usage = NormalizedTokenUsage(
            prompt_tokens=100,
            completion_tokens=50,
            total_tokens=150,
            estimated=False,
            reasoning_tokens=25,
        )

        assert usage.reasoning_tokens == 25

    def test_default_values(self):
        """测试默认值"""
        usage = NormalizedTokenUsage(
            prompt_tokens=100,
            completion_tokens=50,
            total_tokens=150,
            estimated=False,
        )

        assert usage.cached_tokens == 0
        assert usage.cache_creation_tokens == 0
        assert usage.cache_read_tokens == 0
        assert usage.reasoning_tokens == 0
        assert usage.raw_usage is None


class TestExtractUsage:
    """extract_usage() 函数测试"""

    def test_extract_from_none(self):
        """测试从 None 提取"""
        result = extract_usage(None)

        assert result is None

    def test_extract_from_dict_openai_format(self):
        """测试从 OpenAI 格式 dict 提取"""
        response = {
            "usage": {
                "prompt_tokens": 100,
                "completion_tokens": 50,
                "total_tokens": 150,
            }
        }

        usage = extract_usage(response)

        assert usage is not None
        assert usage.prompt_tokens == 100
        assert usage.completion_tokens == 50
        assert usage.total_tokens == 150
        assert usage.estimated is False

    def test_extract_from_dict_with_cached_tokens(self):
        """测试从带缓存的 dict 提取"""
        response = {
            "usage": {
                "prompt_tokens": 100,
                "completion_tokens": 50,
                "total_tokens": 150,
                "prompt_tokens_details": {
                    "cached_tokens": 30,
                },
            }
        }

        usage = extract_usage(response)

        assert usage is not None
        assert usage.cached_tokens == 30

    def test_extract_from_dict_anthropic_format(self):
        """测试从 Anthropic 格式 dict 提取"""
        response = {
            "usage": {
                "input_tokens": 80,
                "output_tokens": 40,
                "cache_creation_input_tokens": 20,
                "cache_read_input_tokens": 10,
            }
        }

        usage = extract_usage(response)

        assert usage is not None
        assert usage.prompt_tokens == 80
        assert usage.completion_tokens == 40
        assert usage.cache_creation_tokens == 20
        assert usage.cache_read_tokens == 10

    def test_extract_from_dict_glm_format(self):
        """测试从 GLM 格式 dict 提取"""
        response = {
            "usage": {
                "input_tokens": 100,
                "output_tokens": 50,
                "total_tokens": 150,
                "input_token_details": {
                    "cache_read": 25,
                },
                "output_token_details": {
                    "reasoning": 15,
                },
            }
        }

        usage = extract_usage(response)

        assert usage is not None
        assert usage.prompt_tokens == 100
        assert usage.completion_tokens == 50
        assert usage.cached_tokens == 25
        assert usage.reasoning_tokens == 15

    def test_extract_from_ai_message_with_usage_metadata(self):
        """测试从 AIMessage 的 usage_metadata 提取"""
        msg = AIMessage(
            content="Hello!",
            usage_metadata={
                "input_tokens": 100,
                "output_tokens": 50,
                "total_tokens": 150,
            },
        )

        usage = extract_usage(msg)

        assert usage is not None
        assert usage.prompt_tokens == 100
        assert usage.completion_tokens == 50

    def test_extract_from_dict_token_usage_key(self):
        """测试从 token_usage 键提取"""
        response = {
            "token_usage": {
                "prompt_tokens": 100,
                "completion_tokens": 50,
                "total_tokens": 150,
            }
        }

        usage = extract_usage(response)

        assert usage is not None
        assert usage.prompt_tokens == 100

    def test_extract_from_incomplete_usage(self):
        """测试从不完整的 usage 提取"""
        response = {
            "usage": {
                "prompt_tokens": 100,
            }
        }

        usage = extract_usage(response)

        assert usage is None


class TestEstimateUsage:
    """estimate_usage() 函数测试"""

    def test_estimate_with_messages(self):
        """测试使用消息估算"""
        messages = [
            SystemMessage(content="你是助手"),
            HumanMessage(content="你好"),
        ]

        usage = estimate_usage(
            prompt_messages=messages,
            completion_text="你好！有什么可以帮你的？",
        )

        assert usage is not None
        assert usage.estimated is True
        assert usage.prompt_tokens > 0
        assert usage.completion_tokens > 0

    def test_estimate_with_none_messages(self):
        """测试 None 消息估算"""
        usage = estimate_usage(
            prompt_messages=None,
            completion_text="Hello!",
        )

        assert usage is not None
        assert usage.estimated is True
        assert usage.prompt_tokens == 0
        assert usage.completion_tokens > 0

    def test_estimate_with_none_completion(self):
        """测试 None 完成文本估算"""
        messages = [HumanMessage(content="Hello")]

        usage = estimate_usage(
            prompt_messages=messages,
            completion_text=None,
        )

        assert usage is not None
        assert usage.estimated is True
        assert usage.prompt_tokens > 0

    def test_estimate_raw_usage_contains_method(self):
        """测试估算结果包含方法信息"""
        usage = estimate_usage(
            prompt_messages=[HumanMessage(content="test")],
            completion_text="response",
        )

        assert usage.raw_usage is not None
        assert usage.raw_usage.get("estimated") is True
        assert usage.raw_usage.get("method") == "heuristic"


class TestParsePricing:
    """parse_pricing() 函数测试"""

    def test_parse_valid_pricing(self):
        """测试解析有效定价"""
        config = {
            "prompt_usd_per_1k_tokens": "0.01",
            "completion_usd_per_1k_tokens": "0.03",
        }

        pricing = parse_pricing(config)

        assert pricing is not None
        assert pricing.prompt_usd_per_1k_tokens == Decimal("0.01")
        assert pricing.completion_usd_per_1k_tokens == Decimal("0.03")

    def test_parse_pricing_with_cache(self):
        """测试解析带缓存定价"""
        config = {
            "prompt_usd_per_1k_tokens": "0.01",
            "completion_usd_per_1k_tokens": "0.03",
            "cached_prompt_usd_per_1k_tokens": "0.005",
            "cache_creation_usd_per_1k_tokens": "0.0125",
            "cache_read_usd_per_1k_tokens": "0.001",
        }

        pricing = parse_pricing(config)

        assert pricing is not None
        assert pricing.cached_prompt_usd_per_1k_tokens == Decimal("0.005")
        assert pricing.cache_creation_usd_per_1k_tokens == Decimal("0.0125")
        assert pricing.cache_read_usd_per_1k_tokens == Decimal("0.001")

    def test_parse_none_config(self):
        """测试解析 None 配置"""
        pricing = parse_pricing(None)

        assert pricing is None

    def test_parse_json_string(self):
        """测试解析 JSON 字符串"""
        config = '{"prompt_usd_per_1k_tokens": "0.01", "completion_usd_per_1k_tokens": "0.03"}'

        pricing = parse_pricing(config)

        assert pricing is not None
        assert pricing.prompt_usd_per_1k_tokens == Decimal("0.01")

    def test_parse_invalid_json(self):
        """测试解析无效 JSON"""
        pricing = parse_pricing("invalid json")

        assert pricing is None

    def test_parse_missing_fields(self):
        """测试解析缺少字段"""
        config = {"prompt_usd_per_1k_tokens": "0.01"}

        pricing = parse_pricing(config)

        assert pricing is None

    def test_parse_negative_price(self):
        """测试解析负数价格"""
        config = {
            "prompt_usd_per_1k_tokens": "-0.01",
            "completion_usd_per_1k_tokens": "0.03",
        }

        pricing = parse_pricing(config)

        assert pricing is None


class TestEstimateCostUsd:
    """estimate_cost_usd() 函数测试"""

    def test_estimate_basic_cost(self):
        """测试基本费用计算"""
        usage = NormalizedTokenUsage(
            prompt_tokens=1000,
            completion_tokens=500,
            total_tokens=1500,
            estimated=False,
        )

        pricing = ModelPricingUsd(
            prompt_usd_per_1k_tokens=Decimal("0.01"),
            completion_usd_per_1k_tokens=Decimal("0.03"),
        )

        cost = estimate_cost_usd(usage=usage, pricing=pricing)

        assert cost is not None
        assert cost.prompt_cost_usd == Decimal("0.01")
        assert cost.completion_cost_usd == Decimal("0.015")
        assert cost.total_cost_usd == Decimal("0.025")

    def test_estimate_cost_with_cache(self):
        """测试带缓存的费用计算"""
        usage = NormalizedTokenUsage(
            prompt_tokens=1000,
            completion_tokens=500,
            total_tokens=1500,
            estimated=False,
            cached_tokens=300,
        )

        pricing = ModelPricingUsd(
            prompt_usd_per_1k_tokens=Decimal("0.01"),
            completion_usd_per_1k_tokens=Decimal("0.03"),
        )

        cost = estimate_cost_usd(usage=usage, pricing=pricing)

        assert cost is not None
        assert cost.cached_prompt_cost_usd > Decimal("0")
        assert cost.savings_from_cache_usd > Decimal("0")

    def test_estimate_cost_with_anthropic_cache(self):
        """测试带 Anthropic 缓存的费用计算"""
        usage = NormalizedTokenUsage(
            prompt_tokens=1000,
            completion_tokens=500,
            total_tokens=1500,
            estimated=False,
            cache_creation_tokens=200,
            cache_read_tokens=100,
        )

        pricing = ModelPricingUsd(
            prompt_usd_per_1k_tokens=Decimal("0.01"),
            completion_usd_per_1k_tokens=Decimal("0.03"),
        )

        cost = estimate_cost_usd(usage=usage, pricing=pricing)

        assert cost is not None
        assert cost.cache_creation_cost_usd > Decimal("0")
        assert cost.cache_read_cost_usd > Decimal("0")

    def test_estimate_cost_none_pricing(self):
        """测试 None 定价"""
        usage = NormalizedTokenUsage(
            prompt_tokens=1000,
            completion_tokens=500,
            total_tokens=1500,
            estimated=False,
        )

        cost = estimate_cost_usd(usage=usage, pricing=None)

        assert cost is None

    def test_estimate_cost_custom_cache_pricing(self):
        """测试自定义缓存定价"""
        usage = NormalizedTokenUsage(
            prompt_tokens=1000,
            completion_tokens=500,
            total_tokens=1500,
            estimated=False,
            cached_tokens=500,
        )

        pricing = ModelPricingUsd(
            prompt_usd_per_1k_tokens=Decimal("0.01"),
            completion_usd_per_1k_tokens=Decimal("0.03"),
            cached_prompt_usd_per_1k_tokens=Decimal("0.002"),
        )

        cost = estimate_cost_usd(usage=usage, pricing=pricing)

        assert cost is not None
        assert cost.cached_prompt_cost_usd == Decimal("0.001")


class TestModelPricingUsd:
    """ModelPricingUsd 数据类测试"""

    def test_create_basic_pricing(self):
        """测试创建基本定价"""
        pricing = ModelPricingUsd(
            prompt_usd_per_1k_tokens=Decimal("0.01"),
            completion_usd_per_1k_tokens=Decimal("0.03"),
        )

        assert pricing.prompt_usd_per_1k_tokens == Decimal("0.01")
        assert pricing.completion_usd_per_1k_tokens == Decimal("0.03")

    def test_create_pricing_with_cache(self):
        """测试创建带缓存定价"""
        pricing = ModelPricingUsd(
            prompt_usd_per_1k_tokens=Decimal("0.01"),
            completion_usd_per_1k_tokens=Decimal("0.03"),
            cached_prompt_usd_per_1k_tokens=Decimal("0.005"),
        )

        assert pricing.cached_prompt_usd_per_1k_tokens == Decimal("0.005")

    def test_default_cache_pricing(self):
        """测试默认缓存定价为 None"""
        pricing = ModelPricingUsd(
            prompt_usd_per_1k_tokens=Decimal("0.01"),
            completion_usd_per_1k_tokens=Decimal("0.03"),
        )

        assert pricing.cached_prompt_usd_per_1k_tokens is None
        assert pricing.cache_creation_usd_per_1k_tokens is None
        assert pricing.cache_read_usd_per_1k_tokens is None


class TestUsageCostUsd:
    """UsageCostUsd 数据类测试"""

    def test_create_basic_cost(self):
        """测试创建基本费用"""
        cost = UsageCostUsd(
            prompt_cost_usd=Decimal("0.01"),
            completion_cost_usd=Decimal("0.015"),
            total_cost_usd=Decimal("0.025"),
        )

        assert cost.prompt_cost_usd == Decimal("0.01")
        assert cost.completion_cost_usd == Decimal("0.015")
        assert cost.total_cost_usd == Decimal("0.025")

    def test_default_cache_cost(self):
        """测试默认缓存费用为 0"""
        cost = UsageCostUsd(
            prompt_cost_usd=Decimal("0.01"),
            completion_cost_usd=Decimal("0.015"),
            total_cost_usd=Decimal("0.025"),
        )

        assert cost.cached_prompt_cost_usd == Decimal("0")
        assert cost.cache_creation_cost_usd == Decimal("0")
        assert cost.cache_read_cost_usd == Decimal("0")
        assert cost.savings_from_cache_usd == Decimal("0")
