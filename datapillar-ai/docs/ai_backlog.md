# Datapillar AI 服务待办（Backlog）

> 目标：统一 AI 相关能力在 datapillar-ai，优先落地模型管理页面（大模型 CRUD）。

## 0. 范围与原则
- 所有新接口必须返回 ApiResponse（与 Studio 端一致）。
- REST 风格：资源化路径 + 标准方法（GET/POST/PATCH/DELETE）。
- 先对齐数据模型与接口字段，再改前端（不做假设）。

## 1. P0（优先）模型管理页面（大模型 CRUD）

### 1.1 现有前端页面现状（已存在静态数据）
- 页面入口：`web/datapillar-studio/src/pages/llm/ModelManagementPage.tsx`
- 页面 UI 与字段：`web/datapillar-studio/src/layouts/llm/ModelManagementView.tsx`
- 前端模型类型：`web/datapillar-studio/src/layouts/llm/types.ts`

### 1.2 模型表结构（最终以 docs 为准）
**最终字段来源**：`../docs/db/datapillar_studio_schema.sql`（唯一来源）
- 核心字段：`tenant_id / model_id / name / provider_id / model_type / description / tags / context_tokens / input_price_usd / output_price_usd / embedding_dimension / api_key / base_url / status / created_by / updated_by`

**需要同步的代码位置**：
- `datapillar-ai/src/infrastructure/repository/system/ai_model.py`
- DTO / API 请求响应结构

### 1.3 模型管理功能范围（页面能力）
1) **列表**：分页、关键词搜索、按供应商/类型/上下文长度筛选
2) **详情**：查看模型配置与价格信息
3) **新增**：创建模型（包含基础配置 + 价格 + 维度）
4) **编辑**：更新模型配置
5) **删除**：删除模型（需确认弹窗）
6) **连接验证**：保存 API Key 前必须验证联通，成功后状态变更
7) **对比**：最多 3 个模型对比（前端已有 UI 逻辑）

### 1.4 模型管理 API 设计（REST + ApiResponse）
> 路由前缀：`/api/ai/llm_manager/models`（已确认）

**GET /api/ai/llm_manager/models**
- Query: `limit, offset, keyword, provider, model_type`
- Response: `ApiResponse<Model[]>` + `limit/offset/total`

**GET /api/ai/llm_manager/models/{id}**
- Response: `ApiResponse<Model>`

**POST /api/ai/llm_manager/models**
- Request: `CreateModelRequest`
- Response: `ApiResponse<Model>`

**PATCH /api/ai/llm_manager/models/{id}**
- Request: `UpdateModelRequest`
- Response: `ApiResponse<Model>`

**DELETE /api/ai/llm_manager/models/{id}**
- Response: `ApiResponse<{ deleted: number }>`

**POST /api/ai/llm_manager/models/{id}/connect**
- Request: `{ api_key, base_url? }`
- Response: `ApiResponse<{ connected: boolean, has_api_key: boolean }>`

> 模型字段以最终 ai_model 结构为准。当前前端表单字段至少包含：`name / provider / baseUrl / description / modelType`（见 `ModelManagementView`）。

### 1.5 前端接入步骤（逐步修改）
1) **新增 API Service**：`web/datapillar-studio/src/services/llmModelService.ts`（对接 ApiResponse）
2) **替换静态模型**：移除 `MODEL_RECORDS`，改为 API 拉取
3) **分页/筛选联动**：filters + search 对接后端参数
4) **CRUD 表单**：Create/Update 接口联动，错误统一从 ApiResponse.message 取
5) **连接/删除**：Connect 成功后状态变更（CONNECT/ACTIVE）
6) **测试与校验**：补前端单测与后端接口测试

---

## 2. P1（高优）AI 接口契约统一（前后端）
> 目标：AI 相关接口全部收敛到 datapillar-ai，统一 ApiResponse。

1) **统一 RAG 路由前缀**：`/api/ai/knowledge/wiki`（前端已固定该前缀）
2) **知识图谱接口返回 ApiResponse**：`/api/ai/knowledge/initial`、`/api/ai/knowledge/search`
3) **指标 AI 接口返回 ApiResponse**：`/api/ai/governance/metric/fill`
4) **ETL 工作流 REST 化**：保持 SSE 但统一会话/运行路径（保留旧路径兼容）

---

## 3. P2（中优）配套能力与质量
1) **OpenAPI 文档**：补齐 `/api/ai/*` 全量接口
2) **权限与审计**：对模型 CRUD 统一权限校验与审计日志
3) **配置加密**：API Key 等敏感字段不明文回传
