<h1 align="center">
  <img src="docs/assets/brand-logo.png" alt="Datapillar Logo" width="56" align="absmiddle" />
  Datapillar
</h1>

<p align="center">
  <a href="./README.md">English</a> | <a href="./README.zh-CN.md">简体中文</a>
</p>

<p align="center">
  <strong>An <code>Agentic</code> <code>ETL</code> data development platform powered by data governance and RAG</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/METADATA_GOVERNANCE-111827?style=for-the-badge&logoColor=white" alt="Metadata Governance" />
  <img src="https://img.shields.io/badge/ZERO--ETL_ORCHESTRATION-111827?style=for-the-badge&logoColor=white" alt="Zero-ETL Orchestration" />
  <img src="https://img.shields.io/badge/AI_ANALYTICS-111827?style=for-the-badge&logoColor=white" alt="AI Analytics" />
  <img src="https://img.shields.io/github/actions/workflow/status/SunnyX6/Datapillar/ci.yml?branch=main&style=for-the-badge&label=CI" alt="CI" />
  <a href="./LICENSE"><img src="https://img.shields.io/github/license/SunnyX6/Datapillar?style=for-the-badge&label=LICENSE" alt="License" /></a>
</p>

<p align="center">
  <img src="docs/assets/demo-en.gif" alt="Datapillar Demo" width="980" />
</p>

## What Datapillar Solves

- Build data development workflows with governance and AI in one platform.
- Keep metadata, lineage graph, and semantic assets synchronized across services.
- Provide a local-debug-first workflow for rapid iteration on multi-service changes.

## Core Capabilities

- Metadata governance based on Datapillar Gravitino (customized from Apache Gravitino).
- Agentic ETL and workflow execution with SQL-centric development.
- OpenLineage ingestion and graph persistence for lineage/knowledge analysis.
- RAG-ready AI service with vector retrieval and SQL summary/embedding processing.
- Multi-service local startup script for full-stack debugging.

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
- Datapillar Gravitino (customized extensions based on Apache Gravitino)

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
- `datapillar-openlineage` (7004)

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

## Module Responsibilities

- `datapillar-api-gateway`: API ingress, routing, assertion relay, and traffic control.
- `datapillar-auth`: Authentication, tenant identity, and security assertion issuing.
- `datapillar-studio-service`: Core business domain (tenant/workflow/invitation and studio APIs).
- `datapillar-ai`: AI-side orchestration, model config access, and RAG/agent runtime.
- `datapillar-openlineage`: OpenLineage event ingest, async task dispatch, and storage write.
- `datapillar-gravitino`: Datapillar-specific metadata governance and tenant-scoped extensions.

## Troubleshooting (Local Debug)

- Port conflict (`7000`~`7004`): run `./scripts/stop-local-all.sh`, then retry startup.
- Nacos sync/auth issues: verify `NACOS_SERVER_ADDR`, `NACOS_NAMESPACE`, `NACOS_USERNAME`, `NACOS_PASSWORD` in `scripts/start-local-all.sh`.
- Build/start failure: inspect `/tmp/datapillar-logs/*.startup.log`.
- Service reachable but frontend errors: verify gateway at `http://localhost:7000` and frontend at `http://localhost:3001`.

## Contributing

Please read [CONTRIBUTING.md](./CONTRIBUTING.md) before opening issues or pull requests.

## License

Licensed under the [Apache License 2.0](./LICENSE).

## Upstream References

- [Apache Gravitino](https://gravitino.apache.org/)
- [Apache Flink](https://flink.apache.org/)
- [OpenLineage](https://openlineage.io/)
- [Apache Dubbo](https://dubbo.apache.org/)
- [Nacos](https://nacos.io/en-us/)
- [Milvus](https://milvus.io/)
- [Neo4j](https://neo4j.com/)
