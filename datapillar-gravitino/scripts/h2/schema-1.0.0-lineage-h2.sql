-- H2-compatible lineage schema
-- Lineage Jobs table
CREATE TABLE IF NOT EXISTS lineage_jobs (
    job_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    namespace VARCHAR(256) NOT NULL,
    job_name VARCHAR(256) NOT NULL,
    job_type VARCHAR(64) DEFAULT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    CONSTRAINT uk_ns_jn UNIQUE (namespace, job_name)
);

-- Lineage Datasets table
CREATE TABLE IF NOT EXISTS lineage_datasets (
    dataset_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    namespace VARCHAR(256) NOT NULL,
    dataset_name VARCHAR(512) NOT NULL,
    dataset_type VARCHAR(64) DEFAULT NULL,
    schema_json CLOB DEFAULT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    CONSTRAINT uk_ns_dn UNIQUE (namespace, dataset_name)
);

-- Lineage Edges table (Job<->Dataset relationships)
CREATE TABLE IF NOT EXISTS lineage_edges (
    edge_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_type VARCHAR(32) NOT NULL,
    source_id BIGINT NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id BIGINT NOT NULL,
    edge_type VARCHAR(32) NOT NULL,
    run_id VARCHAR(128) DEFAULT NULL,
    created_at BIGINT NOT NULL
);

-- Indexes for edges table
CREATE INDEX IF NOT EXISTS idx_esrc ON lineage_edges (source_type, source_id);
CREATE INDEX IF NOT EXISTS idx_etgt ON lineage_edges (target_type, target_id);
CREATE INDEX IF NOT EXISTS idx_erun ON lineage_edges (run_id);

-- Column-level lineage table
CREATE TABLE IF NOT EXISTS lineage_columns (
    column_lineage_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_dataset_id BIGINT NOT NULL,
    source_column VARCHAR(256) NOT NULL,
    target_dataset_id BIGINT NOT NULL,
    target_column VARCHAR(256) NOT NULL,
    transformation CLOB DEFAULT NULL,
    created_at BIGINT NOT NULL
);

-- Indexes for columns table
CREATE INDEX IF NOT EXISTS idx_csrc ON lineage_columns (source_dataset_id, source_column);
CREATE INDEX IF NOT EXISTS idx_ctgt ON lineage_columns (target_dataset_id, target_column);