package com.sunny.job.worker.alert;

import com.sunny.job.core.enums.AlarmChannelType;
import com.sunny.job.worker.alert.sender.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;

/**
 * 告警发送服务
 * <p>
 * 管理所有告警渠道的发送器，根据渠道类型路由到对应的发送器
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Service
public class AlertSenderService {

    private static final Logger log = LoggerFactory.getLogger(AlertSenderService.class);

    private final Map<AlarmChannelType, AlertSender> senderMap;

    public AlertSenderService(DingTalkAlertSender dingTalkSender,
                              WeComAlertSender weComSender,
                              FeishuAlertSender feishuSender,
                              WebhookAlertSender webhookSender,
                              EmailAlertSender emailSender) {
        this.senderMap = new EnumMap<>(AlarmChannelType.class);
        this.senderMap.put(AlarmChannelType.DINGTALK, dingTalkSender);
        this.senderMap.put(AlarmChannelType.WECOM, weComSender);
        this.senderMap.put(AlarmChannelType.FEISHU, feishuSender);
        this.senderMap.put(AlarmChannelType.WEBHOOK, webhookSender);
        this.senderMap.put(AlarmChannelType.EMAIL, emailSender);

        log.info("AlertSenderService 初始化完成，已注册 {} 个告警渠道", senderMap.size());
    }

    /**
     * 发送告警
     *
     * @param channelType   渠道类型
     * @param channelConfig 渠道配置（JSON格式）
     * @param title         告警标题
     * @param content       告警内容
     * @return 发送结果
     */
    public AlertResult send(AlarmChannelType channelType, String channelConfig, String title, String content) {
        AlertSender sender = senderMap.get(channelType);

        if (sender == null) {
            log.warn("未知的告警渠道类型: {}", channelType);
            return AlertResult.fail("未知的告警渠道类型: " + channelType);
        }

        log.debug("发送告警: channelType={}, title={}", channelType, title);
        return sender.send(channelConfig, title, content);
    }

    /**
     * 发送告警（通过渠道类型代码）
     *
     * @param channelTypeCode 渠道类型代码
     * @param channelConfig   渠道配置（JSON格式）
     * @param title           告警标题
     * @param content         告警内容
     * @return 发送结果
     */
    public AlertResult send(int channelTypeCode, String channelConfig, String title, String content) {
        try {
            AlarmChannelType channelType = AlarmChannelType.of(channelTypeCode);
            return send(channelType, channelConfig, title, content);
        } catch (IllegalArgumentException e) {
            log.warn("未知的告警渠道类型代码: {}", channelTypeCode);
            return AlertResult.fail("未知的告警渠道类型代码: " + channelTypeCode);
        }
    }
}
