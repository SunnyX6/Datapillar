-- Datapillar RBAC 初始化数据（基于当前前端路由）
-- 生成时间：2026-01-31 14:16:54
-- 注意：该脚本会清空并重建 RBAC 相关表数据
-- roles / permissions / menus / role_permissions / menu_roles / user_roles
-- 仅重新为用户 "sunny" 绑定 ADMIN 角色

SET NAMES utf8mb4;

START TRANSACTION;

DELETE FROM role_permissions;
DELETE FROM menu_roles;
DELETE FROM user_roles;
DELETE FROM menus;
DELETE FROM permissions;
DELETE FROM roles;

INSERT INTO roles (id, code, name, description) VALUES
  (1, 'ADMIN', '系统管理员', '系统超级管理员'),
  (2, 'USER', '普通用户', '普通用户'),
  (3, 'VIEWER', '访客用户', '只读访客');

INSERT INTO permissions (id, code, name, description) VALUES
  (1, 'governance:view', '查看数据治理', '访问数据治理相关功能'),
  (2, 'ai_model:view', '查看模型&令牌', '访问模型与令牌管理');

INSERT INTO menus (id, parent_id, name, path, icon, permission_code, visible, sort) VALUES
  (1,  NULL, '数据驾驶舱',   '/home',                 NULL, NULL,               1, 10),
  (2,  NULL, '项目',         '/projects',             NULL, NULL,               1, 20),
  (3,  NULL, '团队协作',     '/collaboration',        NULL, NULL,               1, 30),
  (4,  NULL, '工作流构建',   '/workflow',             NULL, NULL,               1, 40),
  (5,  NULL, '知识 Wiki',    '/wiki',                 NULL, NULL,               1, 50),
  (6,  NULL, '统一开发 IDE', '/ide',                  NULL, NULL,               1, 60),
  (7,  NULL, '数据埋点',     '/data-tracking',        NULL, NULL,               1, 70),

  (8,  NULL, '数据治理',     NULL,                    NULL, 'governance:view',  1, 80),
  (9,  8,    '元数据',       '/governance/metadata',  NULL, 'governance:view',  1, 81),
  (10, 8,    '元语义',       '/governance/semantic',  NULL, 'governance:view',  1, 82),
  (11, 8,    '知识图谱RAG',  '/governance/knowledge', NULL, 'governance:view',  1, 83),

  (12, NULL, '个人中心',     '/profile',             NULL, NULL,               1, 90),
  (13, 12,   '模型&令牌',    '/profile/llm/models',   NULL, 'ai_model:view',    1, 91);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p;

INSERT INTO menu_roles (menu_id, role_id)
SELECT m.id, r.id
FROM menus m
CROSS JOIN roles r;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.code = 'ADMIN'
WHERE u.username = 'sunny';

COMMIT;
