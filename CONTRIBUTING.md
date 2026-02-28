# Contributing to Datapillar

English | [简体中文](./CONTRIBUTING.zh-CN.md)

Thanks for contributing to Datapillar.

## Ground Rules

- Only submit changes that are compilable, runnable, and verifiable.
- Keep docs, ports, and script behavior consistent with actual code.
- Do not mix unrelated changes in one pull request.

## Development Setup

- Java: `21+`
- Maven: `3.9+`
- Python: `3.11+` with `uv`
- Node.js: `20+`

Start local full-stack debug:

```bash
./scripts/start-local-all.sh
```

Stop services:

```bash
./scripts/stop-local-all.sh
```

## Validation Before PR

Run checks for affected modules at minimum:

- Java modules:

```bash
mvn -q -pl datapillar-common,datapillar-auth,datapillar-studio-service,datapillar-api-gateway,datapillar-openlineage -am -DskipTests test-compile
```

- AI module:

```bash
cd datapillar-ai
uv run pytest -q
```

- Local scripts:

```bash
bash -n scripts/start-local-all.sh
bash -n scripts/stop-local-all.sh
```

## Pull Request Rules

- Use a title that states scope and purpose directly.
- PR description must include: background, solution, impact scope, and verification commands/results.
- If APIs, ports, configs, or README are changed, update related docs in the same PR.

## Issue Rules

- For bugs, provide reproduction steps, expected result, actual result, and logs/screenshots.
- For features, describe business problem first, then proposal and boundaries.
