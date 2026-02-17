# Datapillar API Gateway

Datapillar 统一 API 网关 - 基于 Spring Cloud Gateway 4.2.x

## 技术栈

- Spring Boot: 3.4.1
- Spring Cloud: 2024.0.0 (Moorgate)
- Spring Cloud Gateway: 4.2.5
- Java: 21

## 功能特性

- ✅ 统一路由转发
- ✅ 跨域处理（CORS）
- ✅ 请求限流（基于 Redis）
- ✅ 健康检查（Actuator）
- ✅ 日志追踪

## 路由配置

| 路径前缀 | 目标服务 | 端口 | 说明 |
|---------|---------|------|------|
| `/api/login/**` | datapillar-auth | 7001 | 登录服务 |
| `/api/auth/**` | datapillar-auth | 7001 | 认证服务 |
| `/api/studio/**` | datapillar-auth | 7001 | 统一鉴权后代理至 Studio |
| `/api/ai/**` | datapillar-auth | 7001 | 统一鉴权后代理至 AI |
| `/api/onemeta/**` | datapillar-auth | 7001 | 统一鉴权后代理至 Gravitino |

## 启动方式

```bash
# 编译
mvn clean package

# 运行
java -jar target/datapillar-api-gateway-1.0.0.jar

# 或者用 Maven
mvn spring-boot:run
```

## 健康检查

```bash
# 网关健康状态
curl http://localhost:7000/actuator/health

# 查看所有路由
curl http://localhost:7000/actuator/gateway/routes
```

## 配置说明

### 环境变量

```bash
# Redis 配置（仅用于限流）
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
REDIS_DATABASE=1
```

### 路由规则

路由规则定义在 `src/main/resources/application.yml` 中，支持动态修改。

## 开发指南

### 添加新路由

编辑 `application.yml`：

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: your-service
          uri: http://localhost:9090
          predicates:
            - Path=/api/yourpath/**
```

### 添加限流

```yaml
filters:
  - name: RequestRateLimiter
    args:
      redis-rate-limiter.replenishRate: 10
      redis-rate-limiter.burstCapacity: 20
```

## 常见问题

### Q: 跨域问题？
A: 已配置全局 CORS，允许所有来源。生产环境请修改 `allowedOrigins`。

### Q: 如何查看路由列表？
A: 访问 `http://localhost:7000/actuator/gateway/routes`

### Q: 如何禁用某个路由？
A: 在路由配置中添加 `enabled: false`

---

**版本**: 1.0.0
**更新时间**: 2025-12-08
**维护者**: Sunny
