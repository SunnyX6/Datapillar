-- Flyway repeatable migration: global reference data

INSERT INTO permissions (code, name, description, level, sort, status)
VALUES
  ('DISABLE', '禁止', '禁用权限', 0, 1, 1),
  ('READ', '查看', '查看权限', 1, 2, 1),
  ('ADMIN', '管理', '管理权限', 2, 3, 1)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  description = VALUES(description),
  level = VALUES(level),
  sort = VALUES(sort),
  status = VALUES(status);

INSERT INTO feature_object_categories (code, name, description, sort, status)
VALUES
  ('LEADER', '管理视角', '管理者核心关注入口', 1, 1),
  ('BUILD', '构建与设计', NULL, 2, 1),
  ('COMPUTE', '计算与连接', NULL, 3, 1),
  ('OBSERVE', '观测', NULL, 4, 1)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  description = VALUES(description),
  sort = VALUES(sort),
  status = VALUES(status);

INSERT INTO feature_objects (parent_id, type, name, category_id, path, location, description, sort, status)
VALUES
  (NULL, 'MENU', '数据驾驶舱', (SELECT id FROM feature_object_categories WHERE code = 'LEADER'), '/home', 'TOP', NULL, 1, 1),
  (NULL, 'MENU', '数据治理', (SELECT id FROM feature_object_categories WHERE code = 'LEADER'), '/governance', 'TOP', NULL, 2, 1),
  (NULL, 'MENU', '项目', (SELECT id FROM feature_object_categories WHERE code = 'LEADER'), '/projects', 'TOP', NULL, 3, 1),
  (NULL, 'MENU', '团队协作', (SELECT id FROM feature_object_categories WHERE code = 'LEADER'), '/collaboration', 'TOP', NULL, 4, 1)
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id),
  type = VALUES(type),
  name = VALUES(name),
  category_id = VALUES(category_id),
  location = VALUES(location),
  description = VALUES(description),
  sort = VALUES(sort),
  status = VALUES(status);

INSERT INTO feature_objects (parent_id, type, name, category_id, path, location, description, sort, status)
VALUES
  (NULL, 'MENU', '元数据', (SELECT id FROM feature_object_categories WHERE code = 'LEADER'), '/governance/metadata', 'TOP', NULL, 1, 1),
  (NULL, 'MENU', '元语义', (SELECT id FROM feature_object_categories WHERE code = 'LEADER'), '/governance/semantic', 'TOP', NULL, 2, 1),
  (NULL, 'MENU', '知识图谱', (SELECT id FROM feature_object_categories WHERE code = 'LEADER'), '/governance/knowledge', 'TOP', NULL, 3, 1)
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id),
  type = VALUES(type),
  name = VALUES(name),
  category_id = VALUES(category_id),
  location = VALUES(location),
  description = VALUES(description),
  sort = VALUES(sort),
  status = VALUES(status);

UPDATE feature_objects child
JOIN feature_objects parent ON parent.path = '/governance'
SET child.parent_id = parent.id
WHERE child.path IN ('/governance/metadata', '/governance/semantic', '/governance/knowledge');

INSERT INTO feature_objects (parent_id, type, name, category_id, path, location, description, sort, status)
VALUES
  (NULL, 'MENU', '知识 Wiki', (SELECT id FROM feature_object_categories WHERE code = 'BUILD'), '/wiki', 'SIDEBAR', NULL, 1, 1),
  (NULL, 'MENU', '工作流构建', (SELECT id FROM feature_object_categories WHERE code = 'BUILD'), '/workflow', 'SIDEBAR', NULL, 2, 1),
  (NULL, 'MENU', '统一开发 IDE', (SELECT id FROM feature_object_categories WHERE code = 'BUILD'), '/ide', 'SIDEBAR', NULL, 3, 1),
  (NULL, 'MENU', '数据埋点', (SELECT id FROM feature_object_categories WHERE code = 'COMPUTE'), '/data-tracking', 'SIDEBAR', NULL, 4, 1),
  (NULL, 'MENU', '计算仓', (SELECT id FROM feature_object_categories WHERE code = 'COMPUTE'), '/compute-warehouse', 'SIDEBAR', NULL, 5, 1),
  (NULL, 'MENU', '发布', (SELECT id FROM feature_object_categories WHERE code = 'COMPUTE'), '/deployments', 'SIDEBAR', NULL, 6, 1),
  (NULL, 'MENU', '日志', (SELECT id FROM feature_object_categories WHERE code = 'OBSERVE'), '/logs', 'SIDEBAR', NULL, 7, 1),
  (NULL, 'MENU', '版本历史', (SELECT id FROM feature_object_categories WHERE code = 'OBSERVE'), '/version', 'SIDEBAR', NULL, 8, 1),
  (NULL, 'MENU', 'Git 运维', (SELECT id FROM feature_object_categories WHERE code = 'OBSERVE'), '/git', 'SIDEBAR', NULL, 9, 1)
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id),
  type = VALUES(type),
  name = VALUES(name),
  category_id = VALUES(category_id),
  location = VALUES(location),
  description = VALUES(description),
  sort = VALUES(sort),
  status = VALUES(status);

INSERT INTO feature_objects (parent_id, type, name, category_id, path, location, description, sort, status)
VALUES
  (NULL, 'MENU', '个人中心', NULL, '/profile', 'PROFILE', NULL, 1, 1),
  (NULL, 'MENU', '权限配置', NULL, '/profile/permission', 'PROFILE', NULL, 2, 1),
  (NULL, 'MENU', 'AI 配置', NULL, '/profile/llm/models', 'PROFILE', NULL, 3, 1)
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id),
  type = VALUES(type),
  name = VALUES(name),
  category_id = VALUES(category_id),
  location = VALUES(location),
  description = VALUES(description),
  sort = VALUES(sort),
  status = VALUES(status);

UPDATE feature_objects child
JOIN feature_objects parent ON parent.path = '/profile'
SET child.parent_id = parent.id
WHERE child.path IN ('/profile/permission', '/profile/llm/models');

INSERT INTO feature_objects (parent_id, type, name, category_id, path, location, description, sort, status)
VALUES
  (NULL, 'PAGE', 'SQL 编辑器', NULL, '/ide/sql', 'PAGE', NULL, 1, 1)
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id),
  type = VALUES(type),
  name = VALUES(name),
  category_id = VALUES(category_id),
  location = VALUES(location),
  description = VALUES(description),
  sort = VALUES(sort),
  status = VALUES(status);

UPDATE feature_objects child
JOIN feature_objects parent ON parent.path = '/ide'
SET child.parent_id = parent.id
WHERE child.path = '/ide/sql';

INSERT INTO ai_provider (code, name, base_url, model_ids)
VALUES
  ('openai', 'OpenAI', 'https://api.openai.com/v1', JSON_ARRAY('openai/gpt-4o', 'openai/text-embedding-3-large')),
  ('anthropic', 'Anthropic', 'https://api.anthropic.com', JSON_ARRAY('anthropic/claude-3.5-sonnet')),
  ('glm', 'GLM', 'https://open.bigmodel.cn/api/paas/v4', JSON_ARRAY('glm-4.7')),
  ('deepseek', 'DeepSeek', 'https://api.deepseek.com/v1', JSON_ARRAY('deepseek/deepseek-chat-v3')),
  ('openrouter', 'OpenRouter', 'https://openrouter.ai/api/v1', JSON_ARRAY('meta-llama/llama-3-70b-instruct', 'google/gemini-pro-1.5', 'mistralai/mistral-large')),
  ('ollama', 'Ollama', 'http://localhost:11434/v1', JSON_ARRAY())
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  base_url = VALUES(base_url),
  model_ids = VALUES(model_ids);
