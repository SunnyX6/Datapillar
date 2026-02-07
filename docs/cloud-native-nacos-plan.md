# Datapillar 云原生配置中心与服务注册发现规划（Nacos 3.x）

## 1. 目标
- 统一配置中心 + 服务注册发现
- 面向 K8s 原生交付，Docker 作为兜底
- 网关走服务发现（不保留静态路由）

## 2. Nacos 规范
**版本**：3.x  
**Group**：`DATAPILLAR`  
**Namespace**：`dev / prod`（需要 staging 时从 prod 模板复制）

### 账号与权限
- `admin`：仅运维使用
- `datapillar-svc`：业务服务账号（只读配置 + 监听 + 注册发现）

**权限边界**
- 配置：读 + 监听（禁止写）
- 注册发现：注册/心跳/查询
- 写配置只允许管理员账号

### 配置热更新
- 开启
- 仅用于运行时配置（开关/阈值/限流/路由）
- 启动级配置仍需重启

## 3. 配置结构规范（无兼容方案）
### DataId 规则（强制）
- `datapillar-api-gateway.yaml`
- `datapillar-auth.yaml`
- `datapillar-studio-service.yaml`
- `datapillar-ai.yaml`

### 环境隔离
- 通过 Namespace 区分环境
- 不允许在同一 Namespace 混用多环境配置

### 优先级
- Nacos 配置为主
- 环境变量只用于敏感项（密码/密钥）

### 模板位置（强制统一）
- 配置模板统一放在 `config/nacos/dev/` 与 `config/nacos/prod/`
- 该目录内容仅用于初始化 Nacos DataId，不作为运行时配置文件使用

## 4. 注册发现规范（无静态路由）
### Java 服务
- Spring Cloud Alibaba Nacos
- `spring.application.name` 必须与服务名一致
- 启动后自动注册

### Python AI 服务
- `nacos-sdk-python==3.0.3`
- 启动注册 + 周期心跳
- 监听配置更新

## 5. 网关方向（直接切正确路线）
**原则**
- 网关路由来源：Nacos 服务发现
- 禁止硬编码静态路由

**当前实现（确定）**
- Spring Cloud Gateway + Nacos Discovery

**后续支持（非当前范围）**
- Higress：仅作为未来可选升级方向，不在本轮落地范围

## 6. K8s & Docker 交付
### K8s
- Nacos 作为基础组件
- 服务通过环境变量注入 Nacos 地址/namespace/账号
- 所有服务统一接入 Nacos

### Docker
- Nacos 单机/集群
- 服务同样走 Nacos

## 7. 落地顺序（无迁移）
1) 搭建 Nacos 3.x + Namespace + 账号 + 权限
2) 完成全量配置上云（DataId 全部建立）
3) Java 服务接入 Nacos Config + Discovery
4) Python AI 服务接入 Nacos Config + Discovery
5) 网关切服务发现路由（删除静态路由）
