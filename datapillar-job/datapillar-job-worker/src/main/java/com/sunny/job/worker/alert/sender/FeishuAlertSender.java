package com.sunny.job.worker.alert.sender;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * 飞书告警发送器
 * <p>
 * 飞书机器人配置格式：
 * {"webhook": "https://open.feishu.cn/open-apis/bot/v2/hook/xxx", "secret": "xxx"}
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Component("feishuAlertSender")
public class FeishuAlertSender implements AlertSender {

    private static final Logger log = LoggerFactory.getLogger(FeishuAlertSender.class);
    private static final Gson GSON = new Gson();
    private static final int TIMEOUT_SECONDS = 30;

    private final HttpClient httpClient;

    public FeishuAlertSender() {
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

            // 构建请求体（使用富文本格式）
            JsonObject requestBody = buildRequestBody(title, content, secret);

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
            log.error("飞书告警发送IO异常", e);
            return AlertResult.fail("网络异常: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("飞书告警发送被中断", e);
            return AlertResult.fail("请求被中断");
        } catch (Exception e) {
            log.error("飞书告警发送异常", e);
            return AlertResult.fail("发送异常: " + e.getMessage());
        }
    }

    /**
     * 构建请求体
     */
    private JsonObject buildRequestBody(String title, String content, String secret) {
        JsonObject body = new JsonObject();
        body.addProperty("msg_type", "post");

        // 如果配置了签名密钥，添加签名
        if (secret != null && !secret.isBlank()) {
            long timestamp = System.currentTimeMillis() / 1000;
            String sign = generateSign(timestamp, secret);
            body.addProperty("timestamp", String.valueOf(timestamp));
            body.addProperty("sign", sign);
        }

        // 构建富文本内容
        JsonObject contentObj = new JsonObject();
        JsonObject post = new JsonObject();
        JsonObject zhCn = new JsonObject();

        zhCn.addProperty("title", title);
        zhCn.add("content", buildRichTextContent(content));

        post.add("zh_cn", zhCn);
        contentObj.add("post", post);
        body.add("content", contentObj);

        return body;
    }

    /**
     * 生成签名
     * <p>
     * 飞书签名算法：
     * 1. 把 timestamp + "\n" + secret 作为签名串
     * 2. 使用 HmacSHA256 计算签名
     * 3. 对签名结果进行 Base64 编码
     */
    private String generateSign(long timestamp, String secret) {
        try {
            String stringToSign = timestamp + "\n" + secret;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(new byte[0], "HmacSHA256"));
            byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signData);
        } catch (Exception e) {
            log.error("飞书签名计算失败", e);
            return "";
        }
    }

    /**
     * 构建富文本内容
     */
    private JsonArray buildRichTextContent(String content) {
        JsonArray contentArray = new JsonArray();

        // 将内容按行分割，每行作为一个段落
        String[] lines = content.split("\n");
        for (String line : lines) {
            JsonArray lineArray = new JsonArray();

            JsonObject textObj = new JsonObject();
            textObj.addProperty("tag", "text");
            textObj.addProperty("text", line);
            lineArray.add(textObj);

            contentArray.add(lineArray);
        }

        return contentArray;
    }

    /**
     * 解析响应
     */
    private AlertResult parseResponse(String responseBody) {
        try {
            JsonObject response = GSON.fromJson(responseBody, JsonObject.class);
            int code = response.has("code") ? response.get("code").getAsInt() : -1;

            if (code == 0) {
                log.info("飞书告警发送成功");
                return AlertResult.ok();
            } else {
                String msg = response.has("msg") ? response.get("msg").getAsString() : "未知错误";
                log.warn("飞书告警发送失败: code={}, msg={}", code, msg);
                return AlertResult.fail("code=" + code + ", msg=" + msg);
            }
        } catch (Exception e) {
            log.error("飞书响应解析失败: {}", responseBody, e);
            return AlertResult.fail("响应解析失败");
        }
    }
}
