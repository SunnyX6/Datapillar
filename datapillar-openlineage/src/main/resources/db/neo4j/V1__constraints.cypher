CREATE CONSTRAINT tenant_id_uk IF NOT EXISTS
FOR (t:Tenant) REQUIRE t.id IS UNIQUE;

CREATE CONSTRAINT tenant_code_uk IF NOT EXISTS
FOR (t:Tenant) REQUIRE t.code IS UNIQUE;

CREATE CONSTRAINT catalog_tenant_id_uk IF NOT EXISTS
FOR (n:Catalog) REQUIRE (n.tenantId, n.id) IS UNIQUE;
CREATE CONSTRAINT schema_tenant_id_uk IF NOT EXISTS
FOR (n:Schema) REQUIRE (n.tenantId, n.id) IS UNIQUE;
CREATE CONSTRAINT table_tenant_id_uk IF NOT EXISTS
FOR (n:Table) REQUIRE (n.tenantId, n.id) IS UNIQUE;
CREATE CONSTRAINT column_tenant_id_uk IF NOT EXISTS
FOR (n:Column) REQUIRE (n.tenantId, n.id) IS UNIQUE;
CREATE CONSTRAINT sql_tenant_id_uk IF NOT EXISTS
FOR (n:SQL) REQUIRE (n.tenantId, n.id) IS UNIQUE;
CREATE CONSTRAINT atomic_metric_tenant_id_uk IF NOT EXISTS
FOR (n:AtomicMetric) REQUIRE (n.tenantId, n.id) IS UNIQUE;
CREATE CONSTRAINT derived_metric_tenant_id_uk IF NOT EXISTS
FOR (n:DerivedMetric) REQUIRE (n.tenantId, n.id) IS UNIQUE;
CREATE CONSTRAINT composite_metric_tenant_id_uk IF NOT EXISTS
FOR (n:CompositeMetric) REQUIRE (n.tenantId, n.id) IS UNIQUE;
CREATE CONSTRAINT wordroot_tenant_id_uk IF NOT EXISTS
FOR (n:WordRoot) REQUIRE (n.tenantId, n.id) IS UNIQUE;
CREATE CONSTRAINT modifier_tenant_id_uk IF NOT EXISTS
FOR (n:Modifier) REQUIRE (n.tenantId, n.id) IS UNIQUE;
CREATE CONSTRAINT unit_tenant_id_uk IF NOT EXISTS
FOR (n:Unit) REQUIRE (n.tenantId, n.id) IS UNIQUE;
CREATE CONSTRAINT valuedomain_tenant_id_uk IF NOT EXISTS
FOR (n:ValueDomain) REQUIRE (n.tenantId, n.id) IS UNIQUE;
CREATE CONSTRAINT tag_tenant_id_uk IF NOT EXISTS
FOR (n:Tag) REQUIRE (n.tenantId, n.id) IS UNIQUE;
