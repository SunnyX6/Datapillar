package com.sunny.datapillar.studio.module.workflow.service;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 工作流DAG业务服务
 * 提供工作流DAG业务业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface WorkflowDagBizService {

    void publishWorkflow(Long id);

    void pauseWorkflow(Long id);

    void resumeWorkflow(Long id);

    JsonNode getDagDetail(Long id);

    JsonNode getDagVersions(Long id, int limit, int offset);

    JsonNode getDagVersion(Long id, int versionNumber);
}
