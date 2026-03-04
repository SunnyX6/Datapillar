package com.sunny.datapillar.studio.module.workflow.service;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * WorkflowDAGbusiness services Provide workflowDAGBusiness capabilities and domain services
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
