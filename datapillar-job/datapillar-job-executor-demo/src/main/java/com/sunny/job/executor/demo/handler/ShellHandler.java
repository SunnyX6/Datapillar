package com.sunny.job.executor.demo.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.job.core.handler.JobContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * SHELL 脚本执行处理器
 * <p>
 * 参数格式（JSON）:
 * {
 *   "script": "echo Hello World",
 *   "timeout": 60
 * }
 *
 * @author SunnyX6
 * @date 2025-12-14
 */
public class ShellHandler {

    private static final Logger log = LoggerFactory.getLogger(ShellHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public void execute(JobContext context) {
        String params = context.getParams();
        log.info("SHELL 任务开始执行，jobRunId={}", context.getInstanceId());

        try {
            JsonNode json = MAPPER.readTree(params);
            String script = json.has("script") ? json.get("script").asText() : "";
            int timeout = json.has("timeout") ? json.get("timeout").asInt() : 60;

            if (script.isBlank()) {
                context.setFail("script 参数不能为空");
                return;
            }

            log.info("执行脚本: {}", script);

            ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", script);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    log.info("[SHELL] {}", line);
                }
            }

            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                context.setFail("脚本执行超时（" + timeout + "秒）");
                return;
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                context.setFail("脚本退出码 " + exitCode + "\n" + output);
                return;
            }

            context.setSuccess(output.toString().trim());
            log.info("SHELL 任务执行成功");

        } catch (Exception e) {
            log.error("SHELL 任务执行异常", e);
            context.setFail("执行异常: " + e.getMessage());
        }
    }
}
