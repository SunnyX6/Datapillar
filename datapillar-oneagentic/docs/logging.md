# Logging Design (JSON-Only)

## Goals
- Use structured JSON logs for all environments.
- Keep logs consistent, searchable, and framework-wide (not agent-only).
- Ensure messages are English-only and free of emojis.
- Standardize severity decisions across the framework.

## Non-Goals
- No custom logging framework. The standard library `logging` remains the API.
- No mixed text/JSON formatters.
- No large payloads or sensitive data in logs.

## Logging Framework
- Base: Python standard library `logging`
- Formatter: `python-json-logger` (JSON-only output)
- Context injection: `contextvars` + `logging.Filter` or `LoggerAdapter`

## Module Layout
Dedicated package: `datapillar_oneagentic.log`

- `log/__init__.py`
  - Public API: `setup_logging`, `set_log_context`, `clear_log_context`, `bind_log_context`
- `log/config.py`
  - JSON formatter/handler configuration and bootstrap
- `log/context.py`
  - `contextvars` storage + filter/adapter injection

## JSON Schema (Flat Structure)
Required fields:
- `timestamp`: ISO-8601 string
- `level`: `DEBUG|INFO|WARNING|ERROR`
- `logger`: logger name (module path)
- `event`: normalized event name
- `message`: short English message (verb-first)

Optional context fields:
- `namespace`
- `session_id`
- `agent_id`
- `request_id`
- `trace_id`
- `span_id`

Optional diagnostic fields:
- `duration_ms`
- `error_type`
- `error`

Extension field:
- `data`: module-specific fields only

## Event Naming
Pattern: `domain.action`

Examples:
- `agent.start`, `agent.end`, `agent.fail`
- `tool.call`, `tool.done`, `tool.fail`
- `llm.create`, `llm.call`, `llm.retry`, `llm.fail`
- `orchestrator.start`, `orchestrator.resume`, `orchestrator.fail`
- `mcp.load`, `a2a.load`, `store.op`, `context.compact`

## Log Level Decision Rules (Mandatory)
- `DEBUG`: internal details, frequent, developer-only.
- `INFO`: key lifecycle events in normal flow.
- `WARNING`: recoverable issues or degradations (flow continues).
- `ERROR`: non-recoverable failures for the current request.
- `EXCEPTION`: use only when stack trace is required; same severity as `ERROR`.

Hard rules:
- If it can auto-recover, use `WARNING`.
- If the request fails, use `ERROR` or `EXCEPTION`.

## LLM / Tool / Storage Data (Under `data`)
LLM:
- `data.provider`, `data.model`, `data.request_id`, `data.latency_ms`
- `data.usage.prompt_tokens`, `data.usage.completion_tokens`, `data.usage.total_tokens`

Tool:
- `data.tool_name`, `data.tool_type`, `data.tool_call_id`

Storage:
- `data.store_type`, `data.collection`, `data.op`

Orchestrator:
- `data.process`, `data.step`, `data.node`

## Message Style
- English-only.
- No emojis.
- Short, verb-first, consistent.
- Do not log secrets or large payloads.

## Example
```json
{
  "timestamp": "2025-01-25T15:42:11.123Z",
  "level": "INFO",
  "logger": "datapillar_oneagentic.runtime.executor",
  "event": "agent.start",
  "message": "Agent started",
  "namespace": "sales",
  "session_id": "s1",
  "agent_id": "alpha",
  "request_id": "req_8f3a",
  "trace_id": null,
  "span_id": null,
  "duration_ms": null,
  "error_type": null,
  "error": null,
  "data": {"tools": 3}
}
```

## Implementation Notes
- Provide a single `setup_logging()` entry point.
- Initialize once during config/bootstrap.
- Use `extra={"event": "...", "data": {...}}` for structured payloads.
- Inject context using context variables to avoid manual string formatting.
