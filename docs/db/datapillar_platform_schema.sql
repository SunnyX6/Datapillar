-- Datapillar platform schema
-- 说明：平台库仅存平台级多租户/IAM/功能对象/授权等数据

CREATE DATABASE IF NOT EXISTS datapillar_platform DEFAULT CHARSET=utf8mb4;
USE datapillar_platform;

SET FOREIGN_KEY_CHECKS=0;
DROP TABLE IF EXISTS tenant_feature_audit;
DROP TABLE IF EXISTS tenant_feature_permissions;
DROP TABLE IF EXISTS role_permissions;
DROP TABLE IF EXISTS user_permission_overrides;
DROP TABLE IF EXISTS user_roles;
DROP TABLE IF EXISTS user_invitation_roles;
DROP TABLE IF EXISTS user_invitation_orgs;
DROP TABLE IF EXISTS user_invitations;
DROP TABLE IF EXISTS roles;
DROP TABLE IF EXISTS permissions;
DROP TABLE IF EXISTS feature_objects;
DROP TABLE IF EXISTS feature_object_categories;
DROP TABLE IF EXISTS tenant_sso_configs;
DROP TABLE IF EXISTS user_identities;
DROP TABLE IF EXISTS tenant_users;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS tenants;
SET FOREIGN_KEY_CHECKS=1;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户（全局身份，tenant_id 为主租户）';

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

CREATE TABLE IF NOT EXISTS feature_object_categories (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '分类ID',
  tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID（0为系统租户）',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='功能对象分类';

CREATE TABLE IF NOT EXISTS feature_objects (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '对象ID',
  tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID（0为系统租户）',
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
  CONSTRAINT fk_permission_object_category FOREIGN KEY (category_id) REFERENCES feature_object_categories(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='功能对象';

CREATE TABLE IF NOT EXISTS permissions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '权限ID',
  tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID（0为系统租户）',
  code VARCHAR(32) NOT NULL COMMENT '权限标识',
  name VARCHAR(64) NOT NULL COMMENT '权限名称',
  description VARCHAR(255) NULL COMMENT '权限说明',
  level INT NOT NULL DEFAULT 0 COMMENT '权限等级（数值越大权限越高）',
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
  CONSTRAINT fk_invitation_org_invitation FOREIGN KEY (invitation_id) REFERENCES user_invitations(id)
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
  object_id BIGINT NOT NULL COMMENT '功能对象ID',
  permission_id BIGINT NOT NULL COMMENT '权限ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uq_user_permission (tenant_id, user_id, object_id, permission_id),
  KEY idx_user_permission_user (tenant_id, user_id),
  KEY idx_user_permission_object (tenant_id, object_id),
  CONSTRAINT fk_user_permission_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_user_permission_object FOREIGN KEY (object_id) REFERENCES feature_objects(id),
  CONSTRAINT fk_user_permission_permission FOREIGN KEY (permission_id) REFERENCES permissions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户权限覆盖';

CREATE TABLE IF NOT EXISTS role_permissions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  tenant_id BIGINT NOT NULL COMMENT '租户ID',
  role_id BIGINT NOT NULL COMMENT '角色ID',
  object_id BIGINT NOT NULL COMMENT '功能对象ID',
  permission_id BIGINT NOT NULL COMMENT '权限ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uq_role_permission (tenant_id, role_id, object_id, permission_id),
  KEY idx_role_permission_role (tenant_id, role_id),
  KEY idx_role_permission_object (tenant_id, object_id),
  CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES roles(id),
  CONSTRAINT fk_role_permission_object FOREIGN KEY (object_id) REFERENCES feature_objects(id),
  CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES permissions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关系';

CREATE TABLE IF NOT EXISTS tenant_feature_permissions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '授权ID',
  tenant_id BIGINT NOT NULL COMMENT '租户ID',
  object_id BIGINT NOT NULL COMMENT '功能对象ID',
  permission_id BIGINT NOT NULL COMMENT '租户可用的最高权限',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0禁用',
  grant_source ENUM('SYSTEM','ADMIN','PLAN') NOT NULL DEFAULT 'ADMIN' COMMENT '授权来源',
  granted_by BIGINT NULL COMMENT '授权人用户ID',
  granted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间',
  updated_by BIGINT NULL COMMENT '更新人用户ID',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uq_tenant_feature (tenant_id, object_id),
  KEY idx_tenant_feature_status (tenant_id, status),
  CONSTRAINT fk_tenant_feature_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  CONSTRAINT fk_tenant_feature_object FOREIGN KEY (object_id) REFERENCES feature_objects(id),
  CONSTRAINT fk_tenant_feature_permission FOREIGN KEY (permission_id) REFERENCES permissions(id),
  CONSTRAINT fk_tenant_feature_granted_by FOREIGN KEY (granted_by) REFERENCES users(id),
  CONSTRAINT fk_tenant_feature_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户功能授权';

CREATE TABLE IF NOT EXISTS tenant_feature_audit (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '审计ID',
  tenant_id BIGINT NOT NULL COMMENT '租户ID',
  object_id BIGINT NOT NULL COMMENT '功能对象ID',
  action ENUM('GRANT','REVOKE','ENABLE','DISABLE','UPDATE_PERMISSION') NOT NULL COMMENT '动作',
  before_status TINYINT NULL COMMENT '变更前状态',
  after_status TINYINT NOT NULL COMMENT '变更后状态',
  before_permission_id BIGINT NULL COMMENT '变更前权限',
  after_permission_id BIGINT NULL COMMENT '变更后权限',
  operator_user_id BIGINT NOT NULL COMMENT '操作人用户ID',
  operator_tenant_id BIGINT NOT NULL COMMENT '操作人租户ID（超管=0）',
  reason VARCHAR(255) NULL COMMENT '原因',
  request_id VARCHAR(64) NULL COMMENT '请求ID/追踪ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  KEY idx_tenant_feature_audit_time (tenant_id, created_at),
  KEY idx_tenant_feature_audit_object (tenant_id, object_id),
  KEY idx_tenant_feature_audit_operator (operator_tenant_id, operator_user_id),
  CONSTRAINT fk_tenant_feature_audit_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  CONSTRAINT fk_tenant_feature_audit_object FOREIGN KEY (object_id) REFERENCES feature_objects(id),
  CONSTRAINT fk_tenant_feature_audit_before_permission FOREIGN KEY (before_permission_id) REFERENCES permissions(id),
  CONSTRAINT fk_tenant_feature_audit_after_permission FOREIGN KEY (after_permission_id) REFERENCES permissions(id),
  CONSTRAINT fk_tenant_feature_audit_operator FOREIGN KEY (operator_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户功能授权审计';

-- =========================
-- Seed Data (Platform tenant & RBAC)
-- =========================
-- 使用 tenant_id=0 作为平台租户（系统租户）
SET @OLD_SQL_MODE := @@SQL_MODE;
SET SQL_MODE = CONCAT(@@SQL_MODE, ',NO_AUTO_VALUE_ON_ZERO');

INSERT INTO tenants (id, parent_id, code, name, type, status, level, path)
SELECT 0, NULL, 'platform', '平台租户', 'PLATFORM', 1, 1, '/platform'
WHERE NOT EXISTS (SELECT 1 FROM tenants WHERE id = 0);

SET @platform_tenant_id := (SELECT id FROM tenants WHERE id = 0 LIMIT 1);

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

-- RBAC 系统租户数据（tenant_id = 0）
INSERT INTO feature_object_categories (tenant_id, code, name, description, sort, status)
SELECT 0, 'BUILD_DESIGN', '构建与设计', NULL, 1, 1
WHERE NOT EXISTS (
  SELECT 1 FROM feature_object_categories WHERE tenant_id = 0 AND code = 'BUILD_DESIGN'
);
INSERT INTO feature_object_categories (tenant_id, code, name, description, sort, status)
SELECT 0, 'COMPUTE_CONNECT', '计算与连接', NULL, 2, 1
WHERE NOT EXISTS (
  SELECT 1 FROM feature_object_categories WHERE tenant_id = 0 AND code = 'COMPUTE_CONNECT'
);
INSERT INTO feature_object_categories (tenant_id, code, name, description, sort, status)
SELECT 0, 'OBSERVE', '观测', NULL, 3, 1
WHERE NOT EXISTS (
  SELECT 1 FROM feature_object_categories WHERE tenant_id = 0 AND code = 'OBSERVE'
);

INSERT INTO permissions (tenant_id, code, name, description, level, sort, status)
SELECT 0, 'READ', '读', NULL, 1, 1, 1
WHERE NOT EXISTS (
  SELECT 1 FROM permissions WHERE tenant_id = 0 AND code = 'READ'
);
INSERT INTO permissions (tenant_id, code, name, description, level, sort, status)
SELECT 0, 'WRITE', '写', NULL, 2, 2, 1
WHERE NOT EXISTS (
  SELECT 1 FROM permissions WHERE tenant_id = 0 AND code = 'WRITE'
);
INSERT INTO permissions (tenant_id, code, name, description, level, sort, status)
SELECT 0, 'ADMIN', '管理', NULL, 3, 3, 1
WHERE NOT EXISTS (
  SELECT 1 FROM permissions WHERE tenant_id = 0 AND code = 'ADMIN'
);

SET @cat_build_design_id := (SELECT id FROM feature_object_categories WHERE tenant_id = 0 AND code = 'BUILD_DESIGN' LIMIT 1);
SET @cat_compute_connect_id := (SELECT id FROM feature_object_categories WHERE tenant_id = 0 AND code = 'COMPUTE_CONNECT' LIMIT 1);
SET @cat_observe_id := (SELECT id FROM feature_object_categories WHERE tenant_id = 0 AND code = 'OBSERVE' LIMIT 1);

-- 菜单对象（MENU）
INSERT INTO feature_objects (tenant_id, parent_id, type, name, category_id, path, location, description, sort, status)
SELECT 0, NULL, 'MENU', '知识 Wiki', @cat_build_design_id, '/wiki', 'SIDEBAR', NULL, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM feature_objects WHERE tenant_id = 0 AND path = '/wiki');
INSERT INTO feature_objects (tenant_id, parent_id, type, name, category_id, path, location, description, sort, status)
SELECT 0, NULL, 'MENU', '工作流构建', @cat_build_design_id, '/workflow', 'SIDEBAR', NULL, 2, 1
WHERE NOT EXISTS (SELECT 1 FROM feature_objects WHERE tenant_id = 0 AND path = '/workflow');
INSERT INTO feature_objects (tenant_id, parent_id, type, name, category_id, path, location, description, sort, status)
SELECT 0, NULL, 'MENU', '统一开发 IDE', @cat_build_design_id, '/ide', 'SIDEBAR', NULL, 3, 1
WHERE NOT EXISTS (SELECT 1 FROM feature_objects WHERE tenant_id = 0 AND path = '/ide');
INSERT INTO feature_objects (tenant_id, parent_id, type, name, category_id, path, location, description, sort, status)
SELECT 0, NULL, 'MENU', '数据埋点', @cat_compute_connect_id, '/data-tracking', 'SIDEBAR', NULL, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM feature_objects WHERE tenant_id = 0 AND path = '/data-tracking');
INSERT INTO feature_objects (tenant_id, parent_id, type, name, category_id, path, location, description, sort, status)
SELECT 0, NULL, 'MENU', '计算仓', @cat_compute_connect_id, '/compute-warehouse', 'SIDEBAR', NULL, 2, 1
WHERE NOT EXISTS (SELECT 1 FROM feature_objects WHERE tenant_id = 0 AND path = '/compute-warehouse');
INSERT INTO feature_objects (tenant_id, parent_id, type, name, category_id, path, location, description, sort, status)
SELECT 0, NULL, 'MENU', '发布', @cat_observe_id, '/deployments', 'SIDEBAR', NULL, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM feature_objects WHERE tenant_id = 0 AND path = '/deployments');
INSERT INTO feature_objects (tenant_id, parent_id, type, name, category_id, path, location, description, sort, status)
SELECT 0, NULL, 'MENU', '日志', @cat_observe_id, '/logs', 'SIDEBAR', NULL, 2, 1
WHERE NOT EXISTS (SELECT 1 FROM feature_objects WHERE tenant_id = 0 AND path = '/logs');
INSERT INTO feature_objects (tenant_id, parent_id, type, name, category_id, path, location, description, sort, status)
SELECT 0, NULL, 'MENU', '版本历史', @cat_observe_id, '/version', 'SIDEBAR', NULL, 3, 1
WHERE NOT EXISTS (SELECT 1 FROM feature_objects WHERE tenant_id = 0 AND path = '/version');
INSERT INTO feature_objects (tenant_id, parent_id, type, name, category_id, path, location, description, sort, status)
SELECT 0, NULL, 'MENU', 'Git 运维', @cat_observe_id, '/git', 'SIDEBAR', NULL, 4, 1
WHERE NOT EXISTS (SELECT 1 FROM feature_objects WHERE tenant_id = 0 AND path = '/git');
INSERT INTO feature_objects (tenant_id, parent_id, type, name, category_id, path, location, description, sort, status)
SELECT 0, NULL, 'MENU', '数据驾驶舱', NULL, '/home', 'TOP', NULL, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM feature_objects WHERE tenant_id = 0 AND path = '/home');
INSERT INTO feature_objects (tenant_id, parent_id, type, name, category_id, path, location, description, sort, status)
SELECT 0, NULL, 'MENU', '数据治理', NULL, '/governance', 'TOP', NULL, 2, 1
WHERE NOT EXISTS (SELECT 1 FROM feature_objects WHERE tenant_id = 0 AND path = '/governance');
INSERT INTO feature_objects (tenant_id, parent_id, type, name, category_id, path, location, description, sort, status)
SELECT 0, NULL, 'MENU', '项目', NULL, '/projects', 'TOP', NULL, 3, 1
WHERE NOT EXISTS (SELECT 1 FROM feature_objects WHERE tenant_id = 0 AND path = '/projects');
INSERT INTO feature_objects (tenant_id, parent_id, type, name, category_id, path, location, description, sort, status)
SELECT 0, NULL, 'MENU', '团队协作', NULL, '/collaboration', 'TOP', NULL, 4, 1
WHERE NOT EXISTS (SELECT 1 FROM feature_objects WHERE tenant_id = 0 AND path = '/collaboration');
INSERT INTO feature_objects (tenant_id, parent_id, type, name, category_id, path, location, description, sort, status)
SELECT 0, NULL, 'MENU', '个人中心', NULL, '/profile', 'PROFILE', NULL, 5, 1
WHERE NOT EXISTS (SELECT 1 FROM feature_objects WHERE tenant_id = 0 AND path = '/profile');

SET @governance_id := (SELECT id FROM feature_objects WHERE tenant_id = 0 AND path = '/governance' LIMIT 1);
SET @profile_id := (SELECT id FROM feature_objects WHERE tenant_id = 0 AND path = '/profile' LIMIT 1);

INSERT INTO feature_objects (tenant_id, parent_id, type, name, category_id, path, location, description, sort, status)
SELECT 0, @governance_id, 'MENU', '元数据', NULL, '/governance/metadata', 'TOP', NULL, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM feature_objects WHERE tenant_id = 0 AND path = '/governance/metadata');
INSERT INTO feature_objects (tenant_id, parent_id, type, name, category_id, path, location, description, sort, status)
SELECT 0, @governance_id, 'MENU', '元语义', NULL, '/governance/semantic', 'TOP', NULL, 2, 1
WHERE NOT EXISTS (SELECT 1 FROM feature_objects WHERE tenant_id = 0 AND path = '/governance/semantic');
INSERT INTO feature_objects (tenant_id, parent_id, type, name, category_id, path, location, description, sort, status)
SELECT 0, @governance_id, 'MENU', '知识图谱RAG', NULL, '/governance/knowledge', 'TOP', NULL, 3, 1
WHERE NOT EXISTS (SELECT 1 FROM feature_objects WHERE tenant_id = 0 AND path = '/governance/knowledge');

INSERT INTO feature_objects (tenant_id, parent_id, type, name, category_id, path, location, description, sort, status)
SELECT 0, @profile_id, 'MENU', '权限分配', NULL, '/profile/permission', 'PROFILE', NULL, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM feature_objects WHERE tenant_id = 0 AND path = '/profile/permission');
INSERT INTO feature_objects (tenant_id, parent_id, type, name, category_id, path, location, description, sort, status)
SELECT 0, @profile_id, 'MENU', 'AI 配置', NULL, '/profile/llm/models', 'PROFILE', NULL, 2, 1
WHERE NOT EXISTS (SELECT 1 FROM feature_objects WHERE tenant_id = 0 AND path = '/profile/llm/models');
INSERT INTO feature_objects (tenant_id, parent_id, type, name, category_id, path, location, description, sort, status)
SELECT 0, @profile_id, 'MENU', '账单', NULL, '/profile/billing', 'PROFILE', NULL, 3, 1
WHERE NOT EXISTS (SELECT 1 FROM feature_objects WHERE tenant_id = 0 AND path = '/profile/billing');

-- 平台租户功能授权（全量 ADMIN）
SET @admin_permission_id := (SELECT id FROM permissions WHERE tenant_id = 0 AND code = 'ADMIN' LIMIT 1);
INSERT INTO tenant_feature_permissions
  (tenant_id, object_id, permission_id, status, grant_source, granted_by, updated_by)
SELECT
  0, o.id, @admin_permission_id, 1, 'SYSTEM', @admin_user_id, @admin_user_id
FROM feature_objects o
WHERE o.tenant_id = 0
  AND NOT EXISTS (
    SELECT 1 FROM tenant_feature_permissions tfp
    WHERE tfp.tenant_id = 0 AND tfp.object_id = o.id
  );

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
JOIN feature_objects o ON o.tenant_id = 0
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

SET SQL_MODE := @OLD_SQL_MODE;

