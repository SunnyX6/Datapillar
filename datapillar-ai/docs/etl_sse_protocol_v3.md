ETL SSE 协议（v3）

本协议用于 Datapillar Web 与 Datapillar AI 的 SSE 通信。
目标是 **事件结构稳定**，前端只更新同一条消息，过程/中断/最终结果都来自同一种事件结构。

## 总览

SSE 事件只有一种：

- stream：统一事件（过程/中断/最终结果）

每次用户输入对应一个 run_id 和一个 message_id，SSE 会持续推送同一个 message_id 的更新。

## 事件结构（固定字段）

```json
{
  "v": 3,
  "ts": 1712345679001,
  "event": "stream",
  "run_id": "run-001",
  "message_id": "msg-run-001",
  "status": "running|interrupt|done|error|aborted",
  "phase": "analysis|catalog|design|develop|review",
  "activity": {
    "id": "phase:analysis",
    "phase": "analysis|catalog|design|develop|review",
    "status": "running|waiting|done|error|aborted",
    "actor": "需求分析师",
    "title": "需求分析",
    "detail": "解析目标与口径",
    "progress": 30
  },
  "message": "回复文本（必显）",
  "interrupt": { "text": "需要确认的信息", "options": ["全量", "增量"] },
  "workflow": null,
  "recommendations": ["查询有哪些元数据", "同步订单表"]
}
```

说明：

- **结构固定**：所有事件字段一致，只有值变化。
- `status` 表示当前事件类型：运行中/中断/完成/错误/终止。
- `activity` 用于过程展示，不需要就传 null。
- `message` 为主回复文本，前端必须展示。
- `interrupt` 仅在中断时使用，其他场景传空对象或 null。
- `workflow` 仅在开发工程师完成后返回（否则为 null）。
- `recommendations` **可选**，只在分析师/元数据专员输出时返回，有就展示，没有就不展示。

## status 语义

- running：过程更新
- interrupt：需要用户确认/补充
- done：最终完成
- error：执行失败
- aborted：用户终止

## interrupt 规则

interrupt 只允许文本 + 选项：

```json
{
  "text": "需要你确认后继续：",
  "options": ["全量", "增量"]
}
```

不允许表单结构。用户输入或点击选项时，前端用 **纯文本** resumeValue 继续：

```json
"resumeValue": "全量"
```

## recommendations 规则

仅当以下场景返回：

- 分析师（AnalystAgent）输出
- 元数据专员（CatalogAgent）输出

其他场景不返回该字段。

## workflow 规则

仅当 **开发工程师完成** 后返回 workflow（来自架构师的结构化工作流），否则为 null。

## 示例

### 过程更新（running）

```json
{
  "v": 3,
  "event": "stream",
  "run_id": "run-001",
  "message_id": "msg-run-001",
  "status": "running",
  "phase": "analysis",
  "activity": {
    "id": "phase:analysis",
    "phase": "analysis",
    "status": "running",
    "actor": "需求分析师",
    "title": "需求分析",
    "detail": "解析目标与口径"
  },
  "message": "",
  "interrupt": { "text": "", "options": [] },
  "workflow": null
}
```

### 中断（interrupt）

```json
{
  "v": 3,
  "event": "stream",
  "run_id": "run-002",
  "message_id": "msg-run-002",
  "status": "interrupt",
  "phase": "analysis",
  "activity": {
    "id": "phase:analysis",
    "phase": "analysis",
    "status": "waiting",
    "actor": "需求分析师",
    "title": "需求分析",
    "detail": "等待补充信息"
  },
  "message": "需求不够明确，请补充以下信息",
  "interrupt": {
    "text": "需求不够明确，请补充以下信息：\n1. 目标表名\n2. 写入模式",
    "options": []
  },
  "workflow": null
}
```

### 最终完成（done + workflow）

```json
{
  "v": 3,
  "event": "stream",
  "run_id": "run-003",
  "message_id": "msg-run-003",
  "status": "done",
  "phase": "develop",
  "activity": {
    "id": "phase:develop",
    "phase": "develop",
    "status": "done",
    "actor": "数据开发工程师",
    "title": "SQL/作业生成",
    "detail": "阶段完成"
  },
  "message": "已生成工作流：订单宽表汇总",
  "interrupt": { "text": "", "options": [] },
  "workflow": {
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
}
```
