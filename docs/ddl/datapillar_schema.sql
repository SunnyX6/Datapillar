-- Datapillar 数据库全量表结构（排除 flyway_schema_history）
-- 生成时间：2026-01-31 14:07:53

SET NAMES utf8mb4;

CREATE TABLE `ai_llm_usage` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `user_id` varchar(64) NOT NULL,
  `session_id` varchar(128) NOT NULL,
  `module` varchar(32) NOT NULL,
  `agent_id` varchar(64) NOT NULL,
  `provider` varchar(32) DEFAULT NULL,
  `model_name` varchar(128) DEFAULT NULL,
  `run_id` varchar(64) NOT NULL,
  `parent_run_id` varchar(64) DEFAULT NULL,
  `prompt_tokens` int DEFAULT NULL,
  `completion_tokens` int DEFAULT NULL,
  `total_tokens` int DEFAULT NULL,
  `estimated` tinyint(1) NOT NULL DEFAULT '0',
  `prompt_cost_usd` decimal(18,8) DEFAULT NULL,
  `completion_cost_usd` decimal(18,8) DEFAULT NULL,
  `total_cost_usd` decimal(18,8) DEFAULT NULL,
  `raw_usage_json` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_llm_usage_run_id` (`run_id`),
  KEY `idx_ai_llm_usage_user_session` (`user_id`,`session_id`),
  KEY `idx_ai_llm_usage_module_agent` (`module`,`agent_id`),
  KEY `idx_ai_llm_usage_model` (`provider`,`model_name`)
) ENGINE=InnoDB AUTO_INCREMENT=1135 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
;

CREATE TABLE `ai_model` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(100) NOT NULL COMMENT '模型名称（显示用）',
  `provider` varchar(50) NOT NULL COMMENT '提供商：openai/claude/zhipu/ollama等',
  `model_name` varchar(100) NOT NULL COMMENT '模型标识：gpt-4/claude-3-sonnet等',
  `model_type` varchar(20) NOT NULL COMMENT '模型类型：chat/embedding',
  `api_key` varchar(500) DEFAULT NULL COMMENT 'API密钥',
  `base_url` varchar(200) DEFAULT NULL COMMENT 'API地址',
  `is_enabled` tinyint(1) DEFAULT '1' COMMENT '是否启用：0-禁用 1-启用',
  `config_json` text COMMENT '扩展配置（JSON格式）',
  `embedding_dimension` int DEFAULT NULL COMMENT 'Embedding模型向量维度',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_provider_model_type` (`provider`,`model_name`,`model_type`),
  KEY `idx_enabled_default` (`is_enabled`,`model_type`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI模型配置表'
;

CREATE TABLE `embedding_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `model_id` bigint NOT NULL COMMENT '目标Embedding模型ID',
  `dimension` int NOT NULL COMMENT '目标向量维度',
  `status` enum('PENDING','RUNNING','COMPLETED','FAILED','CANCELLED') DEFAULT 'PENDING' COMMENT '任务状态',
  `total_count` int DEFAULT '0' COMMENT '总节点数',
  `processed_count` int DEFAULT '0' COMMENT '已处理数',
  `current_label` varchar(50) DEFAULT NULL COMMENT '当前处理的节点类型',
  `triggered_by` bigint NOT NULL COMMENT '触发用户ID',
  `error_message` text COMMENT '错误信息',
  `started_at` timestamp NULL DEFAULT NULL COMMENT '开始时间',
  `completed_at` timestamp NULL DEFAULT NULL COMMENT '完成时间',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_triggered_by` (`triggered_by`),
  KEY `model_id` (`model_id`),
  CONSTRAINT `embedding_task_ibfk_1` FOREIGN KEY (`model_id`) REFERENCES `ai_model` (`id`),
  CONSTRAINT `embedding_task_ibfk_2` FOREIGN KEY (`triggered_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Embedding重向量化任务'
;

CREATE TABLE `job_alarm_channel` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `namespace_id` bigint NOT NULL COMMENT '命名空间ID',
  `channel_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '渠道名称',
  `channel_type` tinyint NOT NULL COMMENT '渠道类型: 1-钉钉 2-企微 3-飞书 4-Webhook 5-邮件',
  `channel_config` json NOT NULL COMMENT '渠道配置（webhook地址、密钥等）',
  `channel_status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 0-禁用 1-启用',
  `description` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '描述',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0-正常 1-删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_namespace` (`namespace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='告警渠道'
;

CREATE TABLE `job_alarm_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `namespace_id` bigint NOT NULL COMMENT '命名空间ID',
  `rule_id` bigint NOT NULL COMMENT '告警规则ID',
  `channel_id` bigint NOT NULL COMMENT '告警渠道ID',
  `job_run_id` bigint DEFAULT NULL COMMENT '任务执行实例ID',
  `workflow_run_id` bigint DEFAULT NULL COMMENT '工作流执行实例ID',
  `alarm_type` tinyint NOT NULL DEFAULT '1' COMMENT '告警类型: 1-告警 2-恢复',
  `alarm_title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '告警标题',
  `alarm_content` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '告警内容',
  `send_status` tinyint NOT NULL DEFAULT '0' COMMENT '发送状态: 0-待发送 1-成功 2-失败',
  `send_result` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发送结果/错误信息',
  `send_time` datetime(3) DEFAULT NULL COMMENT '发送时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0-正常 1-删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_rule` (`rule_id`),
  KEY `idx_job_run` (`job_run_id`),
  KEY `idx_workflow_run` (`workflow_run_id`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='告警发送记录'
;

CREATE TABLE `job_alarm_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `namespace_id` bigint NOT NULL COMMENT '命名空间ID',
  `rule_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规则名称',
  `job_id` bigint DEFAULT NULL COMMENT '关联的任务ID（与workflow_id互斥）',
  `workflow_id` bigint DEFAULT NULL COMMENT '关联的工作流ID（与job_id互斥）',
  `trigger_event` tinyint NOT NULL DEFAULT '1' COMMENT '触发事件: 1-失败 2-超时 3-成功',
  `fail_threshold` int NOT NULL DEFAULT '1' COMMENT '连续失败N次触发告警',
  `notify_on_recover` tinyint NOT NULL DEFAULT '0' COMMENT '恢复时是否通知: 0-否 1-是',
  `channel_id` bigint NOT NULL COMMENT '告警渠道ID',
  `consecutive_fails` int NOT NULL DEFAULT '0' COMMENT '当前连续失败次数',
  `alarm_status` tinyint NOT NULL DEFAULT '0' COMMENT '告警状态: 0-正常 1-已触发',
  `last_trigger_time` bigint DEFAULT NULL COMMENT '上次触发时间（毫秒）',
  `rule_status` tinyint NOT NULL DEFAULT '1' COMMENT '规则状态: 0-禁用 1-启用',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0-正常 1-删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_job` (`job_id`),
  KEY `idx_workflow` (`workflow_id`),
  KEY `idx_namespace` (`namespace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='告警规则'
;

CREATE TABLE `job_component` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `component_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '组件编码（SHELL、PYTHON、SPARK等）',
  `component_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '组件名称',
  `component_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '组件分类（脚本、数据同步、计算引擎等）',
  `job_params` json NOT NULL COMMENT '参数模板（JSON格式，定义参数key和默认值）',
  `description` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '组件描述',
  `icon` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '图标URL',
  `color` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '主题色',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 0-下线 1-上线',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0-正常 1-删除',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_component_code` (`component_code`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务组件定义'
;

CREATE TABLE `job_dependency` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `workflow_id` bigint NOT NULL COMMENT '所属工作流ID',
  `job_id` bigint NOT NULL COMMENT '当前任务ID',
  `parent_job_id` bigint NOT NULL COMMENT '上游任务ID（依赖）',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0-正常 1-删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dependency` (`workflow_id`,`job_id`,`parent_job_id`),
  KEY `idx_job` (`job_id`),
  KEY `idx_parent` (`parent_job_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务依赖关系（设计阶段）'
;

CREATE TABLE `job_info` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `workflow_id` bigint NOT NULL COMMENT '所属工作流ID',
  `job_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务名称',
  `job_type` bigint DEFAULT NULL COMMENT '任务类型: 关联job_component.id',
  `job_params` json DEFAULT NULL COMMENT '任务配置（JSON格式，不同类型结构不同）',
  `timeout_seconds` int NOT NULL DEFAULT '0' COMMENT '执行超时（秒）0-不限制',
  `max_retry_times` int NOT NULL DEFAULT '0' COMMENT '失败重试次数',
  `retry_interval` int NOT NULL DEFAULT '0' COMMENT '重试间隔（秒）',
  `priority` int NOT NULL DEFAULT '0' COMMENT '优先级: 数字越大越优先',
  `position_x` double DEFAULT NULL COMMENT '画布中的 X 坐标',
  `position_y` double DEFAULT NULL COMMENT '画布中的 Y 坐标',
  `description` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '描述',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0-正常 1-删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_workflow_job` (`workflow_id`,`job_name`),
  KEY `idx_workflow` (`workflow_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务定义'
;

CREATE TABLE `job_workflow` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `workflow_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工作流名称',
  `trigger_type` tinyint NOT NULL DEFAULT '1' COMMENT '触发类型: 1-CRON 2-固定频率 3-固定延迟 4-手动 5-API',
  `trigger_value` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '触发配置（CRON表达式或秒数）',
  `timeout_seconds` int NOT NULL DEFAULT '0' COMMENT '整体超时（秒）0-不限制',
  `max_retry_times` int NOT NULL DEFAULT '0' COMMENT '失败重试次数',
  `priority` int NOT NULL DEFAULT '0' COMMENT '优先级: 数字越大越优先',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态: 0-草稿 1-已上线 2-已下线',
  `description` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '描述',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0-正常 1-删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_workflow` (`project_id`,`workflow_name`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流定义'
;

CREATE TABLE `knowledge_document` (
  `document_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '文档ID',
  `namespace_id` bigint unsigned NOT NULL COMMENT '所属命名空间ID',
  `doc_uid` varchar(64) DEFAULT NULL COMMENT '向量库文档ID（hash）',
  `title` varchar(255) NOT NULL COMMENT '文档标题',
  `file_type` varchar(32) NOT NULL COMMENT '文件类型：pdf/docx/md/txt/...',
  `size_bytes` bigint unsigned NOT NULL DEFAULT '0' COMMENT '文件大小（字节）',
  `storage_uri` varchar(1024) DEFAULT NULL COMMENT '存储URI（file:///path 或 s3://bucket/key）',
  `storage_type` varchar(32) DEFAULT NULL COMMENT '存储类型：local/s3',
  `storage_key` varchar(255) DEFAULT NULL COMMENT '对象存储Key（可选）',
  `status` varchar(32) NOT NULL DEFAULT 'processing' COMMENT '处理状态：processing/indexed/error',
  `chunk_count` int NOT NULL DEFAULT '0' COMMENT '切片数量',
  `token_count` int NOT NULL DEFAULT '0' COMMENT 'token统计（暂不启用，默认0）',
  `error_message` varchar(1024) DEFAULT NULL COMMENT '失败原因',
  `embedding_model_id` bigint unsigned DEFAULT NULL COMMENT 'Embedding模型ID（ai_model）',
  `embedding_dimension` int DEFAULT NULL COMMENT 'Embedding向量维度（快照）',
  `chunk_mode` varchar(32) DEFAULT NULL COMMENT '切分模式：general/parent_child/qa',
  `chunk_config_json` json DEFAULT NULL COMMENT '切分配置快照（可复现）',
  `last_chunked_at` timestamp NULL DEFAULT NULL COMMENT '最近切分时间',
  `created_by` bigint unsigned NOT NULL COMMENT '创建人用户ID',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除：0=否，1=是',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`document_id`),
  KEY `idx_doc_namespace` (`namespace_id`),
  KEY `idx_doc_namespace_status` (`namespace_id`,`status`),
  KEY `idx_doc_created_by` (`created_by`),
  KEY `idx_doc_uid` (`doc_uid`),
  KEY `idx_doc_embedding_model` (`embedding_model_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识文档元数据'
;

CREATE TABLE `knowledge_document_job` (
  `job_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `namespace_id` bigint unsigned NOT NULL COMMENT '所属命名空间ID',
  `document_id` bigint unsigned NOT NULL COMMENT '文档ID',
  `job_type` varchar(32) NOT NULL COMMENT '任务类型：chunk/rechunk/reembed',
  `status` varchar(32) NOT NULL DEFAULT 'queued' COMMENT '任务状态：queued/running/success/error/canceled',
  `progress` tinyint NOT NULL DEFAULT '0' COMMENT '进度百分比：0-100',
  `progress_seq` bigint unsigned NOT NULL DEFAULT '0' COMMENT '进度序列号（用于 SSE 去重）',
  `total_chunks` int NOT NULL DEFAULT '0' COMMENT '预计切片数',
  `processed_chunks` int NOT NULL DEFAULT '0' COMMENT '已处理切片数',
  `error_message` varchar(1024) DEFAULT NULL COMMENT '失败原因',
  `started_at` timestamp NULL DEFAULT NULL COMMENT '开始时间',
  `finished_at` timestamp NULL DEFAULT NULL COMMENT '完成时间',
  `created_by` bigint unsigned NOT NULL COMMENT '创建人用户ID',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`job_id`),
  KEY `idx_job_namespace` (`namespace_id`),
  KEY `idx_job_document` (`document_id`),
  KEY `idx_job_status` (`status`),
  KEY `idx_job_created_by` (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文档切分/重嵌入任务进度'
;

CREATE TABLE `knowledge_namespace` (
  `namespace_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '命名空间ID',
  `namespace` varchar(128) NOT NULL COMMENT '命名空间标识（用于向量库隔离）',
  `description` varchar(512) DEFAULT NULL COMMENT '命名空间描述',
  `created_by` bigint unsigned NOT NULL COMMENT '创建人用户ID',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1=启用，0=禁用',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除：0=否，1=是',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`namespace_id`),
  UNIQUE KEY `uk_namespace_creator` (`created_by`,`namespace`),
  KEY `idx_namespace_creator` (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识命名空间（namespace）'
;

CREATE TABLE `menu_roles` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `menu_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_menu_role` (`menu_id`,`role_id`),
  KEY `role_id` (`role_id`),
  CONSTRAINT `menu_roles_ibfk_1` FOREIGN KEY (`menu_id`) REFERENCES `menus` (`id`) ON DELETE CASCADE,
  CONSTRAINT `menu_roles_ibfk_2` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=54 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
;

CREATE TABLE `menus` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `parent_id` bigint DEFAULT NULL,
  `name` varchar(64) NOT NULL,
  `path` varchar(128) DEFAULT NULL,
  `component` varchar(128) DEFAULT NULL,
  `icon` varchar(64) DEFAULT NULL,
  `permission_code` varchar(128) DEFAULT NULL,
  `visible` tinyint NOT NULL DEFAULT '1',
  `sort` int NOT NULL DEFAULT '0',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_menus_parent_id` (`parent_id`),
  KEY `idx_menus_permission_code` (`permission_code`),
  CONSTRAINT `fk_menus_parent` FOREIGN KEY (`parent_id`) REFERENCES `menus` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=26 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
;

CREATE TABLE `permissions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(128) NOT NULL,
  `name` varchar(128) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=122 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
;

CREATE TABLE `projects` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '项目ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项目名称',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '项目描述',
  `owner_id` bigint NOT NULL COMMENT '项目所有者ID',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'active' COMMENT '项目状态：active-活跃，archived-归档，paused-暂停，deleted-删除',
  `tags` json DEFAULT NULL COMMENT '项目标签（JSON格式）',
  `is_favorite` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否收藏',
  `is_visible` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否可见',
  `member_count` int NOT NULL DEFAULT '1' COMMENT '成员数量',
  `last_accessed_at` datetime DEFAULT NULL COMMENT '最后访问时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标志',
  PRIMARY KEY (`id`),
  KEY `idx_owner_id` (`owner_id`),
  KEY `idx_status` (`status`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_updated_at` (`updated_at`),
  KEY `idx_last_accessed_at` (`last_accessed_at`),
  KEY `idx_deleted` (`deleted`),
  CONSTRAINT `projects_ibfk_1` FOREIGN KEY (`owner_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目表'
;

CREATE TABLE `role_permissions` (
  `role_id` bigint NOT NULL,
  `permission_id` bigint NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`role_id`,`permission_id`),
  KEY `fk_role_permissions_permission` (`permission_id`),
  CONSTRAINT `fk_role_permissions_permission` FOREIGN KEY (`permission_id`) REFERENCES `permissions` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_role_permissions_role` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
;

CREATE TABLE `roles` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(64) NOT NULL,
  `name` varchar(64) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
;

CREATE TABLE `user_llm_preference` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `model_id` bigint NOT NULL COMMENT '关联 ai_model.id',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user` (`user_id`),
  KEY `model_id` (`model_id`),
  CONSTRAINT `user_llm_preference_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `user_llm_preference_ibfk_2` FOREIGN KEY (`model_id`) REFERENCES `ai_model` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户LLM偏好设置'
;

CREATE TABLE `user_roles` (
  `user_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`,`role_id`),
  KEY `fk_user_roles_role` (`role_id`),
  CONSTRAINT `fk_user_roles_role` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_user_roles_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
;

CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户唯一标识ID',
  `username` varchar(50) NOT NULL COMMENT '用户登录名，唯一标识',
  `password` varchar(255) NOT NULL COMMENT '用户密码，加密存储',
  `nickname` varchar(100) DEFAULT NULL COMMENT '用户昵称，显示名称',
  `email` varchar(100) DEFAULT NULL COMMENT '用户邮箱地址',
  `phone` varchar(20) DEFAULT NULL COMMENT '用户手机号码',
  `status` tinyint DEFAULT '1' COMMENT '用户状态：1-正常，0-禁用',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记：0-未删除，1-已删除',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `token_sign` varchar(500) DEFAULT NULL COMMENT '登录Token签名（用于SSO验证和Token撤销）',
  `token_expire_time` datetime DEFAULT NULL COMMENT 'Token过期时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`),
  UNIQUE KEY `email` (`email`),
  KEY `idx_token_sign` (`token_sign`(255))
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='User management table - verified V12'
;

