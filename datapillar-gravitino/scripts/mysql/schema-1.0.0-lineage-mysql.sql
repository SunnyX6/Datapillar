--
-- Licensed to the Apache Software Foundation (ASF) under one
-- or more contributor license agreements.  See the NOTICE file--
--  distributed with this work for additional information
-- regarding copyright ownership.  The ASF licenses this file
-- to you under the Apache License, Version 2.0 (the
-- "License"). You may not use this file except in compliance
-- with the License.  You may obtain a copy of the License at
--
--  http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing,
-- software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
-- KIND, either express or implied.  See the License for the
-- specific language governing permissions and limitations
-- under the License.
--

-- Lineage Jobs table
CREATE TABLE IF NOT EXISTS `lineage_jobs` (
    `job_id` BIGINT(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'job id',
    `namespace` VARCHAR(256) NOT NULL COMMENT 'job namespace',
    `job_name` VARCHAR(256) NOT NULL COMMENT 'job name',
    `job_type` VARCHAR(64) DEFAULT NULL COMMENT 'job type',
    `created_at` BIGINT(20) UNSIGNED NOT NULL COMMENT 'created timestamp',
    `updated_at` BIGINT(20) UNSIGNED NOT NULL COMMENT 'updated timestamp',
    PRIMARY KEY (`job_id`),
    UNIQUE KEY `uk_ns_jn` (`namespace`, `job_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT 'lineage jobs';

-- Lineage Datasets table
CREATE TABLE IF NOT EXISTS `lineage_datasets` (
    `dataset_id` BIGINT(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'dataset id',
    `namespace` VARCHAR(256) NOT NULL COMMENT 'dataset namespace',
    `dataset_name` VARCHAR(512) NOT NULL COMMENT 'dataset name',
    `dataset_type` VARCHAR(64) DEFAULT NULL COMMENT 'dataset type',
    `schema_json` MEDIUMTEXT DEFAULT NULL COMMENT 'dataset schema in JSON format',
    `created_at` BIGINT(20) UNSIGNED NOT NULL COMMENT 'created timestamp',
    `updated_at` BIGINT(20) UNSIGNED NOT NULL COMMENT 'updated timestamp',
    PRIMARY KEY (`dataset_id`),
    UNIQUE KEY `uk_ns_dn` (`namespace`, `dataset_name`(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT 'lineage datasets';

-- Lineage Edges table (Job<->Dataset relationships)
CREATE TABLE IF NOT EXISTS `lineage_edges` (
    `edge_id` BIGINT(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'edge id',
    `source_type` VARCHAR(32) NOT NULL COMMENT 'source type: JOB or DATASET',
    `source_id` BIGINT(20) UNSIGNED NOT NULL COMMENT 'source id',
    `target_type` VARCHAR(32) NOT NULL COMMENT 'target type: JOB or DATASET',
    `target_id` BIGINT(20) UNSIGNED NOT NULL COMMENT 'target id',
    `edge_type` VARCHAR(32) NOT NULL COMMENT 'edge type: INPUT or OUTPUT',
    `run_id` VARCHAR(128) DEFAULT NULL COMMENT 'run id from OpenLineage event',
    `created_at` BIGINT(20) UNSIGNED NOT NULL COMMENT 'created timestamp',
    PRIMARY KEY (`edge_id`),
    KEY `idx_source` (`source_type`, `source_id`),
    KEY `idx_target` (`target_type`, `target_id`),
    KEY `idx_run` (`run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT 'lineage edges';

-- Column-level lineage table
CREATE TABLE IF NOT EXISTS `lineage_columns` (
    `column_lineage_id` BIGINT(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'column lineage id',
    `source_dataset_id` BIGINT(20) UNSIGNED NOT NULL COMMENT 'source dataset id',
    `source_column` VARCHAR(256) NOT NULL COMMENT 'source column name',
    `target_dataset_id` BIGINT(20) UNSIGNED NOT NULL COMMENT 'target dataset id',
    `target_column` VARCHAR(256) NOT NULL COMMENT 'target column name',
    `transformation` TEXT DEFAULT NULL COMMENT 'transformation logic or expression',
    `created_at` BIGINT(20) UNSIGNED NOT NULL COMMENT 'created timestamp',
    PRIMARY KEY (`column_lineage_id`),
    KEY `idx_src` (`source_dataset_id`, `source_column`),
    KEY `idx_tgt` (`target_dataset_id`, `target_column`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT 'column level lineage';
