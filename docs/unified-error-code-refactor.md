# 统一错误码改造文档（新项目模式）

## 1. 改造目标

- 全项目只保留一套通用 `code`。
- `code` 只表达通用状态，不表达业务细节。
- 业务细节统一放到 `type` 和 `context`。
- HTTP、RPC、AI、前端全部使用同一错误结构。
- 不做任何兼容映射，不保留旧协议，不保留旧常量，当成新项目直接切换。

## 2. 强制约束（红线）

- 禁止定义第二套 `code`。
- 禁止通过异常字符串解析业务错误（例如 `CODE:tenant`、`contains("TENANT_")`）。
- 禁止保留 `ErrorConstants` / `CryptoErrorConstants` 并行使用。
- 禁止保留旧字段作为“临时兼容”（例如 `legacyCode`、`oldCode`、`cryptoCode`）。
- 禁止使用 message 文案做分支判断。

## 3. `code` 定义（唯一合法值）

> 下面是全系统唯一合法 `code` 列表，任何新需求都不能新增数值。

| code | 名称 | 语义 |
|---|---|---|
| `0` | `OK` | 请求成功 |
| `400` | `BAD_REQUEST` | 参数非法/请求非法 |
| `401` | `UNAUTHORIZED` | 未认证 |
| `403` | `FORBIDDEN` | 已认证但无权限 |
| `404` | `NOT_FOUND` | 资源不存在 |
| `405` | `METHOD_NOT_ALLOWED` | 方法不允许 |
| `409` | `CONFLICT` | 资源冲突/状态冲突/已存在 |
| `429` | `TOO_MANY_REQUESTS` | 触发限流 |
| `500` | `INTERNAL_ERROR` | 服务内部错误 |
| `502` | `BAD_GATEWAY` | 上游连接失败/网关错误 |
| `503` | `SERVICE_UNAVAILABLE` | 服务不可用/前置条件未满足 |

Java 常量定义（单一来源）：

```java
package com.sunny.datapillar.common.constant;

public final class Code {
    public static final int OK = 0;
    public static final int BAD_REQUEST = 400;
    public static final int UNAUTHORIZED = 401;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;
    public static final int METHOD_NOT_ALLOWED = 405;
    public static final int CONFLICT = 409;
    public static final int TOO_MANY_REQUESTS = 429;
    public static final int INTERNAL_ERROR = 500;
    public static final int BAD_GATEWAY = 502;
    public static final int SERVICE_UNAVAILABLE = 503;

    private Code() {
    }
}
```

## 4. 统一错误数据标准

### 4.1 统一结构

```proto
message Error {
  int32 code = 1;
  string type = 2;
  string message = 3;
  map<string, string> context = 4;
  string trace_id = 5;
  bool retryable = 6;
}
```

字段规则：
- `code`：只能取第 3 章定义值。
- `type`：机器可读业务类型（例如 `TENANT_PRIVATE_KEY_ALREADY_EXISTS`）。
- `message`：展示文案，不参与程序判断。
- `context`：结构化上下文（例如 `tenantCode`、`purpose`）。
- `trace_id`：链路追踪。
- `retryable`：是否建议重试。

### 4.2 HTTP 标准

错误响应固定结构：

```json
{
  "code": 409,
  "type": "TENANT_PRIVATE_KEY_ALREADY_EXISTS",
  "message": "私钥文件已存在",
  "context": {"tenantCode": "tenant-acme"},
  "traceId": "...",
  "retryable": false
}
```

### 4.3 RPC 标准

- RPC 业务失败必须返回 `Error`，不能依赖 `StatusRpcException` message。
- `StatusRpcException` 仅表示传输层失败（超时、断连、不可达）。

`crypto.proto` 改造示例：

```proto
message EnsureTenantKeyResult {
  oneof result {
    EnsureTenantKeyResponse data = 1;
    Error error = 2;
  }
}
```

## 5. `type` 规范（不限制数量，统一命名）

命名规则：`[A-Z0-9_]+`，全大写下划线。

首批必须落地的 `type`：
- `TENANT_PRIVATE_KEY_ALREADY_EXISTS`
- `TENANT_PUBLIC_KEY_MISSING`
- `TENANT_PRIVATE_KEY_MISSING`
- `TENANT_KEY_NOT_FOUND`
- `TENANT_KEY_INVALID`
- `PURPOSE_NOT_ALLOWED`
- `CIPHERTEXT_INVALID`
- `KEY_STORAGE_UNAVAILABLE`
- `REQUIRED`

映射规则示例：
- 私钥已存在 -> `code=409`, `type=TENANT_PRIVATE_KEY_ALREADY_EXISTS`
- 公钥缺失 -> `code=409`, `type=TENANT_PUBLIC_KEY_MISSING`
- 私钥缺失 -> `code=409`, `type=TENANT_PRIVATE_KEY_MISSING`
- 密钥不存在 -> `code=404`, `type=TENANT_KEY_NOT_FOUND`
- 参数非法 -> `code=400`, `type=CIPHERTEXT_INVALID` / `PURPOSE_NOT_ALLOWED`
- 存储不可用 -> `code=503`, `type=KEY_STORAGE_UNAVAILABLE`
- 前置条件未满足 -> `code=503`, `type=REQUIRED`

## 6. 模块改造清单（一次性替换）

### 6.1 datapillar-common

- 新增 `Code` 常量（唯一来源）。
- 新增统一 `Error` 协议定义（proto + Java 生成类）。
- 改造 `DatapillarRuntimeException`：增加 `code/type/context/retryable`。
- 改造 `ExceptionMapper`：只输出统一 `code` 与 `type`。
- 改造 `ErrorResponse`：增加 `context/traceId/retryable`。
- 删除 `ErrorConstants` 与 `CryptoErrorConstants`。

### 6.2 datapillar-auth

- `CryptoProvider`：业务错误统一返回 `Error`，禁止吞异常。
- `TenantKeyService`：按状态机直接产出 `code+type+context`。
- `LocalKeyStorage` / `ObjectStorageKeyStorage`：统一语义，不再按存储类型分裂。
- 所有异常 message 改为展示用途，不再承载机器语义。

### 6.3 datapillar-studio-service

- `AuthCryptoRpcClient` 删除字符串解析逻辑（`extractCodeMessage`、`extractTenantCode`）。
- 统一按 `code+type` 分支。
- `tenantCode` 从 `context.tenantCode` 读取。

### 6.4 datapillar-api-gateway

- 删除 `SetupStateChecker` 中本地字符串 code 常量。
- 只消费统一 `code+type`。

### 6.5 datapillar-ai

- 删除 `src/shared/web/error_constants.py` 本地错误码副本。
- 删除 `mapper.py` 对本地副本依赖，改用统一协议定义。
- `crypto_client.py` 停止手写 proto 描述符，直接使用共享 proto 生成物。

### 6.6 web/datapillar-studio

- 前端错误中心只识别统一 `code`。
- 展示文案按 `type`+i18n 映射，不依赖 message 文本匹配。

## 7. 删除清单（必须删干净）

- `datapillar-common/src/main/java/com/sunny/datapillar/common/constant/ErrorConstants.java`
- `datapillar-common/src/main/java/com/sunny/datapillar/common/constant/CryptoErrorConstants.java`
- `datapillar-ai/src/shared/web/error_constants.py`
- 所有 `contains("TENANT_")`、`split(":")`、`indexOf("TENANT_")` 异常字符串解析逻辑

## 8. 测试与验收标准

必须全部通过：

1. 同租户重复初始化：返回 `409 + TENANT_PRIVATE_KEY_ALREADY_EXISTS + context.tenantCode`。
2. RPC 链路不再出现 `UNKNOWN : ???????`。
3. 项目中不存在旧错误码常量引用。
4. 消费端不存在异常字符串解析分支。
5. 用例覆盖：
   - `TenantKeyServiceTest`
   - `LocalKeyStorageTest`
   - `CryptoProviderTest`
   - `TenantServiceImplTest`
   - `AuthCryptoRpcClientTest`
   - AI 异常映射与 RPC 客户端测试

## 9. 实施顺序（强制）

1. 先改 `datapillar-common`（定义标准）。
2. 再改 Provider（`datapillar-auth`）。
3. 再改 Consumer（`studio-service`、`ai`、`gateway`、前端）。
4. 最后全仓清理旧常量与旧逻辑并跑全量测试。

