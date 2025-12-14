package com.sunny.job.worker.alert.sender;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
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

/**
 * 企业微信告警发送器
 * <p>
 * 企业微信机器人配置格式：
 * {"webhook": "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx"}
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Component("wecomAlertSender")
public class WeComAlertSender implements AlertSender {

    private static final Logger log = LoggerFactory.getLogger(WeComAlertSender.class);
    private static final Gson GSON = new Gson();
    private static final int TIMEOUT_SECONDS = 30;

    private final HttpClient httpClient;

    public WeComAlertSender() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .build();
    }

    @Override
    public AlertResult send(String channelConfig, String title, String content) {
        try {
            JsonObject config = GSON.fromJson(channelConfig, JsonObject.class);
            String webhook = config.get("webhook").getAsString();

            // 构建请求体（使用 Markdown 格式）
            JsonObject requestBody = buildRequestBody(title, content);

            // 发送请求
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhook))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody), StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // 解析响应
            return parseResponse(response.body());
        } catch (IOException e) {
            log.error("企业微信告警发送IO异常", e);
            return AlertResult.fail("网络异常: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("企业微信告警发送被中断", e);
            return AlertResult.fail("请求被中断");
        } catch (Exception e) {
            log.error("企业微信告警发送异常", e);
            return AlertResult.fail("发送异常: " + e.getMessage());
        }
    }

    /**
     * 构建请求体
     */
    private JsonObject buildRequestBody(String title, String content) {
        JsonObject body = new JsonObject();
        body.addProperty("msgtype", "markdown");

        JsonObject markdown = new JsonObject();
        markdown.addProperty("content", formatMarkdown(title, content));

        body.add("markdown", markdown);
        return body;
    }

    /**
     * 格式化 Markdown 内容
     * <p>
     * 企业微信 Markdown 语法：
     * - 标题: # 标题
     * - 加粗: **text**
     * - 链接: [text](url)
     * - 绿色: <font color="info">text</font>
     * - 橙色: <font color="warning">text</font>
     * - 红色: <font color="comment">text</font>
     */
    private String formatMarkdown(String title, String content) {
        return "## <font color=\"warning\">" + title + "</font>\n" + content;
    }

    /**
     * 解析响应
     */
    private AlertResult parseResponse(String responseBody) {
        try {
            JsonObject response = GSON.fromJson(responseBody, JsonObject.class);
            int errcode = response.has("errcode") ? response.get("errcode").getAsInt() : -1;

            if (errcode == 0) {
                log.info("企业微信告警发送成功");
                return AlertResult.ok();
            } else {
                String errmsg = response.has("errmsg") ? response.get("errmsg").getAsString() : "未知错误";
                log.warn("企业微信告警发送失败: errcode={}, errmsg={}", errcode, errmsg);
                return AlertResult.fail("errcode=" + errcode + ", errmsg=" + errmsg);
            }
        } catch (Exception e) {
            log.error("企业微信响应解析失败: {}", responseBody, e);
            return AlertResult.fail("响应解析失败");
        }
    }
}
