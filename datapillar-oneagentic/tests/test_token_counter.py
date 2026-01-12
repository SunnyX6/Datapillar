"""
Token 计数器单元测试

测试模块：
- datapillar_oneagentic.providers.token_counter
"""

import pytest

from datapillar_oneagentic.config import datapillar_configure, reset_config
from datapillar_oneagentic.providers.token_counter import (
    BaseTokenCounter,
    TiktokenCounter,
    get_token_counter,
    reset_token_counter,
)


class TestTiktokenCounter:
    """TiktokenCounter 测试"""

    def test_count_empty_text(self):
        """测试空文本计数"""
        counter = TiktokenCounter()

        assert counter.count("") == 0

    def test_count_simple_text(self):
        """测试简单文本计数"""
        counter = TiktokenCounter()
        tokens = counter.count("Hello, world!")

        assert tokens > 0

    def test_count_chinese_text(self):
        """测试中文文本计数"""
        counter = TiktokenCounter()
        tokens = counter.count("你好，世界！")

        assert tokens > 0

    def test_count_mixed_text(self):
        """测试中英混合文本计数"""
        counter = TiktokenCounter()
        tokens = counter.count("Hello, 世界！This is a test. 这是测试。")

        assert tokens > 0

    def test_count_long_text(self):
        """测试长文本计数"""
        counter = TiktokenCounter()
        long_text = "This is a test. " * 1000
        tokens = counter.count(long_text)

        assert tokens > 1000

    def test_count_messages_empty(self):
        """测试空消息列表计数"""
        counter = TiktokenCounter()
        tokens = counter.count_messages([])

        assert tokens == 0

    def test_count_messages_single(self):
        """测试单条消息计数"""
        counter = TiktokenCounter()
        messages = [
            {"role": "user", "content": "Hello!"},
        ]
        tokens = counter.count_messages(messages)

        assert tokens > 0

    def test_count_messages_multiple(self):
        """测试多条消息计数"""
        counter = TiktokenCounter()
        messages = [
            {"role": "system", "content": "你是一个助手。"},
            {"role": "user", "content": "你好！"},
            {"role": "assistant", "content": "你好！有什么可以帮你的？"},
        ]
        tokens = counter.count_messages(messages)

        assert tokens > 0

    def test_count_messages_with_name(self):
        """测试带名称的消息计数"""
        counter = TiktokenCounter()
        messages = [
            {"role": "user", "content": "Hello!", "name": "Alice"},
        ]
        tokens = counter.count_messages(messages)

        assert tokens > 0

    def test_different_models(self):
        """测试不同模型的计数器"""
        counter_gpt4 = TiktokenCounter(model="gpt-4o")
        counter_gpt35 = TiktokenCounter(model="gpt-3.5-turbo")

        text = "Hello, world!"
        tokens_gpt4 = counter_gpt4.count(text)
        tokens_gpt35 = counter_gpt35.count(text)

        assert tokens_gpt4 > 0
        assert tokens_gpt35 > 0

    def test_fallback_encoding(self):
        """测试未知模型回退到默认编码"""
        counter = TiktokenCounter(model="unknown-model-xyz")
        tokens = counter.count("Hello, world!")

        assert tokens > 0


class TestGetTokenCounter:
    """get_token_counter() 函数测试"""

    @pytest.fixture(autouse=True)
    def setup(self):
        """测试前重置"""
        reset_config()
        reset_token_counter()
        yield
        reset_config()
        reset_token_counter()

    def test_get_default_counter(self):
        """测试获取默认计数器"""
        counter = get_token_counter()

        assert counter is not None
        assert isinstance(counter, TiktokenCounter)

    def test_get_same_instance(self):
        """测试返回同一个实例"""
        counter1 = get_token_counter()
        counter2 = get_token_counter()

        assert counter1 is counter2

    def test_get_custom_counter(self):
        """测试获取自定义计数器"""

        class CustomCounter(BaseTokenCounter):
            def count(self, text: str) -> int:
                return len(text)

            def count_messages(self, messages: list[dict]) -> int:
                return sum(len(m.get("content", "")) for m in messages)

        custom = CustomCounter()
        datapillar_configure(token_counter=custom)

        counter = get_token_counter()

        assert counter is custom
        assert counter.count("Hello") == 5


class TestBaseTokenCounter:
    """BaseTokenCounter 抽象基类测试"""

    def test_cannot_instantiate_base_class(self):
        """测试无法实例化基类"""
        with pytest.raises(TypeError):
            BaseTokenCounter()

    def test_custom_implementation(self):
        """测试自定义实现"""

        class CharCounter(BaseTokenCounter):
            def count(self, text: str) -> int:
                return len(text)

            def count_messages(self, messages: list[dict]) -> int:
                total = 0
                for msg in messages:
                    total += len(msg.get("content", ""))
                return total

        counter = CharCounter()

        assert counter.count("Hello") == 5
        assert counter.count_messages([{"content": "Hi"}]) == 2


class TestTokenCounterConsistency:
    """Token 计数器一致性测试"""

    def test_count_consistency(self):
        """测试多次计数结果一致"""
        counter = TiktokenCounter()
        text = "This is a test message for token counting."

        results = [counter.count(text) for _ in range(10)]

        assert len(set(results)) == 1

    def test_message_count_consistency(self):
        """测试消息计数结果一致"""
        counter = TiktokenCounter()
        messages = [
            {"role": "system", "content": "You are a helpful assistant."},
            {"role": "user", "content": "Hello!"},
        ]

        results = [counter.count_messages(messages) for _ in range(10)]

        assert len(set(results)) == 1

    def test_count_additivity(self):
        """测试计数近似可加性"""
        counter = TiktokenCounter()

        text1 = "Hello, world!"
        text2 = " This is a test."
        combined = text1 + text2

        tokens1 = counter.count(text1)
        tokens2 = counter.count(text2)
        tokens_combined = counter.count(combined)

        assert abs(tokens_combined - (tokens1 + tokens2)) <= 2


class TestTokenCounterEdgeCases:
    """Token 计数器边界情况测试"""

    def test_special_characters(self):
        """测试特殊字符"""
        counter = TiktokenCounter()

        texts = [
            "Hello\nWorld",
            "Tab\there",
            "Unicode: 你好世界🌍",
            "Symbols: @#$%^&*()",
            "Numbers: 12345.67890",
        ]

        for text in texts:
            tokens = counter.count(text)
            assert tokens > 0

    def test_whitespace_only(self):
        """测试纯空白字符"""
        counter = TiktokenCounter()

        tokens_space = counter.count("   ")
        tokens_newline = counter.count("\n\n\n")
        tokens_tab = counter.count("\t\t\t")

        assert tokens_space >= 0
        assert tokens_newline >= 0
        assert tokens_tab >= 0

    def test_very_long_text(self):
        """测试超长文本"""
        counter = TiktokenCounter()
        very_long = "word " * 100000

        tokens = counter.count(very_long)

        assert tokens > 100000

    def test_unicode_characters(self):
        """测试 Unicode 字符"""
        counter = TiktokenCounter()

        texts = [
            "😀😃😄😁😆",
            "αβγδε",
            "日本語テスト",
            "한국어 테스트",
            "العربية",
        ]

        for text in texts:
            tokens = counter.count(text)
            assert tokens > 0
