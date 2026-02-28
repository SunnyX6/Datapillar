<h1 align="center">
  <img src="docs/assets/brand-logo.png" alt="Datapillar Logo" width="56" align="absmiddle" />
  Datapillar
</h1>

<p align="center">
  <a href="./README.md">English</a> | <a href="./README.zh-CN.md">简体中文</a>
</p>


<p align="center">
  <strong>依托数据治理以及RAG的 <code>Agentic</code> <code>ETL</code> 数据开发平台</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/METADATA_GOVERNANCE-111827?style=for-the-badge&logoColor=white" alt="Metadata Governance" />
  <img src="https://img.shields.io/badge/ZERO--ETL_ORCHESTRATION-111827?style=for-the-badge&logoColor=white" alt="Zero-ETL Orchestration" />
  <img src="https://img.shields.io/badge/AI_ANALYTICS-111827?style=for-the-badge&logoColor=white" alt="AI Analytics" />
  <img src="https://img.shields.io/github/actions/workflow/status/SunnyX6/Datapillar/ci.yml?branch=main&style=for-the-badge&label=CI" alt="CI" />
  <a href="./LICENSE"><img src="https://img.shields.io/github/license/SunnyX6/Datapillar?style=for-the-badge&label=LICENSE" alt="License" /></a>
</p>

<p align="center">
  <img src="docs/assets/demo-zh.gif" alt="Datapillar Demo" width="980" />
</p>

## Datapillar 解决什么问题

- 在一个平台内完成“数据治理 + AI 驱动”的数据开发流程。
- 让元数据、血缘图谱、语义资产在多服务之间保持一致。
- 以本地调试优先的方式支撑多模块快速迭代。

## 核心能力

- 基于 Datapillar Gravitino（Apache Gravitino 二开）的元数据治理能力。
- 面向 SQL 的 Agentic ETL 与工作流执行能力。
- OpenLineage 事件接入、异步处理与图谱持久化能力。
- 面向 RAG 的向量检索、SQL 摘要与嵌入处理能力。
- 一键启动多服务的本地全链路调试能力。

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
- Datapillar Gravitino（基于 Apache Gravitino 的二开扩展）

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
- `datapillar-openlineage`（7004）

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

## 模块职责

- `datapillar-api-gateway`：统一入口、路由转发、断言透传与流量控制。
- `datapillar-auth`：认证鉴权、租户身份管理与安全断言签发。
- `datapillar-studio-service`：核心业务域（租户/工作流/邀请等）。
- `datapillar-ai`：AI 编排、模型配置访问与 RAG/Agent 运行时。
- `datapillar-openlineage`：OpenLineage 事件接入、异步分发与存储写入。
- `datapillar-gravitino`：Datapillar 元数据治理与租户隔离扩展能力。

## 常见问题（本地调试）

- 端口冲突（`7000`~`7004`）：先执行 `./scripts/stop-local-all.sh` 再重启。
- Nacos 同步/鉴权失败：检查 `scripts/start-local-all.sh` 中 Nacos 相关环境变量。
- 编译或启动失败：查看 `/tmp/datapillar-logs/*.startup.log`。
- 前端异常：确认网关 `http://localhost:7000` 与前端 `http://localhost:3001` 可访问。

## 参与贡献

提交 Issue 或 PR 前，请先阅读 [CONTRIBUTING.zh-CN.md](./CONTRIBUTING.zh-CN.md)。

## 许可证

本项目采用 [Apache License 2.0](./LICENSE) 开源许可证。

## 参考项目

- [Apache Gravitino](https://gravitino.apache.org/)
- [Apache Flink](https://flink.apache.org/)
- [OpenLineage](https://openlineage.io/)
- [Apache Dubbo](https://dubbo.apache.org/)
- [Nacos](https://nacos.io/en-us/)
- [Milvus](https://milvus.io/)
- [Neo4j](https://neo4j.com/)
