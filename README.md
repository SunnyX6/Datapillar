<h1 align="center">
  <img src="docs/assets/brand-logo.png" alt="Datapillar Logo" width="56" align="absmiddle" />
  Datapillar
</h1>

<p align="center">
  <a href="#en">English</a> | <a href="#zh-cn">简体中文</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/METADATA_GOVERNANCE-111827?style=for-the-badge&logoColor=white" alt="Metadata Governance" />
  <img src="https://img.shields.io/badge/ZERO--ETL_ORCHESTRATION-111827?style=for-the-badge&logoColor=white" alt="Zero-ETL Orchestration" />
  <img src="https://img.shields.io/badge/AI_ANALYTICS-111827?style=for-the-badge&logoColor=white" alt="AI Analytics" />
</p>

<a id="en"></a>

## English

<p align="center">
  <strong>An <code>Agentic</code> <code>ETL</code> data development platform powered by data governance and RAG</strong>
</p>

<p align="center">
  <img src="docs/assets/demo-en.gif" alt="Datapillar Demo EN" width="980" />
</p>

## Tech Stack

### Backend and Service Frameworks

- Java 21, Spring Boot 3, Spring Cloud Gateway
- Dubbo 3 (RPC communication)
- Nacos (configuration and service discovery)
- Python 3.11+, FastAPI (AI service)

### Data and Compute Engines

- MySQL (business DB `datapillar`, metadata DB `gravitino`)
- Redis (gateway rate limiting, sessions, and cache)
- Neo4j (data-warehouse knowledge graph and lineage graph)
- Milvus (RAG document vector retrieval)
- Apache Flink (SQL execution)
- Apache Gravitino (unified metadata management)

### Frontend and Tooling

- React 19 + TypeScript + Vite
- React Router, Zustand, Tailwind CSS
- Vitest, Playwright, ESLint, Stylelint, Prettier

## Technical Architecture

![Datapillar Technical Architecture](docs/assets4/architecture.png)

## Local Development Quick Start (Debug)

### 1. Prerequisites

- JDK 21+
- Maven 3.9+
- Python 3.11+ with `uv`
- Node.js 20+ with `npm`
- Nacos 3.x (local default `127.0.0.1:8848`)
- MySQL 8.x, Redis, Neo4j, Milvus

### 2. Start Required Dependencies

Make sure the following services are reachable locally (default ports):

- Nacos: `127.0.0.1:8848`
- MySQL: `127.0.0.1:3306`
- Redis: `127.0.0.1:6379`
- Neo4j: `127.0.0.1:7687`
- Milvus: `127.0.0.1:19530`

> The startup script auto-syncs `config/nacos/dev/DATAPILLAR/*.yaml` to Nacos (`namespace=dev`, `group=DATAPILLAR`).

### 3. Start Backend Services (One Command)

Run from project root:

```bash
./scripts/start-local-all.sh
```

> For local development/debug only. Do not use this script in production.

This script compiles and starts:

- `datapillar-auth` (7001)
- `datapillar-studio-service` (7002)
- `datapillar-api-gateway` (7000)
- `datapillar-ai` (7003)

Log directory:

```bash
/tmp/datapillar-logs
```

### 4. Start Frontend

```bash
cd web/datapillar-studio
npm install
npm run dev
```

Frontend default URL:

- `http://localhost:3001`

### 5. Stop Backend Services

```bash
./scripts/stop-local-all.sh
```

## Project Structure

```text
.
├── config/                     # Nacos templates (dev/prod)
├── docs/                       # Documentation and architecture assets
├── scripts/                    # Local start/stop scripts
├── datapillar-api-gateway/     # Gateway service (Spring Cloud Gateway)
├── datapillar-auth/            # Authentication service
├── datapillar-studio-service/  # Core business service (multi-tenant/SQL/workflow)
├── datapillar-ai/              # AI service (FastAPI/RAG/Agent)
├── datapillar-openlineage/     # OpenLineage sink service
├── datapillar-gravitino/       # Gravitino metadata extensions
└── web/datapillar-studio/      # Frontend app (React + Vite)
```

## Upstream References

- [Apache Gravitino](https://gravitino.apache.org/)
- [Apache Flink](https://flink.apache.org/)
- [OpenLineage](https://openlineage.io/)
- [Apache Dubbo](https://dubbo.apache.org/)
- [Nacos](https://nacos.io/en-us/)
- [Milvus](https://milvus.io/)
- [Neo4j](https://neo4j.com/)

---

<a id="zh-cn"></a>

## 简体中文

<p align="center">
  <strong>依托数据治理以及RAG的 <code>Agentic</code> <code>ETL</code> 数据开发平台</strong>
</p>

<p align="center">
  <img src="docs/assets/demo-zh.gif" alt="Datapillar Demo ZH" width="980" />
</p>

## 技术栈

### 后端与服务框架

- Java 21、Spring Boot 3、Spring Cloud Gateway
- Dubbo 3（RPC 通信）
- Nacos（配置中心与服务注册发现）
- Python 3.11+、FastAPI（AI 服务）

### 数据与计算引擎

- MySQL（业务库 `datapillar`、元数据库 `gravitino`）
- Redis（网关限流、会话与缓存）
- Neo4j（数仓知识库/血缘图谱）
- Milvus（RAG 文档向量检索）
- Apache Flink（SQL 执行）
- Apache Gravitino（统一元数据管理）

### 前端与工程化

- React 19 + TypeScript + Vite
- React Router、Zustand、Tailwind CSS
- Vitest、Playwright、ESLint、Stylelint、Prettier

## 技术架构

![Datapillar 技术架构图](docs/assets4/architecture.png)

## 本地开发快速开始（调试）

### 1. 环境要求

- JDK 21+
- Maven 3.9+
- Python 3.11+ 与 `uv`
- Node.js 20+ 与 `npm`
- Nacos 3.x（本地默认 `127.0.0.1:8848`）
- MySQL 8.x、Redis、Neo4j、Milvus

### 2. 启动基础依赖

先确保以下依赖已在本地可访问（默认端口）：

- Nacos: `127.0.0.1:8848`
- MySQL: `127.0.0.1:3306`
- Redis: `127.0.0.1:6379`
- Neo4j: `127.0.0.1:7687`
- Milvus: `127.0.0.1:19530`

> 启动脚本会自动将 `config/nacos/dev/DATAPILLAR/*.yaml` 同步到 Nacos（namespace=`dev`, group=`DATAPILLAR`）。

### 3. 一键启动后端服务

在项目根目录执行：

```bash
./scripts/start-local-all.sh
```

> 仅用于本地开发调试，不用于生产环境。

该脚本会自动编译并启动：

- `datapillar-auth`（7001）
- `datapillar-studio-service`（7002）
- `datapillar-api-gateway`（7000）
- `datapillar-ai`（7003）

日志目录：

```bash
/tmp/datapillar-logs
```

### 4. 启动前端

```bash
cd web/datapillar-studio
npm install
npm run dev
```

前端默认地址：

- `http://localhost:3001`

### 5. 停止后端服务

```bash
./scripts/stop-local-all.sh
```

## 项目结构

```text
.
├── config/                     # Nacos 配置模板（dev/prod）
├── docs/                       # 项目文档与架构图
├── scripts/                    # 本地一键启动/停止脚本
├── datapillar-api-gateway/     # 网关服务（Spring Cloud Gateway）
├── datapillar-auth/            # 鉴权服务
├── datapillar-studio-service/  # 核心业务服务（多租户/SQL/工作流）
├── datapillar-ai/              # AI 服务（FastAPI/RAG/Agent）
├── datapillar-openlineage/     # OpenLineage Sink 服务
├── datapillar-gravitino/       # Gravitino 元数据能力扩展
└── web/datapillar-studio/      # 前端应用（React + Vite）
```

## 参考项目

- [Apache Gravitino](https://gravitino.apache.org/)
- [Apache Flink](https://flink.apache.org/)
- [OpenLineage](https://openlineage.io/)
- [Apache Dubbo](https://dubbo.apache.org/)
- [Nacos](https://nacos.io/en-us/)
- [Milvus](https://milvus.io/)
- [Neo4j](https://neo4j.com/)
