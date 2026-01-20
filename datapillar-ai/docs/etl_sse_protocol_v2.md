ETL SSE 协议（v2）

本协议用于 Datapillar Web 与 Datapillar AI 的 SSE 通信。目标是让前端只处理业务过程与统一回复，不解析 agent/tool 细节。

## 总览

SSE 事件仅有两类：

- process：过程展示（中间过程）
- reply：统一回复（最终输出或等待补充）

每次用户输入对应一个 run_id，且只会产生一个 reply。

## 事件结构

### process 事件

```json
{
  "v": 2,
  "ts": 1712345678201,
  "event": "process",
  "run_id": "run-001",
  "activity": {
    "id": "phase:analysis",
    "phase": "analysis|catalog|design|develop|review",
    "status": "running|waiting|done|error|aborted",
    "actor": "需求分析师",
    "title": "解析目标与口径",
    "detail": "正在确认目标表与更新方式",
    "progress": 30
  }
}
```

说明：

- process 不包含 message 字段，避免与 reply 混淆。
- activity.id 用于前端更新同一条过程记录。

### reply 事件

```json
{
  "v": 2,
  "ts": 1712345679001,
  "event": "reply",
  "run_id": "run-001",
  "reply": {
    "status": "done|waiting|error|aborted",
    "message": "稳定回复文本（唯一展示文本）",
    "render": { "type": "workflow|ui", "schema": "v1" },
    "payload": {}
  }
}
```

说明：

- reply.message 是唯一稳定文本，前端必展示。
- reply.status=waiting 等价于 interrupt。

## 枚举值

### activity.phase

- analysis：需求分析
- catalog：元数据检索
- design：架构设计
- develop：SQL/作业生成
- review：质量评审

### activity.status

- running
- waiting
- done
- error
- aborted

### reply.status

- done
- waiting
- error
- aborted

### reply.render.type

- workflow：工作流交付（payload 为 WorkflowResponse）
- ui：交互 UI（payload 为 UI 结构）

## UI payload（render.type=ui）

### kind=form

```json
{
  "kind": "form",
  "fields": [
    { "id": "target_table", "label": "目标表", "type": "text", "required": true },
    {
      "id": "mode",
      "label": "写入模式",
      "type": "select",
      "required": true,
      "options": [
        { "label": "全量", "value": "full" },
        { "label": "增量", "value": "incremental" }
      ]
    }
  ],
  "submit": { "label": "确认并继续" }
}
```

### kind=actions

```json
{
  "kind": "actions",
  "actions": [
    { "type": "button", "label": "继续生成", "value": "confirm" },
    { "type": "link", "label": "查看数据字典", "url": "https://example.com" }
  ]
}
```

### kind=info

```json
{
  "kind": "info",
  "level": "info|warning|error",
  "items": ["提示 1", "提示 2"]
}
```

## 交互回传（resumeValue）

当 reply.status=waiting 时，前端需调用 /workflow/chat，并传递 resumeValue 作为中断恢复值。

### 文本输入

```json
"resumeValue": "补充说明：目标表为 dwd_order"
```

### 表单提交

```json
"resumeValue": {
  "kind": "form",
  "values": {
    "target_table": "dwd_order",
    "mode": "incremental"
  }
}
```

### 操作按钮

```json
"resumeValue": {
  "kind": "actions",
  "action": {
    "type": "button",
    "label": "继续生成",
    "value": "confirm"
  }
}
```

## workflow payload（render.type=workflow）

payload 使用前端已有的 WorkflowResponse 结构：

```json
{
  "workflowName": "订单宽表汇总",
  "triggerType": 0,
  "triggerValue": null,
  "timeoutSeconds": 3600,
  "maxRetryTimes": 3,
  "priority": 0,
  "description": "订单ODS -> DWD宽表 -> DWS汇总",
  "jobs": [],
  "dependencies": []
}
```

## 示例

### 正常完成（workflow）

process（若干条） + reply：

```json
{
  "v": 2,
  "ts": 1712345679001,
  "event": "reply",
  "run_id": "run-001",
  "reply": {
    "status": "done",
    "message": "已生成工作流：订单宽表汇总",
    "render": { "type": "workflow", "schema": "workflow_response.v1" },
    "payload": { "...": "..." }
  }
}
```

### 中断等待（waiting）

```json
{
  "v": 2,
  "ts": 1712345680401,
  "event": "reply",
  "run_id": "run-002",
  "reply": {
    "status": "waiting",
    "message": "缺少关键信息，请补充后继续。",
    "render": { "type": "ui", "schema": "ui.v1" },
    "payload": { "kind": "form", "fields": [], "submit": { "label": "确认并继续" } }
  }
}
```
