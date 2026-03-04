-- Flyway repeatable migration: global reference data

INSERT INTO permissions (code, name, description, level, sort, status)
VALUES
  ('DISABLE', 'Disable', 'Disable permission', 0, 1, 1),
  ('READ', 'Read', 'Read permission', 1, 2, 1),
  ('ADMIN', 'Admin', 'Admin permission', 2, 3, 1)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  description = VALUES(description),
  level = VALUES(level),
  sort = VALUES(sort),
  status = VALUES(status);

INSERT INTO feature_object_categories (code, name, description, sort, status)
VALUES
  ('MANAGE_DEFINE', 'Manage & Define', 'Manage & Define entry', 1, 1),
  ('BUILD', 'Build & Design', NULL, 2, 1),
  ('COMPUTE', 'Compute & Connect', NULL, 3, 1),
  ('OBSERVE', 'Observe', NULL, 4, 1)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  description = VALUES(description),
  sort = VALUES(sort),
  status = VALUES(status);

INSERT INTO feature_objects (parent_id, type, name, category_id, path, location, description, sort, status)
VALUES
  (NULL, 'MENU', 'Data Cockpit', (SELECT id FROM feature_object_categories WHERE code = 'MANAGE_DEFINE'), '/home', 'TOP', NULL, 1, 1),
  (NULL, 'MENU', 'Data Governance', (SELECT id FROM feature_object_categories WHERE code = 'MANAGE_DEFINE'), '/governance', 'TOP', NULL, 2, 1),
  (NULL, 'MENU', 'Projects', (SELECT id FROM feature_object_categories WHERE code = 'MANAGE_DEFINE'), '/projects', 'TOP', NULL, 3, 1),
  (NULL, 'MENU', 'Collaboration', (SELECT id FROM feature_object_categories WHERE code = 'MANAGE_DEFINE'), '/collaboration', 'TOP', NULL, 4, 1)
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
  (NULL, 'MENU', 'Metadata', (SELECT id FROM feature_object_categories WHERE code = 'MANAGE_DEFINE'), '/governance/metadata', 'TOP', NULL, 1, 1),
  (NULL, 'MENU', 'Meta Semantic', (SELECT id FROM feature_object_categories WHERE code = 'MANAGE_DEFINE'), '/governance/semantic', 'TOP', NULL, 2, 1),
  (NULL, 'MENU', 'Knowledge Graph', (SELECT id FROM feature_object_categories WHERE code = 'MANAGE_DEFINE'), '/governance/knowledge', 'TOP', NULL, 3, 1)
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
  (NULL, 'MENU', 'Knowledge Wiki', (SELECT id FROM feature_object_categories WHERE code = 'BUILD'), '/wiki', 'SIDEBAR', NULL, 1, 1),
  (NULL, 'MENU', 'Workflow Builder', (SELECT id FROM feature_object_categories WHERE code = 'BUILD'), '/workflow', 'SIDEBAR', NULL, 2, 1),
  (NULL, 'MENU', 'Unified IDE', (SELECT id FROM feature_object_categories WHERE code = 'BUILD'), '/ide', 'SIDEBAR', NULL, 3, 1),
  (NULL, 'MENU', 'Data Tracking', (SELECT id FROM feature_object_categories WHERE code = 'COMPUTE'), '/data-tracking', 'SIDEBAR', NULL, 4, 1),
  (NULL, 'MENU', 'Compute Warehouse', (SELECT id FROM feature_object_categories WHERE code = 'COMPUTE'), '/compute-warehouse', 'SIDEBAR', NULL, 5, 1),
  (NULL, 'MENU', 'Deployments', (SELECT id FROM feature_object_categories WHERE code = 'COMPUTE'), '/deployments', 'SIDEBAR', NULL, 6, 1),
  (NULL, 'MENU', 'Logs', (SELECT id FROM feature_object_categories WHERE code = 'OBSERVE'), '/logs', 'SIDEBAR', NULL, 7, 1),
  (NULL, 'MENU', 'Version History', (SELECT id FROM feature_object_categories WHERE code = 'OBSERVE'), '/version', 'SIDEBAR', NULL, 8, 1),
  (NULL, 'MENU', 'Git Ops', (SELECT id FROM feature_object_categories WHERE code = 'OBSERVE'), '/git', 'SIDEBAR', NULL, 9, 1)
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
  (NULL, 'MENU', 'Profile', (SELECT id FROM feature_object_categories WHERE code = 'MANAGE_DEFINE'), '/profile', 'PROFILE', NULL, 1, 1),
  (NULL, 'MENU', 'Permission Settings', (SELECT id FROM feature_object_categories WHERE code = 'MANAGE_DEFINE'), '/profile/permission', 'PROFILE', NULL, 2, 1),
  (NULL, 'MENU', 'AI Settings', (SELECT id FROM feature_object_categories WHERE code = 'MANAGE_DEFINE'), '/profile/llm/models', 'PROFILE', NULL, 3, 1)
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
  (NULL, 'PAGE', 'SQL Editor', NULL, '/ide/sql', 'PAGE', NULL, 1, 1)
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
