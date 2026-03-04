package com.sunny.datapillar.studio.module.workflow.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.sunny.datapillar.studio.module.workflow.service.WorkflowDagBizService;
import com.sunny.datapillar.studio.module.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * WorkflowDAGBusiness service implementation Implement workflowDAGBusiness business process and
 * rule verification
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Service
@RequiredArgsConstructor
public class WorkflowDagBizServiceImpl implements WorkflowDagBizService {

  private final WorkflowService workflowService;

  @Override
  public void publishWorkflow(Long id) {
    workflowService.publishWorkflow(id);
  }

  @Override
  public void pauseWorkflow(Long id) {
    workflowService.pauseWorkflow(id);
  }

  @Override
  public void resumeWorkflow(Long id) {
    workflowService.resumeWorkflow(id);
  }

  @Override
  public JsonNode getDagDetail(Long id) {
    return workflowService.getDagDetail(id);
  }

  @Override
  public JsonNode getDagVersions(Long id, int limit, int offset) {
    return workflowService.getDagVersions(id, limit, offset);
  }

  @Override
  public JsonNode getDagVersion(Long id, int versionNumber) {
    return workflowService.getDagVersion(id, versionNumber);
  }
}
