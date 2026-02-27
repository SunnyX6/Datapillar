# Datapillar AI 改造文档（ETL Chat 模型选择强约束版）

## 1. 问题定义

当前工作流 ChatInput 虽然允许用户选择模型，但 ETL 接口未接收模型参数，后端也未按用户选择执行，导致实际调用模型与前端选择不一致。

---

## 2. 改造目标

- ETL `/chat` 必须接收并使用前端传入模型。
- 禁止任何默认模型兜底（包括租户默认、最新激活模型）。
- SSE 事件格式保持不变。

---

## 3. 前后端契约（最终版）

### 3.1 请求接口

`POST /api/ai/biz/etl/chat`

请求体：

```json
{
  "sessionId": "session-20260226-001",
  "userInput": "把 ods.orders 同步到 dwd_order_summary，按天增量",
  "resumeValue": null,
  "model": {
    "aiModelId": 101,
    "providerModelId": "openai/gpt-4o"
  }
}
```

字段规则：

- `model` 必填。
- `model.aiModelId` 必填，必须为正整数。
- `model.providerModelId` 必填，必须为非空字符串。
- `resumeValue != null` 的续跑请求，也必须携带 `model`。

### 3.2 请求成功返回

```json
{
  "code": 0,
  "data": {
    "success": true
  }
}
```

说明：

- 前端固定使用 `GET /api/ai/biz/etl/sse?sessionId=...`。

### 3.3 SSE 返回

SSE 格式保持现状，不新增 `model` 字段。

---

## 4. 后端校验与执行规则（强约束）

1. 从网关注入身份获取 `tenant_id`、`user_id`。
2. 按 `tenant_id + user_id + ai_model_id` 查询用户可用模型授权（`ai_model_grant` + `permissions` + `ai_model` + `ai_provider`）：
   - 必须存在授权记录（防止绕过前端直接调 API）。
   - 权限状态必须启用，且权限编码不能是 `DISABLE`。
   - 授权未过期（`expires_at IS NULL OR expires_at > NOW()`）。
   - 模型类型必须是 `chat`。
   - 模型状态必须是 `ACTIVE`。
3. 校验数据库中的 `provider_model_id` 必须与请求 `model.providerModelId` 完全一致，不一致直接拒绝。
4. 构建 OneAgentic 配置时，LLM 参数必须来自该模型记录，不允许 fallback。
5. `resumeValue != null` 时，校验该 `sessionId` 对应运行上下文的绑定模型与本次 `model` 完全一致，不一致拒绝。
6. `GET /sse` 仅允许已通过 `/chat` 初始化的会话。

---

## 5. 错误码约定（建议）

- `400`：请求参数缺失/格式错误（如未传 `model`）。
- `403`：用户无该模型可用权限、授权过期、模型不可用。
- `409`：`providerModelId` 与 `aiModelId` 不一致，或续跑时模型与会话绑定模型不一致。

---

## 6. 如何区分不同租户、不同用户

### 6.1 租户隔离

- AI 网关鉴权后将 `tenant_id` 注入 `current_user`。
- ETL 团队实例按 `tenant_id + user_id + sessionId` 绑定缓存，但 `namespace` 固定为租户维度。
- 团队 `namespace` 含租户维度（`etl_team_{tenant_id}`）。

效果：不同租户的运行上下文与资源空间天然隔离。

### 6.2 用户隔离

- 会话主键不是裸 `sessionId`，而是：`{user_id}:{sessionId}`。
- 同租户下不同用户即使传入同一个 `sessionId`，也会落到不同会话流与状态。

效果：同租户用户之间会话、SSE、中断恢复互不串线。

### 6.3 权限隔离（用户可用模型）

- 模型可见性和可执行性按 `ai_model_grant` 用户授权判断，不再只按租户默认模型。
- 即：同租户不同用户，可使用模型集合可不同。

---

## 7. 兼容性说明

- 本次改造后，`/chat` 若不传 `model` 必须报错，不提供向后兜底。
- SSE 协议无需前端改造。
