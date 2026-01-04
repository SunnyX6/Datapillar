"""
通用 LLM 语义缓存（业务无关）

设计目标：
- 只依赖 LangChain 原生 BaseCache 接口：lookup(prompt, llm_string) / update(prompt, llm_string, return_val)
- 命中只做“高相似直接返回”（不做中等相似参考答案）
- 只使用 prompt + llm_string 推导缓存隔离边界（不引入 tenant/user 等业务概念）
- SQLite 持久化，TTL 过期清理
"""

from __future__ import annotations

import hashlib
import json
import logging
import os
import re
import sqlite3
import time
from collections.abc import Callable, Iterable
from dataclasses import dataclass
from typing import Any, Protocol

import numpy as np
from langchain_core.caches import RETURN_VAL_TYPE, BaseCache
from langchain_core.messages import AIMessage
from langchain_core.outputs import ChatGeneration, Generation

logger = logging.getLogger(__name__)


class Embedder(Protocol):
    """最小 embedding 协议：只要求实现 embed_query。"""

    def embed_query(self, text: str) -> list[float]: ...


@dataclass(frozen=True)
class _PromptMessage:
    role: str
    content: str


def _normalize_text(text: str) -> str:
    """轻量规范化：合并空白，去首尾空格。"""
    text = (text or "").strip()
    text = re.sub(r"\s+", " ", text)
    return text


def _clip(text: str, max_chars: int) -> str:
    text = text or ""
    if len(text) <= max_chars:
        return text
    return text[:max_chars]


def _json_to_text(content: Any) -> str:
    """
    LangChain message content 可能是 str，也可能是 content blocks 列表。
    这里统一转为可哈希/可 embedding 的纯文本。
    """
    if content is None:
        return ""
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts: list[str] = []
        for item in content:
            if isinstance(item, str):
                parts.append(item)
                continue
            if isinstance(item, dict):
                text = item.get("text")
                if isinstance(text, str) and text:
                    parts.append(text)
        return "\n".join(parts)
    return str(content)


def _extract_role(msg: dict) -> str:
    msg_type = msg.get("kwargs", {}).get("type") or msg.get("type") or ""
    msg_type = str(msg_type).lower()
    if msg_type in {"human", "ai", "system", "tool"}:
        return msg_type
    return "unknown"


def _extract_content(msg: dict) -> str:
    content = msg.get("kwargs", {}).get("content")
    if content is None:
        content = msg.get("content")
    return _json_to_text(content)


def _try_parse_prompt(prompt: str) -> list[_PromptMessage] | None:
    """
    尝试解析 LangChain chat prompt 的序列化字符串。
    解析失败时返回 None（保持容错，不做任何假设）。
    """
    try:
        raw = json.loads(prompt)
    except (json.JSONDecodeError, TypeError):
        return None
    if not isinstance(raw, list):
        return None

    messages: list[_PromptMessage] = []
    for item in raw:
        if not isinstance(item, dict):
            continue
        role = _extract_role(item)
        content = _extract_content(item)
        messages.append(_PromptMessage(role=role, content=content))
    return messages


def _extract_query_text(prompt: str, parsed: list[_PromptMessage] | None) -> str:
    """
    提取用于 embedding 的查询文本：
    - 优先：最后一条 human message
    - 兜底：直接使用 prompt 字符串
    """
    if parsed:
        for msg in reversed(parsed):
            if msg.role == "human":
                return _clip(_normalize_text(msg.content), 2000)
    return _clip(_normalize_text(prompt), 2000)


def _build_context_part(
    parsed: list[_PromptMessage] | None,
    *,
    context_window_messages: int,
) -> str:
    """
    构建“上下文部分”，用于计算隔离指纹（scope_key），不参与语义相似检索。

    规则：
    - 取所有 system 消息（强约束，必须参与隔离）
    - 取最近 K 条历史消息（包含 AI/tool/human），但排除“最后一条 human”（当前查询）
    - 对每条内容截断，避免上下文过大
    """
    if not parsed:
        return ""

    last_human_index: int | None = None
    for idx in range(len(parsed) - 1, -1, -1):
        if parsed[idx].role == "human":
            last_human_index = idx
            break

    messages_before_query = parsed if last_human_index is None else parsed[:last_human_index]

    system_parts: list[str] = []
    for msg in parsed:
        if msg.role == "system":
            system_parts.append(f"system:{_clip(_normalize_text(msg.content), 1500)}")

    history_candidates = [m for m in messages_before_query if m.role != "system"]
    history_window = (
        history_candidates[-context_window_messages:] if context_window_messages > 0 else []
    )

    history_parts: list[str] = []
    for msg in history_window:
        history_parts.append(f"{msg.role}:{_clip(_normalize_text(msg.content), 800)}")

    context = "\n".join(system_parts + history_parts)
    return _clip(context, 8000)


def _cosine_similarity(v1: Iterable[float], v2: Iterable[float]) -> float:
    a = np.asarray(list(v1), dtype=np.float32)
    b = np.asarray(list(v2), dtype=np.float32)
    denom = float(np.linalg.norm(a) * np.linalg.norm(b) + 1e-9)
    if denom <= 0:
        return 0.0
    return float(np.dot(a, b) / denom)


def _has_cacheable_text(return_val: RETURN_VAL_TYPE) -> bool:
    """
    只缓存“最终回答文本存在”的结果。
    典型工具调用阶段 content 为空，直接跳过。
    """
    if not return_val:
        return False
    for gen in return_val:
        msg = getattr(gen, "message", None)
        if msg is not None:
            content = getattr(msg, "content", None)
            content_text = _normalize_text(_json_to_text(content))
            if content_text:
                return True
        text = getattr(gen, "text", None)
        if isinstance(text, str) and _normalize_text(text):
            return True
    return False


def _prompt_requests_json(prompt: str) -> bool:
    """
    粗粒度判断：prompt 是否明确要求 JSON 输出。

    说明：
    - 这是“输出格式”级别的判断，不涉及任何业务字段
    - 主要用于避免把明显不符合格式要求的输出缓存起来，导致短时间内重复失败
    """
    p = prompt or ""
    return "```json" in p or "response_format" in p or "JSON 对象" in p


def _has_json_object(text: str) -> bool:
    """
    检测文本是否包含可解析的 JSON 对象（用于缓存有效性校验）。

    支持多种 LLM 返回格式：
    - ```json ... ``` 代码块
    - 裸 JSON 对象 { ... }
    """
    t = text or ""

    # 1. 尝试匹配 ```json ... ``` 代码块
    m = re.search(r"```json\s*([\s\S]*?)\s*```", t)
    if m:
        try:
            obj = json.loads(m.group(1))
            return isinstance(obj, dict)
        except (json.JSONDecodeError, TypeError) as exc:
            logger.debug("json_codeblock_parse_failed: %s", exc)

    # 2. 尝试匹配裸 JSON 对象 { ... }
    m = re.search(r"\{[\s\S]*\}", t)
    if m:
        try:
            obj = json.loads(m.group(0))
            return isinstance(obj, dict)
        except (json.JSONDecodeError, TypeError) as exc:
            logger.debug("json_object_parse_failed: %s", exc)
            return False

    return False


def _return_val_text(return_val: RETURN_VAL_TYPE) -> str:
    if not return_val:
        return ""
    parts: list[str] = []
    for gen in return_val:
        msg = getattr(gen, "message", None)
        if msg is not None:
            content = getattr(msg, "content", None)
            content_text = _json_to_text(content)
            if isinstance(content_text, str) and content_text:
                parts.append(content_text)
                continue
        text = getattr(gen, "text", None)
        if isinstance(text, str) and text:
            parts.append(text)
    return "\n".join(parts)


def _serialize_embedding(embedding: Iterable[float]) -> bytes:
    return json.dumps(list(embedding), ensure_ascii=False).encode("utf-8")


def _deserialize_embedding(blob: bytes) -> list[float] | None:
    try:
        raw = json.loads((blob or b"").decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError, TypeError):
        return None
    if not isinstance(raw, list):
        return None
    out: list[float] = []
    for item in raw:
        if isinstance(item, (int, float)):
            out.append(float(item))
        else:
            return None
    return out


def _serialize_return_val(return_val: RETURN_VAL_TYPE) -> bytes:
    items: list[dict[str, Any]] = []
    for gen in return_val or []:
        msg = getattr(gen, "message", None)
        if msg is not None:
            content = getattr(msg, "content", None)
            try:
                json.dumps(content)
                serializable_content = content
            except TypeError:
                serializable_content = _json_to_text(content)
            items.append({"type": "chat", "content": serializable_content})
            continue
        text = getattr(gen, "text", None)
        items.append({"type": "text", "text": str(text or "")})
    return json.dumps(items, ensure_ascii=False).encode("utf-8")


def _deserialize_return_val(blob: bytes) -> RETURN_VAL_TYPE | None:
    try:
        raw = json.loads((blob or b"").decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError, TypeError):
        return None
    if not isinstance(raw, list):
        return None

    result: list[Any] = []
    for item in raw:
        if not isinstance(item, dict):
            continue
        item_type = item.get("type")
        if item_type == "chat":
            content = item.get("content", "")
            result.append(ChatGeneration(message=AIMessage(content=content)))
        elif item_type == "text":
            result.append(Generation(text=str(item.get("text", ""))))
    return result


class SemanticLLMCache(BaseCache):
    """
    通用语义缓存（SQLite 持久化）

    命中策略：
    - 只做高相似命中（>= hard_threshold）：直接返回缓存，避免一次 LLM 调用
    - 不做“中等相似参考答案”
    """

    def __init__(
        self,
        *,
        database_path: str,
        embedder_factory: Callable[[], Embedder],
        hard_threshold: float = 0.95,
        ttl_seconds: int = 60,
        context_window_messages: int = 6,
        max_candidates: int = 200,
        now_fn: Callable[[], float] = time.time,
    ):
        self.database_path = database_path
        self._embedder_factory = embedder_factory
        self._embedder: Embedder | None = None
        self.hard_threshold = hard_threshold
        self.ttl_seconds = ttl_seconds
        self.context_window_messages = context_window_messages
        self.max_candidates = max_candidates
        self._now = now_fn

        self._init_db()
        logger.info(
            "语义缓存初始化: db=%s, threshold=%.2f, ttl=%ss, K=%s, candidates=%s",
            database_path,
            hard_threshold,
            ttl_seconds,
            context_window_messages,
            max_candidates,
        )

    def _connect(self) -> sqlite3.Connection:
        return sqlite3.connect(self.database_path)

    def _get_embedder(self) -> Embedder:
        if self._embedder is None:
            self._embedder = self._embedder_factory()
        return self._embedder

    def _init_db(self) -> None:
        with self._connect() as conn:
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS llm_semantic_cache (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    scope_key TEXT NOT NULL,
                    query_text TEXT NOT NULL,
                    query_embedding BLOB NOT NULL,
                    response BLOB NOT NULL,
                    llm_string TEXT NOT NULL,
                    created_at REAL NOT NULL,
                    expires_at REAL NOT NULL
                )
                """
            )
            conn.execute(
                "CREATE INDEX IF NOT EXISTS idx_llm_semantic_scope_expires ON llm_semantic_cache(scope_key, expires_at)"
            )
            conn.execute(
                "CREATE INDEX IF NOT EXISTS idx_llm_semantic_scope_created ON llm_semantic_cache(scope_key, created_at)"
            )
            conn.commit()

    def _cleanup_expired(self) -> None:
        now = self._now()
        with self._connect() as conn:
            conn.execute("DELETE FROM llm_semantic_cache WHERE expires_at < ?", (now,))
            conn.commit()

    def _build_scope_key(self, prompt: str, llm_string: str) -> str:
        parsed = _try_parse_prompt(prompt)
        context_part = _build_context_part(
            parsed, context_window_messages=self.context_window_messages
        )
        base = _normalize_text(context_part) + "\n" + (llm_string or "")
        digest = hashlib.sha256(base.encode("utf-8")).hexdigest()
        return digest

    def lookup(self, prompt: str, llm_string: str) -> RETURN_VAL_TYPE | None:
        self._cleanup_expired()

        parsed = _try_parse_prompt(prompt)
        query_text = _extract_query_text(prompt, parsed)
        scope_key = self._build_scope_key(prompt, llm_string)
        require_json = _prompt_requests_json(prompt)

        try:
            query_embedding = self._get_embedder().embed_query(query_text)
        except Exception as e:
            logger.warning("语义缓存 lookup 失败：embedding 异常：%s", e)
            return None

        now = self._now()
        with self._connect() as conn:
            rows = conn.execute(
                """
                SELECT id, query_text, query_embedding, response
                FROM llm_semantic_cache
                WHERE scope_key = ? AND expires_at >= ?
                ORDER BY created_at DESC
                LIMIT ?
                """,
                (scope_key, now, self.max_candidates),
            ).fetchall()

        best_similarity = 0.0
        best_response_blob: bytes | None = None
        best_cached_query: str = ""
        best_row_id: int | None = None

        for row_id, cached_query, embedding_blob, response_blob in rows:
            cached_embedding = _deserialize_embedding(embedding_blob)
            if cached_embedding is None:
                logger.debug("语义缓存条目 embedding 反序列化失败，已跳过: id=%s", row_id)
                continue
            similarity = _cosine_similarity(query_embedding, cached_embedding)
            if similarity > best_similarity:
                best_similarity = similarity
                best_response_blob = response_blob
                best_cached_query = cached_query or ""
                best_row_id = int(row_id)

        if best_response_blob is not None and best_similarity >= self.hard_threshold:
            logger.info(
                "[语义缓存命中] sim=%.2f, query='%s...', cached='%s...'",
                best_similarity,
                query_text[:40],
                best_cached_query[:40],
            )
            try:
                val = _deserialize_return_val(best_response_blob)
                if val is None:
                    raise ValueError("invalid cached response format")
                if require_json:
                    cached_text = _return_val_text(val)
                    if not _has_json_object(cached_text):
                        if best_row_id is not None:
                            with self._connect() as conn:
                                conn.execute(
                                    "DELETE FROM llm_semantic_cache WHERE id = ?", (best_row_id,)
                                )
                                conn.commit()
                        logger.warning(
                            "语义缓存命中但输出非 JSON，已丢弃该缓存条目：id=%s",
                            best_row_id,
                        )
                        return None
                return val
            except Exception as e:
                logger.warning("语义缓存命中但反序列化失败：%s", e)
                if best_row_id is not None:
                    with self._connect() as conn:
                        conn.execute("DELETE FROM llm_semantic_cache WHERE id = ?", (best_row_id,))
                        conn.commit()
                return None

        return None

    def update(self, prompt: str, llm_string: str, return_val: RETURN_VAL_TYPE) -> None:
        if not _has_cacheable_text(return_val):
            return
        if _prompt_requests_json(prompt):
            content_text = _return_val_text(return_val)
            if not _has_json_object(content_text):
                logger.debug("语义缓存跳过：prompt 要求 JSON，但输出不包含有效 JSON")
                return

        parsed = _try_parse_prompt(prompt)
        query_text = _extract_query_text(prompt, parsed)
        scope_key = self._build_scope_key(prompt, llm_string)

        try:
            embedding = self._get_embedder().embed_query(query_text)
        except Exception as e:
            logger.warning("语义缓存 update 失败：embedding 异常：%s", e)
            return

        now = self._now()
        expires_at = now + float(self.ttl_seconds)

        embedding_blob = _serialize_embedding(embedding)
        response_blob = _serialize_return_val(return_val)

        with self._connect() as conn:
            rows = conn.execute(
                """
                SELECT id, query_embedding
                FROM llm_semantic_cache
                WHERE scope_key = ? AND expires_at >= ?
                ORDER BY created_at DESC
                LIMIT ?
                """,
                (scope_key, now, self.max_candidates),
            ).fetchall()

            for row_id, cached_embedding_blob in rows:
                cached_embedding = _deserialize_embedding(cached_embedding_blob)
                if cached_embedding is None:
                    logger.debug("语义缓存条目 embedding 反序列化失败，已跳过: id=%s", row_id)
                    continue
                similarity = _cosine_similarity(embedding, cached_embedding)
                if similarity >= self.hard_threshold:
                    conn.execute(
                        """
                        UPDATE llm_semantic_cache
                        SET response = ?, query_text = ?, created_at = ?, expires_at = ?
                        WHERE id = ?
                        """,
                        (response_blob, query_text, now, expires_at, row_id),
                    )
                    conn.commit()
                    self._cleanup_expired()
                    return

            conn.execute(
                """
                INSERT INTO llm_semantic_cache
                (scope_key, query_text, query_embedding, response, llm_string, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    scope_key,
                    query_text,
                    embedding_blob,
                    response_blob,
                    llm_string or "",
                    now,
                    expires_at,
                ),
            )
            conn.commit()

        self._cleanup_expired()

    def clear(self, **kwargs: Any) -> None:
        with self._connect() as conn:
            conn.execute("DELETE FROM llm_semantic_cache")
            conn.commit()
        logger.info("语义缓存已清空")


def _create_default_embedder() -> Embedder:
    from src.infrastructure.llm.embeddings import UnifiedEmbedder

    return UnifiedEmbedder()


def create_semantic_cache() -> SemanticLLMCache:
    """
    创建默认语义缓存实例（通过环境变量可覆写）。

    环境变量：
    - DATAPILLAR_LLM_CACHE_DB_PATH: SQLite 文件路径（默认 .semantic_cache.db）
    - DATAPILLAR_LLM_CACHE_TTL_SECONDS: TTL 秒（默认 60）
    - DATAPILLAR_LLM_CACHE_HARD_THRESHOLD: 高相似阈值（默认 0.95）
    - DATAPILLAR_LLM_CACHE_CONTEXT_WINDOW_MESSAGES: 上下文窗口 K（默认 6）
    - DATAPILLAR_LLM_CACHE_MAX_CANDIDATES: 候选数量（默认 200）
    """
    db_path = os.getenv("DATAPILLAR_LLM_CACHE_DB_PATH", ".semantic_cache.db")
    ttl_seconds = int(os.getenv("DATAPILLAR_LLM_CACHE_TTL_SECONDS", "60"))
    hard_threshold = float(os.getenv("DATAPILLAR_LLM_CACHE_HARD_THRESHOLD", "0.95"))
    context_window_messages = int(os.getenv("DATAPILLAR_LLM_CACHE_CONTEXT_WINDOW_MESSAGES", "6"))
    max_candidates = int(os.getenv("DATAPILLAR_LLM_CACHE_MAX_CANDIDATES", "200"))

    return SemanticLLMCache(
        database_path=db_path,
        embedder_factory=_create_default_embedder,
        hard_threshold=hard_threshold,
        ttl_seconds=ttl_seconds,
        context_window_messages=context_window_messages,
        max_candidates=max_candidates,
    )
