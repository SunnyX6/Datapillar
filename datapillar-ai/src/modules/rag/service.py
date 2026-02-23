# @author Sunny
# @date 2026-01-28

"""RAG 知识 Wiki 服务层."""

from __future__ import annotations

import asyncio
import json
import logging
import os
import time
import uuid
from typing import Any

from datapillar_oneagentic.knowledge import (
    Knowledge,
    KnowledgeChunkConfig,
    KnowledgeChunkEdit,
    KnowledgeChunkRequest,
    KnowledgeConfig,
    KnowledgeRetrieveConfig,
    KnowledgeService,
    KnowledgeSource,
)
from datapillar_oneagentic.providers.llm.config import EmbeddingConfig
from datapillar_oneagentic.storage.config import VectorStoreConfig

from src.infrastructure.repository.rag import DocumentRepository, JobRepository, NamespaceRepository
from src.infrastructure.repository.system.ai_model import Model as AiModelRepository
from src.infrastructure.repository.system.tenant import Tenant as TenantRepository
from src.infrastructure.rpc.crypto import auth_crypto_rpc_client, is_encrypted_ciphertext
from src.modules.rag.sse import job_event_hub
from src.modules.rag.storage import StorageManager
from src.shared.config.runtime import get_knowledge_wiki_config
from src.shared.context import get_current_tenant_code, get_current_tenant_id
from src.shared.exception import (
    BadRequestException,
    ConflictException,
    InternalException,
    NotFoundException,
    ServiceUnavailableException,
)

logger = logging.getLogger(__name__)


class KnowledgeWikiService:
    def __init__(self) -> None:
        cfg = get_knowledge_wiki_config()
        self._storage = StorageManager(cfg["storage"])
        self._vector_store_cfg = VectorStoreConfig(**cfg["vector_store"])
        self._embedding_batch_size = int(cfg["embedding_batch_size"])
        self._progress_step = int(cfg["progress_step"])

    def list_namespaces(
        self,
        tenant_id: int,
        user_id: int,
        *,
        limit: int,
        offset: int,
    ) -> tuple[list[dict], int]:
        return NamespaceRepository.list_by_user(tenant_id, user_id, limit=limit, offset=offset)

    def create_namespace(self, tenant_id: int, user_id: int, payload: dict[str, Any]) -> int:
        payload = {
            "tenant_id": tenant_id,
            "namespace": payload["namespace"],
            "description": payload.get("description"),
            "created_by": user_id,
            "status": 1,
        }
        return NamespaceRepository.create(payload)

    def update_namespace(
        self,
        tenant_id: int,
        user_id: int,
        namespace_id: int,
        fields: dict[str, Any],
    ) -> int:
        return NamespaceRepository.update(namespace_id, tenant_id, user_id, fields)

    def delete_namespace(self, tenant_id: int, user_id: int, namespace_id: int) -> int:
        return NamespaceRepository.soft_delete(namespace_id, tenant_id, user_id)

    def list_documents(
        self,
        tenant_id: int,
        user_id: int,
        namespace_id: int,
        *,
        status: str | None,
        keyword: str | None,
        limit: int,
        offset: int,
    ) -> tuple[list[dict[str, Any]], int]:
        rows, total = DocumentRepository.list_by_namespace(
            namespace_id,
            tenant_id,
            user_id,
            status=status,
            keyword=keyword,
            limit=limit,
            offset=offset,
        )
        return [self._normalize_document(row) for row in rows], total

    def get_document(self, tenant_id: int, user_id: int, document_id: int) -> dict[str, Any] | None:
        doc = DocumentRepository.get(document_id, tenant_id, user_id)
        return self._normalize_document(doc) if doc else None

    def update_document(
        self,
        tenant_id: int,
        user_id: int,
        document_id: int,
        fields: dict[str, Any],
    ) -> int:
        return DocumentRepository.update(document_id, tenant_id, user_id, fields)

    async def upload_document(
        self,
        *,
        tenant_id: int,
        user_id: int,
        namespace_id: int,
        filename: str,
        content: bytes,
        title: str | None,
    ) -> dict[str, Any]:
        namespace = NamespaceRepository.get(namespace_id, tenant_id, user_id)
        if not namespace:
            raise NotFoundException("namespace 不存在")
        doc_uid = _normalize_doc_uid(_generate_doc_uid())

        storage_result = await self._storage.save(
            namespace_id=namespace_id,
            filename=filename,
            content=content,
        )
        resolved_title = title or filename
        file_type = _infer_file_type(filename)

        document_id = DocumentRepository.create(
            {
                "tenant_id": tenant_id,
                "namespace_id": namespace_id,
                "doc_uid": doc_uid,
                "title": resolved_title,
                "file_type": file_type,
                "size_bytes": storage_result.size_bytes,
                "storage_uri": storage_result.storage_uri,
                "storage_type": storage_result.storage_type,
                "storage_key": storage_result.storage_key,
                "status": "processing",
                "chunk_count": 0,
                "token_count": 0,
                "error_message": None,
                "embedding_model_id": None,
                "embedding_dimension": None,
                "chunk_mode": None,
                "chunk_config_json": None,
                "last_chunked_at": None,
                "created_by": user_id,
            }
        )

        return {
            "document_id": document_id,
            "status": "processing",
        }

    async def start_chunk_job(
        self,
        *,
        user_id: int,
        tenant_id: int,
        document_id: int,
        chunk_mode: str | None,
        chunk_config_json: dict[str, Any] | None,
    ) -> dict[str, Any]:
        document = DocumentRepository.get(document_id, tenant_id, user_id)
        if not document:
            raise NotFoundException("document 不存在")

        embedding_model_id = document.get("embedding_model_id")
        if not embedding_model_id:
            raise BadRequestException("embedding_model_id 不能为空")
        self._ensure_namespace_embedding(
            int(document["namespace_id"]),
            tenant_id,
            int(embedding_model_id),
        )
        _normalize_doc_uid(document.get("doc_uid"))

        resolved_chunk = self._resolve_chunk_config(chunk_mode, chunk_config_json)
        DocumentRepository.update(
            document_id,
            tenant_id,
            user_id,
            {
                "chunk_mode": resolved_chunk.mode,
                "chunk_config_json": json.dumps(resolved_chunk.model_dump(), ensure_ascii=False),
                "status": "processing",
                "error_message": None,
            },
        )

        job_id = self._create_job(
            int(document["namespace_id"]),
            document_id,
            tenant_id,
            user_id,
            job_type="chunk",
        )
        asyncio.create_task(
            self._run_chunk_job(
                job_id=job_id,
                namespace_id=int(document["namespace_id"]),
                document_id=document_id,
                user_id=user_id,
                tenant_id=tenant_id,
                chunk_mode=resolved_chunk.mode,
                chunk_config=resolved_chunk,
            )
        )
        return {
            "job_id": job_id,
            "status": "queued",
            "sse_url": f"/api/ai/biz/knowledge/wiki/jobs/{job_id}/sse",
        }

    def get_job(self, tenant_id: int, user_id: int, job_id: int) -> dict[str, Any] | None:
        return JobRepository.get(job_id, tenant_id, user_id)

    def list_jobs(
        self,
        tenant_id: int,
        user_id: int,
        document_id: int,
        *,
        limit: int,
        offset: int,
    ) -> tuple[list[dict[str, Any]], int]:
        return JobRepository.list_by_document(
            document_id,
            tenant_id,
            user_id,
            limit=limit,
            offset=offset,
        )

    async def list_chunks(
        self,
        *,
        user_id: int,
        tenant_id: int,
        document_id: int,
        limit: int,
        offset: int,
        keyword: str | None,
    ) -> tuple[list[dict[str, Any]], int]:
        document = DocumentRepository.get(document_id, tenant_id, user_id)
        if not document:
            raise NotFoundException("document 不存在")
        doc_uid = _normalize_doc_uid(document.get("doc_uid"))

        namespace_value = self._get_namespace_value(
            int(document["namespace_id"]), tenant_id, user_id
        )
        embedding_model = self._get_embedding_model(int(document["embedding_model_id"]), tenant_id)
        service = self._build_service(namespace=namespace_value, model=embedding_model)
        rows = await service.list_chunks(
            filters={"doc_id": doc_uid},
            limit=None,
            namespace=namespace_value,
        )
        await service.close()

        items = rows
        if keyword:
            items = [item for item in items if keyword in (item.content or "")]

        total = len(items)
        sliced = items[offset : offset + limit]
        return [self._chunk_to_dict(item) for item in sliced], total

    async def edit_chunk(
        self,
        *,
        user_id: int,
        tenant_id: int,
        chunk_id: str,
        content: str,
    ) -> dict[str, Any]:
        doc_uid = _parse_doc_uid(chunk_id)
        if not doc_uid:
            raise BadRequestException("chunk_id 格式错误")
        document = DocumentRepository.get_by_doc_uid(doc_uid, tenant_id, user_id)
        if not document:
            raise NotFoundException("document 不存在")
        namespace_id = int(document["namespace_id"])
        job_id = self._create_job(
            namespace_id,
            int(document["document_id"]),
            tenant_id,
            user_id,
            job_type="reembed",
        )
        asyncio.create_task(
            self._run_chunk_edit_job(
                job_id=job_id,
                document=document,
                chunk_id=chunk_id,
                content=content,
                tenant_id=tenant_id,
                user_id=user_id,
            )
        )
        return {
            "job_id": job_id,
            "status": "queued",
            "sse_url": f"/api/ai/biz/knowledge/wiki/jobs/{job_id}/sse",
        }

    async def delete_chunk(self, *, user_id: int, tenant_id: int, chunk_id: str) -> int:
        doc_uid = _parse_doc_uid(chunk_id)
        if not doc_uid:
            raise BadRequestException("chunk_id 格式错误")
        document = DocumentRepository.get_by_doc_uid(doc_uid, tenant_id, user_id)
        if not document:
            raise NotFoundException("document 不存在")
        namespace_value = self._get_namespace_value(
            int(document["namespace_id"]), tenant_id, user_id
        )
        embedding_model = self._get_embedding_model(int(document["embedding_model_id"]), tenant_id)
        service = self._build_service(namespace=namespace_value, model=embedding_model)
        deleted = await service.delete_chunks(chunk_ids=[chunk_id], namespace=namespace_value)
        await service.close()
        if deleted:
            DocumentRepository.update(
                int(document["document_id"]),
                tenant_id,
                user_id,
                {"chunk_count": max(int(document.get("chunk_count") or 0) - deleted, 0)},
            )
        return deleted

    async def retrieve(
        self, *, user_id: int, tenant_id: int, payload: dict[str, Any]
    ) -> dict[str, Any]:
        namespace_id = int(payload["namespace_id"])
        self._ensure_namespace_owner(tenant_id, user_id, namespace_id)
        model_id = self._resolve_namespace_embedding_model(namespace_id, tenant_id)
        embedding_model = self._get_embedding_model(model_id, tenant_id)
        namespace_value = self._get_namespace_value(namespace_id, tenant_id, user_id)
        service = self._build_service(namespace=namespace_value, model=embedding_model)

        default_retrieve = KnowledgeRetrieveConfig()
        retrieve_override: dict[str, Any] = {
            "method": payload.get("retrieval_mode") or "hybrid",
            "top_k": int(payload.get("top_k") or 5),
            "score_threshold": payload.get("score_threshold"),
        }
        if payload.get("rerank_enabled"):
            retrieve_override["rerank"] = {
                "mode": "model",
                "provider": "sentence_transformers",
                "model": payload.get("rerank_model") or default_retrieve.rerank.model,
            }
        else:
            retrieve_override["rerank"] = {"mode": "off"}

        doc_uids = self._resolve_scope_doc_uids(
            user_id=user_id,
            tenant_id=tenant_id,
            namespace_id=namespace_id,
            payload=payload,
        )
        filters = {"doc_id": doc_uids} if doc_uids else None

        start_ms = time.time()
        result = await service.retrieve(
            query=payload["query"],
            namespaces=[namespace_value],
            knowledge=Knowledge(),
            retrieve=retrieve_override,
            filters=filters,
        )
        latency_ms = int((time.time() - start_ms) * 1000)
        await service.close()

        hits = []
        for chunk, score in result.hits:
            hits.append(
                {
                    "chunk_id": chunk.chunk_id,
                    "doc_id": chunk.doc_id,
                    "doc_title": chunk.doc_title,
                    "score": score,
                    "content": chunk.content,
                    "source_spans": [
                        {
                            "page": span.page,
                            "start_offset": span.start_offset,
                            "end_offset": span.end_offset,
                            "block_id": span.block_id,
                        }
                        for span in (chunk.source_spans or [])
                    ],
                }
            )
        return {"hits": hits, "latency_ms": latency_ms}

    async def delete_document(self, *, user_id: int, tenant_id: int, document_id: int) -> int:
        document = DocumentRepository.get(document_id, tenant_id, user_id)
        if not document:
            raise NotFoundException("document 不存在")
        if document.get("doc_uid"):
            namespace_value = self._get_namespace_value(
                int(document["namespace_id"]),
                tenant_id,
                user_id,
            )
            embedding_model = self._get_embedding_model(
                int(document["embedding_model_id"]), tenant_id
            )
            service = self._build_service(namespace=namespace_value, model=embedding_model)
            await service.delete_document(doc_id=document["doc_uid"], namespace=namespace_value)
            await service.close()
        return DocumentRepository.soft_delete(document_id, tenant_id, user_id)

    async def _run_chunk_job(
        self,
        *,
        job_id: int,
        namespace_id: int,
        document_id: int,
        user_id: int,
        tenant_id: int,
        chunk_mode: str,
        chunk_config: KnowledgeChunkConfig,
    ) -> None:
        JobRepository.mark_running(job_id, tenant_id)
        job_snapshot = JobRepository.get(job_id, tenant_id, user_id)
        if job_snapshot:
            await job_event_hub.publish(job_id, _build_progress_event(job_snapshot))

        document = DocumentRepository.get(document_id, tenant_id, user_id)
        if not document:
            JobRepository.mark_error(job_id, tenant_id, "document not found")
            await job_event_hub.publish(
                job_id, _build_done_event(job_id, "error", "document not found", 0, 0, 0)
            )
            await job_event_hub.close(job_id)
            return

        try:
            embedding_model = self._get_embedding_model(
                int(document["embedding_model_id"]), tenant_id
            )
            namespace_value = self._get_namespace_value(
                int(document["namespace_id"]),
                tenant_id,
                user_id,
            )
            doc_uid = _normalize_doc_uid(document.get("doc_uid"))
            service = self._build_service(namespace=namespace_value, model=embedding_model)
            try:
                source_bytes = await self._storage.read(document["storage_uri"])
                filename = _resolve_filename(document)
                source = KnowledgeSource(
                    source=source_bytes,
                    chunk=chunk_config,
                    doc_uid=doc_uid,
                    name=document["title"],
                    source_type="doc",
                    filename=filename,
                    metadata={"namespace_id": namespace_id, "document_id": document_id},
                    source_uri=document["storage_uri"],
                )

                async def _on_progress(processed: int, total: int) -> None:
                    progress = int(processed * 100 / total) if total else 0
                    JobRepository.update_progress(
                        job_id,
                        tenant_id,
                        processed_chunks=processed,
                        total_chunks=total,
                        progress=progress,
                    )
                    job_snapshot = JobRepository.get(job_id, tenant_id, user_id)
                    if job_snapshot:
                        await job_event_hub.publish(job_id, _build_progress_event(job_snapshot))

                previews = await service.chunk(
                    KnowledgeChunkRequest(
                        sources=[source],
                        batch_size=self._embedding_batch_size,
                        progress_step=self._progress_step,
                    ),
                    namespace=namespace_value,
                    progress_cb=_on_progress,
                )
            finally:
                await service.close()

            preview = previews[0] if previews else None
            if not preview or not preview.chunks:
                raise InternalException("未生成分块")

            total_chunks = len(preview.chunks)

            DocumentRepository.update(
                document_id,
                tenant_id,
                user_id,
                {
                    "doc_uid": doc_uid,
                    "chunk_count": total_chunks,
                    "status": "indexed",
                    "error_message": None,
                    "chunk_mode": chunk_mode,
                    "chunk_config_json": json.dumps(chunk_config.model_dump(), ensure_ascii=False),
                    "last_chunked_at": _now_time_str(),
                },
            )
            JobRepository.mark_success(
                job_id,
                tenant_id,
                processed_chunks=total_chunks,
                total_chunks=total_chunks,
            )
            job_snapshot = JobRepository.get(job_id, tenant_id, user_id)
            if job_snapshot:
                await job_event_hub.publish(job_id, _build_done_event_from_job(job_snapshot))
            await job_event_hub.close(job_id)

        except Exception as exc:
            logger.error("Chunk job failed: %s", exc, exc_info=True)
            JobRepository.mark_error(job_id, tenant_id, str(exc))
            DocumentRepository.update(
                document_id,
                tenant_id,
                user_id,
                {"status": "error", "error_message": str(exc)},
            )
            job_snapshot = JobRepository.get(job_id, tenant_id, user_id)
            if job_snapshot:
                await job_event_hub.publish(job_id, _build_done_event_from_job(job_snapshot))
            else:
                await job_event_hub.publish(
                    job_id,
                    _build_done_event(job_id, "error", str(exc), 0, 0, 0),
                )
            await job_event_hub.close(job_id)

    async def _run_chunk_edit_job(
        self,
        *,
        job_id: int,
        document: dict[str, Any],
        chunk_id: str,
        content: str,
        tenant_id: int,
        user_id: int,
    ) -> None:
        JobRepository.mark_running(job_id, tenant_id)
        job_snapshot = JobRepository.get(job_id, tenant_id, user_id)
        if job_snapshot:
            await job_event_hub.publish(job_id, _build_progress_event(job_snapshot))

        try:
            embedding_model = self._get_embedding_model(
                int(document["embedding_model_id"]), tenant_id
            )
            namespace_value = self._get_namespace_value(
                int(document["namespace_id"]),
                tenant_id,
                user_id,
            )
            service = self._build_service(namespace=namespace_value, model=embedding_model)
            try:
                await service.upsert_chunks(
                    chunks=[KnowledgeChunkEdit(chunk_id=chunk_id, content=content)],
                    namespace=namespace_value,
                )
            finally:
                await service.close()

            JobRepository.mark_success(job_id, tenant_id, processed_chunks=1, total_chunks=1)
            job_snapshot = JobRepository.get(job_id, tenant_id, user_id)
            if job_snapshot:
                await job_event_hub.publish(job_id, _build_done_event_from_job(job_snapshot))
            await job_event_hub.close(job_id)
        except Exception as exc:
            logger.error("Chunk edit failed: %s", exc, exc_info=True)
            JobRepository.mark_error(job_id, tenant_id, str(exc))
            job_snapshot = JobRepository.get(job_id, tenant_id, user_id)
            if job_snapshot:
                await job_event_hub.publish(job_id, _build_done_event_from_job(job_snapshot))
            else:
                await job_event_hub.publish(
                    job_id,
                    _build_done_event(job_id, "error", str(exc), 0, 0, 0),
                )
            await job_event_hub.close(job_id)

    def _resolve_chunk_config(
        self,
        chunk_mode: str | None,
        chunk_config_json: dict[str, Any] | None,
    ) -> KnowledgeChunkConfig:
        if chunk_config_json:
            config = KnowledgeChunkConfig.model_validate(chunk_config_json)
        else:
            config = KnowledgeChunkConfig()
        if chunk_mode:
            config.mode = chunk_mode
        return config

    def _get_embedding_model(
        self,
        model_id: int,
        tenant_id: int,
        tenant_code: str | None = None,
    ) -> dict[str, Any]:
        model = AiModelRepository.get_model(model_id, tenant_id)
        if not model:
            raise BadRequestException("embedding model 不存在")
        if model.get("model_type") != "embeddings":
            raise BadRequestException("embedding model 类型错误")
        if model.get("status") != "ACTIVE":
            raise BadRequestException("embedding model 未启用")
        encrypted_key = model.get("api_key")
        if not encrypted_key:
            raise BadRequestException("embedding model api_key 未配置")
        if not is_encrypted_ciphertext(encrypted_key):
            raise BadRequestException("embedding model api_key 加密格式无效")
        if not model.get("embedding_dimension"):
            raise BadRequestException("embedding_dimension 未配置")
        resolved_tenant_code = self._resolve_tenant_code(tenant_id, tenant_code)
        try:
            decrypted_key = auth_crypto_rpc_client.decrypt_llm_api_key_sync(
                tenant_code=resolved_tenant_code,
                ciphertext=encrypted_key,
            )
        except Exception as exc:
            raise ServiceUnavailableException(
                "embedding model api_key 解密失败", cause=exc
            ) from exc
        normalized = dict(model)
        normalized["api_key"] = decrypted_key
        return normalized

    def _resolve_tenant_code(self, tenant_id: int, tenant_code: str | None) -> str:
        normalized_input = str(tenant_code or "").strip()
        if normalized_input:
            return normalized_input

        scope_tenant_code = str(get_current_tenant_code() or "").strip()
        scope_tenant_id = get_current_tenant_id()
        if scope_tenant_code and scope_tenant_id == tenant_id:
            return scope_tenant_code

        resolved = TenantRepository.get_code(tenant_id)
        if resolved:
            return resolved
        raise BadRequestException("tenant_code 不存在")

    def _build_knowledge_config(
        self,
        model: dict[str, Any],
        *,
        retrieve_config: KnowledgeRetrieveConfig | None = None,
    ) -> KnowledgeConfig:
        embedding = EmbeddingConfig(
            provider=model.get("provider_code"),
            api_key=model.get("api_key"),
            model=model.get("model_id"),
            base_url=model.get("base_url"),
            dimension=int(model.get("embedding_dimension")),
        )
        return KnowledgeConfig(
            embedding=embedding,
            vector_store=self._vector_store_cfg,
            retrieve=retrieve_config or KnowledgeRetrieveConfig(),
        )

    def _build_service(
        self,
        *,
        namespace: str,
        model: dict[str, Any],
        retrieve_config: KnowledgeRetrieveConfig | None = None,
    ) -> KnowledgeService:
        config = self._build_knowledge_config(model, retrieve_config=retrieve_config)
        return KnowledgeService(namespace=namespace, config=config)

    def _normalize_document(self, doc: dict[str, Any] | None) -> dict[str, Any] | None:
        if not doc:
            return None
        normalized = dict(doc)
        raw_config = normalized.get("chunk_config_json")
        if isinstance(raw_config, str):
            try:
                normalized["chunk_config_json"] = json.loads(raw_config)
            except json.JSONDecodeError:
                normalized["chunk_config_json"] = None
        normalized.pop("embedding_model_id", None)
        normalized.pop("embedding_dimension", None)
        return normalized

    def _ensure_namespace_owner(self, tenant_id: int, user_id: int, namespace_id: int) -> None:
        namespace = NamespaceRepository.get(namespace_id, tenant_id, user_id)
        if not namespace:
            raise NotFoundException("namespace 不存在")

    def _get_namespace_value(self, namespace_id: int, tenant_id: int, user_id: int) -> str:
        namespace = NamespaceRepository.get(namespace_id, tenant_id, user_id)
        if not namespace:
            raise NotFoundException("namespace 不存在")
        return namespace["namespace"]

    def _ensure_namespace_embedding(
        self,
        namespace_id: int,
        tenant_id: int,
        embedding_model_id: int,
    ) -> None:
        model_ids = DocumentRepository.list_namespace_embedding_models(namespace_id, tenant_id)
        if model_ids and embedding_model_id not in model_ids:
            raise ConflictException("namespace embedding_model_id 冲突")

    def _resolve_namespace_embedding_model(self, namespace_id: int, tenant_id: int) -> int:
        model_ids = DocumentRepository.list_namespace_embedding_models(namespace_id, tenant_id)
        if not model_ids:
            raise BadRequestException("namespace 未配置 embedding_model")
        return model_ids[0]

    def _resolve_scope_doc_uids(
        self,
        *,
        user_id: int,
        tenant_id: int,
        namespace_id: int,
        payload: dict[str, Any],
    ) -> list[str] | None:
        doc_ids: list[Any] = []
        search_scope = payload.get("search_scope")
        if search_scope and search_scope != "all":
            doc_ids.append(search_scope)
        if payload.get("document_ids"):
            doc_ids.extend(payload["document_ids"])
        if not doc_ids:
            return None

        doc_uids: list[str] = []
        for item in doc_ids:
            if isinstance(item, int) or (isinstance(item, str) and item.isdigit()):
                document = DocumentRepository.get(int(item), tenant_id, user_id)
                if (
                    document
                    and int(document["namespace_id"]) == namespace_id
                    and document.get("doc_uid")
                ):
                    doc_uids.append(document["doc_uid"])
            elif isinstance(item, str):
                document = DocumentRepository.get_by_doc_uid(item, tenant_id, user_id)
                if (
                    document
                    and int(document["namespace_id"]) == namespace_id
                    and document.get("doc_uid")
                ):
                    doc_uids.append(document["doc_uid"])
        return list({uid for uid in doc_uids if uid})

    def _chunk_to_dict(self, chunk) -> dict[str, Any]:
        return {
            "chunk_id": chunk.chunk_id,
            "doc_id": chunk.doc_id,
            "doc_title": chunk.doc_title,
            "content": chunk.content,
            "token_count": 0,
            "updated_at": chunk.updated_at,
            "embedding_status": "synced",
        }

    def _create_job(
        self,
        namespace_id: int,
        document_id: int,
        tenant_id: int,
        user_id: int,
        job_type: str,
    ) -> int:
        return JobRepository.create(
            {
                "tenant_id": tenant_id,
                "namespace_id": namespace_id,
                "document_id": document_id,
                "job_type": job_type,
                "status": "queued",
                "progress": 0,
                "progress_seq": 0,
                "total_chunks": 0,
                "processed_chunks": 0,
                "error_message": None,
                "started_at": None,
                "finished_at": None,
                "created_by": user_id,
            }
        )


def _infer_file_type(filename: str) -> str:
    ext = os.path.splitext(filename)[1].lstrip(".")
    return ext or "txt"


def _generate_doc_uid() -> str:
    return f"doc_{uuid.uuid4().hex}"


def _normalize_doc_uid(doc_uid: str | None) -> str:
    if doc_uid is None:
        raise BadRequestException("doc_uid 不能为空")
    value = str(doc_uid).strip()
    if not value:
        raise BadRequestException("doc_uid 不能为空")
    if ":" in value:
        raise BadRequestException("doc_uid 不能包含 ':'")
    if len(value) > 64:
        raise BadRequestException("doc_uid 长度不能超过 64")
    return value


def _parse_doc_uid(chunk_id: str) -> str | None:
    if ":" not in chunk_id:
        return None
    return chunk_id.split(":", 1)[0]


def _resolve_filename(document: dict[str, Any]) -> str:
    title = document.get("title") or "document"
    file_type = document.get("file_type") or "txt"
    if "." in title:
        return title
    return f"{title}.{file_type}"


def _build_progress_event(job: dict[str, Any]) -> dict[str, Any]:
    payload = {
        "job_id": job["job_id"],
        "status": job["status"],
        "progress": job["progress"],
        "total_chunks": job["total_chunks"],
        "processed_chunks": job["processed_chunks"],
        "progress_seq": job["progress_seq"],
    }
    return {
        "event": "progress",
        "id": str(job["progress_seq"]),
        "data": json.dumps(payload, ensure_ascii=False),
    }


def _build_done_event(
    job_id: int, status: str, message: str, progress: int, processed: int, total: int
):
    payload = {
        "job_id": job_id,
        "status": status,
        "progress": progress,
        "processed_chunks": processed,
        "total_chunks": total,
        "message": message,
    }
    return {
        "event": "done",
        "id": str(int(time.time() * 1000)),
        "data": json.dumps(payload, ensure_ascii=False),
    }


def _build_done_event_from_job(job: dict[str, Any]) -> dict[str, Any]:
    payload = {
        "job_id": job["job_id"],
        "status": job["status"],
        "progress": job["progress"],
        "processed_chunks": job["processed_chunks"],
        "total_chunks": job["total_chunks"],
    }
    return {
        "event": "done",
        "id": str(job["progress_seq"]),
        "data": json.dumps(payload, ensure_ascii=False),
    }


def _now_time_str() -> str:
    return time.strftime("%Y-%m-%d %H:%M:%S", time.gmtime())
