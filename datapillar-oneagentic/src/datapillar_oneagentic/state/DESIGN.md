# Blackboard 状态写入设计（V1）

## 目标与原则
- Blackboard 是 LangGraph 图状态的唯一契约；任何状态字段新增/变更必须先改 `Blackboard`。
- 调用点禁止“按字段零散读写”；必须通过模块化门面一次性读写一组字段。
- 单节点单 patch：一个节点函数最终只返回一个 `update` dict，patch 的生成必须集中管理。
- reducer 语义集中管理：`messages`/`mapreduce_results` 等 reducer 字段禁止被当普通字段写。
- 写入幂等优先：同一轮/同一 checkpoint 恢复后重复执行，不应产生重复 HumanMessage/重复结果条目。

## 术语
- state / Blackboard：LangGraph 在节点间传递并可持久化的状态对象（TypedDict + dict）。
- patch：节点返回的增量更新 dict（`Command(update=patch)` 或直接 return dict）。
- reducer 字段：在 `Blackboard` 上声明了 reducer 的字段（例如 `messages` 使用 `add_messages`，`mapreduce_results` 使用 `operator.add`）。

## 现状问题（必须一次性消灭）
- 状态读写散落在 NodeFactory/Controller/Tool/Orchestrator，多处拼 key、多处做默认值与类型转换。
- `messages` 同时承担“LLM上下文 transcript”与“持久记忆”的语义，导致 checkpoint 恢复时易重复写入 HumanMessage。
- reducer 字段缺乏强约束：调用点能随意 set，导致重复累加、覆盖不完整、难以定位回归。

## 总体方案：StateBuilder（唯一入口）

### 1) StateBuilder（唯一入口）
`StateBuilder` 包装 raw state dict，内部提供：
- `modules`：按领域拆分的模块对象（见“模块划分”）
- `patch()`：返回本轮累积的 patch（集中生成）

调用点规范：
1. 进入节点：`sb = StateBuilder(state)`
2. 读取：只允许 `sb.<module>.snapshot()`（一次性读完模块需要的字段）
3. 写入：只允许调用模块的“用例级方法”（一次写多个字段）
4. 返回：`return Command(update=sb.patch())`

### 2) StateBuilder（集中写入与 reducer 约束）
StateBuilder 负责：
- 校验 key 必须属于 `Blackboard`（未知 key 直接抛错）
- 普通字段覆盖语义：同一 key 多次写入以最后一次为准
- reducer 字段专用写法（见下）

#### reducer 写入规则（硬规则）
- `messages`（add_messages reducer）：
  - 仅允许两种写入：
    1) `append_messages(delta_messages)`：patch 只提交“增量消息”
    2) `replace_messages(all_messages)`：patch 必须是 `RemoveMessage(REMOVE_ALL_MESSAGES) + all_messages`
  - 禁止从“LLM全量 transcript”推断要写回的增量（禁止 diff/前缀对齐兜底全量追加）。
- `mapreduce_results`（operator.add reducer）：
  - 仅允许 `append_results(delta_items)`：patch 只提交新增 items
  - 清空必须显式 `reset_results()`：patch 写 `Overwrite([])`（LangGraph 内置 Overwrite 语义）
  - 禁止 set 非空列表（会被 reducer 再次累加导致重复）

## 模块划分（按领域一块读写）

### session 模块
字段：`namespace/session_id`
- `snapshot()`：返回 `{namespace, session_id}`
说明：只读为主；写入一般由会话入口构建初始 state 完成。

### routing 模块
字段：`active_agent/assigned_task/last_agent_status/last_agent_failure_kind/last_agent_error`
- `snapshot()`：返回路由与上次执行结果的统一视图
- `activate(agent_id)`：设置 `active_agent`
- `assign_task(text)`：设置 `assigned_task`
- `finish_agent(status, failure_kind, error)`：批量写入 `last_agent_*` + 清空 `active_agent` + 清空 `assigned_task`

### memory 模块（reducer）
字段：`messages`
- `snapshot()`：返回已清洗后的持久记忆 messages（仅 Human/AI/必要的 ToolMessage）
- `append(delta_messages)`：追加本轮需要持久化的增量消息
- `replace(all_messages)`：覆盖历史（用于压缩/修复）

硬约束：`memory` 模块不接受“LLM调用中产生的全量 messages”作为输入；只接受业务明确要持久化的 delta。

### deliverables 模块
字段：`deliverable_keys`
- `snapshot()`：返回 key 列表（list[str]）
- `record_saved(agent_id)`：去重追加并写回全量列表

### todo 模块
字段：`todo`（结构化快照）
- `snapshot()`：返回 todo 快照
- `replace(todo)`：覆盖 todo 快照
- `clear()`：清空 todo

说明：todo 的“解析/应用/清理”由节点层协调，但写入必须通过 todo 模块统一完成。

### compression 模块
字段：`compression__context`
- `snapshot()`：返回当前摘要
- `persist_compression(summary)`：写入摘要（用于持久化压缩上下文）

说明：压缩摘要只允许落到 `compression__context`，禁止写入 `messages`。

### react 模块
字段：`goal/plan/reflection/error_retry_count`
- `snapshot()`：返回 react 所需的统一视图
- `save_goal(goal)` / `save_plan(plan)` / `save_reflection(reflection)`
- `reset_error_retry()` / `inc_error_retry()`

### mapreduce 模块（含 reducer）
字段：`mapreduce_goal/mapreduce_understanding/mapreduce_tasks/mapreduce_task/mapreduce_results`
- `snapshot()`：返回 mapreduce 运行态所需的统一视图
- `init_plan(goal, understanding, tasks)`：批量写入 plan 相关字段 + `reset_results()`
- `set_current_task(task)`：设置单个 mapreduce_task
- `append_result(result)`：追加单个结果（走 reducer 追加）

### timeline 模块
字段：`timeline`
- `snapshot()`：返回 timeline dict（或 Timeline DTO）
- `append(entries)`：写入内部 timeline 缓冲并标记 dirty
- `flush()`：生成 timeline patch（整体替换写回）

## 调用点落地规范（必须统一）
- NodeFactory：
  - 只负责“协调领域动作”：deliverable 持久化、todo 应用、routing.finish、memory.append、timeline.flush
  - 禁止在 NodeFactory 里直接读写 `state[...]` 或拼 patch dict
- Controller（ReAct/MapReduce planner）：
  - 只使用 `react`/`mapreduce`/`routing` 模块方法
  - 禁止直接改字段
- Tool（delegation/todo/knowledge）：
  - 工具只产出结构化 payload（ToolMessage 或 dict）
  - 状态写入只发生在节点层（NodeFactory 或 controller），不在工具函数里直接写 state

## HumanMessage 重复写入：根因与硬防护
根因（调用点层面）：
- 把“LLM调用上下文 messages（全量 transcript）”当成“要写回 Blackboard.messages 的增量”
- checkpoint 恢复后 message 对象稳定字段/ID 不一致导致对齐失败，于是全量追加

硬防护（设计层面）：
- 定义 `AgentResult.messages` 语义为“本次需要持久化的 delta messages”（只增量）
- `memory.append()` 只接受 delta；任何 diff/对齐逻辑从系统中移除
- 覆盖历史只允许 `memory.replace()`（明确意图，且用 RemoveMessage 清空）

## 迁移策略（先收口再删旧逻辑）
1) 引入 `StateBuilder/各模块`，并在关键调用点（NodeFactory + React controller + MapReduce planner）一次性替换为模块写入。
2) 删除所有字段级 `get_xxx/set_xxx/clear_xxx` 包装（不保留兼容层）。
3) 给 reducer 字段加测试锁定：
   - checkpoint 恢复后续聊：HumanMessage 不重复
   - mapreduce_results：重复执行节点不重复累加

## 验收清单
- 任意节点/控制器：只返回一个 patch；patch 由 StateBuilder 生成
- 调用点无 `state["xxx"] = ...` 直写
- `messages` 无 diff/对齐兜底逻辑；只支持 append(delta) 或 replace(all)
- checkpoint 恢复后续聊：messages 不重复、deliverable_keys 不重复、mapreduce_results 不重复
