# ETL 多智能体架构设计文档

## 一、架构概览

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                        ETL 多智能体协作架构（自我进化版）                              │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                     │
│                              ┌─────────────────┐                                    │
│                              │   Orchestrator  │                                    │
│                              │    (指挥官)      │                                    │
│                              └────────┬────────┘                                    │
│                                       │                                             │
│    ┌──────────────────────────────────┼──────────────────────────────────┐         │
│    │                                  │                                  │         │
│    ▼                                  ▼                                  ▼         │
│  ┌──────────────┐            ┌──────────────┐            ┌──────────────┐          │
│  │   Memory     │◄──────────►│    Agents    │◄──────────►│   Schemas    │          │
│  │  (记忆模块)   │            │   (智能体)    │            │  (数据结构)   │          │
│  └──────┬───────┘            └──────────────┘            └──────────────┘          │
│         │                                                                           │
│         ▼                                                                           │
│  ┌──────────────┐                                                                   │
│  │   Learning   │  ←── 自我进化学习模块                                              │
│  │  (学习模块)   │                                                                   │
│  └──────────────┘                                                                   │
│                                                                                     │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

## 二、核心特性

### 2.1 自我进化学习

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              自我进化学习循环                                         │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                     │
│   用户任务 ──────► Agent 执行 ──────► 生成结果 ──────► 收集反馈                       │
│       ▲                                                    │                        │
│       │                                                    ▼                        │
│       │                                           ┌────────────────┐                │
│       │                                           │  用户评价      │                │
│       │                                           └───────┬────────┘                │
│       │                                                   │                         │
│       │                    ┌──────────────────────────────┼─────────────────────┐   │
│       │                    │                              │                     │   │
│       │                    ▼                              ▼                     ▼   │
│       │           👍 满意                         👎 不满意              ✏️ 修改    │
│       │                    │                              │                     │   │
│       │                    ▼                              ▼                     ▼   │
│       │          保存成功案例                        分析失败原因         保存修改版本 │
│       │          (ReferenceSQL)                    (FailureAnalysis)              │
│       │                    │                              │                     │   │
│       │                    └──────────────┬───────────────┴─────────────────────┘   │
│       │                                   │                                         │
│       │                                   ▼                                         │
│       │                            Neo4j 知识库                                      │
│       │                                   │                                         │
│       └───────────────────────────────────┘                                         │
│                        下次相似任务检索复用                                            │
│                                                                                     │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 语义意图理解

```
用户输入: "把订单表和用户表 JOIN，按月汇总销售额，写入销售汇总表"

                    ↓ AnalystAgent 语义分解

┌─────────────────────────────────────────────────────────────────┐
│  UserIntent                                                     │
├─────────────────────────────────────────────────────────────────┤
│  summary: "订单表和用户表关联后按月汇总销售额"                     │
│  business_goal: "生成月度销售汇总报表"                           │
│  data_sources:                                                  │
│    - table_name: "订单表"                                       │
│    - table_name: "用户表"                                       │
│  data_target:                                                   │
│    - table_name: "销售汇总表"                                   │
│  operations:  ←── 有序操作分解                                   │
│    - order: 1, action: "join", description: "订单表 JOIN 用户表" │
│    - order: 2, action: "aggregate", description: "按月汇总销售额" │
│    - order: 3, action: "sink", description: "写入销售汇总表"     │
└─────────────────────────────────────────────────────────────────┘
```

## 三、工作流程图

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              工作流程（LangGraph 状态图）                             │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                     │
│   ┌───────┐    ┌───────────────┐    ┌───────────────┐    ┌─────────────────┐       │
│   │ START │───►│ KnowledgeAgent│───►│ AnalystAgent  │───►│ 需要澄清?        │       │
│   └───────┘    │  (知识检索)    │    │  (需求分析)    │    └────────┬────────┘       │
│                └───────────────┘    └───────────────┘             │               │
│                       │                                           │               │
│                       │ 检索参考 SQL                   ┌───────────┤               │
│                       ▼                               │  是       │               │
│               ┌──────────────┐                        ▼           ▼               │
│               │ ReferenceSQL │◄──────────  ┌─────────────────────────┐            │
│               │  (历史案例)   │             │   interrupt(用户澄清)    │            │
│               └──────────────┘             └─────────────────────────┘            │
│                                                       │                           │
│                    ┌──────────────────────────────────┘                           │
│                    ▼                                                               │
│   ┌───────────────────────────┐                                                   │
│   │     ArchitectAgent        │◄─────────────────────────────┐                    │
│   │      (方案设计)            │                              │                    │
│   └─────────────┬─────────────┘                              │                    │
│                 │                                             │                    │
│                 ▼                                             │                    │
│   ┌───────────────────────────┐                              │                    │
│   │     ReviewerAgent         │                              │                    │
│   │      (方案评审)            │                              │                    │
│   └─────────────┬─────────────┘                              │                    │
│                 │                                             │                    │
│        ┌────────┴────────┐                                   │                    │
│        │    评审通过?     │───── 不通过 ─────────────────────┘                    │
│        └────────┬────────┘                                                        │
│            通过 │                                                                  │
│                 ▼                                                                  │
│   ┌───────────────────────────┐                                                   │
│   │     DeveloperAgent        │◄─────────────────────────────┐                    │
│   │      (代码生成)            │                              │                    │
│   └─────────────┬─────────────┘                              │                    │
│                 │                                             │                    │
│                 ▼                                             │                    │
│   ┌───────────────────────────┐                              │                    │
│   │     TesterAgent           │                              │                    │
│   │      (测试验证)            │                              │                    │
│   └─────────────┬─────────────┘                              │                    │
│                 │                                             │                    │
│        ┌────────┴────────┐                                   │                    │
│        │    测试通过?     │───── 不通过 ─────────────────────┘                    │
│        └────────┬────────┘                                                        │
│            通过 │                                                                  │
│                 ▼                                                                  │
│   ┌───────────────────────────┐                                                   │
│   │    FeedbackHandler        │  ←── 新增：反馈收集                                │
│   │    interrupt(用户反馈)     │                                                   │
│   └─────────────┬─────────────┘                                                   │
│                 │                                                                  │
│                 ▼                                                                  │
│   ┌───────────────────────────┐                                                   │
│   │    LearningHandler        │  ←── 新增：学习沉淀                                │
│   │   (案例沉淀/失败分析)       │                                                   │
│   └─────────────┬─────────────┘                                                   │
│                 │                                                                  │
│                 ▼                                                                  │
│   ┌───────────────────────────┐                                                   │
│   │       Finalize            │                                                   │
│   │      (完成处理)            │                                                   │
│   └─────────────┬─────────────┘                                                   │
│                 │                                                                  │
│                 ▼                                                                  │
│            ┌───────┐                                                              │
│            │  END  │                                                              │
│            └───────┘                                                              │
│                                                                                     │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

## 四、目录结构

```
src/agent/etl_agents/
├── __init__.py                 # 模块入口
├── orchestrator.py             # 指挥官：LangGraph 状态图编排
├── ARCHITECTURE.md             # 本文档
│
├── schemas/                    # 数据结构定义
│   ├── __init__.py
│   ├── context.py              # KnowledgeContext - 知识上下文（精简版）
│   ├── requirement.py          # AnalysisResult - 需求分析（语义意图）
│   ├── plan.py                 # ArchitecturePlan - 技术方案
│   └── state.py                # AgentState - 共享状态
│
├── memory/                     # 记忆模块
│   ├── __init__.py
│   ├── knowledge_cache.py      # 知识缓存（表结构、JOIN）
│   ├── case_library.py         # 案例库（历史成功案例）
│   └── memory_manager.py       # 统一记忆管理
│
├── learning/                   # 学习模块（新增）
│   ├── __init__.py
│   ├── feedback.py             # FeedbackCollector - 反馈收集
│   ├── failure_analyzer.py     # FailureAnalyzer - 失败分析
│   └── learning_loop.py        # LearningLoop - 学习循环
│
└── agents/                     # 智能体实现
    ├── __init__.py
    ├── knowledge_agent.py      # 知识检索专家
    ├── analyst_agent.py        # 需求分析师
    ├── architect_agent.py      # 数据架构师
    ├── developer_agent.py      # 数据开发
    ├── reviewer_agent.py       # 方案评审
    └── tester_agent.py         # 测试验证
```

## 五、智能体职责

### 5.1 KnowledgeAgent（知识检索专家）

**技术实现**: LLM + Tools（bind_tools）

**职责**:
- 分析用户查询，理解检索需求
- 自主决定调用哪些知识库工具
- 支持多轮检索，逐步补充信息
- 构建结构化的 KnowledgeContext（精简版）

**可用工具**:
- `search_assets`: 向量 + 全文检索表
- `get_table_lineage`: 获取血缘关系
- `kg_join_hints`: 获取 JOIN 关系
- `kg_quality_rules`: 获取 DQ 规则
- `search_reference_sql`: 检索历史成功 SQL（新增）

```python
# 核心代码示意
KNOWLEDGE_TOOLS = [
    search_assets,
    get_table_lineage,
    kg_join_hints,
    kg_quality_rules,
    search_reference_sql,  # 检索历史案例
]

self.llm_with_tools = self.llm.bind_tools(KNOWLEDGE_TOOLS)
```

### 5.2 AnalystAgent（需求分析师）

**技术实现**: LLM + Structured Output

**职责**:
- 语义理解用户自然语言（不是简单分类）
- 将复杂需求拆解为有序操作步骤
- 识别歧义，生成澄清问题

**输出**: `AnalysisResult`

```python
class Operation(BaseModel):
    """单个操作步骤"""
    action: str           # 操作类型：join/filter/aggregate/window/dedup/transform/sink
    description: str      # 具体做什么
    involved_tables: List[str]
    involved_columns: List[str]
    parameters: Dict[str, Any]
    order: int           # 执行顺序

class UserIntent(BaseModel):
    """用户意图（语义理解）"""
    summary: str         # 一句话概括
    business_goal: str   # 业务目标
    data_sources: List[DataSource]
    data_target: DataTarget
    operations: List[Operation]  # 有序操作列表
    constraints: List[str]
```

### 5.3 ArchitectAgent（数据架构师）

**技术实现**: LLM + Structured Output + 规则降级

**职责**:
- 根据需求分析结果设计技术方案
- 选择架构模式（simple_etl、star_schema、wide_table 等）
- 设计 ETL 节点和数据流
- 识别风险和替代方案

### 5.4 DeveloperAgent（数据开发）

**技术实现**: LLM + SqlValidator + 自我修正循环

**职责**:
- 按拓扑顺序处理节点
- 使用 LLM 生成 SQL
- 验证 SQL 语法和语义
- 失败时让 LLM 根据错误自我修正
- **可参考历史成功 SQL**

### 5.5 ReviewerAgent（方案评审）

**技术实现**: LLM + 规则检查

**评审维度**:
1. 完整性检查
2. 正确性检查（循环依赖、孤立节点）
3. 性能检查（大表 JOIN、笛卡尔积）
4. 安全检查（敏感字段、脱敏）
5. 最佳实践（分区裁剪、幂等性）

### 5.6 TesterAgent（测试验证）

**技术实现**: SqlValidator + 静态分析 + LLM 测试用例生成

**静态分析规则**:
- SELECT * 检查
- 笛卡尔积检查
- 无 WHERE 的 DELETE/UPDATE 检查
- 分区字段使用检查
- 硬编码值检查

## 六、学习模块（新增）

### 6.1 FeedbackCollector（反馈收集）

使用 LangGraph 的 `interrupt()` 暂停工作流，等待用户反馈。

```python
class FeedbackRating(str, Enum):
    SATISFIED = "satisfied"           # 满意，保存成功案例
    UNSATISFIED = "unsatisfied"       # 不满意，分析失败原因
    NEED_MODIFICATION = "need_modification"  # 需要修改
    SKIP = "skip"                     # 跳过

class Feedback(BaseModel):
    rating: FeedbackRating
    comment: Optional[str]
    modified_sql: Optional[str]       # 用户修改后的 SQL
```

### 6.2 FailureAnalyzer（失败分析）

分析失败原因，生成避免策略。

```python
class FailureType(str, Enum):
    SYNTAX_ERROR = "syntax_error"       # SQL 语法错误
    SEMANTIC_ERROR = "semantic_error"   # 表/列不存在
    LOGIC_ERROR = "logic_error"         # JOIN 条件错误
    PERFORMANCE_ERROR = "performance_error"
    REQUIREMENT_MISMATCH = "requirement_mismatch"  # 需求理解偏差

class FailureAnalysis(BaseModel):
    failure_type: FailureType
    error_message: str
    root_cause: str
    avoidance_hint: str   # 下次避免的提示
```

### 6.3 LearningLoop（学习循环）

根据反馈进行学习：

```python
async def learn_from_feedback(feedback, user_query, sql_text, ...):
    if feedback.rating == SATISFIED:
        # 保存成功案例到 Neo4j (ReferenceSQL 节点)
        await case_library.save_case(case)
        # confidence=0.9, 下次可直接复用

    elif feedback.rating == UNSATISFIED:
        # 分析失败原因
        analysis = failure_analyzer.analyze(error_message, sql_text)
        # 记录失败案例，避免重蹈覆辙

    elif feedback.rating == NEED_MODIFICATION:
        # 保存用户修改后的版本
        case.sql_text = feedback.modified_sql
        await case_library.save_case(case)
        # confidence=0.95, 用户确认的最高置信度
```

### 6.4 ReferenceSQL 检索工具

```python
@tool
async def search_reference_sql(query, source_tables, target_tables, limit=3):
    """
    检索历史成功 SQL，供 DeveloperAgent 参考。

    返回字段：
    - fingerprint: SQL 唯一标识
    - sql_text: SQL 代码
    - summary: 用户原始需求
    - confidence: 置信度（0.9+ 表示用户确认过）
    - use_count: 被复用次数（越高说明越通用）
    """
```

## 七、数据结构（精简版）

### 7.1 KnowledgeContext（知识上下文）

```python
class KnowledgeContext(BaseModel):
    """
    知识上下文（精简版）

    只包含轻量级的表级信息，重量级数据通过 Tool 按需查询：
    - 列级血缘 → get_column_lineage()
    - DQ 规则 → get_dq_rules()
    - 参考 SQL → search_reference_sql()
    """

    # 表信息（精简：只保留主键 + 前 10 个字段）
    tables: Dict[str, TableSchema]

    # 表级血缘（轻量，只记录 A→B 关系）
    table_lineage: List[TableLineage]

    # JOIN 关系
    join_hints: List[JoinHint]

    # 业务上下文
    business_context: BusinessContext

    # 知识缺口
    gaps: List[str]
```

### 7.2 TableSchema（表结构）

```python
class TableSchema(BaseModel):
    """表结构信息（精简版）"""
    name: str
    display_name: Optional[str]
    description: Optional[str]

    # 只保留关键列（主键 + 前 10 个字段）
    key_columns: List[ColumnInfo]
    column_count: int  # 总列数

    # 业务层级
    layer: Literal["SRC", "ODS", "DWD", "DWS", "ADS"]
    schema_name: Optional[str]
    subject_name: Optional[str]
    catalog_name: Optional[str]
    domain_name: Optional[str]
```

## 八、数据流转

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│  用户输入     │────►│ Knowledge    │────►│ Analyst      │
│  (自然语言)   │     │ Context      │     │ Result       │
└──────────────┘     │ + Reference  │     │ (语义分解)    │
                     │   SQL        │     └──────────────┘
                     └──────────────┘            │
                                                 ▼
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│  最终输出     │◄────│  Feedback    │◄────│ Architecture │
│  (ETL 工作流) │     │  + Learning  │     │ Plan         │
└──────────────┘     └──────────────┘     └──────────────┘
        │
        ▼
┌──────────────┐
│  Neo4j       │
│ (ReferenceSQL│  ←── 成功案例沉淀，下次复用
│   节点)       │
└──────────────┘
```

## 九、关键设计决策

### 9.1 为什么精简 KnowledgeContext？

**问题**: 原设计将列级血缘、DQ 规则等全量放入上下文，导致 Token 爆炸。

**方案**:
- KnowledgeContext 只保留表级概览
- 列级血缘、DQ 规则等改为 Tool 按需查询

**优势**:
- 上下文精简，LLM 推理更快
- 按需检索，避免无用信息干扰

### 9.2 为什么用语义意图分解而不是固定分类？

**问题**: 原设计用 `Literal["aggregation", "join", ...]` 分类意图，无法处理复杂组合需求。

**方案**:
- `UserIntent.summary` 自由描述用户意图
- `UserIntent.operations` 有序操作列表

**优势**:
- 支持任意复杂的组合需求
- LLM 自由理解，不受固定类型限制

### 9.3 为什么要自我进化学习？

**问题**: Agent 每次执行都是独立的，无法从历史中学习。

**方案**:
- 成功案例保存到 Neo4j (ReferenceSQL)
- 失败案例记录原因和避免策略
- 下次相似任务可检索复用

**优势**:
- Agent 越用越聪明
- 减少重复错误
- 高频场景自动形成"最佳实践"

### 9.4 学习效果量化

| 场景 | 无学习 | 有学习 |
|------|--------|--------|
| 第 1 次执行 | 从头生成 SQL | 从头生成 SQL |
| 第 2 次（相似任务） | 从头生成 SQL | 检索到历史 SQL，直接复用 |
| 第 N 次 | 从头生成 SQL | use_count++ → 成为"最佳实践" |
| 遇到类似错误 | 可能重蹈覆辙 | 检索到 avoidance_hint，主动规避 |

## 十、扩展指南

### 添加新智能体

1. 在 `agents/` 目录创建新文件
2. 实现 `__call__(self, state: AgentState) -> Command` 方法
3. 在 `orchestrator.py` 中添加节点和边
4. 更新 `agents/__init__.py` 导出

### 添加新工具

1. 在 `src/agent/tools/agent_tools.py` 添加工具函数
2. 使用 `@tool` 装饰器
3. 在需要的 Agent 中添加到工具列表

### 添加新学习策略

1. 在 `learning/` 目录添加新策略
2. 在 `LearningLoop` 中注册
3. 更新 `FeedbackRating` 如需新评分选项

## 十一、架构演进历史

| 版本 | 特性 | 问题 |
|------|------|------|
| v1.0 | 线性流程，3 个 Agent | 无反馈循环，错误传递 |
| v2.0 | LangGraph，6 个 Agent，条件路由 | 上下文爆炸，意图固定分类 |
| v2.1 | 精简 KnowledgeContext，语义意图分解 | 无学习能力 |
| **v3.0** | **自我进化学习，反馈收集，案例沉淀** | 当前版本 |
