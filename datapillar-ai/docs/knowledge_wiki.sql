-- 知识 Wiki 元数据表（MySQL）
--
-- 说明：
-- - 仅保存空间(namespace)与文档元数据
-- - 切分策略为“文档级别”配置快照
-- - storage_uri 需支持 file:/// 与 s3:// 等 URI
--
-- 执行：
--   在 datapillar（或系统库）中执行本 DDL

-- =========================
-- 知识命名空间（namespace）
-- =========================
CREATE TABLE IF NOT EXISTS knowledge_namespace (
  namespace_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '命名空间ID',
  namespace VARCHAR(128) NOT NULL COMMENT '命名空间标识（用于向量库隔离）',
  description VARCHAR(512) NULL COMMENT '命名空间描述',
  created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1=启用，0=禁用',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '软删除：0=否，1=是',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (namespace_id),
  UNIQUE KEY uk_namespace_creator (created_by, namespace),
  KEY idx_namespace_creator (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识命名空间（namespace）';


-- =========================
-- 知识文档（元数据）
-- =========================
CREATE TABLE IF NOT EXISTS knowledge_document (
  document_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '文档ID',
  namespace_id BIGINT UNSIGNED NOT NULL COMMENT '所属命名空间ID',
  doc_uid VARCHAR(64) NULL COMMENT '向量库文档ID（hash）',

  title VARCHAR(255) NOT NULL COMMENT '文档标题',
  file_type VARCHAR(32) NOT NULL COMMENT '文件类型：pdf/docx/md/txt/...',
  size_bytes BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',

  -- 存储位置：后端写入（支持本地存储/S3）
  storage_uri VARCHAR(1024) NULL COMMENT '存储URI（file:///path 或 s3://bucket/key）',
  storage_type VARCHAR(32) NULL COMMENT '存储类型：local/s3',
  storage_key VARCHAR(255) NULL COMMENT '对象存储Key（可选）',

  status VARCHAR(32) NOT NULL DEFAULT 'processing' COMMENT '处理状态：processing/indexed/error',
  chunk_count INT NOT NULL DEFAULT 0 COMMENT '切片数量',
  token_count INT NOT NULL DEFAULT 0 COMMENT 'token统计（暂不启用，默认0）',
  error_message VARCHAR(1024) NULL COMMENT '失败原因',

  embedding_model_id BIGINT UNSIGNED NULL COMMENT 'Embedding模型ID（ai_model）',
  embedding_dimension INT NULL COMMENT 'Embedding向量维度（快照）',

  -- 切分策略（文档级）
  chunk_mode VARCHAR(32) NULL COMMENT '切分模式：general/parent_child/qa',
  chunk_config_json JSON NULL COMMENT '切分配置快照（可复现）',
  last_chunked_at TIMESTAMP NULL COMMENT '最近切分时间',

  created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '软删除：0=否，1=是',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (document_id),
  KEY idx_doc_namespace (namespace_id),
  KEY idx_doc_namespace_status (namespace_id, status),
  KEY idx_doc_created_by (created_by),
  KEY idx_doc_uid (doc_uid),
  KEY idx_doc_embedding_model (embedding_model_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识文档元数据';


-- =========================
-- 文档切分任务进度
-- =========================
CREATE TABLE IF NOT EXISTS knowledge_document_job (
  job_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  namespace_id BIGINT UNSIGNED NOT NULL COMMENT '所属命名空间ID',
  document_id BIGINT UNSIGNED NOT NULL COMMENT '文档ID',
  job_type VARCHAR(32) NOT NULL COMMENT '任务类型：chunk/rechunk/reembed',
  status VARCHAR(32) NOT NULL DEFAULT 'queued' COMMENT '任务状态：queued/running/success/error/canceled',
  progress TINYINT NOT NULL DEFAULT 0 COMMENT '进度百分比：0-100',
  progress_seq BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '进度序列号（用于 SSE 去重）',
  total_chunks INT NOT NULL DEFAULT 0 COMMENT '预计切片数',
  processed_chunks INT NOT NULL DEFAULT 0 COMMENT '已处理切片数',
  error_message VARCHAR(1024) NULL COMMENT '失败原因',
  started_at TIMESTAMP NULL COMMENT '开始时间',
  finished_at TIMESTAMP NULL COMMENT '完成时间',
  created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (job_id),
  KEY idx_job_namespace (namespace_id),
  KEY idx_job_document (document_id),
  KEY idx_job_status (status),
  KEY idx_job_created_by (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档切分/重嵌入任务进度';
