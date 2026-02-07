# Datapillar Coding 规划（统一密码 Argon2id）

目标：在 Studio 单产品架构下，统一认证安全基线，密码哈希切换为 Argon2id，AI 服务最后接入。

---

## 0. 约束与共识
- Studio 已有租户隔离（请求头 + MyBatis 多租户拦截）。
- 密钥初始化在 **studio-service**，AI 服务只消费。
- 私钥存储支持 **local / object(S3 兼容)**，默认 local，路径/对象存储参数均可通过 yml 配置。
- Seed 中可写入公钥，但必须保证对应私钥已落到配置路径。

---

## 1. 阶段一：认证与租户基线清理（不动业务）
**目的**：确保租户上下文与认证链路清晰、可控。

**工作项**
- 统一强制 `X-Tenant-Id`（缺失直接拒绝）。
- 清理任何“认证关闭兜底用户”逻辑。
- 统一认证失败响应规范。

**验收**
- 任意请求缺少租户头直接 401。
- 本地/生产无隐式“默认用户”。

---

## 2. 阶段二：密码哈希切换到 Argon2id（核心）
**目的**：统一密码存储算法并具备升级路线。

**工作项**
- 认证服务切换到 **Argon2id**（参数需固定并可配置）。
- **不兼容旧 bcrypt**：切换时强制用户重置密码。
- 更新 seed 用户密码为 Argon2id 产物。
- 更新文档中的密码策略与算法说明。

**验收**
- 新创建/重置密码全部为 Argon2id。
- 旧 bcrypt 密码不可继续使用（需重置）。
- 数据库只允许 Argon2id。

---

## 3. 阶段三：租户密钥初始化（studio-service）
**目的**：为所有 API Key 加解密提供基础设施。

**工作项**
- 租户创建完成后自动生成 RSA 密钥对。
- 公钥写 `tenants.encrypt_public_key`。
- 私钥写入 KeyStorage：
  - local：`{base_path}/{tenant_id}/private.pem`
  - object：`{prefix}/{tenant_id}/private.pem`
- 密钥生成或持久化失败 → 终止创建并回滚事务（不产生半成功租户）。

**验收**
- 新租户创建后立即可用加密能力。
- 未生成密钥的租户不可用（可检测）。

---

## 4. 阶段四：数据隔离补齐（AI 相关）
**目的**：补齐 AI 相关表与查询的租户隔离。

**工作项**
- `ai_model` 全链路写读过滤 `tenant_id`。
- API 文档明确 `tenant_id` 不回传但强制隔离。
- 删除/替换任何旧 DDL 引用，统一 `datapillar_studio_schema.sql`。

**验收**
- 跨租户无法读取/写入模型数据。

---

## 5. 阶段五：AI 服务接入（最后）
**目的**：在安全底座完成后再接入 AI。

**工作项**
- connect 接口仅接受明文，服务端加密落库。
- `api_key` 只返回 `has_api_key`，禁止明文回传。
- 日志禁止输出明文/密钥。

**验收**
- 数据库仅存在 `ENCv1` 密文。
- 日志无敏感明文。

---

## 6. 里程碑顺序（最终）
1) 认证与租户基线清理  
2) 密码哈希切 Argon2id  
3) 租户密钥初始化  
4) AI 数据隔离补齐  
5) AI 服务接入

---

## 7. 目录结构规划（Key Storage 抽象）

### Studio-service（Java）
```
datapillar-studio-service/src/main/java/com/sunny/datapillar/studio
  config/
    KeyStorageProperties.java
    KeyStorageConfig.java
  security/
    keystore/
      KeyStorage.java
      impl/
        LocalKeyStorage.java
        ObjectStorageKeyStorage.java
  module/tenant/
    service/
      impl/
        TenantServiceImpl.java

datapillar-common/src/main/java/com/sunny/datapillar/common
  utils/
    KeyCryptoUtil.java
    JwtUtil.java
```

### datapillar-ai（Python）
```
src/
  shared/
    config/
  infrastructure/
    keystore/
      base.py
      local.py
      object_store.py
    crypto/
      key_crypto.py
```

### 职责边界（必须遵守）
- **KeyStorage** 只做存取，不做加解密。
- **KeyCryptoUtil** 只做算法，不关心存储。
- **TenantService** 在创建租户时完成密钥对生成、公钥落库、私钥入存储。
- **AI 服务** 只读取/解密使用，不参与生成。

### 配置约定（示例）
```yaml
security:
  key-storage:
    type: local # local | object
    local:
      path: /data/datapillar/privkeys
    object:
      endpoint: http://localhost:9000
      bucket: datapillar-keys
      access-key: minio_access_key
      secret-key: minio_secret_key
      region: us-east-1
      prefix: privkeys
```
