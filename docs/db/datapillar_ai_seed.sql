-- =========================================================
-- Datapillar AI Seed Data
-- 目标：初始化内置 AI 供应商
-- 数据库：datapillar
-- =========================================================

USE datapillar;

-- 注意：base_url 仅用于前端预填，实际以 ai_model.base_url 为准
INSERT INTO ai_provider (code, name, base_url, model_ids)
VALUES
  ('openai', 'OpenAI', 'https://api.openai.com/v1', '["openai/gpt-4o","openai/text-embedding-3-large"]'),
  ('anthropic', 'Anthropic', 'https://api.anthropic.com', '["anthropic/claude-3.5-sonnet"]'),
  ('glm', 'GLM', 'https://open.bigmodel.cn/api/paas/v4', '["glm-4.7"]'),
  ('deepseek', 'DeepSeek', 'https://api.deepseek.com/v1', '["deepseek/deepseek-chat-v3"]'),
  ('openrouter', 'OpenRouter', 'https://openrouter.ai/api/v1', '["meta-llama/llama-3-70b-instruct","google/gemini-pro-1.5","mistralai/mistral-large"]'),
  ('ollama', 'Ollama', 'http://localhost:11434/v1', '[]')
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  base_url = VALUES(base_url),
  model_ids = VALUES(model_ids);
