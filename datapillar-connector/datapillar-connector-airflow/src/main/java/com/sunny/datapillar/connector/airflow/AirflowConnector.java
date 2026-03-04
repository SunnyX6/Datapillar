package com.sunny.datapillar.connector.airflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.sunny.datapillar.connector.airflow.domain.WorkflowService;
import com.sunny.datapillar.connector.spi.Connector;
import com.sunny.datapillar.connector.spi.ConnectorInvocation;
import com.sunny.datapillar.connector.spi.ConnectorManifest;
import com.sunny.datapillar.connector.spi.ConnectorResponse;
import com.sunny.datapillar.connector.spi.ErrorType;
import com.sunny.datapillar.connector.spi.OperationSpec;
import java.util.Map;

/** Airflow connector implementation. */
public class AirflowConnector implements Connector {

  public static final String CONNECTOR_ID = "airflow";

  public static final String OP_DEPLOY = "airflow.workflow.deploy";
  public static final String OP_DELETE = "airflow.workflow.delete";
  public static final String OP_PAUSE = "airflow.workflow.pause";
  public static final String OP_RESUME = "airflow.workflow.resume";
  public static final String OP_GET_DAG = "airflow.workflow.get";
  public static final String OP_LIST_DAG_VERSIONS = "airflow.workflow.listVersions";
  public static final String OP_GET_DAG_VERSION = "airflow.workflow.getVersion";
  public static final String OP_TRIGGER_RUN = "airflow.run.trigger";
  public static final String OP_LIST_RUNS = "airflow.run.list";
  public static final String OP_GET_RUN = "airflow.run.get";
  public static final String OP_LIST_TASKS = "airflow.task.list";
  public static final String OP_GET_TASK = "airflow.task.get";
  public static final String OP_GET_TASK_LOGS = "airflow.task.logs";
  public static final String OP_RERUN_TASK = "airflow.task.rerun";
  public static final String OP_SET_TASK_STATE = "airflow.task.setState";
  public static final String OP_CLEAR_TASKS = "airflow.task.clear";

  private final WorkflowService workflowService;

  public AirflowConnector(WorkflowService workflowService) {
    this.workflowService = workflowService;
  }

  @Override
  public ConnectorManifest manifest() {
    return new ConnectorManifest(
        CONNECTOR_ID,
        "1.0.0",
        Map.ofEntries(
            Map.entry(OP_DEPLOY, OperationSpec.write(OP_DEPLOY)),
            Map.entry(OP_DELETE, OperationSpec.write(OP_DELETE)),
            Map.entry(OP_PAUSE, OperationSpec.write(OP_PAUSE)),
            Map.entry(OP_RESUME, OperationSpec.write(OP_RESUME)),
            Map.entry(OP_GET_DAG, OperationSpec.read(OP_GET_DAG)),
            Map.entry(OP_LIST_DAG_VERSIONS, OperationSpec.read(OP_LIST_DAG_VERSIONS)),
            Map.entry(OP_GET_DAG_VERSION, OperationSpec.read(OP_GET_DAG_VERSION)),
            Map.entry(OP_TRIGGER_RUN, OperationSpec.write(OP_TRIGGER_RUN)),
            Map.entry(OP_LIST_RUNS, OperationSpec.read(OP_LIST_RUNS)),
            Map.entry(OP_GET_RUN, OperationSpec.read(OP_GET_RUN)),
            Map.entry(OP_LIST_TASKS, OperationSpec.read(OP_LIST_TASKS)),
            Map.entry(OP_GET_TASK, OperationSpec.read(OP_GET_TASK)),
            Map.entry(OP_GET_TASK_LOGS, OperationSpec.read(OP_GET_TASK_LOGS)),
            Map.entry(OP_RERUN_TASK, OperationSpec.write(OP_RERUN_TASK)),
            Map.entry(OP_SET_TASK_STATE, OperationSpec.write(OP_SET_TASK_STATE)),
            Map.entry(OP_CLEAR_TASKS, OperationSpec.write(OP_CLEAR_TASKS))));
  }

  @Override
  public ConnectorResponse invoke(ConnectorInvocation invocation) {
    String operationId = invocation.operationId();
    JsonNode payload = invocation.payload();

    JsonNode response =
        switch (operationId) {
          case OP_DEPLOY -> workflowService.deploy(payload, invocation.context());
          case OP_DELETE -> workflowService.delete(payload, invocation.context());
          case OP_PAUSE -> workflowService.pause(payload, invocation.context());
          case OP_RESUME -> workflowService.resume(payload, invocation.context());
          case OP_GET_DAG -> workflowService.getDag(payload, invocation.context());
          case OP_LIST_DAG_VERSIONS ->
              workflowService.listDagVersions(payload, invocation.context());
          case OP_GET_DAG_VERSION -> workflowService.getDagVersion(payload, invocation.context());
          case OP_TRIGGER_RUN -> workflowService.triggerRun(payload, invocation.context());
          case OP_LIST_RUNS -> workflowService.listRuns(payload, invocation.context());
          case OP_GET_RUN -> workflowService.getRun(payload, invocation.context());
          case OP_LIST_TASKS -> workflowService.listTasks(payload, invocation.context());
          case OP_GET_TASK -> workflowService.getTask(payload, invocation.context());
          case OP_GET_TASK_LOGS -> workflowService.getTaskLogs(payload, invocation.context());
          case OP_RERUN_TASK -> workflowService.rerunTask(payload, invocation.context());
          case OP_SET_TASK_STATE -> workflowService.setTaskState(payload, invocation.context());
          case OP_CLEAR_TASKS -> workflowService.clearTasks(payload, invocation.context());
          default ->
              throw new com.sunny.datapillar.connector.spi.ConnectorException(
                  ErrorType.BAD_REQUEST, "Unsupported airflow operation: " + operationId);
        };
    return ConnectorResponse.of(response);
  }
}
