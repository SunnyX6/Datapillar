# 模型管理接口契约（v1）

> 目标：支撑 Studio 模型管理页面，统一 ApiResponse。
> Base Path：`/api/ai/llm_manager`

## 0. 租户与安全要求（强制）
- **所有接口必须携带 `X-Tenant-Id`**，缺失直接拒绝。
- **数据隔离**：`ai_model` 仅在当前租户范围内读写（`tenant_id` 不对外返回）。
- **API Key 加解密**：落库为密文，格式 `ENCv1:<base64>`；统一方案见 `../../docs/auth-security.md`。

## 1. 统一响应结构
```json
{
  "status": 200,
  "code": "OK",
  "message": "Success",
  "data": {},
  "timestamp": "2026-02-05T12:00:00Z",
  "path": "/api/ai/llm_manager/models",
  "traceId": "xxx",
  "limit": 20,
  "offset": 0,
  "total": 120
}
```

**成功判定**：`status === 200 && code === "OK"`  
**失败响应**：`data = null`

## 2. 供应商（Provider）
> 仅用于前端选择供应商与预填 Base URL。最终事实以 `ai_model.base_url` 为准。
> 前端选项必须由本接口返回，严格限制在后端支持的 provider 列表中。
> `model_ids` 为建议列表，允许用户自定义 `model_id`，最终以 `ai_model.model_id` 为准。

### GET /api/ai/llm_manager/providers
**Response**：`ApiResponse<Provider[]>`
```json
{
  "status": 200,
  "code": "OK",
  "message": "Success",
  "data": [
    {
      "id": 1,
      "code": "openai",
      "name": "OpenAI",
      "base_url": "https://api.openai.com/v1",
      "model_ids": ["openai/gpt-4o", "openai/text-embedding-3-large"]
    }
  ],
  "timestamp": "2026-02-05T12:00:00Z",
  "path": "/api/ai/llm_manager/providers",
  "traceId": "xxx"
}
```

## 3. 模型（Model）

### 3.1 ModelDTO
```json
{
  "id": 1,
  "model_id": "openai/gpt-4o",
  "name": "OpenAI: GPT-4o",
  "provider_id": 1,
  "provider_code": "openai",
  "provider_name": "OpenAI",
  "model_type": "chat",
  "description": "....",
  "tags": ["chat", "vision"],
  "context_tokens": 128000,
  "input_price_usd": "5.000000",
  "output_price_usd": "15.000000",
  "embedding_dimension": null,
  "base_url": "https://api.openai.com/v1",
  "status": "ACTIVE",
  "has_api_key": true,
  "created_by": 1001,
  "updated_by": 1001,
  "created_at": "2026-02-05T12:00:00Z",
  "updated_at": "2026-02-05T12:00:00Z"
}
```

**说明**
- `api_key` 不回传，只返回 `has_api_key`。
- `tenant_id` 不回传，由 `X-Tenant-Id` 决定数据范围。
- `input_price_usd/output_price_usd` 允许为空。
- 路径参数 `{id}` 为 **数值 id**。
- `status`：连接状态，`CONNECT=未连接`，`ACTIVE=已连接`。

### 3.2 GET /api/ai/llm_manager/models
**Query**
- `limit`（默认 20）
- `offset`（默认 0）
- `keyword`（匹配 `model_id`/`name`）
- `provider`（供应商 code）
- `model_type`（chat/embeddings/reranking/code）

**Response**：`ApiResponse<ModelDTO[]>` + `limit/offset/total`

### 3.3 GET /api/ai/llm_manager/models/{id}
**Response**：`ApiResponse<ModelDTO>`

### 3.4 POST /api/ai/llm_manager/models
**Request**
```json
{
  "model_id": "openai/gpt-4o",
  "name": "OpenAI: GPT-4o",
  "provider_code": "openai",
  "model_type": "chat",
  "description": "...",
  "tags": ["chat", "vision"],
  "context_tokens": 128000,
  "input_price_usd": "5.000000",
  "output_price_usd": "15.000000",
  "embedding_dimension": null,
  "base_url": "https://api.openai.com/v1"
}
```

**Response**：`ApiResponse<ModelDTO>`

### 3.5 PATCH /api/ai/llm_manager/models/{id}
**说明**
- 仅更新元数据，`api_key` **不在请求体中**。

**Request（可部分更新）**
```json
{
  "name": "OpenAI: GPT-4o (new)",
  "description": "...",
  "tags": ["chat"],
  "context_tokens": 128000,
  "input_price_usd": "4.500000",
  "output_price_usd": "13.500000",
  "embedding_dimension": null,
  "base_url": "https://api.openai.com/v1"
}
```

**Response**：`ApiResponse<ModelDTO>`

### 3.6 DELETE /api/ai/llm_manager/models/{id}
**Response**
```json
{
  "status": 200,
  "code": "OK",
  "message": "Success",
  "data": { "deleted": 1 },
  "timestamp": "2026-02-05T12:00:00Z",
  "path": "/api/ai/llm_manager/models/1",
  "traceId": "xxx"
}
```

### 3.7 连接模型（保存 API Key）
**POST /api/ai/llm_manager/models/{id}/connect**

**Request**
```json
{
  "api_key": "sk-xxx",
  "base_url": "https://api.openai.com/v1"
}
```

**规则**
- 必须先验证联通性，验证通过才写入 `api_key`。
- `base_url` 可选：不传则使用 `ai_model.base_url`。
- 验证失败：不落库。
- 连接成功：`status=ACTIVE`。
- `api_key` 仅用于服务端加密落库，禁止明文日志输出。

**Response**
```json
{
  "status": 200,
  "code": "OK",
  "message": "Connected",
  "data": { "connected": true, "has_api_key": true },
  "timestamp": "2026-02-05T12:00:00Z",
  "path": "/api/ai/llm_manager/models/1/connect",
  "traceId": "xxx"
}
```

## 4. 错误码
- `INVALID_ARGUMENT`：参数错误 / 禁止字段更新
- `RESOURCE_NOT_FOUND`：资源不存在
- `DUPLICATE_RESOURCE`：`model_id` 冲突
- `INTERNAL_ERROR`：服务异常
