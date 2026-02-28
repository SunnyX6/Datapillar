# Datapillar 贡献指南

[English](./CONTRIBUTING.md) | 简体中文

感谢你愿意参与 Datapillar。

## 基本规则

- 仅提交可编译、可运行、可验证的改动。
- 文档、端口、脚本行为必须与代码保持一致。
- 不要在一个 PR 中混入无关改动。

## 开发环境

- Java：`21+`
- Maven：`3.9+`
- Python：`3.11+`（`uv`）
- Node.js：`20+`

本地一键调试：

```bash
./scripts/start-local-all.sh
```

停止服务：

```bash
./scripts/stop-local-all.sh
```

## 提交 PR 前验证

至少覆盖你改动涉及的检查：

- Java 模块：

```bash
mvn -q -pl datapillar-common,datapillar-auth,datapillar-studio-service,datapillar-api-gateway,datapillar-openlineage -am -DskipTests test-compile
```

- AI 模块：

```bash
cd datapillar-ai
uv run pytest -q
```

- 本地脚本：

```bash
bash -n scripts/start-local-all.sh
bash -n scripts/stop-local-all.sh
```

## Pull Request 规范

- 标题直接说明改动范围和目标。
- 描述必须包含：问题背景、方案说明、影响范围、验证命令与结果。
- 涉及接口、端口、配置或 README 变更时，必须同步更新文档。

## Issue 规范

- Bug：提供复现步骤、期望结果、实际结果、日志或截图。
- Feature：先说明业务问题，再说明方案和边界条件。
