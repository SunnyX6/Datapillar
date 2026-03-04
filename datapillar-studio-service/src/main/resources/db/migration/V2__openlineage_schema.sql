-- Flyway schema migration for Datapillar OpenLineage tables

CREATE TABLE IF NOT EXISTS lineage_events (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'physical primary key',
  tenant_id BIGINT NOT NULL COMMENT 'Tenant ID',
  tenant_code VARCHAR(64) NOT NULL COMMENT 'Tenant Code',
  tenant_name VARCHAR(128) NOT NULL COMMENT 'Tenant Name',
  event_time DATETIME(6) NOT NULL COMMENT 'event time',
  event_type VARCHAR(32) NULL COMMENT 'RUN_EVENT type',
  run_uuid CHAR(36) NULL COMMENT 'run uuid',
  job_name VARCHAR(255) NULL COMMENT 'job name',
  job_namespace VARCHAR(255) NULL COMMENT 'job namespace',
  producer VARCHAR(512) NULL COMMENT 'producer',
  _event_type VARCHAR(64) NOT NULL DEFAULT 'RUN_EVENT' COMMENT 'RUN_EVENT/DATASET_EVENT/JOB_EVENT',
  event JSON NOT NULL COMMENT 'raw event',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Created At',
  KEY idx_le_tenant_time (tenant_id, event_time DESC),
  KEY idx_le_tenant_event_type_time (tenant_id, _event_type, event_time DESC),
  KEY idx_le_tenant_run_time (tenant_id, run_uuid, event_time DESC),
  KEY idx_le_tenant_job_time (tenant_id, job_namespace, job_name, event_time DESC),
  KEY idx_le_created_at (created_at DESC),
  CHECK (_event_type IN ('RUN_EVENT', 'DATASET_EVENT', 'JOB_EVENT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OpenLineage raw event table';

CREATE TABLE IF NOT EXISTS ol_async_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Task ID',
  task_type VARCHAR(32) NOT NULL COMMENT 'EMBEDDING/SQL_SUMMARY',
  tenant_id BIGINT NOT NULL COMMENT 'Tenant ID',
  tenant_code VARCHAR(64) NOT NULL COMMENT 'Tenant Code',
  resource_type VARCHAR(32) NOT NULL COMMENT 'resource type',
  resource_id VARCHAR(128) NOT NULL COMMENT 'resource ID',
  content_hash CHAR(64) NOT NULL COMMENT 'content hash',
  model_fingerprint VARCHAR(256) NOT NULL COMMENT 'Model fingerprint',
  status VARCHAR(16) NOT NULL COMMENT 'Task Status',
  priority INT NOT NULL DEFAULT 100 COMMENT 'Priority',
  retry_count INT NOT NULL DEFAULT 0 COMMENT 'retry count',
  max_retry INT NOT NULL DEFAULT 5 COMMENT 'Maximum retry count',
  next_run_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'next execution time',
  claim_token VARCHAR(64) NULL COMMENT 'claim token',
  claim_until DATETIME(6) NULL COMMENT 'claim expiration time',
  last_error VARCHAR(1024) NULL COMMENT 'last error',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  UNIQUE KEY uq_task_dedup (task_type, tenant_id, resource_type, resource_id, model_fingerprint, content_hash),
  KEY idx_task_poll (status, next_run_at, priority, id),
  KEY idx_task_tenant (tenant_id, task_type, status, next_run_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='async task main table';

CREATE TABLE IF NOT EXISTS ol_async_task_attempt (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'detail ID',
  task_id BIGINT NOT NULL COMMENT 'Task ID',
  attempt_no INT NOT NULL COMMENT 'attempt number',
  worker_id VARCHAR(64) NOT NULL COMMENT 'worker instance ID',
  started_at DATETIME(6) NOT NULL COMMENT 'Start time',
  finished_at DATETIME(6) NULL COMMENT 'end time',
  status VARCHAR(16) NOT NULL COMMENT 'Status',
  batch_no VARCHAR(64) NULL COMMENT 'batch number',
  input_size INT NULL COMMENT 'input count',
  latency_ms BIGINT NULL COMMENT 'latency ms',
  error_type VARCHAR(128) NULL COMMENT 'error type',
  error_message VARCHAR(1024) NULL COMMENT 'error message',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  KEY idx_attempt_task (task_id, attempt_no),
  KEY idx_attempt_batch (batch_no),
  CONSTRAINT fk_attempt_task FOREIGN KEY (task_id) REFERENCES ol_async_task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='async task execution details';

CREATE TABLE IF NOT EXISTS ol_async_batch (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'batch ID',
  batch_no VARCHAR(64) NOT NULL COMMENT 'batch number',
  task_type VARCHAR(32) NOT NULL COMMENT 'Task Type',
  tenant_id BIGINT NOT NULL COMMENT 'Tenant ID',
  model_fingerprint VARCHAR(256) NOT NULL COMMENT 'Model fingerprint',
  worker_id VARCHAR(64) NOT NULL COMMENT 'worker instance ID',
  planned_size INT NOT NULL COMMENT 'planned count',
  success_count INT NOT NULL DEFAULT 0 COMMENT 'success count',
  failed_count INT NOT NULL DEFAULT 0 COMMENT 'failure count',
  started_at DATETIME(6) NOT NULL COMMENT 'Start time',
  finished_at DATETIME(6) NULL COMMENT 'end time',
  status VARCHAR(16) NOT NULL COMMENT 'Status',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uq_batch_no (batch_no),
  KEY idx_batch_tenant_type_time (tenant_id, task_type, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='async batch tracking';
