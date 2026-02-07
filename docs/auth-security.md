# Datapillar 认证安全基线与落地方案

适用范围：`datapillar-auth`、`datapillar-api-gateway`（网关）

目标：
- 保护登录凭据与会话，防止爆破、会话劫持、跨站请求伪造、敏感数据泄露
- 明确网关与认证服务的安全职责边界
- 提供可验证的最小安全闭环（MVP）与强化路径

---

## 1. 结论先行（不绕弯）

- **网关是第一道防线，但不是全部**。网关负责传输安全与流量防护；认证服务负责凭据、会话与权限边界。
- **登录请求 payload 里能看到密码是正常现象**。客户端自己发请求，必然可见。安全重点是：HTTPS、日志不记录明文、强哈希、限流与锁定。
- **唯一方案：Cookie + JWT + 强 CSRF**。不提供替代选项。

---

## 2. 安全职责划分（边界清晰）

### 2.1 网关（入口防线）
- 强制 HTTPS（含 HSTS）
- 全局限流 / 恶意流量拦截
- 统一安全响应头（HSTS、X-Content-Type-Options 等）
- 日志脱敏（不记录请求体）
- 统一 CORS 入口（可选，若不在网关则在 auth 做）

### 2.2 认证服务（核心防线）
- 登录凭据处理（校验、哈希存储）
- 会话与 Token 管理（刷新策略、旋转、过期）
- CSRF 防护（使用 Cookie 时必须）
- 登录风控（限流/锁定/验证码）
- 安全审计日志（成功/失败、不泄露明文）

---

## 3. 强制安全要求（P0，必须做）

### 3.1 传输与会话安全
- **HTTPS 全链路**（网关/反向代理强制）
- Cookie 必须：`HttpOnly + Secure + SameSite=Strict`
- 强制开启 HSTS

**验收**：登录响应 `Set-Cookie` 包含 `HttpOnly; Secure; SameSite=Strict`

### 3.2 CSRF 防护（Cookie 方案强制）
- **双提交 CSRF Token**（必做）
- **严格校验 `Origin/Referer`**（必做）

**验收**：跨站请求无法携带 Cookie 通过

### 3.3 CORS 白名单
- 禁止 `* + allowCredentials=true`
- 只允许生产前端域名

**验收**：非白名单域名请求被拒

### 3.4 登录限流与锁定
- IP + 账号 + 租户 三维度限流
- 失败阈值触发短期锁定（指数退避）
- 统一错误提示（不区分账号不存在/密码错误）

**验收**：爆破脚本被阻断，错误信息统一

### 3.5 密码与存储
- Argon2id（推荐，内存/时间/并行度参数可配置）
- 默认参数：`memory=64MB`, `iterations=3`, `parallelism=1`（允许通过 yml 覆盖）
- 切换 Argon2id 后不兼容旧 bcrypt，需强制重置密码
- 禁止明文/可逆存储

**验收**：数据库无明文密码

### 3.6 API Key/Secret 加解密（租户级，统一方案）
- **适用对象**：API Key、Token、Access Secret（必须可逆）
- **禁用**：把 API Key 当密码哈希处理（会导致无法调用第三方）
- **租户级密钥**：
  - 公钥：`tenants.encrypt_public_key`（DB 字段）
  - 私钥：不入库，存密钥存储（可插拔）
    - 本地文件（默认）：`privkeys/{tenant_id}/private.pem`
    - 对象存储（S3/OSS/MinIO）
  - **存储选择**：通过 yml 配置选择存储类型，默认 local
  - **生成流程**：租户创建时自动生成密钥对（不允许人工手填公钥）
- **算法**：RSA-OAEP(SHA-256) + AES-GCM 混合加密
- **密文格式**：`ENCv1:<base64>`
  - base64 内容 = `enc_aes_key + nonce + tag + ciphertext`
- **加密流程**：
  1) 读取租户公钥  
  2) 生成随机 AES key/nonce  
  3) AES-GCM 加密明文  
  4) RSA-OAEP 加密 AES key  
  5) 拼装并 base64，落库
- **解密流程**：
  1) 按 `tenant_id` 取私钥  
  2) 解析 `ENCv1`  
  3) 解密 AES key → 解密明文  
  4) 明文仅内存使用，禁止日志输出
- **不做轮换**：当前不引入版本字段与迁移逻辑（后续再补）

**验收**：
- 库内字段只出现 `ENCv1:` 开头密文
- 无任何日志包含明文/密钥

### 3.7 私钥丢失与迁移（运维约束）
- 私钥丢失 = 既有密文不可恢复
- 必须具备备份/版本策略（对象存储或定时快照）
- 迁移服务器需迁移私钥存储或指向同一存储
- 丢失后只能重建密钥并要求用户重新录入 API Key

---

## 4. 强制强化要求（P1）

- Refresh Token 轮换 + 复用检测（必做）
- 登录审计日志（成功/失败、IP、UA、tenantId、userId）

---

## 5. 进阶安全要求（P2）

- MFA（高权限强制）
- 高风险操作二次验证
- 异常行为检测（异常 IP / 频率）

---

## 6. 落地顺序（回答“先做网关还是 auth”）

**结论：先网关，再 auth，并行推进。**

### 6.1 网关先行（必须先有）
1) HTTPS 强制 + HSTS
2) 全局限流 / IP 拦截
3) 日志脱敏（不记录请求体）

### 6.2 认证服务落地（必须紧随）
1) Cookie 安全（HttpOnly/Secure/SameSite）
2) CSRF 防护
3) CORS 白名单
4) 登录限流 + 锁定
5) Refresh 轮换

> 网关解决“入口安全”，auth 解决“凭据与会话安全”。缺一不可。

---

## 6.3 Studio-Service 租户密钥初始化（必须）

**结论：密钥初始化归属 studio-service（租户生命周期管理），不归 auth。**

**触发时机**：租户创建成功后（同一事务或补偿事务）

**执行流程**：
1) 创建租户记录  
2) 生成 RSA 密钥对  
3) 写入 `tenants.encrypt_public_key`  
4) 私钥写入 `privkeys/{tenant_id}/private.pem`  
5) 任一步失败 → 回滚或标记失败并阻止租户使用

---

## 7. 环境策略（不影响本地开发）

**dev 环境**
- `cookie.secure=false`（必须显式配置）
- `allowed-origins` 仅包含本地前端域名
- 限流阈值放宽但不关闭
- HSTS 关闭

**prod 环境**
- `cookie.secure=true`（必须显式配置）
- `allowed-origins` 只允许生产域名
- 限流/锁定全量启用
- HSTS 开启

---

## 8. 风险声明（必须接受）

- 任何“非 HTTPS”部署都属于高风险，不接受上线
- JWT 仅依赖签名，**无法强制下线**（除非引入服务端 Token 黑名单/轮换）
- “payload 中看见密码”是客户端可见行为，不是安全问题

---

## 9. 验收清单（上线前必须过）

- [ ] HTTPS + HSTS 生效
- [ ] `Set-Cookie` 含 `HttpOnly; Secure; SameSite`
- [ ] CORS 白名单生效
- [ ] CSRF 防护可阻断跨站请求
- [ ] 登录限流与锁定生效
- [ ] 密码仅以 Argon2id 存储
- [ ] 日志无密码明文
