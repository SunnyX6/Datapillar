-- Datapillar studio schema
-- 说明：产品库仅存 Studio 业务数据，tenant_id 仅做逻辑隔离

CREATE DATABASE IF NOT EXISTS datapillar_studio DEFAULT CHARSET=utf8mb4;
USE datapillar_studio;

SET FOREIGN_KEY_CHECKS=0;
DROP TABLE IF EXISTS ai_llm_usage;
DROP TABLE IF EXISTS knowledge_document_job;
DROP TABLE IF EXISTS knowledge_document;
DROP TABLE IF EXISTS knowledge_namespace;
DROP TABLE IF EXISTS job_dependency;
DROP TABLE IF EXISTS job_info;
DROP TABLE IF EXISTS job_component;
DROP TABLE IF EXISTS job_workflow;
DROP TABLE IF EXISTS projects;
DROP TABLE IF EXISTS org_users;
DROP TABLE IF EXISTS orgs;
SET FOREIGN_KEY_CHECKS=1;

CREATE TABLE IF NOT EXISTS orgs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '组织ID',
  tenant_id BIGINT NOT NULL COMMENT '租户ID',
  parent_id BIGINT NULL COMMENT '父组织ID',
  code VARCHAR(64) NOT NULL COMMENT '组织编码',
  name VARCHAR(128) NOT NULL COMMENT '组织名称',
  org_type VARCHAR(32) NULL COMMENT '组织类型（归一化）：部门/事业部/区域等',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0禁用',
  level INT NOT NULL DEFAULT 1 COMMENT '层级深度',
  path VARCHAR(512) NOT NULL COMMENT '层级路径，如 /1/3/8',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uq_org_code (tenant_id, code),
  KEY idx_org_parent (tenant_id, parent_id),
  CONSTRAINT fk_org_parent FOREIGN KEY (parent_id) REFERENCES orgs(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户内组织结构';

CREATE TABLE IF NOT EXISTS org_users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  tenant_id BIGINT NOT NULL COMMENT '租户ID',
  org_id BIGINT NOT NULL COMMENT '组织ID',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0禁用',
  joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  UNIQUE KEY uq_org_user (tenant_id, org_id, user_id),
  KEY idx_org_user_user (tenant_id, user_id),
  CONSTRAINT fk_org_user_org FOREIGN KEY (org_id) REFERENCES orgs(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组织成员关系';

CREATE TABLE IF NOT EXISTS projects (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '项目ID',
  tenant_id BIGINT NOT NULL COMMENT '租户ID',
  name VARCHAR(128) NOT NULL COMMENT '项目名称',
  description VARCHAR(512) NULL COMMENT '项目描述',
  owner_id BIGINT NOT NULL COMMENT '项目所有者ID',
  status TINYINT NOT NULL COMMENT '项目状态',
  tags JSON NULL COMMENT '项目标签',
  is_favorite TINYINT NOT NULL DEFAULT 0 COMMENT '是否收藏',
  is_visible TINYINT NOT NULL DEFAULT 1 COMMENT '是否可见',
  member_count INT NOT NULL DEFAULT 1 COMMENT '成员数量',
  last_accessed_at DATETIME NULL COMMENT '最近访问时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  KEY idx_project_tenant (tenant_id),
  KEY idx_project_owner (tenant_id, owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目';

CREATE TABLE IF NOT EXISTS job_workflow (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '工作流ID',
  tenant_id BIGINT NOT NULL COMMENT '租户ID',
  project_id BIGINT NOT NULL COMMENT '项目ID',
  workflow_name VARCHAR(128) NOT NULL COMMENT '工作流名称',
  trigger_type TINYINT NOT NULL COMMENT '触发类型',
  trigger_value VARCHAR(128) NULL COMMENT '触发值',
  timeout_seconds INT NULL COMMENT '超时秒数',
  max_retry_times INT NULL COMMENT '最大重试次数',
  priority INT NULL COMMENT '优先级',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '状态',
  description VARCHAR(512) NULL COMMENT '描述',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY idx_workflow_tenant (tenant_id),
  KEY idx_workflow_project (tenant_id, project_id),
  CONSTRAINT fk_workflow_project FOREIGN KEY (project_id) REFERENCES projects(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流';

CREATE TABLE IF NOT EXISTS job_component (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '组件ID',
  tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID（0为系统租户）',
  component_code VARCHAR(64) NOT NULL COMMENT '组件编码',
  component_name VARCHAR(128) NOT NULL COMMENT '组件名称',
  component_type VARCHAR(64) NOT NULL COMMENT '组件类型',
  job_params JSON NULL COMMENT '参数定义',
  description VARCHAR(512) NULL COMMENT '描述',
  icon VARCHAR(128) NULL COMMENT '图标',
  color VARCHAR(32) NULL COMMENT '颜色',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0禁用',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uq_component_code (tenant_id, component_code),
  KEY idx_component_type (tenant_id, component_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务组件';

CREATE TABLE IF NOT EXISTS job_info (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '任务ID',
  tenant_id BIGINT NOT NULL COMMENT '租户ID',
  workflow_id BIGINT NOT NULL COMMENT '工作流ID',
  job_name VARCHAR(128) NOT NULL COMMENT '任务名称',
  job_type BIGINT NOT NULL COMMENT '任务类型（组件ID）',
  job_params JSON NULL COMMENT '任务参数',
  timeout_seconds INT NULL COMMENT '超时秒数',
  max_retry_times INT NULL COMMENT '最大重试次数',
  retry_interval INT NULL COMMENT '重试间隔（秒）',
  priority INT NULL COMMENT '优先级',
  position_x DOUBLE NULL COMMENT '画布X坐标',
  position_y DOUBLE NULL COMMENT '画布Y坐标',
  description VARCHAR(512) NULL COMMENT '描述',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY idx_job_tenant (tenant_id),
  KEY idx_job_workflow (tenant_id, workflow_id),
  CONSTRAINT fk_job_workflow FOREIGN KEY (workflow_id) REFERENCES job_workflow(id),
  CONSTRAINT fk_job_component FOREIGN KEY (job_type) REFERENCES job_component(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务';

CREATE TABLE IF NOT EXISTS job_dependency (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '依赖ID',
  tenant_id BIGINT NOT NULL COMMENT '租户ID',
  workflow_id BIGINT NOT NULL COMMENT '工作流ID',
  job_id BIGINT NOT NULL COMMENT '任务ID',
  parent_job_id BIGINT NOT NULL COMMENT '父任务ID',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY idx_dependency_tenant (tenant_id),
  KEY idx_dependency_workflow (tenant_id, workflow_id),
  KEY idx_dependency_job (tenant_id, job_id),
  CONSTRAINT fk_dependency_workflow FOREIGN KEY (workflow_id) REFERENCES job_workflow(id),
  CONSTRAINT fk_dependency_job FOREIGN KEY (job_id) REFERENCES job_info(id),
  CONSTRAINT fk_dependency_parent_job FOREIGN KEY (parent_job_id) REFERENCES job_info(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务依赖';

CREATE TABLE IF NOT EXISTS knowledge_namespace (
  namespace_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '命名空间ID',
  tenant_id BIGINT NOT NULL COMMENT '租户ID',
  namespace VARCHAR(128) NOT NULL COMMENT '命名空间标识',
  description VARCHAR(512) NULL COMMENT '命名空间描述',
  created_by BIGINT NOT NULL COMMENT '创建人用户ID',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0禁用',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (namespace_id),
  UNIQUE KEY uk_namespace_creator (tenant_id, created_by, namespace),
  KEY idx_namespace_tenant (tenant_id),
  KEY idx_namespace_creator (tenant_id, created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识命名空间';

CREATE TABLE IF NOT EXISTS knowledge_document (
  document_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '文档ID',
  tenant_id BIGINT NOT NULL COMMENT '租户ID',
  namespace_id BIGINT UNSIGNED NOT NULL COMMENT '命名空间ID',
  doc_uid VARCHAR(64) NOT NULL COMMENT '稳定文档ID',
  title VARCHAR(255) NOT NULL COMMENT '文档标题',
  file_type VARCHAR(32) NOT NULL COMMENT '文件类型',
  size_bytes BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
  storage_uri VARCHAR(1024) NULL COMMENT '存储URI',
  storage_type VARCHAR(32) NULL COMMENT '存储类型',
  storage_key VARCHAR(255) NULL COMMENT '对象存储Key',
  status VARCHAR(32) NOT NULL DEFAULT 'processing' COMMENT '处理状态',
  chunk_count INT NOT NULL DEFAULT 0 COMMENT '切片数量',
  token_count INT NOT NULL DEFAULT 0 COMMENT 'token统计',
  error_message VARCHAR(1024) NULL COMMENT '失败原因',
  embedding_model_id BIGINT UNSIGNED NULL COMMENT 'Embedding模型ID',
  embedding_dimension INT NULL COMMENT '向量维度',
  chunk_mode VARCHAR(32) NULL COMMENT '切分模式',
  chunk_config_json JSON NULL COMMENT '切分配置快照',
  last_chunked_at TIMESTAMP NULL COMMENT '最近切分时间',
  created_by BIGINT NOT NULL COMMENT '创建人用户ID',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (document_id),
  KEY idx_doc_namespace (tenant_id, namespace_id),
  KEY idx_doc_namespace_status (tenant_id, namespace_id, status),
  KEY idx_doc_created_by (tenant_id, created_by),
  KEY idx_doc_uid (tenant_id, doc_uid),
  KEY idx_doc_embedding_model (tenant_id, embedding_model_id),
  CONSTRAINT fk_doc_namespace FOREIGN KEY (namespace_id) REFERENCES knowledge_namespace(namespace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识文档';

CREATE TABLE IF NOT EXISTS knowledge_document_job (
  job_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  tenant_id BIGINT NOT NULL COMMENT '租户ID',
  namespace_id BIGINT UNSIGNED NOT NULL COMMENT '命名空间ID',
  document_id BIGINT UNSIGNED NOT NULL COMMENT '文档ID',
  job_type VARCHAR(32) NOT NULL COMMENT '任务类型',
  status VARCHAR(32) NOT NULL DEFAULT 'queued' COMMENT '任务状态',
  progress TINYINT NOT NULL DEFAULT 0 COMMENT '进度百分比',
  progress_seq BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '进度序列号',
  total_chunks INT NOT NULL DEFAULT 0 COMMENT '预计切片数',
  processed_chunks INT NOT NULL DEFAULT 0 COMMENT '已处理切片数',
  error_message VARCHAR(1024) NULL COMMENT '失败原因',
  started_at TIMESTAMP NULL COMMENT '开始时间',
  finished_at TIMESTAMP NULL COMMENT '完成时间',
  created_by BIGINT NOT NULL COMMENT '创建人用户ID',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (job_id),
  KEY idx_job_namespace (tenant_id, namespace_id),
  KEY idx_job_document (tenant_id, document_id),
  KEY idx_job_status (tenant_id, status),
  KEY idx_job_created_by (tenant_id, created_by),
  CONSTRAINT fk_kdj_namespace FOREIGN KEY (namespace_id) REFERENCES knowledge_namespace(namespace_id),
  CONSTRAINT fk_kdj_document FOREIGN KEY (document_id) REFERENCES knowledge_document(document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档切分任务';

CREATE TABLE IF NOT EXISTS ai_llm_usage (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  tenant_id BIGINT NOT NULL COMMENT '租户ID',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  user_id VARCHAR(64) NOT NULL COMMENT '用户ID',
  session_id VARCHAR(128) NOT NULL COMMENT '会话ID',
  module VARCHAR(32) NOT NULL COMMENT '模块标识',
  agent_id VARCHAR(64) NOT NULL COMMENT '智能体ID',
  provider VARCHAR(32) NULL COMMENT '模型供应商',
  model_name VARCHAR(128) NULL COMMENT '模型名称',
  run_id VARCHAR(64) NOT NULL COMMENT '调用ID',
  parent_run_id VARCHAR(64) NULL COMMENT '父调用ID',
  prompt_tokens INT NULL COMMENT '提示词token数',
  completion_tokens INT NULL COMMENT '补全token数',
  total_tokens INT NULL COMMENT '总token数',
  estimated TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否估算',
  prompt_cost_usd DECIMAL(18, 8) NULL COMMENT '提示词成本(USD)',
  completion_cost_usd DECIMAL(18, 8) NULL COMMENT '补全成本(USD)',
  total_cost_usd DECIMAL(18, 8) NULL COMMENT '总成本(USD)',
  raw_usage_json JSON NULL COMMENT '原始统计JSON',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_llm_usage_run_id (tenant_id, run_id),
  KEY idx_ai_llm_usage_user_session (tenant_id, user_id, session_id),
  KEY idx_ai_llm_usage_module_agent (tenant_id, module, agent_id),
  KEY idx_ai_llm_usage_model (tenant_id, provider, model_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LLM调用用量';
