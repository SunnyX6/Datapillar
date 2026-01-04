-- LLM Token 使用量资产表（MySQL）
--
-- 说明：
-- - 记录粒度：每一次 LLM 调用（run_id 唯一）
-- - 目标：可审计、可聚合（按 user/session/agent/model 统计）
-- - token 口径：优先使用厂商真实 usage；拿不到时 estimated=1（启发式估算）
--
-- 执行：
--   在 datapillar（或系统库）中执行本 DDL

CREATE TABLE IF NOT EXISTS ai_llm_usage (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

  user_id VARCHAR(64) NOT NULL,
  session_id VARCHAR(128) NOT NULL,
  module VARCHAR(32) NOT NULL,
  agent_id VARCHAR(64) NOT NULL,

  provider VARCHAR(32) NULL,
  model_name VARCHAR(128) NULL,

  run_id VARCHAR(64) NOT NULL,
  parent_run_id VARCHAR(64) NULL,

  prompt_tokens INT NULL,
  completion_tokens INT NULL,
  total_tokens INT NULL,
  estimated TINYINT(1) NOT NULL DEFAULT 0,

  prompt_cost_usd DECIMAL(18, 8) NULL,
  completion_cost_usd DECIMAL(18, 8) NULL,
  total_cost_usd DECIMAL(18, 8) NULL,

  raw_usage_json JSON NULL,

  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_llm_usage_run_id (run_id),
  KEY idx_ai_llm_usage_user_session (user_id, session_id),
  KEY idx_ai_llm_usage_module_agent (module, agent_id),
  KEY idx_ai_llm_usage_model (provider, model_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

