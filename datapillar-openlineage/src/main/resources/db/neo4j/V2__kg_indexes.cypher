CREATE CONSTRAINT kg_tenant_id_unique IF NOT EXISTS
FOR (n:Tenant)
REQUIRE n.id IS UNIQUE;

CREATE CONSTRAINT kg_tenant_code_unique IF NOT EXISTS
FOR (n:Tenant)
REQUIRE n.code IS UNIQUE;

CREATE CONSTRAINT kg_knowledge_id_unique IF NOT EXISTS
FOR (n:Knowledge)
REQUIRE n.id IS UNIQUE;

CREATE CONSTRAINT kg_catalog_id_unique IF NOT EXISTS
FOR (n:Catalog)
REQUIRE n.id IS UNIQUE;

CREATE CONSTRAINT kg_schema_id_unique IF NOT EXISTS
FOR (n:Schema)
REQUIRE n.id IS UNIQUE;

CREATE CONSTRAINT kg_table_id_unique IF NOT EXISTS
FOR (n:Table)
REQUIRE n.id IS UNIQUE;

CREATE CONSTRAINT kg_column_id_unique IF NOT EXISTS
FOR (n:Column)
REQUIRE n.id IS UNIQUE;

CREATE CONSTRAINT kg_sql_id_unique IF NOT EXISTS
FOR (n:SQL)
REQUIRE n.id IS UNIQUE;

CREATE CONSTRAINT kg_tag_id_unique IF NOT EXISTS
FOR (n:Tag)
REQUIRE n.id IS UNIQUE;

CREATE CONSTRAINT kg_atomic_metric_id_unique IF NOT EXISTS
FOR (n:AtomicMetric)
REQUIRE n.id IS UNIQUE;

CREATE CONSTRAINT kg_derived_metric_id_unique IF NOT EXISTS
FOR (n:DerivedMetric)
REQUIRE n.id IS UNIQUE;

CREATE CONSTRAINT kg_composite_metric_id_unique IF NOT EXISTS
FOR (n:CompositeMetric)
REQUIRE n.id IS UNIQUE;

CREATE CONSTRAINT kg_wordroot_id_unique IF NOT EXISTS
FOR (n:WordRoot)
REQUIRE n.id IS UNIQUE;

CREATE CONSTRAINT kg_modifier_id_unique IF NOT EXISTS
FOR (n:Modifier)
REQUIRE n.id IS UNIQUE;

CREATE CONSTRAINT kg_unit_id_unique IF NOT EXISTS
FOR (n:Unit)
REQUIRE n.id IS UNIQUE;

CREATE CONSTRAINT kg_value_domain_id_unique IF NOT EXISTS
FOR (n:ValueDomain)
REQUIRE n.id IS UNIQUE;

CREATE FULLTEXT INDEX kg_knowledge_text_idx IF NOT EXISTS
FOR (n:Knowledge)
ON EACH [n.name, n.description, n.content, n.summary, n.tags];

CREATE FULLTEXT INDEX kg_table_text_idx IF NOT EXISTS
FOR (n:Table)
ON EACH [n.name, n.description, n.content, n.summary, n.tags];

CREATE FULLTEXT INDEX kg_sql_text_idx IF NOT EXISTS
FOR (n:SQL)
ON EACH [n.content, n.summary, n.tags];

CREATE VECTOR INDEX kg_global_embedding_idx IF NOT EXISTS
FOR (n:Knowledge)
ON (n.embedding);

CREATE VECTOR INDEX kg_catalog_embedding_idx IF NOT EXISTS
FOR (n:Catalog)
ON (n.embedding);

CREATE VECTOR INDEX kg_schema_embedding_idx IF NOT EXISTS
FOR (n:Schema)
ON (n.embedding);

CREATE VECTOR INDEX kg_table_embedding_idx IF NOT EXISTS
FOR (n:Table)
ON (n.embedding);

CREATE VECTOR INDEX kg_column_embedding_idx IF NOT EXISTS
FOR (n:Column)
ON (n.embedding);

CREATE VECTOR INDEX kg_sql_embedding_idx IF NOT EXISTS
FOR (n:SQL)
ON (n.embedding);

CREATE VECTOR INDEX kg_tag_embedding_idx IF NOT EXISTS
FOR (n:Tag)
ON (n.embedding);

CREATE VECTOR INDEX kg_atomic_metric_embedding_idx IF NOT EXISTS
FOR (n:AtomicMetric)
ON (n.embedding);

CREATE VECTOR INDEX kg_derived_metric_embedding_idx IF NOT EXISTS
FOR (n:DerivedMetric)
ON (n.embedding);

CREATE VECTOR INDEX kg_composite_metric_embedding_idx IF NOT EXISTS
FOR (n:CompositeMetric)
ON (n.embedding);

CREATE VECTOR INDEX kg_wordroot_embedding_idx IF NOT EXISTS
FOR (n:WordRoot)
ON (n.embedding);

CREATE VECTOR INDEX kg_modifier_embedding_idx IF NOT EXISTS
FOR (n:Modifier)
ON (n.embedding);

CREATE VECTOR INDEX kg_unit_embedding_idx IF NOT EXISTS
FOR (n:Unit)
ON (n.embedding);

CREATE VECTOR INDEX kg_value_domain_embedding_idx IF NOT EXISTS
FOR (n:ValueDomain)
ON (n.embedding);

CALL db.awaitIndexes(300);
