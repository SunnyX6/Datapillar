# Datapillar 后端开发规范（DTO 与异常处理统一版）

## 1. 文档定位

本规范用于统一指导 Datapillar 后端开发，是后端开发唯一执行基线。

---

## 2. 适用范围

- 适用于 `datapillar-auth`、`datapillar-studio-service`、`datapillar-common`。
- 聚焦两类核心规范：
    - DTO 分层、命名、目录、校验与复用规范
    - 异常链路、DB 转换、业务映射、Web 输出规范

---

## 3. 不可破坏边界（硬约束）

- 不改 API 路径
- 不改 HTTP 方法
- 不改请求/响应 JSON 字段名、字段层级、可空语义
- 不改统一响应壳结构：`code` / `data` / `limit` / `offset` / `total`
- 不改统一错误响应结构：`code` / `type` / `message` / `traceId`

---

## 4. 总体架构原则

### 4.1 DTO 架构原则

- 统一目录：`dto/<domain>/request|response`
- 按业务域组织，不按技术层横切组织
- Controller 只做编排，不承载业务判断

### 4.2 异常架构原则

- 链路固定：**存储异常 -> 业务异常 -> Web 映射**
- 存储层只做 SQL 语义，不做业务语义
- 业务语义映射必须在服务级 translator 单点收口

---

## 5. DTO 规范

## 5.1 目录规范（强制）

- `auth`：`auth/dto/<domain>/request|response`
- `studio-service`：`studio/dto/<domain>/request|response`
- 禁止新增 `shared/common dto` 目录
- 禁止在 `module/*/dto` 恢复散落 DTO

## 5.2 命名规范（强制）

- 入参：`*Request`
- 出参：`*Response`
- 列表项/子对象：`*Item`
- 批量操作：`*BatchRequest`
- 状态变更：`*StatusRequest`

## 5.3 文件规范（强制）

- 一个类一个文件
- 禁止静态内部类 DTO 汇总大文件
- 禁止空壳继承 DTO（仅 `extends` 无有效内容）

## 5.4 职责规范（强制）

- Request DTO：只放字段与参数校验注解（`jakarta.validation`）
- Response DTO：只放返回字段，不写业务逻辑
- DTO 禁止包含数据库实体注解

## 5.5 校验规范（强制）

- 字段级规则：DTO 注解
- 跨字段规则：`validation` 包
- Controller 禁止堆积手写参数分支校验

## 5.6 复用规范（强制）

- 语义一致直接复用已有 DTO
- 语义变化才新建 DTO
- 禁止复制“几乎相同”的 DTO

---

## 6. 异常处理规范

## 6.1 通用语义异常层（common）

统一使用 `datapillar-common` 的 concrete 异常：

- `BadRequestException`
- `UnauthorizedException`
- `ForbiddenException`
- `NotFoundException`
- `AlreadyExistsException`
- `ConflictException`
- `InternalException`
- `ServiceUnavailableException`
- `ConnectionFailedException`
- `TooManyRequestsException`
- `RequiredException`

禁止恢复 `common/exception/system/*`。

## 6.2 DB 存储异常层（common/exception/db）

统一 DB 异常模型：

- `DbStorageException`（基类）
- `DbUniqueConstraintViolationException`
- `DbForeignKeyViolationException`
- `DbNotNullViolationException`
- `DbCheckConstraintViolationException`
- `DbDataTooLongException`
- `DbDeadlockException`（retryable=true）
- `DbLockTimeoutException`（retryable=true）
- `DbConnectionFailedException`（retryable=true）
- `DbInternalException`

`DbStorageException` 必须承载：`errorCode/sqlState/constraintName`（可空）。

## 6.3 DB 转换内核职责（common/exception/db）

- 方言 converter 只做：`SQLException -> DbStorageException`
- 允许组件：`SQLExceptionConverter`、`SQLExceptionConverterFactory`、`SQLExceptionUtils`、`ConstraintNameExtractor`、方言 converter
- 禁止在 DB 转换层做业务语义区分（如邮箱/用户名）

## 6.4 业务层异常映射（服务单点）

- 业务 Service 捕获 `RuntimeException`
- 通过 `SQLExceptionUtils.translate(...)` 转 DB 异常
- 统一交给服务级 translator 做 scene 映射
- 约束匹配只允许基于 `constraintName` 精确匹配

标准模板：

```java
try {
    mapper.insert(entity);
} catch (RuntimeException re) {
    DbStorageException dbException = SQLExceptionUtils.translate(re);
    if (dbException != null) {
        throw domainExceptionTranslator.map(scene, dbException);
    }
    throw re;
}
```

## 6.5 Web 层规则

- `DatapillarRuntimeException` -> `ErrorResponse`
- 参数绑定异常 -> 字段级 400
- 未识别异常 -> 500
- `ControllerAdvice` 禁止 DB/业务语义推断

---

## 7. 业务异常目录规范

- `auth`：`auth/exception/<domain>/...`
- `studio-service`：`studio/exception/<domain>/...`
- `translator` 目录用于服务级 DB 语义映射收口（如 `StudioDbExceptionTranslator`）

禁止项：

- 禁止 `module/*/exception` 散落目录
- 禁止新增 `exception/base` 中间层
- 禁止新增 `exception/domain` 目录
- 用户通用冲突（邮箱/用户名）统一在 `user` 目录，不得按场景重复造异常类

---

## 8. 全局禁止项（强制）

- 禁止 `catch DuplicateKeyException` 做业务分支
- 禁止 `message.contains(...)` 推断业务语义
- 禁止默认 `select exists + insert` 双轨并发控制
- 禁止 DB 转换层直接抛业务异常
- 禁止新增 `Entity*` 与 `Db*` 混合语义命名异常
- 禁止在 Response DTO 写业务逻辑

---

## 9. 开发流程（执行顺序）

1. 先确认目录落点与命名是否符合本规范
2. 再确认 DTO 是否只承载契约与校验
3. 再实现 Service 异常模板与 translator 映射
4. 最后检查 Web 输出壳是否保持统一契约
5. 提交前必须完成测试与静态检查

---

## 10. 评审检查清单（PR 必检）

- DTO 是否位于 `dto/<domain>/request|response`
- DTO 命名是否符合 `Request/Response/Item`
- Response DTO 是否无业务逻辑
- 是否存在 `module/*/dto` 或 `module/*/exception` 回流
- 是否存在 `DuplicateKeyException` 业务判断
- 是否存在 `message.contains(email/username/uq_)` 语义判断
- Service 是否统一走 `translate -> translator.map(scene, dbException)`
- Web 层是否仅做异常映射而非业务推断
- 错误响应壳字段是否保持 `code/type/message/traceId`

---

## 11. 验收门禁

- DTO 契约测试通过（序列化字段与层级一致）
- DTO 校验测试通过（正常/边界/异常）
- DB 异常转换测试通过（方言与约束场景）
- translator 映射测试通过（scene + constraintName）
- 相关模块单测通过，构建通过
