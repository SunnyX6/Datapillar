# Contributing to Datapillar

感谢你愿意参与 Datapillar。

## Ground Rules

- 仅提交可编译、可运行、可验证的改动。
- 文档、端口、脚本行为必须与代码保持一致。
- 不要在一个 PR 里混入无关改动。

## Development Setup

- Java: `21+`
- Maven: `3.9+`
- Python: `3.11+`（`uv`）
- Node.js: `20+`

本地一键调试：

```bash
./scripts/start-local-all.sh
```

停止服务：

```bash
./scripts/stop-local-all.sh
```

## Validation Before PR

请至少覆盖你改动涉及的检查：

- Java 模块改动：

```bash
mvn -q -pl datapillar-common,datapillar-auth,datapillar-studio-service,datapillar-api-gateway,datapillar-openlineage -am -DskipTests test-compile
```

- AI 模块改动：

```bash
cd datapillar-ai
uv run pytest -q
```

- 脚本改动：

```bash
bash -n scripts/start-local-all.sh
bash -n scripts/stop-local-all.sh
```

## Pull Request Rules

- 标题直接说明改动范围和目标。
- 描述中必须包含：问题背景、方案说明、影响范围、验证方式（命令和结果）。
- 涉及接口、端口、配置或 README 变更时，必须同步文档。

## Issue Rules

- Bug 请提供复现步骤、期望结果、实际结果、日志或截图。
- Feature 请先说明业务问题，再说明方案和边界条件。
