// ==================== OpenLineage 知识图谱 Neo4j 初始化脚本 ====================
// 节点唯一性通过 id 保证，层级关系通过边表达

// ==================== 约束（唯一性） ====================

// Catalog 节点：id 唯一
CREATE CONSTRAINT catalog_unique IF NOT EXISTS
FOR (c:Catalog) REQUIRE c.id IS UNIQUE;

// Schema 节点：id 唯一
CREATE CONSTRAINT schema_unique IF NOT EXISTS
FOR (s:Schema) REQUIRE s.id IS UNIQUE;

// Table 节点：id 唯一
CREATE CONSTRAINT table_unique IF NOT EXISTS
FOR (t:Table) REQUIRE t.id IS UNIQUE;

// Column 节点：id 唯一
CREATE CONSTRAINT column_unique IF NOT EXISTS
FOR (c:Column) REQUIRE c.id IS UNIQUE;

// SQL 节点：id 唯一
CREATE CONSTRAINT sql_unique IF NOT EXISTS
FOR (s:SQL) REQUIRE s.id IS UNIQUE;

// 指标节点：三种类型分别约束
CREATE CONSTRAINT atomic_metric_unique IF NOT EXISTS
FOR (m:AtomicMetric) REQUIRE m.id IS UNIQUE;

CREATE CONSTRAINT derived_metric_unique IF NOT EXISTS
FOR (m:DerivedMetric) REQUIRE m.id IS UNIQUE;

CREATE CONSTRAINT composite_metric_unique IF NOT EXISTS
FOR (m:CompositeMetric) REQUIRE m.id IS UNIQUE;

// 词根节点：id 唯一
CREATE CONSTRAINT wordroot_unique IF NOT EXISTS
FOR (w:WordRoot) REQUIRE w.id IS UNIQUE;

// 修饰符节点：id 唯一
CREATE CONSTRAINT modifier_unique IF NOT EXISTS
FOR (m:Modifier) REQUIRE m.id IS UNIQUE;

// 单位节点：id 唯一
CREATE CONSTRAINT unit_unique IF NOT EXISTS
FOR (u:Unit) REQUIRE u.id IS UNIQUE;

// 值域节点：id 唯一
CREATE CONSTRAINT valuedomain_unique IF NOT EXISTS
FOR (v:ValueDomain) REQUIRE v.id IS UNIQUE;

// ==================== 普通索引（加速查询） ====================

// Catalog 索引
CREATE INDEX catalog_name IF NOT EXISTS FOR (c:Catalog) ON (c.name);
CREATE INDEX catalog_metalake IF NOT EXISTS FOR (c:Catalog) ON (c.metalake);

// Schema 索引
CREATE INDEX schema_name IF NOT EXISTS FOR (s:Schema) ON (s.name);

// Table 索引
CREATE INDEX table_name IF NOT EXISTS FOR (t:Table) ON (t.name);
CREATE INDEX table_creator IF NOT EXISTS FOR (t:Table) ON (t.creator);
CREATE INDEX table_created_at IF NOT EXISTS FOR (t:Table) ON (t.createdAt);

// Column 索引
CREATE INDEX column_name IF NOT EXISTS FOR (c:Column) ON (c.name);
CREATE INDEX column_data_type IF NOT EXISTS FOR (c:Column) ON (c.dataType);

// SQL 索引
CREATE INDEX sql_dialect IF NOT EXISTS FOR (s:SQL) ON (s.dialect);

// 指标索引：三种类型
CREATE INDEX atomic_metric_code IF NOT EXISTS FOR (m:AtomicMetric) ON (m.code);
CREATE INDEX atomic_metric_name IF NOT EXISTS FOR (m:AtomicMetric) ON (m.name);

CREATE INDEX derived_metric_code IF NOT EXISTS FOR (m:DerivedMetric) ON (m.code);
CREATE INDEX derived_metric_name IF NOT EXISTS FOR (m:DerivedMetric) ON (m.name);

CREATE INDEX composite_metric_code IF NOT EXISTS FOR (m:CompositeMetric) ON (m.code);
CREATE INDEX composite_metric_name IF NOT EXISTS FOR (m:CompositeMetric) ON (m.name);

// 词根索引
CREATE INDEX wordroot_code IF NOT EXISTS FOR (w:WordRoot) ON (w.code);
CREATE INDEX wordroot_name IF NOT EXISTS FOR (w:WordRoot) ON (w.name);

// 修饰符索引
CREATE INDEX modifier_code IF NOT EXISTS FOR (m:Modifier) ON (m.code);
CREATE INDEX modifier_type IF NOT EXISTS FOR (m:Modifier) ON (m.modifierType);

// 单位索引
CREATE INDEX unit_code IF NOT EXISTS FOR (u:Unit) ON (u.code);
CREATE INDEX unit_name IF NOT EXISTS FOR (u:Unit) ON (u.name);

// 值域索引
CREATE INDEX valuedomain_domain_code IF NOT EXISTS FOR (v:ValueDomain) ON (v.domainCode);
CREATE INDEX valuedomain_domain_type IF NOT EXISTS FOR (v:ValueDomain) ON (v.domainType);
CREATE INDEX valuedomain_domain_level IF NOT EXISTS FOR (v:ValueDomain) ON (v.domainLevel);

// ==================== 向量索引（语义搜索） ====================

// Table embedding 向量索引
CREATE VECTOR INDEX table_embedding IF NOT EXISTS
FOR (t:Table) ON (t.embedding)
OPTIONS {indexConfig: {
  `vector.dimensions`: 2048,
  `vector.similarity_function`: 'cosine'
}};

// Column embedding 向量索引
CREATE VECTOR INDEX column_embedding IF NOT EXISTS
FOR (c:Column) ON (c.embedding)
OPTIONS {indexConfig: {
  `vector.dimensions`: 2048,
  `vector.similarity_function`: 'cosine'
}};

// 指标 embedding 向量索引：三种类型
CREATE VECTOR INDEX atomic_metric_embedding IF NOT EXISTS
FOR (m:AtomicMetric) ON (m.embedding)
OPTIONS {indexConfig: {
  `vector.dimensions`: 2048,
  `vector.similarity_function`: 'cosine'
}};

CREATE VECTOR INDEX derived_metric_embedding IF NOT EXISTS
FOR (m:DerivedMetric) ON (m.embedding)
OPTIONS {indexConfig: {
  `vector.dimensions`: 2048,
  `vector.similarity_function`: 'cosine'
}};

CREATE VECTOR INDEX composite_metric_embedding IF NOT EXISTS
FOR (m:CompositeMetric) ON (m.embedding)
OPTIONS {indexConfig: {
  `vector.dimensions`: 2048,
  `vector.similarity_function`: 'cosine'
}};

// 词根 embedding 向量索引
CREATE VECTOR INDEX wordroot_embedding IF NOT EXISTS
FOR (w:WordRoot) ON (w.embedding)
OPTIONS {indexConfig: {
  `vector.dimensions`: 2048,
  `vector.similarity_function`: 'cosine'
}};

// 修饰符 embedding 向量索引
CREATE VECTOR INDEX modifier_embedding IF NOT EXISTS
FOR (m:Modifier) ON (m.embedding)
OPTIONS {indexConfig: {
  `vector.dimensions`: 2048,
  `vector.similarity_function`: 'cosine'
}};

// 单位 embedding 向量索引
CREATE VECTOR INDEX unit_embedding IF NOT EXISTS
FOR (u:Unit) ON (u.embedding)
OPTIONS {indexConfig: {
  `vector.dimensions`: 2048,
  `vector.similarity_function`: 'cosine'
}};

// 值域 embedding 向量索引
CREATE VECTOR INDEX valuedomain_embedding IF NOT EXISTS
FOR (v:ValueDomain) ON (v.embedding)
OPTIONS {indexConfig: {
  `vector.dimensions`: 2048,
  `vector.similarity_function`: 'cosine'
}};

// ==================== 全文索引（文本搜索） ====================

// Table 全文索引
CREATE FULLTEXT INDEX table_fulltext IF NOT EXISTS
FOR (t:Table) ON EACH [t.name, t.description];

// Column 全文索引
CREATE FULLTEXT INDEX column_fulltext IF NOT EXISTS
FOR (c:Column) ON EACH [c.name, c.description];

// 指标全文索引：三种类型
CREATE FULLTEXT INDEX atomic_metric_fulltext IF NOT EXISTS
FOR (m:AtomicMetric) ON EACH [m.name, m.description];

CREATE FULLTEXT INDEX derived_metric_fulltext IF NOT EXISTS
FOR (m:DerivedMetric) ON EACH [m.name, m.description];

CREATE FULLTEXT INDEX composite_metric_fulltext IF NOT EXISTS
FOR (m:CompositeMetric) ON EACH [m.name, m.description];

// 词根全文索引
CREATE FULLTEXT INDEX wordroot_fulltext IF NOT EXISTS
FOR (w:WordRoot) ON EACH [w.code, w.name, w.description];

// 修饰符全文索引
CREATE FULLTEXT INDEX modifier_fulltext IF NOT EXISTS
FOR (m:Modifier) ON EACH [m.code, m.description];

// 单位全文索引
CREATE FULLTEXT INDEX unit_fulltext IF NOT EXISTS
FOR (u:Unit) ON EACH [u.code, u.name, u.description];

// 值域全文索引
CREATE FULLTEXT INDEX valuedomain_fulltext IF NOT EXISTS
FOR (v:ValueDomain) ON EACH [v.domainCode, v.domainName, v.items, v.description];

// ==================== 验证索引创建 ====================
SHOW INDEXES;
