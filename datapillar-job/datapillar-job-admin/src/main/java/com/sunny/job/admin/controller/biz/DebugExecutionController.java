package com.sunny.job.admin.controller.biz;

import com.sunny.job.admin.dto.DebugExecutionRequestDTO;
import com.sunny.job.admin.model.DatapillarJobGroup;
import com.sunny.job.admin.scheduler.conf.DatapillarJobAdminConfig;
import com.sunny.job.admin.scheduler.route.ExecutorRouteStrategyEnum;
import com.sunny.job.core.biz.model.LogResult;
import com.sunny.job.core.biz.model.ReturnT;
import com.sunny.job.core.biz.model.TriggerParam;
import com.sunny.job.core.enums.ExecutorBlockStrategyEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * IDE代码调试执行Controller
 * 提供临时执行代码的能力，无需创建Job任务
 *
 * @author sunny
 * @since 2025-12-08
 */
@Slf4j
@RestController
@RequestMapping("/debug")
public class DebugExecutionController {

    /**
     * 调试执行响应数据
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DebugExecutionResult {
        /**
         * 日志ID（调试模式固定为-1）
         */
        private Long logId;

        /**
         * 触发信息
         */
        private String triggerMessage;

        /**
         * 执行日志（完整日志内容）
         */
        private String logs;
    }

    /**
     * 执行IDE调试代码
     *
     * @param request 调试执行请求
     * @return 执行响应
     */
    @PostMapping("/execute")
    public ReturnT<DebugExecutionResult> execute(@RequestBody DebugExecutionRequestDTO request) {
        try {
            // 1. 参数校验
            if (request.getLanguage() == null || request.getLanguage().trim().isEmpty()) {
                return ReturnT.ofFail("语言类型不能为空");
            }
            if (request.getCode() == null || request.getCode().trim().isEmpty()) {
                return ReturnT.ofFail("代码内容不能为空");
            }

            // 2. 映射语言类型到 executorHandler
            // 所有类型都使用 BEAN 模式，调用 GeneralDatapillarJob 中注册的 Handler
            String language = request.getLanguage().toLowerCase();
            String executorHandler;

            switch (language) {
                case "shell":
                    executorHandler = "shellJobHandler";
                    break;
                case "python":
                    executorHandler = "pythonJobHandler";
                    break;
                case "javascript":
                    executorHandler = "javascriptJobHandler";
                    break;
                case "mysql":
                case "postgresql":
                case "trino":
                case "impala":
                    executorHandler = "jdbcJobHandler";
                    break;
                case "hivesql":
                    executorHandler = "hiveJobHandler";
                    break;
                case "flinksql":
                case "sparksql":
                    executorHandler = "flinkJobHandler";
                    break;
                default:
                    return ReturnT.ofFail("不支持的语言类型: " + request.getLanguage());
            }

            // 3. 获取或选择执行器分组
            DatapillarJobGroup executorGroup;
            if (request.getExecutorGroupId() != null) {
                executorGroup = DatapillarJobAdminConfig.getAdminConfig()
                        .getDatapillarJobGroupMapper()
                        .load(request.getExecutorGroupId());
                if (executorGroup == null) {
                    return ReturnT.ofFail("执行器分组不存在: " + request.getExecutorGroupId());
                }
            } else {
                // 使用第一个可用的执行器分组
                List<DatapillarJobGroup> groups = DatapillarJobAdminConfig.getAdminConfig()
                        .getDatapillarJobGroupMapper()
                        .findAll();
                if (groups == null || groups.isEmpty()) {
                    return ReturnT.ofFail("没有可用的执行器分组");
                }
                executorGroup = groups.get(0);
            }

            // 4. 检查执行器是否在线
            if (executorGroup.getRegistryList() == null || executorGroup.getRegistryList().isEmpty()) {
                return ReturnT.ofFail("执行器分组无在线节点: " + executorGroup.getTitle());
            }

            // 5. 构建触发参数（不写数据库）
            TriggerParam triggerParam = new TriggerParam();
            triggerParam.setJobId(-1);
            triggerParam.setExecutorHandler(executorHandler);
            triggerParam.setExecutorBlockStrategy(ExecutorBlockStrategyEnum.SERIAL_EXECUTION.name());
            triggerParam.setExecutorTimeout(request.getTimeout() != null ? request.getTimeout() : 60);
            triggerParam.setLogId(-1);
            triggerParam.setLogDateTime(System.currentTimeMillis());
            triggerParam.setGlueType("BEAN");
            triggerParam.setGlueSource(null);
            triggerParam.setGlueUpdatetime(0);
            triggerParam.setBroadcastIndex(0);
            triggerParam.setBroadcastTotal(1);
            triggerParam.setExecutorParams(buildExecutionParams(language, request.getCode(), request.getParams()));

            // 6. 使用路由策略选择执行器地址
            String executorAddress = request.getExecutorAddress();
            if (executorAddress == null || executorAddress.trim().isEmpty()) {
                ExecutorRouteStrategyEnum routeStrategy = ExecutorRouteStrategyEnum.LEAST_FREQUENTLY_USED;
                ReturnT<String> routeResult = routeStrategy.getRouter().route(triggerParam, executorGroup.getRegistryList());

                if (routeResult.isSuccess()) {
                    executorAddress = routeResult.getContent();
                } else {
                    return ReturnT.ofFail("路由选择执行器失败: " + routeResult.getMsg());
                }
            }

            // 7. 同步调用debugRun接口（不写数据库）
            com.sunny.job.core.biz.ExecutorBiz executorBiz = com.sunny.job.admin.scheduler.scheduler.DatapillarJobScheduler.getExecutorBiz(executorAddress);
            ReturnT<LogResult> debugResult = executorBiz.debugRun(triggerParam);

            // 8. 返回结果
            if (debugResult.isSuccess()) {
                LogResult logResult = debugResult.getContent();
                String logs = logResult != null ? logResult.getLogContent() : "";

                log.info("IDE调试执行成功, language={}, executor={}", request.getLanguage(), executorAddress);

                DebugExecutionResult result = DebugExecutionResult.builder()
                        .logId(-1L)
                        .triggerMessage("执行成功")
                        .logs(logs)
                        .build();

                return ReturnT.ofSuccess(result);
            } else {
                log.warn("IDE调试执行失败, language={}, error={}", request.getLanguage(), debugResult.getMsg());
                return ReturnT.ofFail(debugResult.getMsg());
            }

        } catch (Exception e) {
            log.error("IDE调试执行异常", e);
            return ReturnT.ofFail("执行异常: " + e.getMessage());
        }
    }

    /**
     * 构建执行参数（JSON格式）
     * 根据不同语言类型构建对应的参数格式
     */
    private String buildExecutionParams(String language, String code, String params) {
        String jsonParams;

        switch (language) {
            case "shell":
            case "python":
            case "javascript":
                // 脚本类型：传递 scriptContent 字段（与正式执行一致）
                jsonParams = String.format("{\"scriptContent\": %s}",
                        escapeJson(code));
                break;

            case "mysql":
            case "postgresql":
            case "trino":
            case "impala":
                // JDBC类型
                jsonParams = String.format("{\"sql\": %s, \"datasourceId\": %s}",
                        escapeJson(code),
                        params != null ? escapeJson(params) : "\"default\"");
                break;

            case "hivesql":
                // Hive类型
                jsonParams = String.format("{\"hiveql\": %s, \"database\": %s}",
                        escapeJson(code),
                        params != null ? escapeJson(params) : "\"default\"");
                break;

            case "flinksql":
            case "sparksql":
                // Flink/Spark SQL类型
                jsonParams = String.format("{\"sql\": %s, \"params\": %s}",
                        escapeJson(code),
                        params != null ? escapeJson(params) : "\"\"");
                break;

            default:
                jsonParams = String.format("{\"code\": %s}", escapeJson(code));
        }

        return jsonParams;
    }

    /**
     * JSON字符串转义
     */
    private String escapeJson(String str) {
        if (str == null) {
            return "null";
        }
        return "\"" + str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }
}
