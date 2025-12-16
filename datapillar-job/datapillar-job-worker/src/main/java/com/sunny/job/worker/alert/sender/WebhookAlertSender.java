package com.sunny.job.worker.alert.sender;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sunny.job.worker.alert.AlertResult;
import com.sunny.job.worker.alert.AlertSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * 通用 Webhook 告警发送器
 * <p>
 * Webhook 配置格式：
 * {"url": "https://example.com/alert", "method": "POST", "headers": {"Authorization": "Bearer xxx"}}
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Component("webhookAlertSender")
public class WebhookAlertSender implements AlertSender {

    private static final Logger log = LoggerFactory.getLogger(WebhookAlertSender.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int TIMEOUT_SECONDS = 30;

    private final HttpClient httpClient;

    public WebhookAlertSender() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .build();
    }

    @Override
    public AlertResult send(String channelConfig, String title, String content) {
        try {
            JsonNode config = MAPPER.readTree(channelConfig);
            String url = config.get("url").asText();
            String method = config.has("method") ? config.get("method").asText().toUpperCase() : "POST";

            // 构建请求体
            ObjectNode requestBody = buildRequestBody(title, content);

            // 构建请求
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS));

            // 添加自定义请求头
            if (config.has("headers") && config.get("headers").isObject()) {
                Map<String, String> headers = MAPPER.convertValue(
                        config.get("headers"),
                        new TypeReference<Map<String, String>>() {}
                );
                headers.forEach(requestBuilder::header);
            }

            // 设置请求方法和请求体
            String bodyStr = MAPPER.writeValueAsString(requestBody);
            if ("POST".equals(method)) {
                requestBuilder.POST(HttpRequest.BodyPublishers.ofString(bodyStr, StandardCharsets.UTF_8));
            } else if ("PUT".equals(method)) {
                requestBuilder.PUT(HttpRequest.BodyPublishers.ofString(bodyStr, StandardCharsets.UTF_8));
            } else {
                requestBuilder.method(method, HttpRequest.BodyPublishers.ofString(bodyStr, StandardCharsets.UTF_8));
            }

            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // 判断响应状态
            int statusCode = response.statusCode();
            if (statusCode >= 200 && statusCode < 300) {
                log.info("Webhook告警发送成功: url={}, statusCode={}", url, statusCode);
                return AlertResult.ok();
            } else {
                log.warn("Webhook告警发送失败: url={}, statusCode={}, body={}",
                        url, statusCode, response.body());
                return AlertResult.fail("HTTP状态码: " + statusCode);
            }
        } catch (IOException e) {
            log.error("Webhook告警发送IO异常", e);
            return AlertResult.fail("网络异常: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Webhook告警发送被中断", e);
            return AlertResult.fail("请求被中断");
        } catch (Exception e) {
            log.error("Webhook告警发送异常", e);
            return AlertResult.fail("发送异常: " + e.getMessage());
        }
    }

    /**
     * 构建请求体
     */
    private ObjectNode buildRequestBody(String title, String content) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("title", title);
        body.put("content", content);
        body.put("timestamp", System.currentTimeMillis());
        body.put("source", "datapillar-job");
        return body;
    }
}
