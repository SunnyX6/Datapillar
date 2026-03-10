-- Flyway schema migration for Datapillar OpenLineage hard-cut tables

CREATE TABLE ai_embedding_binding (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Binding ID',
  tenant_id BIGINT NOT NULL COMMENT 'Tenant ID',
  scope VARCHAR(32) NOT NULL COMMENT 'Binding scope: DW / KNOWLEDGE_WIKI',
  owner_user_id BIGINT NOT NULL DEFAULT 0 COMMENT 'Binding owner user ID: DW must be 0, KNOWLEDGE_WIKI must be > 0',
  ai_model_id BIGINT NOT NULL COMMENT 'Embedding model ID',
  revision BIGINT NOT NULL DEFAULT 1 COMMENT 'Binding revision',
  set_by BIGINT NOT NULL COMMENT 'Set by user ID',
  set_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Set At',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated At',
  UNIQUE KEY uq_ai_embedding_binding_scope_owner (tenant_id, scope, owner_user_id),
  KEY idx_ai_embedding_binding_tenant_model (tenant_id, ai_model_id),
  KEY idx_ai_embedding_binding_scope_owner_lookup (tenant_id, scope, owner_user_id),
  KEY idx_ai_embedding_binding_tenant_set_by (tenant_id, set_by),
  CONSTRAINT fk_ai_embedding_binding_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  CONSTRAINT fk_ai_embedding_binding_tenant_model FOREIGN KEY (tenant_id, ai_model_id) REFERENCES ai_model(tenant_id, id),
  CONSTRAINT fk_ai_embedding_binding_tenant_user FOREIGN KEY (tenant_id, set_by) REFERENCES tenant_users(tenant_id, user_id),
  CONSTRAINT ck_ai_embedding_binding_scope CHECK (scope IN ('DW', 'KNOWLEDGE_WIKI')),
  CONSTRAINT ck_ai_embedding_binding_owner CHECK (
    (scope = 'DW' AND owner_user_id = 0)
    OR (scope = 'KNOWLEDGE_WIKI' AND owner_user_id > 0)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Embedding model binding by scope owner';
