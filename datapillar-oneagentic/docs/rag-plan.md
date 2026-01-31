# RAG 方案与实施顺序

## 目标
- 将知识 RAG 设计为**统一能力内核**（与具体后端解耦）。
- 保证**同一检索管道**覆盖所有后端（含 Milvus、langchain_milvus）。
- 输出**纯文本/Markdown**检索结果。
- **工具注入与 agent/团队绑定**放在最后实现。

## 当前关键问题（需修正）
- Milvus 走独立分支，绕过统一 Retriever（导致 rerank/去重/parent-child 失效）。
- Milvus ingest 只写 chunk，不写 source/doc 元数据（可追溯链断裂）。
- 知识输出在 ContextBuilder 内部渲染，概念混乱（误导为系统上下文注入）。

## 设计原则
- **RAG 内核统一**：所有后端必须走同一条检索管道。
- **后端适配层**：后端差异仅存在于 Store/Adapter，不允许上层分叉。
- **输出纯文本/Markdown**：不使用 JSON 作为默认输出。
- **ContextBuilder 不负责知识渲染**：知识输出仅是工具返回结果（非系统上下文）。

## 方案概述
1) **统一 Knowledge 内核**
   - KnowledgeService 只依赖统一 Retriever + Store 适配层。
   - 保留 langchain_milvus，但作为 Store/Adapter 的一种实现，进入统一检索管道。
   - 对外暴露两类对象（职责清晰）：
     - `KnowledgeConfig`：namespaces + embedding + vector_store + retrieve（库级检索配置）
     - `KnowledgeSource`：source + chunk + metadata（文档级切分配置）

2) **补齐元数据链路**
   - Ingest 必须写 source/doc/chunk 三类实体（保持可追溯）。

3) **拆除 ContextBuilder 依赖**
   - 移除 ContextBuilder 中的知识渲染逻辑。
   - 改为知识模块内的“纯格式化函数”，供工具层使用。

4) **输出格式与父子关系**
   - 默认输出 Markdown。
   - 父子关系在检索阶段聚合，输出为可读文本。

5) **工具注入与绑定策略（最后做）**
   - 工具只是调用入口，不参与内核逻辑。
   - Agent/Team 绑定策略与工具注入放在最后实现与收口。
- 绑定方式不使用 kb_id/registry，直接传 `KnowledgeConfig` 对象：
  - Team: `Datapillar(..., knowledge=KnowledgeConfig(namespaces=["kb_x"], ...))`
  - Agent: `@agent(..., knowledge=KnowledgeConfig(namespaces=["kb_x"], ...))`
- 工具参数最小化（运行时不暴露底层细节）：
  - `query: str`（必填）
  - `namespaces: list[str]`（必填；框架在工具调用前注入绑定的 namespaces）
  - `retrieve: dict | None`（可选，覆盖本次检索 top_k/score_threshold/rerank/method）
  - `filters: dict | None`（可选，元数据过滤）

## 对外 API（先明确，再实现工具/绑定）
### 1) 文档级切分（必填 chunk）
- `KnowledgeSource` 作为唯一外部入口：
  - `source`: 文件路径/URL/文本/bytes
  - `chunk`: 文档级切分配置（必填）
  - `metadata/tags/filename/mime_type/parser_hint`: 可选
- 示例（伪码）：
  - `KnowledgeSource(source=..., chunk=..., metadata=...)`
  - `KnowledgeService.chunk(KnowledgeChunkRequest(sources=[...]), namespace="kb_x")`
  - 写入必须显式传 `namespace`（写入只能针对单库）

### 2) 库级检索（可选覆盖）
- `KnowledgeConfig.retrieve` 作为默认检索配置。
- 多库绑定通过 `KnowledgeConfig.namespaces` 声明（单库传 1 个，多库传多个）。
- 单次检索可用 `retrieve` 覆盖：
  - `method(semantic|hybrid|full_text)/top_k/score_threshold/rerank/quality/tuning`
- 绑定后工具只关心 `query/retrieve/filters`，不暴露底层 DB/embedding 细节。
- 检索调用必须显式指定 `namespaces`（至少 1 个）。
- 多库检索使用同一套检索配置（统一 rerank/过滤/TopK），不做库级配置分叉。

#### 多库并行召回（性能）
- Query routing / query expansion 只执行一次（全局统一）。
- 多 namespace 并行召回（I/O 并发）。
- 合并候选后全局 rerank（强制开启）。
- parent/window 上下文回查按 namespace 分组并行。

### 3) Chunk 级编辑（新增）
- 目标：支持按 chunk 编辑/删除，保持检索一致性。
- 前提：ingest 时将 `chunk_config` 写入 source/doc metadata，编辑时复用相同预处理/切分策略。

#### list_chunks（服务层）
- 作用：让用户知道哪些 chunk 可编辑。
- 输入：`filters/limit/offset/order_by`
- 默认排序：`doc_id, parent_id, chunk_index`
- 返回字段必须包含：`chunk_id/doc_id/source_id/chunk_type/parent_id/chunk_index/content/metadata/updated_at`
- 通用性：无父子结构时 `parent_id=None`、`chunk_type=parent`

#### upsert_chunks（服务层）
- 用于编辑单个或多个 chunk。
- 必须执行：预处理 → 重新 embedding → `upsert_chunks`
- 若启用 window，需修复文档级结构（window metadata / doc 向量）
- 不允许改结构字段（`doc_id/parent_id/chunk_index/chunk_type`）；结构变更必须整文档重 ingest

#### delete_chunks（服务层）
- 删除指定 chunk
- 删除 parent 默认级联删除 child
- 删除后修复 doc 向量；无剩余 chunk 时删除 doc

## RAG 优化路线（逐项支持）
> 参考 Milvus 的 RAG 优化建议，按项落地，不做“大杂烩”一次性上线。

1) **查询路由（Query Router）**
   - 判断是否走 RAG，以及选择 dense/sparse/hybrid + 是否启用 rerank。
   - 规则优先，LLM 仅作兜底（已有 llm_provider）。

2) **查询增强（Query Expansion）**
   - Multi-query 扩展；可选 HyDE（有 LLM 才启用）。
   - 合并检索结果后去重，再进入重排序。

3) **句子窗口检索（Sentence Window）**
   - 检索粒度=小 chunk，返回上下文=父级或相邻窗口。
   - 输出仍是 Markdown，不注入系统提示词。

4) **重排序策略优化**
   - 基于 Milvus reranker（BGE/CrossEncoder/Cohere/Jina/Voyage）。
   - 增加阈值/TopK 限制，控制成本与噪音。
   - 兼容 rerank 结果不足场景，保留原始候选避免掉档。

5) **二次检索/回退策略**
   - 当前版本禁用回退策略（配置将直接报错）。
   - 触发条件：`min_hits` / `min_score` / `min_doc_count` 之一未满足。
   - 回退动作：放宽阈值、扩容 `pool_k`、切换 hybrid、启用查询扩展、可选覆盖 rerank 模式。


## 实施顺序（先干什么后干什么）
1. **统一检索管道**
   - 合并 Milvus 与非 Milvus 的检索路径，进入同一 Retriever。
2. **补齐 Ingest 元数据写入**
   - Source/Doc/Chunk 全量写入与一致性处理。
3. **移除 ContextBuilder 的知识渲染**
   - 将渲染逻辑迁出，避免“系统上下文注入”误解。
4. **实现 Markdown 输出格式化**
- 输出格式为纯文本/Markdown。
5. **最后实现工具与绑定**
   - 工具入口与 agent/team 绑定策略最后落地。
   - 工具代码统一放在 `tools/` 目录，禁止散落在业务代码里。
