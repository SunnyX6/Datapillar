"""
文档指针工具

工具列表：
- resolve_doc_pointer: 解析文档/规范指针为可引用证据
"""

import json
import logging
import time
from typing import Any

import httpx
from pydantic import BaseModel, Field

from datapillar_oneagentic import tool

logger = logging.getLogger(__name__)


class ResolveDocPointerInput(BaseModel):
    """
    解析 DocPointer 的参数

    说明：
    - provider/ref 组合定义"指向哪里"，不做强制结构约束
    - 解析结果必须返回可引用证据（content + source 元信息）
    """

    provider: str = Field(..., description="文档指针提供方（例如 url/gitlab/vectordb 等）")
    ref: dict[str, Any] = Field(
        default_factory=dict, description="不透明引用（由 provider 自行定义）"
    )


async def _fetch_url_text(url: str, *, timeout_seconds: int = 10) -> str:
    """获取 URL 文本内容"""
    async with httpx.AsyncClient(
        timeout=timeout_seconds,
        follow_redirects=True,
        headers={
            "User-Agent": "DatapillarAI/etl-agent",
            "Accept": "text/plain, text/markdown, application/json, */*",
        },
    ) as client:
        resp = await client.get(url)
        resp.raise_for_status()
        return resp.text


def _apply_span(text: str, span: object) -> str:
    """应用文本截取范围"""
    if not isinstance(span, dict):
        return text
    start = span.get("start")
    end = span.get("end")
    if isinstance(start, int) and start < 0:
        start = 0
    if isinstance(end, int) and end < 0:
        end = 0
    if not isinstance(start, int):
        start = 0
    if not isinstance(end, int):
        end = None
    return text[start:end]


def _truncate(text: str, *, max_chars: int) -> tuple[str, bool]:
    """截断文本"""
    if max_chars <= 0:
        return "", True
    if len(text) <= max_chars:
        return text, False
    return text[:max_chars], True


@tool(
    "resolve_doc_pointer",
    args_schema=ResolveDocPointerInput,
)
async def resolve_doc_pointer(provider: str, ref: dict[str, Any]) -> str:
    """
    解析文档/规范指针为可引用证据

    返回：
    - status: success|error
    - source: {provider, ref}
    - content: 证据内容（可能截断）
    - retrieved_at_ms: 拉取时间

    输入示例（JSON）：
    - {"provider": "url", "ref": {"url": "https://example.com/doc"}}
    - {"provider": "inline", "ref": {"content": "规范内容...", "max_chars": 2000}}
    """
    provider_norm = (provider or "").strip().lower()
    ref = ref or {}
    max_chars = ref.get("max_chars")
    if not isinstance(max_chars, int):
        max_chars = 4000

    try:
        if provider_norm in {"inline", "text"}:
            content = ref.get("content")
            if not isinstance(content, str) or not content.strip():
                return json.dumps(
                    {
                        "status": "error",
                        "message": "inline/text 指针缺少 ref.content",
                        "source": {"provider": provider, "ref": ref},
                    },
                    ensure_ascii=False,
                )
            content = _apply_span(content, ref.get("span"))
            content, truncated = _truncate(content, max_chars=max_chars)
            return json.dumps(
                {
                    "status": "success",
                    "source": {"provider": provider, "ref": ref},
                    "content": content,
                    "content_length": len(content),
                    "truncated": truncated,
                    "retrieved_at_ms": int(time.time() * 1000),
                },
                ensure_ascii=False,
            )

        if provider_norm in {"url", "http", "https", "gitlab"}:
            url = ref.get("url") or ref.get("raw_url") or ref.get("source_url")
            if not isinstance(url, str) or not url.strip():
                return json.dumps(
                    {
                        "status": "error",
                        "message": "url 指针缺少 ref.url（或 raw_url/source_url）",
                        "source": {"provider": provider, "ref": ref},
                    },
                    ensure_ascii=False,
                )
            if not (url.startswith("http://") or url.startswith("https://")):
                return json.dumps(
                    {
                        "status": "error",
                        "message": "仅支持 http/https URL",
                        "source": {"provider": provider, "ref": ref},
                    },
                    ensure_ascii=False,
                )
            text = await _fetch_url_text(url, timeout_seconds=10)
            text = _apply_span(text, ref.get("span"))
            content, truncated = _truncate(text, max_chars=max_chars)
            return json.dumps(
                {
                    "status": "success",
                    "source": {"provider": provider, "ref": ref},
                    "content": content,
                    "content_length": len(content),
                    "truncated": truncated,
                    "retrieved_at_ms": int(time.time() * 1000),
                },
                ensure_ascii=False,
            )

        return json.dumps(
            {
                "status": "error",
                "message": f"不支持的 provider: {provider}",
                "source": {"provider": provider, "ref": ref},
            },
            ensure_ascii=False,
        )
    except httpx.HTTPError as e:
        return json.dumps(
            {
                "status": "error",
                "message": f"文档解析失败: {str(e)}",
                "source": {"provider": provider, "ref": ref},
            },
            ensure_ascii=False,
        )
    except Exception as e:
        logger.error("resolve_doc_pointer 执行失败: %s", e, exc_info=True)
        return json.dumps(
            {
                "status": "error",
                "message": str(e),
                "source": {"provider": provider, "ref": ref},
            },
            ensure_ascii=False,
        )


DOC_TOOLS = [
    resolve_doc_pointer,
]
