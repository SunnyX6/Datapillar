MATCH (t:Tenant)
WITH t, datetime() AS nowAt
MERGE (k:Knowledge {id: 'kg:tenant:' + toString(t.id) + ':root'})
ON CREATE SET k.createdAt = nowAt
SET
  k.kind = 'TENANT_ROOT',
  k.name = 'Knowledge Root',
  k.updatedAt = nowAt
MERGE (t)-[:OWNS]->(k);

MATCH (t:Tenant)
WITH t, datetime() AS nowAt
MERGE (catalogRoot:Catalog {id: 'kg:tenant:' + toString(t.id) + ':catalog:root'})
ON CREATE SET catalogRoot.createdAt = nowAt
SET
  catalogRoot.kind = 'CATALOG_ROOT',
  catalogRoot.name = 'Catalog Root',
  catalogRoot.updatedAt = nowAt
MERGE (t)-[:OWNS]->(catalogRoot);

MATCH (t:Tenant)
WITH t, datetime() AS nowAt
MERGE (schemaRoot:Schema {id: 'kg:tenant:' + toString(t.id) + ':schema:root'})
ON CREATE SET schemaRoot.createdAt = nowAt
SET
  schemaRoot.kind = 'SCHEMA_ROOT',
  schemaRoot.name = 'Schema Root',
  schemaRoot.updatedAt = nowAt
MERGE (t)-[:OWNS]->(schemaRoot);

MATCH (t:Tenant)
WITH t, datetime() AS nowAt
MERGE (tableRoot:Table {id: 'kg:tenant:' + toString(t.id) + ':table:root'})
ON CREATE SET tableRoot.createdAt = nowAt
SET
  tableRoot.kind = 'TABLE_ROOT',
  tableRoot.name = 'Table Root',
  tableRoot.updatedAt = nowAt
MERGE (t)-[:OWNS]->(tableRoot);

MATCH (t:Tenant)
WITH t, datetime() AS nowAt
MERGE (columnRoot:Column {id: 'kg:tenant:' + toString(t.id) + ':column:root'})
ON CREATE SET columnRoot.createdAt = nowAt
SET
  columnRoot.kind = 'COLUMN_ROOT',
  columnRoot.name = 'Column Root',
  columnRoot.updatedAt = nowAt
MERGE (t)-[:OWNS]->(columnRoot);

MATCH (t:Tenant)
WITH t, datetime() AS nowAt
MERGE (sqlRoot:SQL {id: 'kg:tenant:' + toString(t.id) + ':sql:root'})
ON CREATE SET sqlRoot.createdAt = nowAt
SET
  sqlRoot.kind = 'SQL_ROOT',
  sqlRoot.name = 'SQL Root',
  sqlRoot.content = '',
  sqlRoot.updatedAt = nowAt
MERGE (t)-[:OWNS]->(sqlRoot);

MATCH (t:Tenant)
WITH t, datetime() AS nowAt
MERGE (tagRoot:Tag {id: 'kg:tenant:' + toString(t.id) + ':tag:root'})
ON CREATE SET tagRoot.createdAt = nowAt
SET
  tagRoot.kind = 'TAG_ROOT',
  tagRoot.name = 'Tag Root',
  tagRoot.updatedAt = nowAt
MERGE (t)-[:OWNS]->(tagRoot);

MATCH (t:Tenant)
WITH t, datetime() AS nowAt
MERGE (atomicMetricRoot:AtomicMetric {id: 'kg:tenant:' + toString(t.id) + ':atomic_metric:root'})
ON CREATE SET atomicMetricRoot.createdAt = nowAt
SET
  atomicMetricRoot.kind = 'ATOMIC_METRIC_ROOT',
  atomicMetricRoot.name = 'Atomic Metric Root',
  atomicMetricRoot.updatedAt = nowAt
MERGE (t)-[:OWNS]->(atomicMetricRoot);

MATCH (t:Tenant)
WITH t, datetime() AS nowAt
MERGE (derivedMetricRoot:DerivedMetric {id: 'kg:tenant:' + toString(t.id) + ':derived_metric:root'})
ON CREATE SET derivedMetricRoot.createdAt = nowAt
SET
  derivedMetricRoot.kind = 'DERIVED_METRIC_ROOT',
  derivedMetricRoot.name = 'Derived Metric Root',
  derivedMetricRoot.updatedAt = nowAt
MERGE (t)-[:OWNS]->(derivedMetricRoot);

MATCH (t:Tenant)
WITH t, datetime() AS nowAt
MERGE (compositeMetricRoot:CompositeMetric {id: 'kg:tenant:' + toString(t.id) + ':composite_metric:root'})
ON CREATE SET compositeMetricRoot.createdAt = nowAt
SET
  compositeMetricRoot.kind = 'COMPOSITE_METRIC_ROOT',
  compositeMetricRoot.name = 'Composite Metric Root',
  compositeMetricRoot.updatedAt = nowAt
MERGE (t)-[:OWNS]->(compositeMetricRoot);

MATCH (t:Tenant)
WITH t, datetime() AS nowAt
MERGE (wordRootRoot:WordRoot {id: 'kg:tenant:' + toString(t.id) + ':wordroot:root'})
ON CREATE SET wordRootRoot.createdAt = nowAt
SET
  wordRootRoot.kind = 'WORDROOT_ROOT',
  wordRootRoot.name = 'WordRoot Root',
  wordRootRoot.updatedAt = nowAt
MERGE (t)-[:OWNS]->(wordRootRoot);

MATCH (t:Tenant)
WITH t, datetime() AS nowAt
MERGE (modifierRoot:Modifier {id: 'kg:tenant:' + toString(t.id) + ':modifier:root'})
ON CREATE SET modifierRoot.createdAt = nowAt
SET
  modifierRoot.kind = 'MODIFIER_ROOT',
  modifierRoot.name = 'Modifier Root',
  modifierRoot.updatedAt = nowAt
MERGE (t)-[:OWNS]->(modifierRoot);

MATCH (t:Tenant)
WITH t, datetime() AS nowAt
MERGE (unitRoot:Unit {id: 'kg:tenant:' + toString(t.id) + ':unit:root'})
ON CREATE SET unitRoot.createdAt = nowAt
SET
  unitRoot.kind = 'UNIT_ROOT',
  unitRoot.name = 'Unit Root',
  unitRoot.updatedAt = nowAt
MERGE (t)-[:OWNS]->(unitRoot);

MATCH (t:Tenant)
WITH t, datetime() AS nowAt
MERGE (valueDomainRoot:ValueDomain {id: 'kg:tenant:' + toString(t.id) + ':value_domain:root'})
ON CREATE SET valueDomainRoot.createdAt = nowAt
SET
  valueDomainRoot.kind = 'VALUE_DOMAIN_ROOT',
  valueDomainRoot.name = 'Value Domain Root',
  valueDomainRoot.updatedAt = nowAt
MERGE (t)-[:OWNS]->(valueDomainRoot);
