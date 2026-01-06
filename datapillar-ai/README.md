# ETL 多智能体系统

基于 LangGraph 的 ETL 多智能体协作系统，采用 Boss-员工模式进行任务分配和协调。

## 架构概览

```
                              用户请求
                                  │
                                  ▼
┌──────────────────────────────────────────────────────────────────┐
│                       EtlOrchestratorV2                          │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │                     LangGraph 状态图                        │  │
│  │                                                            │  │
│  │         ┌─────────┐                                        │  │
│  │         │  Boss   │◄───────────────────┐                   │  │
│  │         │ (老板)  │                    │                   │  │
│  │         └────┬────┘                    │                   │  │
│  │              │ 决策                     │ 汇报              │  │
│  │              ▼                         │                   │  │
│  │    ┌─────────────────────────────────────────────────┐     │  │
│  │    │                   员工节点                       │     │  │
│  │    │                                                 │     │  │
│  │    │   Analyst    Architect    Developer   Reviewer  │     │  │
│  │    │   (分析师)    (架构师)     (开发)      (评审)    │─────┘  │
│  │    │                                                 │        │
│  │    │                    Human (人机交互)              │        │
│  │    └─────────────────────────────────────────────────┘        │
│  │                                                               │
│  └───────────────────────────────────────────────────────────────┘
└──────────────────────────────────────────────────────────────────┘
```

## Boss-员工协作模式

### Boss（老板）

**职责**：
- 理解用户需求，分配任务
- 查看员工汇报，协调下一步
- 追踪交接物引用，出错时定位问题

**决策优先级**：
1. 前置拦截：human 请求（人机交互）最优先
2. 前置拦截：delegate 请求（员工委派）
3. 确定性推进：有进度时按规则推导
4. LLM 决策：其余情况由 LLM 判断

**默认流水线**（确定性规则）：
```
analyst → architect → reviewer(设计) → developer → reviewer(开发) → finalize
```

### 员工（Agent）

| Agent | 职责 | 依赖 | 输出 |
|-------|------|------|------|
| analyst_agent | 需求分析、业务步骤拆分 | 无 | AnalysisResult |
| architect_agent | 工作流设计、技术组件选型 | analyst | Workflow |
| developer_agent | SQL 代码生成 | architect | SQL |
| reviewer_agent | 设计/代码评审 | analyst + developer | ReviewResult |

### KnowledgeAgent（共享服务）

**定位**：不是独立员工，而是共享的知识检索服务。

**核心能力**：
- 全局混合检索（向量 + 全文 + 图遍历）
- 返回分类指针：tables, columns, valuedomains, sqls
- 指针自带 tools 字段，告诉调用方可用哪些工具

## 知识指针与工具权限

### 指针（Pointer）机制

KnowledgeAgent 检索后返回的不是完整数据，而是"指针"——轻量级的索引卡片。

```
┌─────────────────────────────────────────────────────────────────┐
│                      KnowledgeContext                           │
│                                                                 │
│   tables: [TablePointer, ...]      ← 表指针                     │
│   columns: [ColumnPointer, ...]    ← 列指针                     │
│   valuedomains: [ValueDomainPointer, ...]  ← 值域指针（自包含）  │
│   sqls: [SqlPointer, ...]          ← SQL 指针                   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**指针内容**：

| 指针类型 | 包含信息 | 特点 |
|---------|---------|------|
| TablePointer | catalog, schema, table, description | 带 tools 钥匙 |
| ColumnPointer | catalog, schema, table, column, data_type | 带 tools 钥匙 |
| ValueDomainPointer | code, name, domain_type, values | 自包含，无需工具 |
| SqlPointer | sql_id, summary, source_tables, target_table | 带 tools 钥匙 |

### 钥匙（Tools）机制

**核心理念**：指针是"索引卡"，tools 是"钥匙"。

```
┌─────────────────────────────────────────────────────────────────┐
│                         指针携带的钥匙                           │
│                                                                 │
│   TablePointer.tools = [                                        │
│       "get_table_detail",    ← 查看表详情                        │
│       "get_table_lineage",   ← 查看血缘                          │
│       "get_lineage_sql"      ← 查看血缘 SQL                      │
│   ]                                                             │
│                                                                 │
│   ColumnPointer.tools = ["get_table_detail"]                    │
│                                                                 │
│   ValueDomainPointer.tools = []  ← 自包含，无需钥匙              │
│                                                                 │
│   SqlPointer.tools = ["get_lineage_sql"]                        │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 员工权限（Allowlist）

每个员工只能使用特定的工具，这是"钥匙孔"：

| 员工 | 可用工具（钥匙孔） |
|-----|------------------|
| analyst | get_table_detail |
| architect | get_table_lineage, list_component |
| developer | get_table_detail, get_column_valuedomain, get_table_lineage, get_lineage_sql |
| reviewer | get_table_detail, get_column_valuedomain |

### 权限过滤流程

```
1. KnowledgeAgent 检索
       │
       ▼
2. 返回指针（每个指针带 tools 钥匙）
       │
       ▼
3. 员工调用 to_llm_context(allowlist=员工权限)
       │
       ▼
4. 过滤：指针.tools ∩ 员工权限 = 实际可用工具
       │
       ▼
5. 员工只能看到自己有权限的钥匙
```

**设计意图**：

- **导航信息共享**：所有员工都能看到表/列在哪里（catalog.schema.table）
- **钥匙需要权限**：只有有权限的员工才能使用对应工具获取详情
- **值域自包含**：ValueDomain 直接内联枚举值，无需调用工具

### 为什么这样设计？

1. **最小权限原则**：员工只能访问职责范围内的信息
2. **减少上下文膨胀**：指针轻量，详情按需获取
3. **职责清晰**：分析师不需要看血缘，架构师不需要看值域细节
4. **安全可控**：敏感操作（如血缘 SQL）只有特定员工能执行

## 状态管理

### 三层状态架构

```
┌─────────────────────────────────────────────────────────────────┐
│                     Blackboard (Boss 工作台)                     │
│                                                                 │
│   • task: 当前任务                                               │
│   • reports: 员工汇报 {agent_id: AgentReport}                    │
│   • pending_requests: 待处理请求 (human/delegate)                │
│   • deliverable: 最终交付物                                      │
│   • memory: SessionMemory (短期记忆)                             │
│                                                                 │
│                              │ 引用                              │
│                              ▼                                   │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                    Handover (交接物)                     │    │
│  │                                                         │    │
│  │   • 存储运行时交接物：AnalysisResult, Workflow, SQL      │    │
│  │   • 不持久化，会话结束即清                                │    │
│  │   • 员工通过 deliverable_ref 引用                        │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

### Blackboard 设计原则

- 只放老板关心的信息：任务、汇报、请求、交付物
- 不放员工内部逻辑：对话历史、压缩策略、中间计算
- 员工通过汇报中的 deliverable_ref 引用交接物

### Handover 设计原则

- 只存运行时交接物，用完即弃
- 不持久化（会话结束即清）
- SQL 原文、Workflow JSON 在这里传递，不存入记忆

## 记忆体系

### 三层记忆架构

```
┌─────────────────────────────────────────────────────────────────┐
│                         长期记忆                                 │
│                      (Neo4j 知识图谱)                            │
│                                                                 │
│   Table / Column / Schema / Catalog / SQL 血缘                   │
│   Metric (指标) / ValueDomain (值域) / Tag (标签)                │
│                                                                 │
│                              ▲ 检索                              │
│                              │                                   │
│                      KnowledgeAgent                              │
│                  (混合检索: 向量 + 全文 + 图)                     │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                         短期记忆                                 │
│                      (SessionMemory)                             │
│                                                                 │
│   • requirement_todos: 需求 TODO 清单                            │
│   • agent_statuses: 各 Agent 产物状态                            │
│   • agent_conversations: 按 Agent 隔离的对话历史                  │
│       - recent_turns: 最近几轮对话（未压缩）                      │
│       - compressed_summary: 历史对话压缩摘要                      │
│                                                                 │
│                              │ 持久化                            │
│                              ▼                                   │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                      Checkpoint                          │    │
│  │                 (Redis / LangGraph Saver)                │    │
│  │                                                         │    │
│  │   用途：断点续跑 / 中断恢复 / 容灾                        │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

### SessionMemory 设计原则

- **按 Agent 隔离**：每个 Agent 有独立的对话历史和压缩摘要
- **只存"小而精"**：状态/摘要，不存原文
- **总大小控制**：~5-8KB

### Checkpoint 用途

- **断点续跑**：服务重启后恢复执行
- **中断恢复**：人机交互后继续
- **容灾**：异常崩溃后恢复状态

## 上下文压缩

### 触发条件

1. **手动触发**：用户输入 `/compress`
2. **自动触发**：Agent 上下文 token 数 >= 软限制（默认 80%）

### 压缩流程

1. 估算当前 Agent 的上下文 token 数
2. 判断是否需要压缩（手动触发或超阈值）
3. 调用 LLM 压缩对话历史
4. 更新 SessionMemory

### Fallback 策略

- LLM 压缩失败时，**保留 recent_turns，不丢弃原始数据**
- 返回 CompressionResult 让上层感知压缩状态

## SSE 流式输出

### 端点

| 端点 | 方法 | 用途 |
|------|------|------|
| /workflow/start | POST | 启动工作流 |
| /workflow/sse | GET | SSE 流端点 |
| /workflow/continue | POST | 从中断点继续 |
| /session/clear | POST | 清理会话 |

### 断线重连

- 基于 Last-Event-ID HTTP 头
- 自动重放该序列号之后的所有缓冲事件
- 缓冲大小：2000 条记录

## 目录结构

```
src/modules/etl/
├── api.py                    # FastAPI 路由
├── boss.py                   # Boss 决策引擎
├── orchestrator_v2.py        # LangGraph 编排器
├── sse_manager.py            # SSE 流管理
│
├── agents/                   # Agent 实现
│   ├── analyst_agent.py      # 需求分析师
│   ├── architect_agent.py    # 数据架构师
│   ├── developer_agent.py    # 数据开发
│   ├── knowledge_agent.py    # 知识检索服务
│   └── reviewer_agent.py     # 方案评审
│
├── context/                  # 上下文管理
│   ├── handover.py           # 交接物存储
│   └── compress/             # 压缩模块
│
├── memory/                   # 记忆模块
│   └── session_memory.py     # 短期记忆
│
├── state/                    # 状态管理
│   └── blackboard.py         # Blackboard 定义
│
├── schemas/                  # 数据结构
│   ├── agent_result.py       # Agent 返回类型
│   ├── workflow.py           # 工作流
│   └── sse_msg.py            # SSE 消息格式
│
└── tools/                    # Agent 工具
    ├── table.py              # 表查询工具
    └── column.py             # 列查询工具
```

## 核心流程

```
1. 用户请求 → API 接收
       │
       ▼
2. Orchestrator 创建 Blackboard，启动 LangGraph
       │
       ▼
3. Boss 决策下一步
       │
       ├─→ 有 human 请求 → 人机交互节点 → 等待用户输入
       │
       ├─→ 有 delegate 请求 → 分配给目标 Agent
       │
       ├─→ 确定性规则匹配 → 按流水线推进
       │
       └─→ LLM 决策 → 分配给合适的 Agent
       │
       ▼
4. Agent 执行
       │
       ├─→ 检查是否需要压缩上下文
       │
       ├─→ 调用 KnowledgeAgent 获取知识上下文
       │
       ├─→ 执行业务逻辑（调用工具、LLM 推理）
       │
       └─→ 返回 AgentResult
       │
       ▼
5. Orchestrator 处理结果
       │
       ├─→ 存储交付物到 Handover
       │
       ├─→ 更新 Blackboard（汇报、状态）
       │
       └─→ 处理特殊状态（needs_clarification → 创建 human 请求）
       │
       ▼
6. 回到 Boss → 继续决策（循环直到 finalize）
```

## 设计原则

1. **状态清晰**：Blackboard 只放老板关心的信息，员工细节在各自内部
2. **职责分离**：Agent 只负责执行，Boss 负责决策和协调
3. **按需压缩**：达到阈值自动触发，失败不丢数据
4. **断点续跑**：通过 Checkpoint 支持中断恢复
5. **混合决策**：确定性规则优先（可复现），LLM 兜底（灵活性）
6. **交接物隔离**：运行时大对象在 Handover，不污染持久化状态
