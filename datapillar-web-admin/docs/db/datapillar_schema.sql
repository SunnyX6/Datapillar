-- Datapillar unified schema (multi-tenant)
-- Notes:
-- 1) tenant_id is the hard isolation boundary for all data and RBAC tables.
-- 2) tenant_id = 0 indicates global/shared records (e.g., common components/permissions).

-- =========================
-- Tenants
-- =========================
CREATE TABLE IF NOT EXISTS tenants (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '租户ID',
  parent_id BIGINT NULL COMMENT '上级租户ID',
  code VARCHAR(64) NOT NULL COMMENT '租户编码',
  name VARCHAR(128) NOT NULL COMMENT '租户名称',
  type VARCHAR(32) NOT NULL COMMENT '租户类型',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0禁用',
  level INT NOT NULL DEFAULT 1 COMMENT '层级深度',
  path VARCHAR(512) NOT NULL COMMENT '层级路径，如 /1/3/8',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uq_tenant_code (code),
  KEY idx_tenant_parent (parent_id),
  CONSTRAINT fk_tenant_parent FOREIGN KEY (parent_id) REFERENCES tenants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户';

CREATE TABLE IF NOT EXISTS users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
  tenant_id BIGINT NOT NULL COMMENT '主租户ID（默认租户）',
  username VARCHAR(64) NOT NULL COMMENT '用户名',
  password VARCHAR(255) NOT NULL COMMENT '密码哈希',
  nickname VARCHAR(64) NULL COMMENT '昵称',
  email VARCHAR(128) NULL COMMENT '邮箱',
  phone VARCHAR(32) NULL COMMENT '手机号',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0禁用',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uq_user_username (username),
  UNIQUE KEY uq_user_email (email),
  KEY idx_user_tenant (tenant_id),
  CONSTRAINT fk_user_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户（全局身份）';

CREATE TABLE IF NOT EXISTS tenant_users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  tenant_id BIGINT NOT NULL COMMENT '租户ID',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0禁用',
  is_default TINYINT NOT NULL DEFAULT 0 COMMENT '是否默认租户',
  token_sign VARCHAR(255) NULL COMMENT 'Token签名（租户维度）',
  token_expire_time DATETIME NULL COMMENT 'Token过期时间（租户维度）',
  joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  UNIQUE KEY uq_tenant_user (tenant_id, user_id),
  KEY idx_tu_user (user_id),
  CONSTRAINT fk_tu_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  CONSTRAINT fk_tu_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户成员关系';

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
  CONSTRAINT fk_org_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
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
  CONSTRAINT fk_org_user_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  CONSTRAINT fk_org_user_org FOREIGN KEY (org_id) REFERENCES orgs(id),
  CONSTRAINT fk_org_user_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组织成员关系';

CREATE TABLE IF NOT EXISTS user_identities (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  tenant_id BIGINT NOT NULL COMMENT '租户ID',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  provider VARCHAR(32) NOT NULL COMMENT '身份来源：wecom/dingtalk/feishu等',
  external_user_id VARCHAR(128) NOT NULL COMMENT '外部用户ID',
  union_id VARCHAR(128) NULL COMMENT '外部统一标识',
  open_id VARCHAR(128) NULL COMMENT '外部OpenID',
  email VARCHAR(128) NULL COMMENT '外部邮箱',
  mobile VARCHAR(32) NULL COMMENT '外部手机号',
  profile_json JSON NULL COMMENT '外部用户扩展信息',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0禁用',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uq_user_identity (tenant_id, provider, external_user_id),
  KEY idx_user_identity_user (tenant_id, user_id),
  CONSTRAINT fk_user_identity_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_user_identity_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户身份映射';

CREATE TABLE IF NOT EXISTS tenant_sso_configs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  tenant_id BIGINT NOT NULL COMMENT '租户ID',
  provider VARCHAR(32) NOT NULL COMMENT 'SSO提供方：dingtalk/wecom/feishu/lark',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0禁用',
  base_url VARCHAR(255) NULL COMMENT '开放平台基础域名/环境（可选）',
  config_json JSON NOT NULL COMMENT 'SSO配置JSON（clientId/clientSecret/redirectUri/scope/corpId等）',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uq_tenant_sso (tenant_id, provider),
  KEY idx_tenant_sso_tenant (tenant_id),
  CONSTRAINT fk_tenant_sso_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户SSO配置';

-- =========================
-- RBAC
-- =========================
CREATE TABLE IF NOT EXISTS permission_object_categories (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '分类ID',
  tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID（0为全局）',
  code VARCHAR(64) NOT NULL COMMENT '分类编码',
  name VARCHAR(64) NOT NULL COMMENT '分类名称',
  description VARCHAR(255) NULL COMMENT '分类说明',
  sort INT NOT NULL DEFAULT 0 COMMENT '排序值',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0禁用',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uq_permission_object_category_code (tenant_id, code),
  UNIQUE KEY uq_permission_object_category_name (tenant_id, name),
  KEY idx_permission_object_category_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限对象分类';

CREATE TABLE IF NOT EXISTS permission_objects (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '对象ID',
  tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID（0为全局）',
  parent_id BIGINT NULL COMMENT '父对象ID',
  type ENUM('MENU','PAGE') NOT NULL COMMENT '对象类型：MENU/PAGE',
  name VARCHAR(64) NOT NULL COMMENT '对象名称',
  category_id BIGINT NULL COMMENT '分类ID',
  path VARCHAR(128) NOT NULL COMMENT '对象路径',
  location ENUM('TOP','SIDEBAR','PROFILE','PAGE') NOT NULL COMMENT '位置',
  description VARCHAR(255) NULL COMMENT '对象说明',
  sort INT NOT NULL DEFAULT 0 COMMENT '排序值',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0禁用',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uq_menu_path (tenant_id, path),
  KEY idx_permission_object_category (tenant_id, category_id, sort),
  KEY idx_permission_object_parent (tenant_id, parent_id),
  KEY idx_permission_object_type (tenant_id, type, sort),
  CONSTRAINT fk_permission_object_category FOREIGN KEY (category_id) REFERENCES permission_object_categories(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限对象';

CREATE TABLE IF NOT EXISTS permissions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '权限ID',
  tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID（0为全局）',
  code VARCHAR(32) NOT NULL COMMENT '权限标识',
  name VARCHAR(64) NOT NULL COMMENT '权限名称',
  description VARCHAR(255) NULL COMMENT '权限说明',
  sort INT NOT NULL DEFAULT 0 COMMENT '排序值',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0禁用',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uq_permission_code (tenant_id, code),
  KEY idx_permission_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限集合';

CREATE TABLE IF NOT EXISTS roles (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '角色ID',
  tenant_id BIGINT NOT NULL COMMENT '租户ID',
  type ENUM('ADMIN','USER') NOT NULL DEFAULT 'USER' COMMENT '角色类型',
  name VARCHAR(64) NOT NULL COMMENT '角色名称',
  description VARCHAR(255) NULL COMMENT '角色说明',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0禁用',
  sort INT NOT NULL DEFAULT 0 COMMENT '排序值',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uq_role_name (tenant_id, name),
  KEY idx_role_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色';

CREATE TABLE IF NOT EXISTS user_invitations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '邀请ID',
  tenant_id BIGINT NOT NULL COMMENT '租户ID',
  inviter_user_id BIGINT NOT NULL COMMENT '邀请人用户ID',
  invitee_email VARCHAR(128) NULL COMMENT '被邀请人邮箱',
  invitee_mobile VARCHAR(32) NULL COMMENT '被邀请人手机号',
  invitee_key VARCHAR(128) NOT NULL COMMENT '被邀请人唯一键（邮箱/手机号归一化）',
  active_invitee_key VARCHAR(128) NULL COMMENT '待接受唯一键（status=0时=invitee_key，其它状态为NULL）',
  invite_code VARCHAR(64) NOT NULL COMMENT '邀请码',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0待接受，1已接受，2已过期，3已取消',
  expires_at DATETIME NULL COMMENT '过期时间',
  accepted_user_id BIGINT NULL COMMENT '接受邀请的用户ID',
  accepted_at DATETIME NULL COMMENT '接受时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uq_invite_code (invite_code),
  UNIQUE KEY uq_invite_active (tenant_id, active_invitee_key),
  KEY idx_invite_tenant (tenant_id, status),
  KEY idx_invite_email (tenant_id, invitee_email),
  KEY idx_invite_mobile (tenant_id, invitee_mobile),
  KEY idx_invite_key (tenant_id, invitee_key),
  CHECK (invitee_email IS NOT NULL OR invitee_mobile IS NOT NULL),
  CONSTRAINT fk_invite_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  CONSTRAINT fk_invite_inviter FOREIGN KEY (inviter_user_id) REFERENCES users(id),
  CONSTRAINT fk_invite_accepted_user FOREIGN KEY (accepted_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户邀请';

CREATE TABLE IF NOT EXISTS user_invitation_orgs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  invitation_id BIGINT NOT NULL COMMENT '邀请ID',
  org_id BIGINT NOT NULL COMMENT '组织ID',
  UNIQUE KEY uq_invitation_org (invitation_id, org_id),
  CONSTRAINT fk_invitation_org_invitation FOREIGN KEY (invitation_id) REFERENCES user_invitations(id),
  CONSTRAINT fk_invitation_org_org FOREIGN KEY (org_id) REFERENCES orgs(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邀请关联组织';

CREATE TABLE IF NOT EXISTS user_invitation_roles (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  invitation_id BIGINT NOT NULL COMMENT '邀请ID',
  role_id BIGINT NOT NULL COMMENT '角色ID',
  UNIQUE KEY uq_invitation_role (invitation_id, role_id),
  CONSTRAINT fk_invitation_role_invitation FOREIGN KEY (invitation_id) REFERENCES user_invitations(id),
  CONSTRAINT fk_invitation_role_role FOREIGN KEY (role_id) REFERENCES roles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邀请关联角色';

CREATE TABLE IF NOT EXISTS user_roles (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  tenant_id BIGINT NOT NULL COMMENT '租户ID',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  role_id BIGINT NOT NULL COMMENT '角色ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uq_user_role (tenant_id, user_id, role_id),
  KEY idx_user_role_user (tenant_id, user_id),
  KEY idx_user_role_role (tenant_id, role_id),
  CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES roles(id),
  CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关系';

CREATE TABLE IF NOT EXISTS user_permission_overrides (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  tenant_id BIGINT NOT NULL COMMENT '租户ID',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  object_id BIGINT NOT NULL COMMENT '权限对象ID',
  permission_id BIGINT NOT NULL COMMENT '权限ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uq_user_permission (tenant_id, user_id, object_id, permission_id),
  KEY idx_user_permission_user (tenant_id, user_id),
  KEY idx_user_permission_object (tenant_id, object_id),
  CONSTRAINT fk_user_permission_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_user_permission_object FOREIGN KEY (object_id) REFERENCES permission_objects(id),
  CONSTRAINT fk_user_permission_permission FOREIGN KEY (permission_id) REFERENCES permissions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户权限覆盖';

CREATE TABLE IF NOT EXISTS role_permissions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  tenant_id BIGINT NOT NULL COMMENT '租户ID',
  role_id BIGINT NOT NULL COMMENT '角色ID',
  object_id BIGINT NOT NULL COMMENT '权限对象ID',
  permission_id BIGINT NOT NULL COMMENT '权限ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uq_role_permission (tenant_id, role_id, object_id, permission_id),
  KEY idx_role_permission_role (tenant_id, role_id),
  KEY idx_role_permission_object (tenant_id, object_id),
  CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES roles(id),
  CONSTRAINT fk_role_permission_object FOREIGN KEY (object_id) REFERENCES permission_objects(id),
  CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES permissions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关系';

-- =========================
-- Projects
-- =========================
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
  KEY idx_project_owner (tenant_id, owner_id),
  CONSTRAINT fk_project_owner FOREIGN KEY (owner_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目';

-- =========================
-- Workflow
-- =========================
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
  tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID（0为全局）',
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

-- =========================
-- Knowledge Wiki
-- =========================
CREATE TABLE IF NOT EXISTS knowledge_namespace (
  namespace_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '命名空间ID',
  tenant_id BIGINT NOT NULL COMMENT '租户ID',
  namespace VARCHAR(128) NOT NULL COMMENT '命名空间标识',
  description VARCHAR(512) NULL COMMENT '命名空间描述',
  created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0禁用',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (namespace_id),
  UNIQUE KEY uk_namespace_creator (tenant_id, created_by, namespace),
  KEY idx_namespace_tenant (tenant_id),
  KEY idx_namespace_creator (tenant_id, created_by),
  CONSTRAINT fk_namespace_creator FOREIGN KEY (created_by) REFERENCES users(id)
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
  created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (document_id),
  KEY idx_doc_namespace (tenant_id, namespace_id),
  KEY idx_doc_namespace_status (tenant_id, namespace_id, status),
  KEY idx_doc_created_by (tenant_id, created_by),
  KEY idx_doc_uid (tenant_id, doc_uid),
  KEY idx_doc_embedding_model (tenant_id, embedding_model_id),
  CONSTRAINT fk_doc_namespace FOREIGN KEY (namespace_id) REFERENCES knowledge_namespace(namespace_id),
  CONSTRAINT fk_doc_creator FOREIGN KEY (created_by) REFERENCES users(id)
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
  created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (job_id),
  KEY idx_job_namespace (tenant_id, namespace_id),
  KEY idx_job_document (tenant_id, document_id),
  KEY idx_job_status (tenant_id, status),
  KEY idx_job_created_by (tenant_id, created_by),
  CONSTRAINT fk_kdj_namespace FOREIGN KEY (namespace_id) REFERENCES knowledge_namespace(namespace_id),
  CONSTRAINT fk_kdj_document FOREIGN KEY (document_id) REFERENCES knowledge_document(document_id),
  CONSTRAINT fk_kdj_creator FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档切分任务';

-- =========================
-- AI LLM Usage
-- =========================
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

-- =========================
-- Seed Data (Platform tenant & RBAC)
-- =========================
-- 平台租户（SaaS 管理租户）
INSERT INTO tenants (parent_id, code, name, type, status, level, path)
SELECT NULL, 'platform', '平台租户', 'PLATFORM', 1, 1, '/platform'
WHERE NOT EXISTS (SELECT 1 FROM tenants WHERE code = 'platform');

SET @platform_tenant_id := (SELECT id FROM tenants WHERE code = 'platform' LIMIT 1);

-- 平台超管用户（admin / 123456asd）
INSERT INTO users (tenant_id, username, password, nickname, email, phone, status, deleted)
SELECT @platform_tenant_id, 'admin', '$2a$10$i3GAzhUoDzqI1Xv/.zrHsuK4CyKg4dmP3LplgD5LvGGkHaRBWRe9S', 'admin', 'admin@datapillar.com', NULL, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin');

SET @admin_user_id := (SELECT id FROM users WHERE username = 'admin' LIMIT 1);

INSERT INTO tenant_users (tenant_id, user_id, status, is_default)
SELECT @platform_tenant_id, @admin_user_id, 1, 1
WHERE @platform_tenant_id IS NOT NULL
  AND @admin_user_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM tenant_users WHERE tenant_id = @platform_tenant_id AND user_id = @admin_user_id
  );

-- RBAC 全局数据（tenant_id = 0）
INSERT INTO permission_object_categories (tenant_id, code, name, description, sort, status)
SELECT 0, 'BUILD_DESIGN', '构建与设计', NULL, 1, 1
WHERE NOT EXISTS (
  SELECT 1 FROM permission_object_categories WHERE tenant_id = 0 AND code = 'BUILD_DESIGN'
);
INSERT INTO permission_object_categories (tenant_id, code, name, description, sort, status)
SELECT 0, 'COMPUTE_CONNECT', '计算与连接', NULL, 2, 1
WHERE NOT EXISTS (
  SELECT 1 FROM permission_object_categories WHERE tenant_id = 0 AND code = 'COMPUTE_CONNECT'
);
INSERT INTO permission_object_categories (tenant_id, code, name, description, sort, status)
SELECT 0, 'OBSERVE', '观测', NULL, 3, 1
WHERE NOT EXISTS (
  SELECT 1 FROM permission_object_categories WHERE tenant_id = 0 AND code = 'OBSERVE'
);

INSERT INTO permissions (tenant_id, code, name, description, sort, status)
SELECT 0, 'READ', '读', NULL, 1, 1
WHERE NOT EXISTS (
  SELECT 1 FROM permissions WHERE tenant_id = 0 AND code = 'READ'
);
INSERT INTO permissions (tenant_id, code, name, description, sort, status)
SELECT 0, 'WRITE', '写', NULL, 2, 1
WHERE NOT EXISTS (
  SELECT 1 FROM permissions WHERE tenant_id = 0 AND code = 'WRITE'
);

SET @cat_build_design_id := (SELECT id FROM permission_object_categories WHERE tenant_id = 0 AND code = 'BUILD_DESIGN' LIMIT 1);
SET @cat_compute_connect_id := (SELECT id FROM permission_object_categories WHERE tenant_id = 0 AND code = 'COMPUTE_CONNECT' LIMIT 1);
SET @cat_observe_id := (SELECT id FROM permission_object_categories WHERE tenant_id = 0 AND code = 'OBSERVE' LIMIT 1);

-- 菜单对象（MENU）
INSERT INTO permission_objects (tenant_id, parent_id, type, name, category_id, path, location, description, sort, status)
SELECT 0, NULL, 'MENU', '知识 Wiki', @cat_build_design_id, '/wiki', 'SIDEBAR', NULL, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM permission_objects WHERE tenant_id = 0 AND path = '/wiki');
INSERT INTO permission_objects (tenant_id, parent_id, type, name, category_id, path, location, description, sort, status)
SELECT 0, NULL, 'MENU', '工作流构建', @cat_build_design_id, '/workflow', 'SIDEBAR', NULL, 2, 1
WHERE NOT EXISTS (SELECT 1 FROM permission_objects WHERE tenant_id = 0 AND path = '/workflow');
INSERT INTO permission_objects (tenant_id, parent_id, type, name, category_id, path, location, description, sort, status)
SELECT 0, NULL, 'MENU', '统一开发 IDE', @cat_build_design_id, '/ide', 'SIDEBAR', NULL, 3, 1
WHERE NOT EXISTS (SELECT 1 FROM permission_objects WHERE tenant_id = 0 AND path = '/ide');
INSERT INTO permission_objects (tenant_id, parent_id, type, name, category_id, path, location, description, sort, status)
SELECT 0, NULL, 'MENU', '数据埋点', @cat_compute_connect_id, '/data-tracking', 'SIDEBAR', NULL, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM permission_objects WHERE tenant_id = 0 AND path = '/data-tracking');
INSERT INTO permission_objects (tenant_id, parent_id, type, name, category_id, path, location, description, sort, status)
SELECT 0, NULL, 'MENU', '计算仓', @cat_compute_connect_id, '/compute-warehouse', 'SIDEBAR', NULL, 2, 1
WHERE NOT EXISTS (SELECT 1 FROM permission_objects WHERE tenant_id = 0 AND path = '/compute-warehouse');
INSERT INTO permission_objects (tenant_id, parent_id, type, name, category_id, path, location, description, sort, status)
SELECT 0, NULL, 'MENU', '发布', @cat_observe_id, '/deployments', 'SIDEBAR', NULL, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM permission_objects WHERE tenant_id = 0 AND path = '/deployments');
INSERT INTO permission_objects (tenant_id, parent_id, type, name, category_id, path, location, description, sort, status)
SELECT 0, NULL, 'MENU', '日志', @cat_observe_id, '/logs', 'SIDEBAR', NULL, 2, 1
WHERE NOT EXISTS (SELECT 1 FROM permission_objects WHERE tenant_id = 0 AND path = '/logs');
INSERT INTO permission_objects (tenant_id, parent_id, type, name, category_id, path, location, description, sort, status)
SELECT 0, NULL, 'MENU', '版本历史', @cat_observe_id, '/version', 'SIDEBAR', NULL, 3, 1
WHERE NOT EXISTS (SELECT 1 FROM permission_objects WHERE tenant_id = 0 AND path = '/version');
INSERT INTO permission_objects (tenant_id, parent_id, type, name, category_id, path, location, description, sort, status)
SELECT 0, NULL, 'MENU', 'Git 运维', @cat_observe_id, '/git', 'SIDEBAR', NULL, 4, 1
WHERE NOT EXISTS (SELECT 1 FROM permission_objects WHERE tenant_id = 0 AND path = '/git');
INSERT INTO permission_objects (tenant_id, parent_id, type, name, category_id, path, location, description, sort, status)
SELECT 0, NULL, 'MENU', '数据驾驶舱', NULL, '/home', 'TOP', NULL, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM permission_objects WHERE tenant_id = 0 AND path = '/home');
INSERT INTO permission_objects (tenant_id, parent_id, type, name, category_id, path, location, description, sort, status)
SELECT 0, NULL, 'MENU', '数据治理', NULL, '/governance', 'TOP', NULL, 2, 1
WHERE NOT EXISTS (SELECT 1 FROM permission_objects WHERE tenant_id = 0 AND path = '/governance');
INSERT INTO permission_objects (tenant_id, parent_id, type, name, category_id, path, location, description, sort, status)
SELECT 0, NULL, 'MENU', '项目', NULL, '/projects', 'TOP', NULL, 3, 1
WHERE NOT EXISTS (SELECT 1 FROM permission_objects WHERE tenant_id = 0 AND path = '/projects');
INSERT INTO permission_objects (tenant_id, parent_id, type, name, category_id, path, location, description, sort, status)
SELECT 0, NULL, 'MENU', '团队协作', NULL, '/collaboration', 'TOP', NULL, 4, 1
WHERE NOT EXISTS (SELECT 1 FROM permission_objects WHERE tenant_id = 0 AND path = '/collaboration');
INSERT INTO permission_objects (tenant_id, parent_id, type, name, category_id, path, location, description, sort, status)
SELECT 0, NULL, 'MENU', '个人中心', NULL, '/profile', 'PROFILE', NULL, 5, 1
WHERE NOT EXISTS (SELECT 1 FROM permission_objects WHERE tenant_id = 0 AND path = '/profile');

SET @governance_id := (SELECT id FROM permission_objects WHERE tenant_id = 0 AND path = '/governance' LIMIT 1);
SET @profile_id := (SELECT id FROM permission_objects WHERE tenant_id = 0 AND path = '/profile' LIMIT 1);

INSERT INTO permission_objects (tenant_id, parent_id, type, name, category_id, path, location, description, sort, status)
SELECT 0, @governance_id, 'MENU', '元数据', NULL, '/governance/metadata', 'TOP', NULL, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM permission_objects WHERE tenant_id = 0 AND path = '/governance/metadata');
INSERT INTO permission_objects (tenant_id, parent_id, type, name, category_id, path, location, description, sort, status)
SELECT 0, @governance_id, 'MENU', '元语义', NULL, '/governance/semantic', 'TOP', NULL, 2, 1
WHERE NOT EXISTS (SELECT 1 FROM permission_objects WHERE tenant_id = 0 AND path = '/governance/semantic');
INSERT INTO permission_objects (tenant_id, parent_id, type, name, category_id, path, location, description, sort, status)
SELECT 0, @governance_id, 'MENU', '知识图谱RAG', NULL, '/governance/knowledge', 'TOP', NULL, 3, 1
WHERE NOT EXISTS (SELECT 1 FROM permission_objects WHERE tenant_id = 0 AND path = '/governance/knowledge');

INSERT INTO permission_objects (tenant_id, parent_id, type, name, category_id, path, location, description, sort, status)
SELECT 0, @profile_id, 'MENU', '权限分配', NULL, '/profile/permission', 'PROFILE', NULL, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM permission_objects WHERE tenant_id = 0 AND path = '/profile/permission');
INSERT INTO permission_objects (tenant_id, parent_id, type, name, category_id, path, location, description, sort, status)
SELECT 0, @profile_id, 'MENU', 'AI 配置', NULL, '/profile/llm/models', 'PROFILE', NULL, 2, 1
WHERE NOT EXISTS (SELECT 1 FROM permission_objects WHERE tenant_id = 0 AND path = '/profile/llm/models');
INSERT INTO permission_objects (tenant_id, parent_id, type, name, category_id, path, location, description, sort, status)
SELECT 0, @profile_id, 'MENU', '账单', NULL, '/profile/billing', 'PROFILE', NULL, 3, 1
WHERE NOT EXISTS (SELECT 1 FROM permission_objects WHERE tenant_id = 0 AND path = '/profile/billing');

-- 角色（每个租户一条超级管理员）
INSERT INTO roles (tenant_id, type, name, description, sort, status)
SELECT t.id, 'ADMIN', '超级管理员', NULL, 1, 1
FROM tenants t
WHERE NOT EXISTS (
  SELECT 1 FROM roles r WHERE r.tenant_id = t.id AND r.name = '超级管理员'
);

-- 绑定超级管理员权限
INSERT INTO role_permissions (tenant_id, role_id, object_id, permission_id)
SELECT r.tenant_id, r.id, o.id, p.id
FROM roles r
JOIN permission_objects o ON o.tenant_id = 0
JOIN permissions p ON p.tenant_id = 0
WHERE r.name = '超级管理员'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.tenant_id = r.tenant_id
      AND rp.role_id = r.id
      AND rp.object_id = o.id
      AND rp.permission_id = p.id
  );

-- 给平台超管用户绑定角色
INSERT INTO user_roles (tenant_id, user_id, role_id)
SELECT r.tenant_id, u.id, r.id
FROM users u
JOIN roles r ON r.tenant_id = u.tenant_id AND r.name = '超级管理员'
WHERE u.username = 'admin'
  AND NOT EXISTS (
    SELECT 1 FROM user_roles ur
    WHERE ur.tenant_id = r.tenant_id
      AND ur.user_id = u.id
      AND ur.role_id = r.id
  );
