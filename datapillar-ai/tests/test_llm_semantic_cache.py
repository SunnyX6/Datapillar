import json
import hashlib

from langchain_core.outputs import ChatGeneration
from langchain_core.messages import AIMessage

from src.infrastructure.llm.semantic_cache import SemanticLLMCache


class FakeEmbedder:
    """确定性 embedder：相同文本得到相同向量，不同文本几乎不可能高相似。"""

    def embed_query(self, text: str) -> list[float]:
        digest = hashlib.sha256((text or "").encode("utf-8")).digest()
        return [b / 255.0 for b in digest[:16]]


def _prompt(messages: list[tuple[str, str]]) -> str:
    return json.dumps([{"type": role, "content": content} for role, content in messages])


def test_hit_same_query_same_scope(tmp_path):
    now = {"t": 1000.0}

    def now_fn() -> float:
        return now["t"]

    cache = SemanticLLMCache(
        database_path=str(tmp_path / "cache.db"),
        embedder_factory=FakeEmbedder,
        hard_threshold=0.95,
        ttl_seconds=60,
        context_window_messages=6,
        now_fn=now_fn,
    )

    llm_string = "model=x;temperature=0"
    prompt = _prompt(
        [
            ("system", "你是一个严谨的助手，输出中文。"),
            ("human", "你好"),
            ("ai", "你好，有什么我能帮你？"),
            ("human", "统计订单金额汇总"),
        ]
    )

    assert cache.lookup(prompt, llm_string) is None

    cache.update(prompt, llm_string, [ChatGeneration(message=AIMessage(content="答案A"))])
    hit = cache.lookup(prompt, llm_string)
    assert hit is not None
    assert hit[0].message.content == "答案A"


def test_miss_when_system_prompt_changes(tmp_path):
    now = {"t": 1000.0}

    def now_fn() -> float:
        return now["t"]

    cache = SemanticLLMCache(
        database_path=str(tmp_path / "cache.db"),
        embedder_factory=FakeEmbedder,
        hard_threshold=0.95,
        ttl_seconds=60,
        context_window_messages=6,
        now_fn=now_fn,
    )

    llm_string = "model=x;temperature=0"
    prompt_a = _prompt(
        [
            ("system", "你是一个严谨的助手，输出中文。"),
            ("human", "统计订单金额汇总"),
        ]
    )
    prompt_b = _prompt(
        [
            ("system", "你是一个随意的助手，可以输出英文。"),
            ("human", "统计订单金额汇总"),
        ]
    )

    cache.update(prompt_a, llm_string, [ChatGeneration(message=AIMessage(content="答案A"))])
    assert cache.lookup(prompt_b, llm_string) is None


def test_miss_when_llm_string_changes(tmp_path):
    now = {"t": 1000.0}

    def now_fn() -> float:
        return now["t"]

    cache = SemanticLLMCache(
        database_path=str(tmp_path / "cache.db"),
        embedder_factory=FakeEmbedder,
        hard_threshold=0.95,
        ttl_seconds=60,
        context_window_messages=6,
        now_fn=now_fn,
    )

    prompt = _prompt(
        [
            ("system", "你是一个严谨的助手，输出中文。"),
            ("human", "统计订单金额汇总"),
        ]
    )

    cache.update(prompt, "model=x;temperature=0", [ChatGeneration(message=AIMessage(content="答案A"))])
    assert cache.lookup(prompt, "model=x;temperature=0.1") is None


def test_history_window_ignores_old_messages_outside_k(tmp_path):
    now = {"t": 1000.0}

    def now_fn() -> float:
        return now["t"]

    cache = SemanticLLMCache(
        database_path=str(tmp_path / "cache.db"),
        embedder_factory=FakeEmbedder,
        hard_threshold=0.95,
        ttl_seconds=60,
        context_window_messages=6,
        now_fn=now_fn,
    )

    llm_string = "model=x;temperature=0"
    base_history = [
        ("system", "你是一个严谨的助手，输出中文。"),
        ("human", "H1"),
        ("ai", "A1"),
        ("human", "H2"),
        ("ai", "A2"),
        ("human", "H3"),
        ("ai", "A3"),
        ("human", "H4"),
        ("ai", "A4"),
        ("human", "H5"),
        ("ai", "A5"),
        ("human", "H6"),
        ("ai", "A6"),
        ("human", "H7"),
        ("ai", "A7"),
    ]

    prompt_a = _prompt(base_history + [("human", "统计订单金额汇总")])
    cache.update(prompt_a, llm_string, [ChatGeneration(message=AIMessage(content="答案A"))])

    # 修改最早的历史消息（超出 K=6 的窗口），不应影响 scope_key，从而应命中
    mutated_history = base_history.copy()
    mutated_history[1] = ("human", "H1-CHANGED")
    prompt_b = _prompt(mutated_history + [("human", "统计订单金额汇总")])

    hit = cache.lookup(prompt_b, llm_string)
    assert hit is not None
    assert hit[0].message.content == "答案A"


def test_ttl_expiration(tmp_path):
    now = {"t": 1000.0}

    def now_fn() -> float:
        return now["t"]

    cache = SemanticLLMCache(
        database_path=str(tmp_path / "cache.db"),
        embedder_factory=FakeEmbedder,
        hard_threshold=0.95,
        ttl_seconds=60,
        context_window_messages=6,
        now_fn=now_fn,
    )

    llm_string = "model=x;temperature=0"
    prompt = _prompt(
        [
            ("system", "你是一个严谨的助手，输出中文。"),
            ("human", "统计订单金额汇总"),
        ]
    )

    cache.update(prompt, llm_string, [ChatGeneration(message=AIMessage(content="答案A"))])
    assert cache.lookup(prompt, llm_string) is not None

    now["t"] += 61.0
    assert cache.lookup(prompt, llm_string) is None


def test_skip_caching_non_json_when_prompt_requests_json(tmp_path):
    now = {"t": 1000.0}

    def now_fn() -> float:
        return now["t"]

    cache = SemanticLLMCache(
        database_path=str(tmp_path / "cache.db"),
        embedder_factory=FakeEmbedder,
        hard_threshold=0.95,
        ttl_seconds=60,
        context_window_messages=6,
        now_fn=now_fn,
    )

    llm_string = "model=x;temperature=0"
    prompt = _prompt(
        [
            ("system", "无论如何只能输出 JSON 对象 ```json {\"success\": true} ```"),
            ("human", "统计订单金额汇总"),
        ]
    )

    cache.update(prompt, llm_string, [ChatGeneration(message=AIMessage(content="这不是JSON"))])
    assert cache.lookup(prompt, llm_string) is None
