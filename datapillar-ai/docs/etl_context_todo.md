## ETL 上下文压缩：阶段性设计与待办（按 session 隔离）

本文件用于记录上下文压缩机制的关键决策，避免后续讨论反复走回头路。

## 0. 这套“压缩”到底是什么（先把话说清楚）
这里的“压缩”不是把对话全文塞进 prompt 再让模型“自己总结自己”，而是把上下文拆成两类、分别治理：

1) **用户需求压缩（事实源）**：把用户话术增量提炼成结构化 TODO（稳定 ID + 类型 + 优先级 + 证据锚点）
2) **智能体产出压缩（高噪声源）**：不总结 SQL/plan 原文，只做索引目录（版本/状态/hash/长度/证据预览）

压缩的目的只有一个：**让系统在上下文有限时仍然可收敛、可追溯、可回滚**。

### 1. Session 定义（已对齐）
- session = 前端新开的一个会话窗口（window-level session）
- 该 session 的所有“记忆/压缩/产物/协作状态”必须隔离，禁止跨窗口串话
- 当前系统隔离根键为 `thread_id = etl:user:{user_id}:session:{session_id}`（LangGraph checkpoint 使用该键）

### 2. 压缩的两大对象（已对齐）
1) 用户需求对话（事实源）
2) 智能体产出（高噪声源：产物/证据/流程状态）

本阶段已实现：用户需求对话 → 结构化 TODO（见 `src/modules/etl/context/requirement_todo.py`）

## 2.1 用户需求压缩怎么用（Requirement TODO）
核心产物：
- `RequirementTodoSnapshot`：当前会话的结构化 TODO 快照（短、稳定）
- `RequirementTodoDelta`：每次用户输入对应的增量变更（add/update/cancel/reset）
- `EvidenceAnchor`：证据锚点（`turn_index` + `message_id` + 用户原话 quote）

推荐调用方式（增量）：
1) 准备：`current_snapshot`（第一次为空就用 `RequirementTodoSnapshot(session_id=...)`）
2) 调用：`llm_update_requirement_todo_snapshot(...)` 得到 `(new_snapshot, delta)`
3) 持久化：把 `new_snapshot` 写回会话 state/checkpoint

注意：
- `evidence_history` 是全量审计历史，不应投喂给 LLM（避免无限膨胀）
- `build_requirement_todo_snapshot_view(...)` 会生成“投喂视图”，只保留受控数量的 evidence

## 2.2 智能体产出压缩怎么用（Artifact/Evidence Index）
核心产物：
- `ArtifactStore(session_id=...)`：会话级产物索引（global + per-agent + evidence）
- `register_artifact_to_store(...)`：统一入口，登记 analysis/plan/test/sql_job/sql_workflow
- `register_tool_evidence_to_store(...)`：登记工具证据（输入/输出指纹 + 短预览）
- `build_artifact_context_view(...)`：三段式投喂视图（Global anchors + Focus jobs + per-agent recent）

重要约束：
- 索引里不放 SQL 原文/完整 JSON 原文，只存 hash/长度/关键字段/证据预览
- 原文仍然由上层 state/checkpoint 持有（这是权威事实源）

### 3. 两层需求记忆（关键）
目标：保证大方向不偏，允许局部可改。

- Global Requirement TODO（全局）
  - 跨所有 Agent 一致的事实需求：输入/输出/口径/验收/未决问题
  - 按 session 存储
- Agent-Scoped TODO（按 Agent 隔离）
  - 用户“正在对某个 Agent 说话”的局部约束/补充/偏好
  - 同样按 session 存储，并在 session 内按 `agent_id` 分桶

### 4. “用户正在和哪个 Agent 对话”的判定（前端无感）
前端不传 `agent_id`。判定逻辑属于系统能力，由“语义路由器（Semantic Router）”提供建议，但必须由编排层做确定性落地。

补充（不要混淆概念）：
- **Commander（指挥官）**：控制面内核，决定“下一步跑谁 / 是否需要澄清 / 是否返工 / 是否通过门禁”等，并对 state 做确定性写回。
- **Semantic Router（语义路由器）**：仅输出“语义判定建议与白名单动作候选”；不得直接修改 state。

#### 4.1 确定性优先（稳定性分支）
- 当存在 `pending_requests(kind=human, status=pending)` 时：
  - 用户输入被视为对该 request 的回答
  - 由 `created_by/resume_to` 决定归因与恢复节点
  - 禁止语义路由器抢路由

#### 4.2 语义分流（智能分支）
当不存在待处理的 human request 时：
- 由 LLM 做语义判定：用户是在对哪一个 Agent 说话
- 输出必须是严格 JSON，并且只能落在“允许动作集合”内
- 编排层只执行白名单动作（确定性策略为主），不执行自由文本

### 5. 方案 1（已拍板）：默认写 scoped，必要时提升到 global
- 默认：用户新输入 → 更新 `scoped_todo[target_agent]`
- 可选：当模型判断“这是全局事实需求变更”时，输出 `propose_global_delta`
  - 低置信不自动改全局，只生成需要澄清的问题（open_questions）或发起澄清请求

### 6. 低置信阈值（可配置，已拍板）
- `confidence < 0.7` 时不允许随便选 Agent 路由
- 行为：发起澄清（clarification），而不是硬路由导致全线崩塌
- 阈值必须可配置，配置 key 命名遵循 `etl_*` 前缀规范

### 7. 语义指挥官的最小元数据输入（必须提供）
为避免 LLM “随便指一个”：
- `agents`：每个 Agent 的职责边界（scope）与允许动作
- `state_summary`：当前黑板状态（已有/缺失产物、当前阶段）
- `coordination_snapshot`：pending_requests/request_results 摘要
- `global_requirement_todo_snapshot`
- `scoped_todo_snapshots`
- `artifact_index`（当前有效产物目录，后续实现）

补充约束（实现层硬规则）：
- 语义路由输出必须通过 `meta.agents` / `meta.allowed_actions` 校验
- 一旦目标不在 agents 白名单或动作不在 allowed_actions 白名单：强制转澄清，禁止落地修改

### 8. 输出协议（必须结构化）
语义指挥官输出为严格 JSON：
- `target_agent`
- `confidence`
- `actions[]`（白名单动作：update_scoped_todo / propose_global_delta / request_clarification / invalidate_artifact 等）

### 9. 实施顺序（避免耦合过早）
1) 先完成“用户需求压缩（TODO）”模块 ✅
2) 实现“语义指挥官”模块（仅模块与单测，不与多智能体集成）
   - 并提供“动作落地到两层 TODO Store”的确定性应用函数（不做兜底猜测）
3) 再讨论“智能体产出压缩”（artifact/evidence/coordination）
4) 最后统一集成到 Orchestrator（图内增加语义路由节点）

### 10. 压缩触发（手动 + 自动）
压缩触发必须是“控制面策略”，不能靠 LLM 自己决定（否则会出现：越用越乱、关键 TODO 被覆盖、不可复现）。

#### 10.1 用户手动触发（推荐）
为了避免把真实需求误判成“压缩指令”，只接受显式命令/按钮：
- `/compress`：压缩需求 + 产出（默认）
- `/compress requirement`：只压缩需求（TODO）
- `/compress artifacts`：只压缩产出（artifact/evidence）

对应实现：`src/modules/etl/context/context_budget.py:parse_manual_compress_command`
集成后语义：会在当前 session 内开启 `force_context_clipping`，后续每次调用 LLM 都会对 payload 做预算裁剪。

#### 10.2 自动触发（预算阈值）
当即将触达模型上下文窗口上限时，必须提前触发压缩，避免 `context_length_exceeded`：
- soft 阈值：建议压缩（避免频繁触顶）
- hard 阈值：必须压缩（否则非常容易报错）

对应实现：`src/modules/etl/context/context_budget.py:decide_compression_trigger`

#### 10.3 推荐接入点（后续集成时）
在每次真正调用 LLM 前，使用“即将发送的 messages 列表”做预算估算：
1) 先构建 messages（system/task/context/human）
2) `decide_compression_trigger(...)`
3) 触发则执行压缩（需求 TODO/产出索引裁剪），再重建 messages
4) 仍然超限则失败并要求用户缩小范围（不要无限重试）
