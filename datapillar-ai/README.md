# datapillar-ai（AI 服务）

Datapillar 的 AI 服务子项目，基于 FastAPI + LangGraph，提供：
- ETL 多智能体工作流生成（黑板编排）
- 知识图谱查询（Neo4j）
- OpenLineage 事件接收与入库（Neo4j 元数据/血缘）
- 指标治理相关的 AI 能力

## 目录结构（开发入口）

```
src/
  app.py                     # FastAPI 入口与启动生命周期
  api/router.py              # 自动扫描 modules/ 并注册路由
  modules/                   # 业务域（etl / knowledge / openlineage / governance）
  infrastructure/            # 外部依赖与数据访问（mysql/neo4j/redis/llm/repository）
  shared/                    # 通用能力（settings/auth/logging/exceptions）
tests/                       # 单测（核心行为已覆盖）
config/                      # Dynaconf 配置（settings.toml/.secrets.toml 等）
```

## 主要路由

FastAPI 统一前缀为 `/api`，模块路由自动挂载在 `/api/ai/*`：
- `/api/ai/etl/*`：ETL 工作流生成
- `/api/ai/openlineage`：OpenLineage RunEvent 接收
- `/api/ai/openlineage/stats`：处理器统计
- `/api/ai/knowledge/*`：知识图谱查询
- `/api/ai/governance/metric/fill`：指标 AI 填写
- `/health`：健康检查

## 配置（Dynaconf）

- 配置文件：`config/settings.toml`、`config/.secrets.toml`、`config/settings.local.toml`
- 环境切换：`ENV_FOR_DYNACONF=development|production`
- 环境变量前缀：`DATAPILLAR_`（例如 `DATAPILLAR_MYSQL_HOST`）

## 模型配置（ai_model）

LLM/Embedding 的模型配置来自 MySQL 的系统表 `ai_model`：
- 数据访问：`src/infrastructure/repository/system/repository.py`（`ModelRepository`）
- 读取与默认模型选择：`src/infrastructure/llm/model_manager.py`（`ModelManager` / `model_manager`）

说明：配置层（`src/shared/config`）不再承载数据库读取逻辑，避免分层反转。

## 开发与质量门禁（uv + ruff + black）

本项目使用 `uv` 管理环境（`.venv/`）。

### 安装依赖

在本目录执行：
- `UV_CACHE_DIR=.uv-cache uv sync --extra dev`

### Ruff（含 SRP/复杂度门禁）

- `./.venv/bin/ruff check .`

### Black

- `./.venv/bin/black .`

## pre-commit（提交前自动门禁）

本仓库是 monorepo：pre-commit 配置在 Git 根目录（`Datapillar/.pre-commit-config.yaml`），并且只对 `datapillar-ai/` 生效。

在 Git 根目录执行：
- `PRE_COMMIT_HOME=.cache/pre-commit datapillar-ai/.venv/bin/pre-commit install`

手动跑一次（可选）：
- `PRE_COMMIT_HOME=.cache/pre-commit datapillar-ai/.venv/bin/pre-commit run --all-files`

## 开发约束（必须遵守）

- 新增代码必须通过 `ruff check`；复杂函数/高分支会被直接拦截（单一职责门禁）。
- 命名必须遵守 PEP8：类 `CamelCase`，函数/方法/参数 `snake_case`；函数/方法名必须“言简意赅”，最多三段式（最多 2 个下划线），由 pre-commit 强制拦截。
- 禁止新增 `per-file-ignores` 来“逃门禁”；历史豁免只能减少不能增加。
- `api/router.py` 会记录模块导入失败日志；不要再用“吞异常静默失败”的方式注册路由。

## ETL Checkpoint 与记忆（重要概念）

本项目把“可恢复执行”和“知识记忆”严格分层，避免混用导致不稳定与幻觉：

- Checkpoint（断点持久化，最权威）：LangGraph 的 `checkpointer`，用于中断恢复/断点续跑/容灾；ETL 默认使用 Redis（见 `src/modules/etl/checkpointer.py`），并在 `RunnableConfig.configurable.checkpoint_ns="etl"` 下隔离。
- 短记忆（会话内、可丢）：单次会话/单次运行内的临时信息（通常落在 `AgentState` 里），随 Checkpoint 一起持久化；不要额外复制到 prompt。
- 长记忆（跨会话、不可丢）：知识库与业务权威数据（Neo4j/Gravitino/MySQL 等），只能通过“工具层”检索回填到 state；禁止在 prompt 里散落工具能力描述或硬编码“记忆事实”。
