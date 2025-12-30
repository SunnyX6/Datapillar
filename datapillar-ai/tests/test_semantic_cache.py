#!/usr/bin/env python3
"""
独立集成测试：语义缓存（仅高相似直接命中）

运行方式：
  GLM_API_KEY=... GLM_BASE_URL=https://open.bigmodel.cn/api/paas/v4 .venv/bin/python tests/test_semantic_cache.py

说明：
- 这是集成测试脚本，不作为 pytest 用例收集。
- 只验证“相同问题”命中缓存；相似但不够高相似的问题应走真实 LLM（避免误答）。
"""

import os
import time
import sqlite3
import pickle
import json
import numpy as np

from langchain_core.globals import set_llm_cache
from langchain_core.caches import BaseCache
from langchain_openai import ChatOpenAI, OpenAIEmbeddings


GLM_API_KEY = os.getenv("GLM_API_KEY", "")
GLM_BASE_URL = os.getenv("GLM_BASE_URL", "https://open.bigmodel.cn/api/paas/v4")


class SemanticCacheIntegration(BaseCache):
    """集成测试用语义缓存（仅高相似命中）"""

    def __init__(self, database_path=".test_cache.db", high_threshold=0.95, ttl_seconds=60):
        self.database_path = database_path
        self.high_threshold = high_threshold
        self.ttl_seconds = ttl_seconds
        self._embedder = None
        self.last_lookup_hit: bool = False
        self.last_best_similarity: float | None = None
        self.last_lookup_user_input: str = ""
        self.last_update_stored: bool = False
        self._init_db()

    def _init_db(self):
        with sqlite3.connect(self.database_path) as conn:
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS semantic_cache (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_input TEXT NOT NULL,
                    llm_string TEXT NOT NULL,
                    embedding BLOB NOT NULL,
                    response BLOB NOT NULL,
                    created_at REAL NOT NULL
                )
                """
            )
            conn.commit()

    def _get_embedder(self):
        if self._embedder is None:
            self._embedder = OpenAIEmbeddings(
                api_key=GLM_API_KEY,
                base_url=GLM_BASE_URL,
                model="embedding-3",
            )
        return self._embedder

    def _extract_user_input(self, prompt: str) -> str:
        try:
            messages = json.loads(prompt)
            for msg in reversed(messages):
                msg_type = msg.get("kwargs", {}).get("type") or msg.get("type")
                if msg_type == "human":
                    content = msg.get("kwargs", {}).get("content") or msg.get("content", "")
                    return content[:500]
            return prompt[:500]
        except Exception:
            return prompt[:500]

    def _cosine_similarity(self, v1, v2):
        a, b = np.array(v1), np.array(v2)
        return float(np.dot(a, b) / (np.linalg.norm(a) * np.linalg.norm(b) + 1e-9))

    def lookup(self, prompt: str, llm_string: str):
        self.last_lookup_hit = False
        self.last_best_similarity = None
        self.last_lookup_user_input = ""
        self.last_update_stored = False

        user_input = self._extract_user_input(prompt)
        self.last_lookup_user_input = user_input

        try:
            query_embedding = self._get_embedder().embed_query(user_input)
        except Exception as e:
            print(f"  [缓存] embedding 失败: {e}")
            return None

        cutoff = time.time() - self.ttl_seconds
        with sqlite3.connect(self.database_path) as conn:
            rows = conn.execute(
                "SELECT user_input, embedding, response FROM semantic_cache WHERE llm_string = ? AND created_at >= ?",
                (llm_string, cutoff),
            ).fetchall()

        best_match = None
        best_similarity = 0.0

        for cached_input, embedding_blob, response_blob in rows:
            cached_embedding = pickle.loads(embedding_blob)
            similarity = self._cosine_similarity(query_embedding, cached_embedding)
            if similarity > best_similarity:
                best_similarity = similarity
                best_match = response_blob

        self.last_best_similarity = best_similarity

        if best_match and best_similarity >= self.high_threshold:
            print(f"  [缓存命中-高] 相似度={best_similarity:.2%}")
            self.last_lookup_hit = True
            return pickle.loads(best_match)

        if best_similarity > 0:
            print(f"  [缓存未命中] 最高相似度={best_similarity:.2%}")
        else:
            print("  [缓存未命中] 无缓存")
        return None

    def update(self, prompt: str, llm_string: str, return_val):
        user_input = self._extract_user_input(prompt)

        try:
            embedding = self._get_embedder().embed_query(user_input)
        except Exception:
            return

        with sqlite3.connect(self.database_path) as conn:
            conn.execute(
                "INSERT INTO semantic_cache (user_input, llm_string, embedding, response, created_at) VALUES (?, ?, ?, ?, ?)",
                (user_input, llm_string, pickle.dumps(embedding), pickle.dumps(return_val), time.time()),
            )
            conn.commit()
        self.last_update_stored = True
        print(f"  [缓存存储] 输入='{user_input[:30]}...'")

    def clear(self, **kwargs):
        with sqlite3.connect(self.database_path) as conn:
            conn.execute("DELETE FROM semantic_cache")
            conn.commit()


def run_test():
    if not GLM_API_KEY:
        raise RuntimeError("缺少 GLM_API_KEY 环境变量，无法运行集成测试")

    print("=" * 60)
    print("测试：语义缓存（仅高相似命中）")
    print("=" * 60)

    cache = SemanticCacheIntegration(database_path=".test_cache.db", ttl_seconds=60, high_threshold=0.95)
    set_llm_cache(cache)

    llm = ChatOpenAI(
        api_key=GLM_API_KEY,
        base_url=GLM_BASE_URL,
        model="glm-4-flash",
        streaming=False,
    )

    embedder = OpenAIEmbeddings(
        api_key=GLM_API_KEY,
        base_url=GLM_BASE_URL,
        model="embedding-3",
    )

    def invoke_case(case_title: str, query: str) -> dict:
        print(f"\n[{case_title}] {query}")
        start = time.time()
        result = llm.invoke(query)
        elapsed = time.time() - start
        hit_flag = "✅ 命中" if cache.last_lookup_hit else "❌ 未命中"
        sim = cache.last_best_similarity
        sim_text = f"{sim:.2%}" if isinstance(sim, float) else "N/A"
        stored_text = "已存储" if cache.last_update_stored else "未存储"
        print(f"  缓存: {hit_flag}（最高相似度 {sim_text}，{stored_text}）")
        print(f"  耗时: {elapsed:.2f}s")
        print(f"  回复: {result.content[:80]}...")
        return {
            "elapsed": elapsed,
            "hit": bool(cache.last_lookup_hit),
            "best_similarity": sim,
        }

    r1 = invoke_case("测试1 首次调用", "计算订单总金额")
    r2 = invoke_case("测试2 完全相同", "计算订单总金额")

    near_dup = "计算订单总金额。"
    try:
        sim = cache._cosine_similarity(embedder.embed_query("计算订单总金额"), embedder.embed_query(near_dup))
    except Exception as e:
        sim = None
        print(f"\n[测试3] 近似改写相似度计算失败: {e}")

    if sim is not None:
        print(f"\n[测试3] 近似改写 - {near_dup}（embedding 相似度 {sim:.2%}，>=0.95 才会命中）")
    else:
        print(f"\n[测试3] 近似改写 - {near_dup}（无法计算相似度，直接执行调用）")

    r3 = invoke_case("测试3 近似改写", near_dup)
    r4 = invoke_case("测试4 相对相似但不等价", "统计订单金额汇总")

    print("\n" + "=" * 60)
    print("测试汇总（以缓存回调标记判断是否命中）:")
    print(f"  测试1（首次）: {r1['elapsed']:.2f}s - {'✅ 命中' if r1['hit'] else '调用 LLM'}")
    print(f"  测试2（相同）: {r2['elapsed']:.2f}s - {'✅ 命中' if r2['hit'] else '❌ 未命中'}")
    print(f"  测试3（近似改写）: {r3['elapsed']:.2f}s - {'✅ 命中' if r3['hit'] else '❌ 未命中（相似度不够）'}")
    print(f"  测试4（相对相似）: {r4['elapsed']:.2f}s - {'✅ 命中' if r4['hit'] else '调用 LLM'}")
    print("=" * 60)


if __name__ == "__main__":
    run_test()
