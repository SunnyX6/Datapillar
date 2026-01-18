# 知识 RAG 框架设计（基座能力）

## 目标与原则
- 知识框架独立于 Agent，不是服务，不暴露 HTTP API。
- 对外只提供两类能力：文档切分与检索增强。
- 预览无状态；草稿编辑是显式能力；发布后才索引。
- scope 用于检索范围选择，默认 scope=namespace；V1 仅支持单 namespace，跨 namespace 规划 V3。
- 复用现有存储、向量库、Embedding Provider、配置体系。
- 上下文格式化由 ContextBuilder 统一负责。

## 关键概念
- namespace：逻辑隔离边界，写入表字段并作为检索过滤条件。
- scope：检索范围选择器，V1 仅支持单 namespace（跨 namespace 规划）。
- 团队级 knowledge：Datapillar(knowledge=...) 作为团队默认知识入口。
- DocumentInput：用户输入（text/path/bytes/url + metadata）。
- Chunk：块是一等公民，支持编辑与排序。
- Draft：草稿块集合，发布后形成索引版本。

## 用户使用流程
### 1) 直接发布（跳过预览）
- 解析 -> 清洗 -> 切分 -> 向量化 -> 入库

### 2) 预览 -> 草稿编辑 -> 发布
- 预览无状态，仅返回切分结果
- 草稿显式创建，支持块级编辑
- 发布触发索引

### 3) 检索（解耦 Agent）
- 任何调用方都可使用检索增强能力
- 不指定 scope 时默认使用 namespace

## 检索流水线（含 rerank 与证据治理）
1) 召回：V1 支持 semantic/hybrid，keyword/full_text 规划后续接入。
2) 重排：可选 rerank（model 或 weighted），默认按 rank 处理分数差异，必要时做归一化。
3) Evidence Grouping：内部按 document_id + parent_id 分组，用户仅配置 max_per_document。
4) 去重：先 exact hash，再 semantic 阈值去重。
5) 上下文格式化：由 ContextBuilder 根据 inject 配置输出。

说明：grouping 字段不暴露给用户，避免使用成本；仅提供语义级参数。

## 质量评估（切分 / 检索）
### 评估集格式（JSON）
- evalset_id: 评估集标识
- documents: 文档列表（doc_id/source_id/text/title/metadata）
- queries: 查询列表（query_id/query/expected_doc_ids/expected_chunk_ids/relevance_doc/relevance_chunk）
- k_values: 评估 K 值（如 [1,3,5,10]）

示例：
```json
{
  "evalset_id": "kb_eval_v1",
  "documents": [
    {"doc_id": "d1", "source_id": "kb", "text": "alpha beta", "title": "文档1"}
  ],
  "queries": [
    {
      "query_id": "q1",
      "query": "alpha",
      "expected_doc_ids": ["d1"],
      "expected_chunk_ids": ["d1:0"],
      "relevance_doc": {"d1": 2}
    }
  ],
  "k_values": [1, 3, 5, 10]
}
```

注意：
- expected_doc_ids 必须来自 documents。
- expected_chunk_ids 需与切分配置产出的 chunk_id 对齐（默认规则为 doc_id:idx）。

### 切分质量指标
- 覆盖率：SourceSpan 覆盖文档内容的比例（无 span 时记为 None）。
- 重叠率：切分 span 的重叠比例（用于识别过度重叠）。
- 重复率：chunk 内容 hash 重复占比。
- 长度统计：min/max/mean/std。

### 检索质量指标
- Hit@k：前 k 是否命中相关文档/分片。
- Recall@k：前 k 覆盖相关项的比例。
- MRR@k：首条命中的倒数排名。
- nDCG@k：按相关度权重的排序质量（无 relevance 时为二值相关）。

### 使用方式
```python
from datapillar_oneagentic.knowledge.evaluation import load_eval_set, KnowledgeEvaluator

evalset = load_eval_set("tests/data/knowledge_evalset.json")
evaluator = KnowledgeEvaluator(
    store=knowledge_store,
    embedding_provider=embedding_provider,
    chunk_config=chunk_config,
    retrieve_config=retrieve_config,
)
report = await evaluator.evaluate(evalset)
print(report.summary())
```

## 底层表结构（仅 3 表）
> 与现有 VectorKnowledgeStore 的 collection 对齐：knowledge_sources、knowledge_docs、knowledge_chunks。  
> 其它“模型/对象”仅为运行时 DTO，不是表。

### 1) knowledge_sources（数据源）
- source_key (PK)
- namespace
- source_id
- name
- source_type
- source_uri
- tags (json)
- metadata (json)
- created_at
- updated_at
- vector (可选，默认零向量)

### 2) knowledge_docs（文档）
- doc_key (PK)
- namespace
- doc_id
- source_id (FK)
- source_uri
- title
- version
- status: draft | published | archived
- language
- content_hash
- content_ref（原文或解析结果的对象存储引用）
- tags (json)
- metadata (json：包含 mime_type、parser、attachments 列表等)
- created_at
- updated_at
- vector（可选，文档级向量）

### 3) knowledge_chunks（分块）
- chunk_key (PK)
- namespace
- chunk_id
- doc_id (FK)
- source_id (FK)
- doc_title（冗余字段，便于检索展示）
- content
- token_count
- chunk_index（块顺序）
- section_path
- version
- status: draft | published
- chunk_type: parent | child
- parent_id（child 指向 parent）
- content_hash
- metadata (json：source_spans、chunk_config、attachments_ref 等)
- sparse_vector (json：可选，支持 hybrid)
- created_at
- updated_at
- vector（必需，支持 semantic/hybrid）

## 运行时模型（非表）
### DocumentInput
- source: text | path | bytes | url
- filename: str | None
- mime_type: str | None
- parser_hint: str | None
- metadata: dict

### Chunk
- chunk_id
- document_id
- parent_id: str | None
- order: int
- type: parent | child
- content: str
- status: draft | published
- source_spans: list[SourceSpan]
- metadata: dict

### ChunkPreview / ChunkDraft
- document_id
- chunks: list[Chunk]
- attachments: list[Attachment]

### ChunkOp（编辑动作）
- edit_text
- split_at
- merge_with_next
- move_before / move_after
- reparent
- enable / disable

### KnowledgeScope
- namespaces: list[str] | None
- document_ids: list[str] | None
- tags: list[str] | None

### RetrievalConfig
- method: semantic | full_text | hybrid | keyword
- top_k
- score_threshold
- rerank: RerankConfig | None
- tuning: pool_k, rerank_k, rrf_k
- quality: dedupe, dedupe_threshold, max_per_document
- inject: mode, max_tokens, max_chunks, format

### RerankConfig
- mode: off | model | weighted
- provider
- model
- top_n
- score_threshold
- score_mode: rank | normalize | raw
- normalize: min_max | sigmoid | softmax | zscore
- params: dict

### ParsedDocument
- document_id
- source_type: text | file | url
- mime_type
- text
- pages: list[str]
- attachments: list[Attachment]
- content_hash
- metadata

### Attachment
- attachment_id
- name
- mime_type
- content（内存字节，可选）
- content_ref
- metadata

### SourceSpan
- page: int | None
- start_offset: int | None
- end_offset: int | None
- block_id: str | None

## 行为规则
- 只允许同一文档内移动。
- child 允许跨 parent 重归类。
- child 命中时优先回溯 parent，parent 内容保持切分结果。
- 上下文输出按 order，不再打乱。
- 草稿只保留当前版本，发布时覆盖旧草稿内容。

## 模块职责
- ParserRegistry：统一解析器入口，识别输入格式。
- Cleaner：预处理规则（去空白、去 URL/Email 等）。
- Chunker：general / parent_child / qa 切分。
- DraftEditor：块级编辑与校验。
- Indexer：向量化与入库。
- Retriever：检索与证据治理。
- ScopeResolver：scope 解析与 namespace 映射。
- AgentAdapter：薄适配，仅调用检索接口。

## 解析器设计与文档类型
### 支持类型（V1）
- 文本类：txt、md、html
- 文档类：pdf、docx
- 表格类：csv、xlsx
- 其它类型可通过自定义 Parser 扩展

### 解析器选择原则
- 优先使用成熟、社区验证的解析库；实现前必须对齐官方文档。
- 支持流式解析，避免大文件一次性读入内存。
- 输出统一 ParsedDocument，图片等非文本作为 Attachment 归一处理。

### 解析器选型建议（实现前需验证官方文档）
- PDF：参考 Dify 使用 pypdfium2 进行文本+图片抽取；必要时提供 PyMuPDF 作为高质量备用。
- DOCX：优先 python-docx 或 mammoth（按保留结构与性能取舍）。
- XLSX/CSV：优先 pandas + openpyxl/csv 标准库。
- HTML：优先 readability + lxml/bs4（提取正文）。
- Markdown：使用成熟 Markdown 解析库做清洗与结构化。
- 统一管线：可选 unstructured 做多格式统一解析（成本更高）。

### PDF 图像处理
- PDF 解析器需同时抽取文本与图片。
- 图片以 Attachment 形式返回（仅由调用方决定是否持久化）。
- 文本中插入可追溯引用（attachment_id），便于调用方后续存储映射。
- 图片解析失败不阻断流程，至少保证文本可用。

### 解析器注册示例
- ParserRegistry.register(\"application/pdf\", PdfParser)
- ParserRegistry.register(\"application/vnd.openxmlformats-officedocument.wordprocessingml.document\", DocxParser)

## 当前目录结构
```
src/datapillar_oneagentic/knowledge/
  DESIGN.md
  __init__.py
  config.py
  models.py
  parser/
    __init__.py
    registry.py
    base.py
    pdf.py
    docx.py
    markdown.py
    html.py
    text.py
    csv.py
    xlsx.py
  chunker/
    __init__.py
  retriever/
    __init__.py
    retriever.py
    evidence.py
  ingest/
    __init__.py
    pipeline.py
```

## 接口形态（库级）
```python
# 预览（无状态）
parsed = parser_registry.parse(doc_input)
preview = chunker.preview(parsed)

# 直接发布（跳过预览）
await ingestor.ingest(source=source, documents=[doc_input], sparse_embedder=sparse_embedder)

# 检索（scope 可选，不传默认使用 namespace）
result = await retriever.retrieve(query=query, knowledge=knowledge, scope=None)
inject_config = retriever.resolve_inject_config(knowledge)
context = ContextBuilder.build_knowledge_context(
    chunks=[chunk for chunk, _ in result.hits],
    inject=inject_config,
)
```


## 配置结构（仅两类）
### knowledge.chunk
- mode: general | parent_child | qa
- preprocess: list[str]
- general: delimiter, max_tokens, overlap
- parent_child: parent{...}, child{...}

### knowledge.retrieve
- method: semantic | full_text | hybrid | keyword
- top_k
- score_threshold
- rerank
- tuning: pool_k, rerank_k, rrf_k
- quality: dedupe, dedupe_threshold, max_per_document
- inject: mode, max_tokens, max_chunks, format

## 参数语义统一（检索增强）
- top_k：最终返回的结果数（经过 rerank、grouping、去重之后）。
- pool_k：召回阶段的候选池规模。
- rerank_k：rerank 后进入 grouping/去重的候选规模。
- rrf_k：仅在 hybrid/RRF 融合时生效，用于平衡不同召回器。
- max_per_document：每个文档最大保留块数（控制长文档占比）。
- dedupe_threshold：语义去重阈值，结合 exact hash 先行去重。
- 默认策略：只用排序（score_mode=rank）屏蔽模型分数差异；高级参数通过 rerank.params 透传。

## scope 规则
- scope 可选，不传时默认 scope=namespace。
- V1 仅支持单 namespace；跨 namespace 聚合规划 V3。
- tags 过滤暂不支持，document_ids 过滤已支持。
- scope 只影响检索范围，不改变存储隔离策略。

## 与现有实现对齐
- 复用 EmbeddingProviderClient 与 KnowledgeStore。
- 上下文格式化由 ContextBuilder 统一处理。
- 拆分 KnowledgeIngestor：Parser/Chunker/Indexer 独立。
- 复用配置合并模式（全局默认 + Agent 覆盖）。

## 参考与对齐点（已确认）
- Dify 预览模式：indexing_estimate 只预览不入库。
- Dify 父子切分：child 命中、parent 召回上下文。
- 现有经验检索与知识检索的排序与去重模式。

## 落地阶段（最小路径）
- V1：preview + publish + retrieve（无草稿编辑）
- V2：草稿编辑（ChunkOp 全量支持）
- V3：跨 namespace scope 聚合检索与融合
