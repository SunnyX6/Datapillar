package com.sunny.job.executor.jobhandler;

import com.sunny.job.core.context.DatapillarJobHelper;
import com.sunny.job.core.handler.annotation.DatapillarJob;
import com.sunny.job.core.util.GsonTool;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;

/**
 * Workflow节点对应的JobHandler实现
 * 根据前端workflow组件类型自动选择对应的JobHandler执行
 *
 * @author sunny
 * @date 2025-11-10
 */
@Component
public class GeneralDatapillarJob {

    /**
     * 工作流开始节点处理器
     * Start 节点通常不需要执行任何逻辑，只是作为工作流的入口点
     */
    @DatapillarJob("startJobHandler")
    public void startJobHandler() throws Exception {
        DatapillarJobHelper.log("工作流开始节点执行");
        // Start 节点不需要任何业务逻辑，直接返回成功即可
        DatapillarJobHelper.handleSuccess();
    }

    /**
     * 工作流结束节点处理器
     * End 节点通常不需要执行任何逻辑，只是作为工作流的出口点
     */
    @DatapillarJob("endJobHandler")
    public void endJobHandler() throws Exception {
        DatapillarJobHelper.log("工作流结束节点执行");
        // End 节点不需要任何业务逻辑，直接返回成功即可
        DatapillarJobHelper.handleSuccess();
    }

    /**
     * JDBC数据源任务处理器
     * 参数格式:
     * {
     *   "datasourceId": "数据源ID",
     *   "sql": "SELECT * FROM table",
     *   "timeout": 300
     * }
     */
    @DatapillarJob("jdbcJobHandler")
    public void jdbcJobHandler() throws Exception {
        String param = DatapillarJobHelper.getJobParam();
        DatapillarJobHelper.log("开始执行JDBC任务，参数: {}", param);

        if (param == null || param.trim().isEmpty()) {
            DatapillarJobHelper.handleFail("JDBC任务参数为空");
            return;
        }

        try {
            // 解析参数
            Map<String, Object> paramMap = GsonTool.fromJson(param, Map.class);
            String datasourceId = (String) paramMap.get("datasourceId");
            String sql = (String) paramMap.get("sql");

            DatapillarJobHelper.log("数据源ID: {}, SQL: {}", datasourceId, sql);

            // TODO: 实现JDBC执行逻辑
            // 1. 根据datasourceId获取数据源连接
            // 2. 执行SQL
            // 3. 处理结果

            DatapillarJobHelper.log("JDBC任务执行成功");
        } catch (Exception e) {
            DatapillarJobHelper.log(e);
            DatapillarJobHelper.handleFail("JDBC任务执行失败: " + e.getMessage());
        }
    }

    /**
     * DataX数据同步任务处理器
     * 参数格式:
     * {
     *   "jobJson": "DataX配置JSON",
     *   "jvmParams": "-Xms1G -Xmx1G"
     * }
     */
    @DatapillarJob("dataxJobHandler")
    public void dataxJobHandler() throws Exception {
        String param = DatapillarJobHelper.getJobParam();
        DatapillarJobHelper.log("开始执行DataX任务，参数: {}", param);

        if (param == null || param.trim().isEmpty()) {
            DatapillarJobHelper.handleFail("DataX任务参数为空");
            return;
        }

        try {
            Map<String, Object> paramMap = GsonTool.fromJson(param, Map.class);
            String jobJson = (String) paramMap.get("jobJson");
            String jvmParams = (String) paramMap.getOrDefault("jvmParams", "-Xms1G -Xmx1G");

            DatapillarJobHelper.log("DataX配置: {}", jobJson);

            // TODO: 实现DataX执行逻辑
            // 1. 生成临时配置文件
            // 2. 调用DataX命令
            // 3. 监控执行状态

            DatapillarJobHelper.log("DataX任务执行成功");
        } catch (Exception e) {
            DatapillarJobHelper.log(e);
            DatapillarJobHelper.handleFail("DataX任务执行失败: " + e.getMessage());
        }
    }

    /**
     * Hive任务处理器
     * 参数格式:
     * {
     *   "hiveql": "INSERT INTO table SELECT * FROM source",
     *   "database": "default"
     * }
     */
    @DatapillarJob("hiveJobHandler")
    public void hiveJobHandler() throws Exception {
        String param = DatapillarJobHelper.getJobParam();
        DatapillarJobHelper.log("开始执行Hive任务，参数: {}", param);

        if (param == null || param.trim().isEmpty()) {
            DatapillarJobHelper.handleFail("Hive任务参数为空");
            return;
        }

        try {
            Map<String, Object> paramMap = GsonTool.fromJson(param, Map.class);
            String hiveql = (String) paramMap.get("hiveql");
            String database = (String) paramMap.getOrDefault("database", "default");

            DatapillarJobHelper.log("数据库: {}, HiveQL: {}", database, hiveql);

            // TODO: 实现Hive执行逻辑
            // 1. 连接Hive Server2
            // 2. 执行HiveQL
            // 3. 处理结果

            DatapillarJobHelper.log("Hive任务执行成功");
        } catch (Exception e) {
            DatapillarJobHelper.log(e);
            DatapillarJobHelper.handleFail("Hive任务执行失败: " + e.getMessage());
        }
    }

    /**
     * Flink任务处理器
     * 参数格式:
     * {
     *   "jarPath": "/path/to/flink-job.jar",
     *   "mainClass": "com.example.FlinkJob",
     *   "args": "--input xxx --output yyy",
     *   "parallelism": 4
     * }
     */
    @DatapillarJob("flinkJobHandler")
    public void flinkJobHandler() throws Exception {
        String param = DatapillarJobHelper.getJobParam();
        DatapillarJobHelper.log("开始执行Flink任务，参数: {}", param);

        if (param == null || param.trim().isEmpty()) {
            DatapillarJobHelper.handleFail("Flink任务参数为空");
            return;
        }

        try {
            Map<String, Object> paramMap = GsonTool.fromJson(param, Map.class);
            String jarPath = (String) paramMap.get("jarPath");
            String mainClass = (String) paramMap.get("mainClass");
            String args = (String) paramMap.getOrDefault("args", "");
            Integer parallelism = (Integer) paramMap.getOrDefault("parallelism", 1);

            DatapillarJobHelper.log("Jar路径: {}, 主类: {}, 并行度: {}", jarPath, mainClass, parallelism);

            // TODO: 实现Flink执行逻辑
            // 1. 使用flink run命令提交任务
            // 2. 监控任务状态
            // 3. 获取任务结果

            DatapillarJobHelper.log("Flink任务执行成功");
        } catch (Exception e) {
            DatapillarJobHelper.log(e);
            DatapillarJobHelper.handleFail("Flink任务执行失败: " + e.getMessage());
        }
    }

    /**
     * 自定义任务处理器
     * 参数格式: 自定义JSON
     */
    @DatapillarJob("customJobHandler")
    public void customJobHandler() throws Exception {
        String param = DatapillarJobHelper.getJobParam();
        DatapillarJobHelper.log("开始执行自定义任务，参数: {}", param);

        try {
            // TODO: 实现自定义任务逻辑
            DatapillarJobHelper.log("自定义任务执行成功");
        } catch (Exception e) {
            DatapillarJobHelper.log(e);
            DatapillarJobHelper.handleFail("自定义任务执行失败: " + e.getMessage());
        }
    }

    /**
     * Shell脚本执行器
     * 参数格式：
     * {
     *   "scriptContent": "#!/bin/bash\necho 'Hello'\nls -la"
     * }
     */
    @DatapillarJob("shellJobHandler")
    public void shellJobHandler() throws Exception {
        String param = DatapillarJobHelper.getJobParam();

        if (param == null || param.trim().isEmpty()) {
            DatapillarJobHelper.handleFail("Shell任务参数为空");
            return;
        }

        int exitValue = -1;
        BufferedReader bufferedReader = null;
        java.io.File tempScriptFile = null;
        StringBuilder outputCollector = new StringBuilder();

        try {
            Map<String, Object> paramMap = GsonTool.fromJson(param, Map.class);
            String scriptContent = (String) paramMap.get("scriptContent");

            if (scriptContent == null || scriptContent.trim().isEmpty()) {
                DatapillarJobHelper.handleFail("scriptContent 不能为空");
                return;
            }

            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.redirectErrorStream(true);

            // 创建临时脚本文件
            tempScriptFile = java.io.File.createTempFile("datapillar_job_shell_", ".sh");
            tempScriptFile.setExecutable(true);

            // 写入脚本内容
            java.nio.file.Files.write(tempScriptFile.toPath(), scriptContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // 执行脚本
            processBuilder.command("bash", tempScriptFile.getAbsolutePath());

            Process process = processBuilder.start();

            BufferedInputStream bufferedInputStream = new BufferedInputStream(process.getInputStream());
            bufferedReader = new BufferedReader(new InputStreamReader(bufferedInputStream, java.nio.charset.StandardCharsets.UTF_8));

            // 获取日志文件名，直接写入纯文本输出
            String logFileName = DatapillarJobHelper.getJobLogFileName();

            // 输出日志（直接追加，不使用DatapillarJobHelper.log避免格式化）
            // 同时收集最后100行输出
            java.util.LinkedList<String> lastLines = new java.util.LinkedList<>();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (logFileName != null && !logFileName.isEmpty()) {
                    com.sunny.job.core.log.DatapillarJobFileAppender.appendLog(logFileName, line);
                }

                // 保留最后100行用于返回消息
                lastLines.add(line);
                if (lastLines.size() > 100) {
                    lastLines.removeFirst();
                }
            }

            // 等待执行完成
            process.waitFor();
            exitValue = process.exitValue();

            // 构建输出消息（成功和失败都返回）
            if (exitValue == 0) {
                outputCollector.append("输出: \n");
                // 成功时返回最后的输出
                if (!lastLines.isEmpty()) {
                    for (String outputLine : lastLines) {
                        outputCollector.append(outputLine).append("\n");
                    }
                } else {
                    outputCollector.append("Shell脚本执行成功 (无输出)");
                }
            } else {
                // 失败时返回错误信息
                outputCollector.append("Shell脚本执行失败 (退出码: ").append(exitValue).append(")\n");
                if (!lastLines.isEmpty()) {
                    outputCollector.append("最后输出:\n");
                    for (String outputLine : lastLines) {
                        outputCollector.append(outputLine).append("\n");
                    }
                } else {
                    outputCollector.append("(无输出)");
                }
            }

        } catch (Exception e) {
            DatapillarJobHelper.log(e);
            DatapillarJobHelper.handleFail("Shell任务执行异常: " + e.getMessage());
            return;
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (Exception ignored) {}
            }
            // 删除临时脚本文件
            if (tempScriptFile != null && tempScriptFile.exists()) {
                tempScriptFile.delete();
            }
        }

        if (exitValue == 0) {
            DatapillarJobHelper.handleSuccess(outputCollector.toString());
        } else {
            DatapillarJobHelper.handleFail(outputCollector.toString());
        }
    }

    /**
     * Python脚本执行器
     * 参数格式:
     * {
     *   "script": "print('Hello Python')",
     *   "workDir": "/tmp",
     *   "timeout": 300
     * }
     */
    @DatapillarJob("pythonJobHandler")
    public void pythonJobHandler() throws Exception {
        String param = DatapillarJobHelper.getJobParam();

        if (param == null || param.trim().isEmpty()) {
            DatapillarJobHelper.handleFail("Python脚本参数为空");
            return;
        }

        int exitValue = -1;
        BufferedReader bufferedReader = null;
        java.io.File tempScriptFile = null;
        StringBuilder outputCollector = new StringBuilder();

        try {
            Map<String, Object> paramMap = GsonTool.fromJson(param, Map.class);
            String script = (String) paramMap.get("script");
            String workDir = (String) paramMap.get("workDir");

            if (script == null || script.trim().isEmpty()) {
                DatapillarJobHelper.handleFail("Python脚本内容为空");
                return;
            }

            // 创建临时脚本文件
            tempScriptFile = java.io.File.createTempFile("datapillar_job_python_", ".py");
            java.nio.file.Files.write(tempScriptFile.toPath(), script.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            ProcessBuilder processBuilder = new ProcessBuilder("python3", tempScriptFile.getAbsolutePath());
            processBuilder.redirectErrorStream(true);

            if (workDir != null && !workDir.trim().isEmpty()) {
                processBuilder.directory(new java.io.File(workDir));
            }

            Process process = processBuilder.start();

            BufferedInputStream bufferedInputStream = new BufferedInputStream(process.getInputStream());
            bufferedReader = new BufferedReader(new InputStreamReader(bufferedInputStream, java.nio.charset.StandardCharsets.UTF_8));

            // 获取日志文件名，直接写入纯文本输出
            String logFileName = DatapillarJobHelper.getJobLogFileName();

            // 输出日志，同时收集最后100行输出
            java.util.LinkedList<String> lastLines = new java.util.LinkedList<>();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (logFileName != null && !logFileName.isEmpty()) {
                    com.sunny.job.core.log.DatapillarJobFileAppender.appendLog(logFileName, line);
                }

                // 保留最后100行用于返回消息
                lastLines.add(line);
                if (lastLines.size() > 100) {
                    lastLines.removeFirst();
                }
            }

            // 等待执行完成
            process.waitFor();
            exitValue = process.exitValue();

            // 构建输出消息（成功和失败都返回）
            if (exitValue == 0) {
                // 成功时返回最后的输出
                if (!lastLines.isEmpty()) {
                    outputCollector.append("Python脚本执行成功\n");
                    outputCollector.append("输出:\n");
                    for (String outputLine : lastLines) {
                        outputCollector.append(outputLine).append("\n");
                    }
                } else {
                    outputCollector.append("Python脚本执行成功 (无输出)");
                }
            } else {
                // 失败时返回错误信息
                outputCollector.append("Python脚本执行失败 (退出码: ").append(exitValue).append(")\n");
                if (!lastLines.isEmpty()) {
                    outputCollector.append("最后输出:\n");
                    for (String outputLine : lastLines) {
                        outputCollector.append(outputLine).append("\n");
                    }
                } else {
                    outputCollector.append("(无输出)");
                }
            }

        } catch (Exception e) {
            DatapillarJobHelper.log(e);
            DatapillarJobHelper.handleFail("Python脚本执行异常: " + e.getMessage());
            return;
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (Exception ignored) {}
            }
            if (tempScriptFile != null && tempScriptFile.exists()) {
                tempScriptFile.delete();
            }
        }

        if (exitValue == 0) {
            DatapillarJobHelper.handleSuccess(outputCollector.toString());
        } else {
            DatapillarJobHelper.handleFail(outputCollector.toString());
        }
    }

    /**
     * JavaScript (Node.js) 脚本执行器
     * 参数格式:
     * {
     *   "script": "console.log('Hello Node.js')",
     *   "workDir": "/tmp",
     *   "timeout": 300
     * }
     */
    @DatapillarJob("javascriptJobHandler")
    public void javascriptJobHandler() throws Exception {
        String param = DatapillarJobHelper.getJobParam();

        if (param == null || param.trim().isEmpty()) {
            DatapillarJobHelper.handleFail("JavaScript脚本参数为空");
            return;
        }

        int exitValue = -1;
        BufferedReader bufferedReader = null;
        java.io.File tempScriptFile = null;
        StringBuilder outputCollector = new StringBuilder();

        try {
            Map<String, Object> paramMap = GsonTool.fromJson(param, Map.class);
            String script = (String) paramMap.get("script");
            String workDir = (String) paramMap.get("workDir");

            if (script == null || script.trim().isEmpty()) {
                DatapillarJobHelper.handleFail("JavaScript脚本内容为空");
                return;
            }

            // 创建临时脚本文件
            tempScriptFile = java.io.File.createTempFile("datapillar_job_javascript_", ".js");
            java.nio.file.Files.write(tempScriptFile.toPath(), script.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            ProcessBuilder processBuilder = new ProcessBuilder("node", tempScriptFile.getAbsolutePath());
            processBuilder.redirectErrorStream(true);

            if (workDir != null && !workDir.trim().isEmpty()) {
                processBuilder.directory(new java.io.File(workDir));
            }

            Process process = processBuilder.start();

            BufferedInputStream bufferedInputStream = new BufferedInputStream(process.getInputStream());
            bufferedReader = new BufferedReader(new InputStreamReader(bufferedInputStream, java.nio.charset.StandardCharsets.UTF_8));

            // 获取日志文件名，直接写入纯文本输出
            String logFileName = DatapillarJobHelper.getJobLogFileName();

            // 输出日志，同时收集最后100行输出
            java.util.LinkedList<String> lastLines = new java.util.LinkedList<>();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (logFileName != null && !logFileName.isEmpty()) {
                    com.sunny.job.core.log.DatapillarJobFileAppender.appendLog(logFileName, line);
                }

                // 保留最后100行用于返回消息
                lastLines.add(line);
                if (lastLines.size() > 100) {
                    lastLines.removeFirst();
                }
            }

            // 等待执行完成
            process.waitFor();
            exitValue = process.exitValue();

            // 构建输出消息（成功和失败都返回）
            if (exitValue == 0) {
                // 成功时返回最后的输出
                if (!lastLines.isEmpty()) {
                    outputCollector.append("JavaScript脚本执行成功\n");
                    outputCollector.append("输出:\n");
                    for (String outputLine : lastLines) {
                        outputCollector.append(outputLine).append("\n");
                    }
                } else {
                    outputCollector.append("JavaScript脚本执行成功 (无输出)");
                }
            } else {
                // 失败时返回错误信息
                outputCollector.append("JavaScript脚本执行失败 (退出码: ").append(exitValue).append(")\n");
                if (!lastLines.isEmpty()) {
                    outputCollector.append("最后输出:\n");
                    for (String outputLine : lastLines) {
                        outputCollector.append(outputLine).append("\n");
                    }
                } else {
                    outputCollector.append("(无输出)");
                }
            }

        } catch (Exception e) {
            DatapillarJobHelper.log(e);
            DatapillarJobHelper.handleFail("JavaScript脚本执行异常: " + e.getMessage());
            return;
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (Exception ignored) {}
            }
            if (tempScriptFile != null && tempScriptFile.exists()) {
                tempScriptFile.delete();
            }
        }

        if (exitValue == 0) {
            DatapillarJobHelper.handleSuccess(outputCollector.toString());
        } else {
            DatapillarJobHelper.handleFail(outputCollector.toString());
        }
    }
}
