package com.sunny.job.worker.alert.sender;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sunny.job.worker.alert.AlertResult;
import com.sunny.job.worker.alert.AlertSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * 钉钉告警发送器
 * <p>
 * 钉钉机器人配置格式：
 * {"webhook": "https://oapi.dingtalk.com/robot/send?access_token=xxx", "secret": "SECxxx"}
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Component("dingtalkAlertSender")
public class DingTalkAlertSender implements AlertSender {

    private static final Logger log = LoggerFactory.getLogger(DingTalkAlertSender.class);
    private static final Gson GSON = new Gson();
    private static final int TIMEOUT_SECONDS = 30;

    private final HttpClient httpClient;

    public DingTalkAlertSender() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .build();
    }

    @Override
    public AlertResult send(String channelConfig, String title, String content) {
        try {
            JsonObject config = GSON.fromJson(channelConfig, JsonObject.class);
            String webhook = config.get("webhook").getAsString();
            String secret = config.has("secret") ? config.get("secret").getAsString() : null;

            // 如果配置了签名密钥，需要在URL中添加签名参数
            String url = buildUrl(webhook, secret);

            // 构建请求体（使用 Markdown 格式）
            JsonObject requestBody = buildRequestBody(title, content);

            // 发送请求
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody), StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // 解析响应
            return parseResponse(response.body());
        } catch (IOException e) {
            log.error("钉钉告警发送IO异常", e);
            return AlertResult.fail("网络异常: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("钉钉告警发送被中断", e);
            return AlertResult.fail("请求被中断");
        } catch (Exception e) {
            log.error("钉钉告警发送异常", e);
            return AlertResult.fail("发送异常: " + e.getMessage());
        }
    }

    /**
     * 构建带签名的URL
     */
    private String buildUrl(String webhook, String secret) {
        if (secret == null || secret.isBlank()) {
            return webhook;
        }

        try {
            long timestamp = System.currentTimeMillis();
            String stringToSign = timestamp + "\n" + secret;

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            String sign = URLEncoder.encode(Base64.getEncoder().encodeToString(signData), StandardCharsets.UTF_8);

            String separator = webhook.contains("?") ? "&" : "?";
            return webhook + separator + "timestamp=" + timestamp + "&sign=" + sign;
        } catch (Exception e) {
            log.error("钉钉签名计算失败", e);
            return webhook;
        }
    }

    /**
     * 构建请求体
     */
    private JsonObject buildRequestBody(String title, String content) {
        JsonObject body = new JsonObject();
        body.addProperty("msgtype", "markdown");

        JsonObject markdown = new JsonObject();
        markdown.addProperty("title", title);
        markdown.addProperty("text", formatMarkdown(title, content));

        body.add("markdown", markdown);
        return body;
    }

    /**
     * 格式化 Markdown 内容
     */
    private String formatMarkdown(String title, String content) {
        return "## " + title + "\n\n" + content.replace("\n", "\n\n");
    }

    /**
     * 解析响应
     */
    private AlertResult parseResponse(String responseBody) {
        try {
            JsonObject response = GSON.fromJson(responseBody, JsonObject.class);
            int errcode = response.has("errcode") ? response.get("errcode").getAsInt() : -1;

            if (errcode == 0) {
                log.info("钉钉告警发送成功");
                return AlertResult.ok();
            } else {
                String errmsg = response.has("errmsg") ? response.get("errmsg").getAsString() : "未知错误";
                log.warn("钉钉告警发送失败: errcode={}, errmsg={}", errcode, errmsg);
                return AlertResult.fail("errcode=" + errcode + ", errmsg=" + errmsg);
            }
        } catch (Exception e) {
            log.error("钉钉响应解析失败: {}", responseBody, e);
            return AlertResult.fail("响应解析失败");
        }
    }
}
