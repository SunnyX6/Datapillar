# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-29
"""Knowledge tools (factory)."""

from __future__ import annotations

from typing import Any, TYPE_CHECKING, Callable

from langchain_core.tools import BaseTool
from pydantic import BaseModel, Field

from datapillar_oneagentic.knowledge import KnowledgeConfig
from datapillar_oneagentic.knowledge.config import MetadataFilterConfig
from datapillar_oneagentic.knowledge.filtering import build_auto_filters
from datapillar_oneagentic.knowledge.models import KnowledgeChunk
from datapillar_oneagentic.tools.registry import tool as register_tool
from datapillar_oneagentic.utils.prompt_format import format_markdown

if TYPE_CHECKING:
    from datapillar_oneagentic.knowledge import KnowledgeService
    from datapillar_oneagentic.providers.llm.llm import ResilientChatModel


def format_knowledge_output(hits: list[tuple[KnowledgeChunk, float]]) -> str:
    """Render knowledge retrieval results as plain text/Markdown."""
    if not hits:
        return ""

    lines: list[str] = []
    for idx, (chunk, score) in enumerate(hits, 1):
        content = (chunk.content or "").strip()
        if not content:
            continue

        lines.append(f"### Chunk {idx}")
        if chunk.doc_title or chunk.doc_id:
            lines.append(f"- Document: {chunk.doc_title or chunk.doc_id}")
        if chunk.source_id:
            lines.append(f"- Source: {chunk.source_id}")
        if chunk.chunk_id:
            lines.append(f"- Chunk ID: {chunk.chunk_id}")
        if chunk.chunk_type == "summary":
            lines.append("- Type: summary")
        if chunk.parent_id:
            lines.append(f"- Parent ID: {chunk.parent_id}")
        if chunk.section_path:
            lines.append(f"- Section: {chunk.section_path}")
        lines.append(f"- Score: {score:.4f}")
        lines.append(content)
        lines.append("")

    body = "\n".join(lines).strip()
    if not body:
        return ""
    return format_markdown(
        title="Knowledge Results",
        sections=[("Content", body)],
    )


def create_knowledge_retrieve_tool(
    *,
    knowledge_config: KnowledgeConfig,
    get_service: Callable[..., "KnowledgeService"],
    llm_provider: Callable[[], "ResilientChatModel"] | None = None,
) -> BaseTool:
    """Create the knowledge_retrieve tool bound to a knowledge config."""
    if knowledge_config is None:
        raise ValueError("knowledge_config is required")

    bound_config = knowledge_config.model_copy(deep=True)

    class _KnowledgeRetrieveInput(BaseModel):
        query: str = Field(description="User query text")
        namespaces: list[str] = Field(
            description="Knowledge namespaces to retrieve from (explicit list)",
        )
        retrieve: dict[str, Any] | None = Field(
            default=None,
            description=(
                "Retrieval overrides (method/top_k/score_threshold/rerank/tuning/quality/"
                "params/routing/expansion/context)"
            ),
        )
        filters: dict[str, Any] | None = Field(
            default=None,
            description="Metadata filters for retrieval",
        )

    @register_tool("knowledge_retrieve", args_schema=_KnowledgeRetrieveInput)
    async def knowledge_retrieve(
        query: str,
        namespaces: list[str],
        retrieve: dict[str, Any] | None = None,
        filters: dict[str, Any] | None = None,
    ) -> str:
        """Retrieve knowledge and return content."""
        service = get_service(config=bound_config)
        resolved_namespaces = _normalize_namespaces(namespaces)
        resolved_filters = filters
        if resolved_filters is None:
            filter_config = _resolve_filter_config(bound_config.retrieve, retrieve)
            resolved_filters = await build_auto_filters(
                query=query,
                config=filter_config,
                llm_provider=llm_provider if filter_config.use_llm else None,
            )
        result = await service.retrieve(
            query=query,
            namespaces=resolved_namespaces,
            retrieve=retrieve or None,
            filters=resolved_filters,
            llm_provider=llm_provider,
        )
        knowledge_text = format_knowledge_output(result.hits)
        return knowledge_text or "No relevant knowledge found."

    knowledge_retrieve.bound_namespaces = list(knowledge_config.namespaces or [])
    return knowledge_retrieve


def _resolve_filter_config(
    defaults,
    override: dict[str, Any] | None,
) -> MetadataFilterConfig:
    config = defaults.filtering if defaults and defaults.filtering else MetadataFilterConfig()
    if not override or "filtering" not in override:
        return config
    payload = dict(config.model_dump())
    override_cfg = override.get("filtering") or {}
    fields_override = override_cfg.pop("fields", None)
    for key, value in override_cfg.items():
        if value is not None:
            payload[key] = value
    if fields_override:
        payload_fields = dict(payload.get("fields") or {})
        payload_fields.update(fields_override)
        payload["fields"] = payload_fields
    return MetadataFilterConfig.model_validate(payload)


def _normalize_namespaces(namespaces: list[str]) -> list[str]:
    cleaned: list[str] = []
    seen: set[str] = set()
    for item in namespaces or []:
        value = (item or "").strip()
        if not value or value in seen:
            continue
        seen.add(value)
        cleaned.append(value)
    if not cleaned:
        raise ValueError("namespaces cannot be empty")
    return cleaned
