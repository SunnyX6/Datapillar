# 上下文内容分层设计（V1 导论）

## 目标
- 上下文=LLM 调用输入的 messages；状态=LangGraph 的 Blackboard。两者必须解耦。
- 调用点不允许拼接提示词细节；只能消费 `__context` 结尾的“块”与 `checkpoint_messages`。
- “共享 Agent 上下文”指跨 Agent 复用的 `__context` 注入块（不是 `messages` 记忆）；Agent 的系统提示词永远是私有层。
- 统一分层、顺序、生命周期与落盘策略，避免“哪里都在拼上下文”的散乱实现。

## 职责边界（硬约束）
### 为什么需要“组装器/Composer”（不等于文件数）
上下文组装在工程上必须拆成两件事：
- **状态侧（State/记忆）**：如何读写 Blackboard、如何生成 patch、如何遵守 reducer（`messages`/`mapreduce_results`）规则。
- **渲染侧（Context/Prompt）**：如何把分层内容渲染成 `list[BaseMessage]`（纯函数、无副作用）。

这两件事混在一个 Builder 里，会直接导致：
- 调用点把“LLM transcript”当“持久记忆”写回（checkpoint 恢复后重复 HumanMessage 的根因之一）
- 上下文细节（工具提示词/模板字符串）泄漏到框架各处，任何人都能随手拼一段进 SystemMessage

所以必须存在一个独立的 **ContextComposer/ContextAssembler 组件**（可以是单独文件，也可以在同一文件里，但必须保持依赖与副作用隔离）：
- Composer：只接收 `system_prompt + checkpoint_messages + contexts(__context) + query`，输出 messages；**不读 state，不写 state**。
- Builder/State：只负责 state/patch/reducer；**不包含提示词模板与拼接细节**。

### 组件责任（V1 最终形态）
1) ContextCollector（收集层，不渲染）
- 输入：Blackboard state + runtime services（knowledge/todo/experience 等）
- 输出：`contexts: dict[str, str]`，key 必须为 `__context` 结尾
- 约束：默认不落盘；只有显式允许的层（V1：compression__context）可以通过状态模块写入

2) ContextComposer（渲染层，纯函数）
- 输入：`system_prompt`、`checkpoint_messages`、`contexts`、`query/human_message`
- 输出：`list[BaseMessage]`
- 约束：不持有/不读取 raw state dict；不做 todo/knowledge 检索；不写 patch；不包含硬编码工具提示词模板

3) StateBuilder/Memory 模块（状态侧）
- 负责 `messages` 的 append/replace（reducer 语义），确保“输入只出现一次”，并避免 checkpoint 重复写入

## 分层定义（必须具备且显式）
上下文由以下层组成（名称为领域名，不暴露内部模板）：

1) Agent 系统提示词（system_prompt）
- 来源：Agent 自己定义（AgentSpec/Agent class）。
- 形态：`SystemMessage`。
- 生命周期：仅本次 LLM 调用使用，不落盘。

2) Checkpoint 记忆（checkpoint_messages）
- 来源：Blackboard 的 `messages`（持久记忆）。
- 形态：Human/AI 等对话消息序列（不包含系统提示词细节）。
- 生命周期：持久化（checkpoint），跨节点共享。

3) Todo 上下文（todo__context）
- 来源：Blackboard.todo（结构化快照）渲染而成；或由运行时覆盖。
- 形态：单个 `__context` 字符串块（最终渲染为 `SystemMessage`）。
- 生命周期：默认 runtime-only；如需复现可显式落盘为 `todo__context`（但写入必须由状态模块统一管理）。

4) 压缩上下文（compression__context）
- 来源：Compactor 对历史窗口做摘要后的结果。
- 形态：单个 `__context` 字符串块（最终渲染为 `SystemMessage`）。
- 生命周期：必须可持久化，否则 checkpoint 恢复后会丢失“历史摘要”。
- 强约束：禁止把压缩摘要当作 `SystemMessage` 塞进 `messages` 再被过滤；压缩摘要要么落到 `compression__context` 字段，要么以稳定的 AIMessage（非 internal）持久化。V1 推荐 `compression__context` 字段。

5) 知识上下文（knowledge__context）
- 来源：KnowledgeRetriever 检索结果格式化。
- 形态：`__context` 字符串块。
- 生命周期：默认 runtime-only；只有“必须复现”场景才落盘。

6) 经验上下文（experience__context）
- 来源：ExperienceRetriever 检索结果格式化。
- 形态：`__context` 字符串块。
- 生命周期：默认 runtime-only；只有“必须复现”场景才落盘。

7) 框架内置上下文（framework__context）
- 来源：框架内部固定信息（协议约束/输出契约/运行限制/工具使用约束等）。
- 形态：`__context` 字符串块。
- 生命周期：仅本次 LLM 调用使用，不落盘（因为它是框架版本相关的，不应污染持久记忆）。

## 共享 Agent 上下文（Shared Agent Context）
### 定义（硬约束）
- `checkpoint_messages`（Blackboard.messages）是持久记忆，默认天然跨 Agent 共享；它不是本节讨论的“共享上下文”。
- “共享 Agent 上下文”指：跨 Agent 复用的一组 `__context` 注入块，由 ContextCollector 统一收集、由 ContextComposer 统一渲染；调用点禁止拼接/修改其内容与顺序。
- Agent 系统提示词（system_prompt）永远是私有层，不允许作为共享上下文传播（否则会污染角色边界）。

### 默认共享层（V1 必备）
- `framework__context`：框架内置约束/输出契约/运行限制。
- `compression__context`：历史摘要（必须可持久化，保证 checkpoint 恢复可复现）。
- `todo__context`：Todo 渲染块（默认 runtime-only，可按策略复现）。
- `knowledge__context`：知识检索块（默认 runtime-only，可按策略复现）。
- `experience__context`：经验检索块（默认 runtime-only，可按策略复现）。

### 共享策略（必须团队级可配置）
共享上下文的开关属于 Context 域能力，必须由 Datapillar 顶层配置控制（而不是散落在 NodeFactory/Agent 内部）。

仅保留一个开关（唯一入口）：
- `enable_share_context`: Datapillar 初始化参数，是否注入共享 `__context` 块到 LLM messages。

说明：
- `checkpoint_messages` 是否注入由调用点控制（例如 MapReduce worker 强制 `messages=[]`）。
- State/Blackboard 的持久化仍然按 StateBuilder 的规则运行；这里只影响“LLM 输入组装”，不影响状态写入。

### Process 级硬规则（V1）
- MapReduce worker：不注入 `checkpoint_messages`（调用点强制 `messages=[]`）；默认 `enable_share_context=false`（只允许注入 `framework__context`，以及按需的 `knowledge__context`）。
- MapReduce planner/reducer：不注入 `checkpoint_messages`（调用点强制 `messages=[]`）；可按需注入 `experience__context/knowledge__context`。
- Sequential/Dynamic/Hierarchical/ReAct：默认 `enable_share_context=true`，`checkpoint_messages` 由记忆模块正常注入。

## 现状偏差（当前代码未做到的点）
> 这部分是为了明确“为什么现在看起来混乱”，并给后续重构提供验收标准。

1) 组装器读取 raw state dict 并内置提示词细节
- `context/assembler.py` 直接读取 `state` 并拼接 knowledge/todo/assigned_task 等内容，同时硬编码了 todo/knowledge 的工具提示词文本。
- 这违反了“调用点只消费 `__context`”的原则：因为 `__context` 没有形成统一的数据结构，模板细节散落在组装器内部。

2) Builder 同时承担 state/patch 与 context 渲染入口
- `context/builder.py` 既做 state 管理（messages/timeline/checkpoint/compaction），又提供 `compose_llm_messages()` 并把 raw state 交给组装器。
- 这导致调用点很难分清“什么时候在写状态、什么时候在构造 LLM 输入”。

3) query 注入路径不唯一
- 组装器存在“当没有 upstream_messages 时再追加 query”的逻辑，这会与入口把用户输入写入 `messages` 的路径产生双轨，进而诱发重复输入或对齐失败后的全量追加风险。

4) NodeFactory 在构建上下文（职责越界）
- `core/nodes.py` 在运行前拼接 knowledge/todo 上下文并写入运行态 state，这属于 Context 域职责，必须迁移到 ContextCollector。

## 命名约定（对调用点的唯一暴露）
- 所有“注入块”字段统一以 `__context` 结尾：`todo__context/knowledge__context/...`
- 历史对话记忆只用 `checkpoint_messages`（来自 `messages` reducer 字段），不叫 `xxx_context`。
- 禁止在调用点出现“拼接提示词字符串”；调用点只拿 `__context`。

## 组装顺序（默认策略）
组装由 ContextComposer 统一完成，调用点不可改写拼接细节。默认顺序：

1. `SystemMessage(system_prompt)`
2. `SystemMessage(framework__context)`（框架约束必须最靠前）
3. `SystemMessage(experience__context)`（外部经验）
4. `SystemMessage(knowledge__context)`（外部知识）
5. `SystemMessage(todo__context)`（进度/拆解）
6. `SystemMessage(compression__context)`（历史摘要，若存在）
7. `checkpoint_messages`（Human/AI 消息序列）
8. `HumanMessage(query)`（本次用户输入；必须保证“输入只出现一次”）

说明：如果你希望把 compression 放在 checkpoint_messages 前后属于策略问题，但只能在 Composer 内统一调整，禁止局部改。

## 生命周期与落盘规则（硬约束）
- `messages`：只存持久记忆（checkpoint），禁止从 LLM 全量 transcript 推断增量写回。
- `__context`：默认 runtime-only（不落盘）；只有明确标记为可复现的层（V1：compression__context）允许落盘。
- “落盘/清理”必须由 Blackboard 状态模块统一管理，禁止由 Agent/Tool/Node 临时写字段。

## API 形态（对框架内部）
Context 构建分两步：
1) `ContextCollector`：从 state + runtime 检索得到 `contexts: dict[str, str]`（键为 `__context`）。
2) `ContextComposer`：接收 `system_prompt + contexts + checkpoint_messages + query`，输出 `list[BaseMessage]`。

调用点（Node/Executor/AgentContext）只做一件事：请求 Composer 输出 messages，不参与内容拼装细节。

## 重构验收标准（必须可测）
- Composer 不接收 `state: dict` 入参；只能接收显式参数（system_prompt/checkpoint_messages/contexts/query）。
- Collector 输出的所有块 key 必须以 `__context` 结尾；调用点不再拼任何提示词字符串。
- query 注入只有一条路径（要么入口写入 checkpoint_messages，要么 runtime-only human_message），绝不允许双轨。
- 压缩结果必须进入 `compression__context`（可持久化），checkpoint 恢复后不会丢摘要且不会重复写入 HumanMessage。
