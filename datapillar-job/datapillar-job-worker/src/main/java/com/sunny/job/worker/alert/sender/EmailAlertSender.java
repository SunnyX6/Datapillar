package com.sunny.job.worker.alert.sender;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.job.worker.alert.AlertResult;
import com.sunny.job.worker.alert.AlertSender;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * 邮件告警发送器
 * <p>
 * 邮件配置格式：
 * {"smtp": "smtp.example.com", "port": 465, "ssl": true, "username": "xxx", "password": "xxx", "from": "xxx@example.com", "to": ["a@b.com"]}
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Component("emailAlertSender")
public class EmailAlertSender implements AlertSender {

    private static final Logger log = LoggerFactory.getLogger(EmailAlertSender.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int TIMEOUT_MS = 30000;

    @Override
    public AlertResult send(String channelConfig, String title, String content) {
        try {
            JsonNode config = MAPPER.readTree(channelConfig);

            String smtp = config.get("smtp").asText();
            int port = config.has("port") ? config.get("port").asInt() : 465;
            boolean ssl = !config.has("ssl") || config.get("ssl").asBoolean();
            String username = config.get("username").asText();
            String password = config.get("password").asText();
            String from = config.has("from") ? config.get("from").asText() : username;
            List<String> toList = parseRecipients(config);

            if (toList.isEmpty()) {
                return AlertResult.fail("收件人列表为空");
            }

            // 配置邮件属性
            Properties props = new Properties();
            props.put("mail.smtp.host", smtp);
            props.put("mail.smtp.port", String.valueOf(port));
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.connectiontimeout", String.valueOf(TIMEOUT_MS));
            props.put("mail.smtp.timeout", String.valueOf(TIMEOUT_MS));
            props.put("mail.smtp.writetimeout", String.valueOf(TIMEOUT_MS));

            if (ssl) {
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
                props.put("mail.smtp.socketFactory.port", String.valueOf(port));
            } else {
                props.put("mail.smtp.starttls.enable", "true");
            }

            // 创建会话
            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password);
                }
            });

            // 创建邮件
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.setSubject(title, "UTF-8");
            message.setContent(formatHtmlContent(title, content), "text/html;charset=UTF-8");

            // 添加收件人
            for (String to : toList) {
                message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
            }

            // 发送邮件
            Transport.send(message);

            log.info("邮件告警发送成功: to={}", toList);
            return AlertResult.ok();
        } catch (Exception e) {
            log.error("邮件告警发送异常", e);
            return AlertResult.fail("发送异常: " + e.getMessage());
        }
    }

    /**
     * 解析收件人列表
     */
    private List<String> parseRecipients(JsonNode config) {
        List<String> recipients = new ArrayList<>();

        if (config.has("to")) {
            JsonNode toNode = config.get("to");
            if (toNode.isArray()) {
                for (JsonNode item : toNode) {
                    recipients.add(item.asText());
                }
            } else {
                recipients.add(toNode.asText());
            }
        }

        return recipients;
    }

    /**
     * 格式化 HTML 内容
     */
    private String formatHtmlContent(String title, String content) {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; margin: 0; padding: 20px; background-color: #f5f5f5; }
                        .container { max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); overflow: hidden; }
                        .header { background: linear-gradient(135deg, #ff6b6b 0%%, #ee5a5a 100%%); color: white; padding: 20px 24px; }
                        .header h1 { margin: 0; font-size: 18px; font-weight: 600; }
                        .content { padding: 24px; color: #333; line-height: 1.6; }
                        .content pre { background-color: #f8f9fa; padding: 16px; border-radius: 4px; overflow-x: auto; font-size: 13px; border: 1px solid #e9ecef; }
                        .footer { background-color: #f8f9fa; padding: 16px 24px; font-size: 12px; color: #666; border-top: 1px solid #e9ecef; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>%s</h1>
                        </div>
                        <div class="content">
                            <pre>%s</pre>
                        </div>
                        <div class="footer">
                            此邮件由 Datapillar Job 调度平台自动发送，请勿直接回复。
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(escapeHtml(title), escapeHtml(content));
    }

    /**
     * HTML 转义
     */
    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
