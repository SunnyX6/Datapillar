# Datapillar API Gateway

Datapillar unify API gateway - Based on Spring Cloud Gateway 4.2.x

## technology stack

- Spring Boot:3.4.1
- Spring Cloud:2024.0.0 (Moorgate)
- Spring Cloud Gateway:4.2.5
- Java:21

## Features

- ✅ Unified routing and forwarding
- ✅ Cross-domain processing(CORS)
- ✅ Request current limit(Based on Redis)
- ✅ health check(Actuator)
- ✅ Log tracking

## Routing configuration

| path prefix | target service | port | Description |
|---------|---------|------|------|
| `/api/login/**` | datapillar-auth | 7001 | Login service |
| `/api/auth/**` | datapillar-auth | 7001 | Authentication services |
| `/api/studio/**` | datapillar-auth | 7001 | After unified authentication,proxy to Studio |
| `/api/ai/**` | datapillar-auth | 7001 | After unified authentication,proxy to AI |

## Start mode

```bash
# compile
mvn clean package

# run
java -jar target/datapillar-api-gateway-1.0.0.jar

# Or use Maven
mvn spring-boot:run
```

## health check

```bash
# Gateway health status
curl http://localhost:7000/actuator/health

# View all routes
curl http://localhost:7000/actuator/gateway/routes
```

## Configuration instructions

### environment variables

```bash
# Redis Configuration(Only for current limiting)
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
REDIS_DATABASE=1
```

### routing rules

Routing rules are defined in `src/main/resources/application.yml` in,Support dynamic modification.## Development Guide

### Add new route

Edit `application.yml`:```yaml
spring:cloud:gateway:routes:- id:your-service
 uri:http://localhost:9090
 predicates:- Path=/api/yourpath/**
```

### Add current limit

```yaml
filters:- name:RequestRateLimiter
 args:redis-rate-limiter.replenishRate:10
 redis-rate-limiter.burstCapacity:20
```

## FAQ

### Q:Cross-domain issues?A:Globally configured CORS,allow all sources.Please modify the production environment `allowedOrigins`.### Q:How to view the route list?A:visit `http://localhost:7000/actuator/gateway/routes`

### Q:How to disable a route?A:Add in routing configuration `enabled:false`

---

**version**:1.0.0
**Update time**:2025-12-08
**maintainer**:Sunny
