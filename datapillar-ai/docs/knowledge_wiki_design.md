# 知识 Wiki 设计文档

> 说明：本文档用于记录 Wiki 的整体设计与接口协议。

## 模块位置
- 后端代码放在：`src/modules/rag`
- 路由前缀：`/api/ai/knowledge/wiki`

## 向量库与 Hybrid
- 向量库：Milvus 2.5+ Standalone（开源版）
- Hybrid 检索：使用 Milvus 原生 BM25 + Dense（RRF 融合）
- 禁止自动降级：若 Hybrid 不可用直接报错

## 附件持久化与存储
- 上传文件必须持久化到文件存储
- 支持存储后端：本地文件系统、S3 兼容对象存储
- `knowledge_document.storage_uri/storage_type/storage_key` 为后端内部字段
- 前端**不传** `storage_uri`，上传接口由后端写入存储并记录元数据
- 本地存储 `storage_uri` 必须带 `file:///` 前缀

## 数据表设计
- DDL 文件：`docs/knowledge_wiki.sql`
- 当前表：
  - knowledge_namespace（命名空间）
  - knowledge_document（文档元数据）
  - knowledge_document_job（切分/重嵌入任务进度）
- 说明：`token_count` 暂不统计，默认返回 0（后续核心功能完善再启用）
- 说明：`doc_uid` 为后端生成的稳定文档ID（必填、由 datapillar-ai 生成），用于与向量库对齐
- 约束：`doc_uid` **不得包含冒号**（`:`），长度建议 ≤ 64
- 说明：`embedding_model_id/embedding_dimension` 从 `ai_model` 选择并快照

## 切分任务进度与断线恢复（不使用 Redis）
- 实时进度：通过 SSE 推送；DB 仅做持久化与断线恢复快照
- 断线恢复流程：
  1. 前端先 `GET /jobs/{job_id}` 拉取最新快照
  2. 再建立 SSE 连接，后端先推送当前快照
  3. 任务进行中按批次更新 job 表，SSE 推最新进度
  4. `status=success/error` 后推送并关闭 SSE

- 更新策略：每处理 N 个 chunk 更新一次（建议 N=20~50）

- 进度去重：`knowledge_document_job.progress_seq` 每次更新 +1；SSE 使用该值作为 Last-Event-ID

## 统一响应协议（ApiResponse）
- 统一响应与 `datapillar-web/src/types/api.ts` 一致
- 仅 SSE 事件不使用该包装
- **列表接口的 data 直接是数组**，分页字段使用 ApiResponse 顶层的 `limit/offset/total`

**响应结构**
```json
{
  "status": 200,
  "code": "OK",
  "message": "Success",
  "data": {},
  "timestamp": "2026-01-28T12:00:00Z",
  "path": "/api/ai/knowledge/wiki/namespaces",
  "traceId": "9b8d1f...",
  "limit": 20,
  "offset": 0,
  "total": 100
}
```

**成功判定**
- `status === 200` 且 `code === "OK"`

**错误示例**
```json
{
  "status": 400,
  "code": "INVALID_PARAM",
  "message": "namespace 不能为空",
  "data": null,
  "timestamp": "2026-01-28T12:00:00Z",
  "path": "/api/ai/knowledge/wiki/namespaces",
  "traceId": "9b8d1f..."
}
```

## 接口协议（v1）
前缀：`/api/ai/knowledge/wiki`

### 1) 命名空间（Namespace）
**GET** `/namespaces?limit=&offset=`
```json
{
  "status": 200,
  "code": "OK",
  "message": "Success",
  "data": [
    {
      "namespace_id": 1,
      "namespace": "研发技术栈",
      "description": "后端架构、API 文档与运维手册",
      "status": 1,
      "created_by": 1001,
      "created_at": "2026-01-28T10:00:00Z",
      "updated_at": "2026-01-28T10:00:00Z"
    }
  ],
  "timestamp": "2026-01-28T12:00:00Z",
  "limit": 20,
  "offset": 0,
  "total": 12
}
```

**POST** `/namespaces`
```json
{
  "namespace": "产品与设计",
  "description": "PRD、UI 规范"
}
```

**PATCH** `/namespaces/{namespace_id}`
```json
{
  "description": "更新描述",
  "status": 1
}
```

**DELETE** `/namespaces/{namespace_id}`

### 2) 文档（Document）
**GET** `/namespaces/{namespace_id}/documents?status=&keyword=&limit=&offset=`
```json
{
  "status": 200,
  "code": "OK",
  "message": "Success",
  "data": [
    {
      "document_id": 10,
      "namespace_id": 1,
      "doc_uid": "doc_20260128_0001",
      "title": "Backend_Arch.md",
      "file_type": "md",
      "size_bytes": 12034,
      "status": "indexed",
      "chunk_count": 96,
      "token_count": 0,
      "error_message": null,
      "embedding_model_id": 3,
      "embedding_dimension": 1024,
      "chunk_mode": "general",
      "chunk_config_json": { "mode": "general", "general": { "max_tokens": 800, "overlap": 120 } },
      "last_chunked_at": "2026-01-28T10:05:00Z",
      "created_by": 1001,
      "created_at": "2026-01-28T10:00:00Z",
      "updated_at": "2026-01-28T10:05:00Z"
    }
  ],
  "timestamp": "2026-01-28T12:00:00Z",
  "limit": 20,
  "offset": 0,
  "total": 3
}
```

**POST** `/namespaces/{namespace_id}/documents/upload`（multipart）
- form fields：`file`、`title?`、`chunk_mode?`、`chunk_config_json?`、`auto_chunk?`
- form fields：`embedding_model_id`（必填）

响应：
```json
{
  "status": 200,
  "code": "OK",
  "message": "Success",
  "data": {
    "document_id": 10,
    "status": "processing",
    "job_id": 88,
    "sse_url": "/api/ai/knowledge/wiki/jobs/88/sse"
  },
  "timestamp": "2026-01-28T12:00:00Z"
}
```

**GET** `/documents/{document_id}`
**PATCH** `/documents/{document_id}`（仅元数据）
```json
{ "title": "新标题" }
```

**DELETE** `/documents/{document_id}`

### 3) 切分任务（Job）
**POST** `/documents/{document_id}/chunk`
```json
{
  "chunk_mode": "parent_child",
  "chunk_config_json": {
    "mode": "parent_child",
    "parent_child": {
      "parent": { "max_tokens": 800, "overlap": 120 },
      "child": { "max_tokens": 200, "overlap": 40 }
    }
  },
  "reembed": true
}
```

响应：
```json
{
  "status": 200,
  "code": "OK",
  "message": "Success",
  "data": {
    "job_id": 88,
    "status": "queued",
    "sse_url": "/api/ai/knowledge/wiki/jobs/88/sse"
  },
  "timestamp": "2026-01-28T12:00:00Z"
}
```

**GET** `/jobs/{job_id}`
```json
{
  "status": 200,
  "code": "OK",
  "message": "Success",
  "data": {
    "job_id": 88,
    "namespace_id": 1,
    "document_id": 10,
    "job_type": "chunk",
    "status": "running",
    "progress": 35,
    "total_chunks": 200,
    "processed_chunks": 70,
    "error_message": null,
    "started_at": "2026-01-28T10:01:00Z",
    "finished_at": null
  },
  "timestamp": "2026-01-28T12:00:00Z"
}
```

**GET** `/documents/{document_id}/jobs?limit=&offset=`

**SSE** `/jobs/{job_id}/sse`（不包 ApiResponse）
```
event: progress
data: {"job_id":88,"status":"running","progress":35,"processed_chunks":70,"total_chunks":200}

event: done
data: {"job_id":88,"status":"success","progress":100}
```

### 4) 切片（Chunk）
**GET** `/documents/{document_id}/chunks?limit=&offset=&keyword=`
```json
{
  "status": 200,
  "code": "OK",
  "message": "Success",
  "data": [
    {
      "chunk_id": "doc123:5",
      "doc_id": "doc123",
      "doc_title": "Backend_Arch.md",
      "content": "....",
      "token_count": 0,
      "updated_at": "2026-01-28T10:06:00Z",
      "embedding_status": "synced"
    }
  ],
  "timestamp": "2026-01-28T12:00:00Z",
  "limit": 20,
  "offset": 0,
  "total": 96
}
```

**PATCH** `/chunks/{chunk_id}`
```json
{ "content": "修正后的切片内容" }
```
响应（异步处理）：
```json
{
  "status": 200,
  "code": "OK",
  "message": "Success",
  "data": {
    "job_id": 99,
    "status": "queued",
    "sse_url": "/api/ai/knowledge/wiki/jobs/99/sse"
  },
  "timestamp": "2026-01-28T12:00:00Z"
}
```

**DELETE** `/chunks/{chunk_id}`

### 5) 召回测试（Retrieval）
**POST** `/retrieve`
说明：`retrieval_mode` 仅支持 `semantic` / `hybrid`（与后端能力对齐）
请求体：
```json
{
  "namespace_id": 1,
  "query": "这份文档的限流策略是什么？",
  "search_scope": "all",
  "document_ids": ["doc123"],
  "retrieval_mode": "semantic",
  "rerank_enabled": true,
  "rerank_model": "bge-reranker-v2",
  "top_k": 5,
  "score_threshold": 0.7
}
```

响应体：
```json
{
  "status": 200,
  "code": "OK",
  "message": "Success",
  "data": {
    "hits": [
      {
        "chunk_id": "doc123:5",
        "doc_id": "doc123",
        "doc_title": "Backend_Arch.md",
        "score": 0.92,
        "content": "Rate Limits: ...",
        "source_spans": [{"start_offset": 120, "end_offset": 240}]
      }
    ],
    "latency_ms": 342
  },
  "timestamp": "2026-01-28T12:00:00Z"
}
```

## 切分配置结构（chunk_config_json）
```json
{
  "mode": "general | parent_child | qa",
  "preprocess": ["normalize_whitespace"],
  "general": { "max_tokens": 800, "overlap": 120, "delimiter": "\n\n" },
  "parent_child": {
    "parent": { "max_tokens": 800, "overlap": 120, "delimiter": "\n\n" },
    "child": { "max_tokens": 200, "overlap": 40, "delimiter": "\n\n" }
  },
  "qa": { "pattern": "Q\\d+:\\s*(.*?)\\s*A\\d+:\\s*([\\s\\S]*?)(?=Q\\d+:|$)" }
}
```
