-- =========================================================
-- Datapillar Studio Seed Data (单产品初始化)
-- 说明：Studio 单产品 + 租户隔离；权限/菜单字典全局共享
-- =========================================================

SET FOREIGN_KEY_CHECKS=0;
TRUNCATE TABLE tenant_feature_audit;
TRUNCATE TABLE tenant_feature_permissions;
TRUNCATE TABLE role_permissions;
TRUNCATE TABLE user_permission_overrides;
TRUNCATE TABLE user_roles;
TRUNCATE TABLE user_invitation_roles;
TRUNCATE TABLE user_invitations;
TRUNCATE TABLE roles;
TRUNCATE TABLE permissions;
TRUNCATE TABLE feature_objects;
TRUNCATE TABLE feature_object_categories;
TRUNCATE TABLE tenant_sso_configs;
TRUNCATE TABLE user_identities;
TRUNCATE TABLE tenant_users;
TRUNCATE TABLE users;
TRUNCATE TABLE tenants;
SET FOREIGN_KEY_CHECKS=1;

-- 租户
INSERT INTO tenants (id, parent_id, code, name, type, status, level, path, created_at, updated_at)
VALUES (1, NULL, 'acme', 'ACME', 'ENTERPRISE', 1, 1, '/1', NOW(), NOW());

-- 用户（默认管理员）
INSERT INTO users (id, tenant_id, username, password, nickname, email, phone, status, deleted, created_at, updated_at)
VALUES (1, 1, 'sunny', '$argon2id$v=19$m=65536,t=3,p=1$KsEQOZ66AT2CQvZQPbK5WQ$HAgp/6rHQ1Fyt2107zbFnMNnbE6+NfAwkT+eJanWe8s',
        'Sunny', 'sunny@datapillar.com', NULL, 1, 0, NOW(), NOW());

INSERT INTO tenant_users (id, tenant_id, user_id, status, is_default, token_sign, token_expire_time, joined_at)
VALUES (1, 1, 1, 1, 1, NULL, NULL, NOW());

-- 权限字典（全局）
INSERT INTO permissions (id, code, name, description, level, sort, status, created_at, updated_at) VALUES
  (1, 'READ',  '读',   '只读权限',   1, 1, 1, NOW(), NOW()),
  (2, 'WRITE', '写',   '编辑权限',   2, 2, 1, NOW(), NOW()),
  (3, 'ADMIN', '管理', '管理权限',   3, 3, 1, NOW(), NOW());

-- 功能分类（全局）
INSERT INTO feature_object_categories (id, code, name, description, sort, status, created_at, updated_at) VALUES
  (10, 'BUILD',   '构建与设计', NULL, 1, 1, NOW(), NOW()),
  (11, 'COMPUTE', '计算与连接', NULL, 2, 1, NOW(), NOW()),
  (12, 'OBSERVE', '观测',     NULL, 3, 1, NOW(), NOW());

-- 功能对象（菜单/页面，全局）
INSERT INTO feature_objects (id, parent_id, type, name, category_id, path, location, description, sort, status, created_at, updated_at) VALUES
  (100, NULL, 'MENU', '数据驾驶舱', NULL, '/home', 'TOP', NULL, 1, 1, NOW(), NOW()),
  (101, NULL, 'MENU', '数据治理', NULL, '/governance', 'TOP', NULL, 2, 1, NOW(), NOW()),
  (102, NULL, 'MENU', '项目', NULL, '/projects', 'TOP', NULL, 3, 1, NOW(), NOW()),
  (103, NULL, 'MENU', '团队协作', NULL, '/collaboration', 'TOP', NULL, 4, 1, NOW(), NOW()),

  (110, 101, 'MENU', '元数据', NULL, '/governance/metadata', 'TOP', NULL, 1, 1, NOW(), NOW()),
  (111, 101, 'MENU', '元语义', NULL, '/governance/semantic', 'TOP', NULL, 2, 1, NOW(), NOW()),
  (112, 101, 'MENU', '知识图谱', NULL, '/governance/knowledge', 'TOP', NULL, 3, 1, NOW(), NOW()),

  (122, NULL, 'MENU', '知识 Wiki', 10, '/wiki', 'SIDEBAR', NULL, 1, 1, NOW(), NOW()),
  (120, NULL, 'MENU', '工作流构建', 10, '/workflow', 'SIDEBAR', NULL, 2, 1, NOW(), NOW()),
  (121, NULL, 'MENU', '统一开发 IDE', 10, '/ide', 'SIDEBAR', NULL, 3, 1, NOW(), NOW()),
  (123, NULL, 'MENU', '数据埋点', 11, '/data-tracking', 'SIDEBAR', NULL, 4, 1, NOW(), NOW()),
  (124, NULL, 'MENU', '计算仓', 11, '/compute-warehouse', 'SIDEBAR', NULL, 5, 1, NOW(), NOW()),
  (125, NULL, 'MENU', '发布', 11, '/deployments', 'SIDEBAR', NULL, 6, 1, NOW(), NOW()),
  (126, NULL, 'MENU', '日志', 12, '/logs', 'SIDEBAR', NULL, 7, 1, NOW(), NOW()),
  (127, NULL, 'MENU', '版本历史', 12, '/version', 'SIDEBAR', NULL, 8, 1, NOW(), NOW()),
  (128, NULL, 'MENU', 'Git 运维', 12, '/git', 'SIDEBAR', NULL, 9, 1, NOW(), NOW()),

  (130, NULL, 'MENU', '个人中心', NULL, '/profile', 'PROFILE', NULL, 1, 1, NOW(), NOW()),
  (131, 130, 'MENU', '功能权限', NULL, '/profile/permission', 'PROFILE', NULL, 2, 1, NOW(), NOW()),
  (132, 130, 'MENU', 'AI 配置', NULL, '/profile/llm/models', 'PROFILE', NULL, 3, 1, NOW(), NOW()),

  (133, 121, 'PAGE', 'SQL 编辑器', NULL, '/ide/sql', 'PAGE', NULL, 1, 1, NOW(), NOW());

-- 角色
INSERT INTO roles (id, tenant_id, type, name, description, status, sort, created_at, updated_at)
VALUES (1, 1, 'ADMIN', '超级管理员', '系统内置管理员', 1, 1, NOW(), NOW());

-- 用户角色
INSERT INTO user_roles (id, tenant_id, user_id, role_id, created_at)
VALUES (1, 1, 1, 1, NOW());

-- 租户功能授权上限（全部 ADMIN）
INSERT INTO tenant_feature_permissions
  (tenant_id, object_id, permission_id, status, grant_source, granted_by, granted_at, updated_by, updated_at)
VALUES
  (1, 100, 3, 1, 'SYSTEM', 1, NOW(), 1, NOW()),
  (1, 101, 3, 1, 'SYSTEM', 1, NOW(), 1, NOW()),
  (1, 102, 3, 1, 'SYSTEM', 1, NOW(), 1, NOW()),
  (1, 103, 3, 1, 'SYSTEM', 1, NOW(), 1, NOW()),
  (1, 110, 3, 1, 'SYSTEM', 1, NOW(), 1, NOW()),
  (1, 111, 3, 1, 'SYSTEM', 1, NOW(), 1, NOW()),
  (1, 112, 3, 1, 'SYSTEM', 1, NOW(), 1, NOW()),
  (1, 120, 3, 1, 'SYSTEM', 1, NOW(), 1, NOW()),
  (1, 121, 3, 1, 'SYSTEM', 1, NOW(), 1, NOW()),
  (1, 122, 3, 1, 'SYSTEM', 1, NOW(), 1, NOW()),
  (1, 123, 3, 1, 'SYSTEM', 1, NOW(), 1, NOW()),
  (1, 124, 3, 1, 'SYSTEM', 1, NOW(), 1, NOW()),
  (1, 125, 3, 1, 'SYSTEM', 1, NOW(), 1, NOW()),
  (1, 126, 3, 1, 'SYSTEM', 1, NOW(), 1, NOW()),
  (1, 127, 3, 1, 'SYSTEM', 1, NOW(), 1, NOW()),
  (1, 128, 3, 1, 'SYSTEM', 1, NOW(), 1, NOW()),
  (1, 130, 3, 1, 'SYSTEM', 1, NOW(), 1, NOW()),
  (1, 131, 3, 1, 'SYSTEM', 1, NOW(), 1, NOW()),
  (1, 132, 3, 1, 'SYSTEM', 1, NOW(), 1, NOW()),
  (1, 133, 3, 1, 'SYSTEM', 1, NOW(), 1, NOW());

-- 角色权限（超级管理员全量 ADMIN）
INSERT INTO role_permissions (tenant_id, role_id, object_id, permission_id, created_at) VALUES
  (1, 1, 100, 3, NOW()),
  (1, 1, 101, 3, NOW()),
  (1, 1, 102, 3, NOW()),
  (1, 1, 103, 3, NOW()),
  (1, 1, 110, 3, NOW()),
  (1, 1, 111, 3, NOW()),
  (1, 1, 112, 3, NOW()),
  (1, 1, 120, 3, NOW()),
  (1, 1, 121, 3, NOW()),
  (1, 1, 122, 3, NOW()),
  (1, 1, 123, 3, NOW()),
  (1, 1, 124, 3, NOW()),
  (1, 1, 125, 3, NOW()),
  (1, 1, 126, 3, NOW()),
  (1, 1, 127, 3, NOW()),
  (1, 1, 128, 3, NOW()),
  (1, 1, 130, 3, NOW()),
  (1, 1, 131, 3, NOW()),
  (1, 1, 132, 3, NOW()),
  (1, 1, 133, 3, NOW());
