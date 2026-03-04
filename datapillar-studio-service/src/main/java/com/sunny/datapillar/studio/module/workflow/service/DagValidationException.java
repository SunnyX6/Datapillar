package com.sunny.datapillar.studio.module.workflow.service;

/**
 * DAGValidationAbnormal DescriptionDAGValidationException semantics and error context
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class DagValidationException
    extends com.sunny.datapillar.studio.module.workflow.service.dag.DagValidationException {

  public DagValidationException(String message) {
    super(message);
  }

  public DagValidationException(String message, Throwable cause) {
    super(message, cause);
  }
}
