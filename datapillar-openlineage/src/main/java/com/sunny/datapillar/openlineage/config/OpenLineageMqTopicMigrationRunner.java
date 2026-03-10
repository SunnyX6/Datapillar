package com.sunny.datapillar.openlineage.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.common.constant.PermName;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.spring.autoconfigure.RocketMQProperties;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Execute RocketMQ topic bootstrap in openlineage service startup. */
@Slf4j
@Component
@Order(1)
public class OpenLineageMqTopicMigrationRunner implements ApplicationRunner {

  private final OpenLineageRuntimeConfig runtimeProperties;
  private final RocketMQProperties rocketMQProperties;

  public OpenLineageMqTopicMigrationRunner(
      OpenLineageRuntimeConfig runtimeProperties, RocketMQProperties rocketMQProperties) {
    this.runtimeProperties = runtimeProperties;
    this.rocketMQProperties = rocketMQProperties;
  }

  @Override
  public void run(ApplicationArguments args) {
    OpenLineageRuntimeConfig.Bootstrap bootstrap = runtimeProperties.getMq().getBootstrap();
    if (!bootstrap.isEnabled()) {
      log.info("openlineage_mq_topic_migrations disabled by configuration");
      return;
    }

    String nameServer = trimToNull(rocketMQProperties.getNameServer());
    if (nameServer == null) {
      throw new IllegalStateException("rocketmq.name-server is empty");
    }

    String clusterName =
        requireText(bootstrap.getClusterName(), "openlineage.mq.bootstrap.cluster-name");
    int readQueueNums =
        requirePositive(bootstrap.getReadQueueNums(), "openlineage.mq.bootstrap.read-queue-nums");
    int writeQueueNums =
        requirePositive(bootstrap.getWriteQueueNums(), "openlineage.mq.bootstrap.write-queue-nums");
    List<String> topics = resolveRequiredTopics();
    String failureMessage =
        "openlineage_mq_topic_migrations failed cluster=%s namesrv=%s"
            .formatted(clusterName, nameServer);

    DefaultMQAdminExt admin = createAdmin(nameServer);
    try {
      admin.start();
      Set<String> brokerAddrs = resolveMasterBrokerAddrs(admin, clusterName);
      for (String topic : topics) {
        TopicConfig config = buildTopicConfig(topic, readQueueNums, writeQueueNums);
        for (String brokerAddr : brokerAddrs) {
          admin.createAndUpdateTopicConfig(brokerAddr, config);
        }
      }
      log.info(
          "openlineage_mq_topic_migrations applied cluster={} topics={} brokerCount={}",
          clusterName,
          String.join(",", topics),
          brokerAddrs.size());
    } catch (MQBrokerException | MQClientException | RemotingException ex) {
      throw new IllegalStateException(failureMessage, ex);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(failureMessage, ex);
    } finally {
      admin.shutdown();
    }
  }

  private DefaultMQAdminExt createAdmin(String nameServer) {
    String accessKey = resolveAccessKey();
    String secretKey = resolveSecretKey();

    DefaultMQAdminExt admin;
    if (accessKey != null || secretKey != null) {
      if (accessKey == null || secretKey == null) {
        throw new IllegalStateException(
            "rocketmq access-key/secret-key must be both configured or both empty");
      }
      admin =
          new DefaultMQAdminExt(new AclClientRPCHook(new SessionCredentials(accessKey, secretKey)));
    } else {
      admin = new DefaultMQAdminExt();
    }
    admin.setNamesrvAddr(nameServer);
    admin.setAdminExtGroup("ol-topic-migration-admin-" + UUID.randomUUID());
    return admin;
  }

  private Set<String> resolveMasterBrokerAddrs(DefaultMQAdminExt admin, String clusterName)
      throws InterruptedException, MQBrokerException, RemotingException {
    ClusterInfo clusterInfo = admin.examineBrokerClusterInfo();
    Map<String, Set<String>> clusterAddrTable = clusterInfo.getClusterAddrTable();
    Set<String> brokerNames = clusterAddrTable == null ? null : clusterAddrTable.get(clusterName);
    if (brokerNames == null || brokerNames.isEmpty()) {
      throw new IllegalStateException("RocketMQ cluster not found: " + clusterName);
    }

    Map<String, BrokerData> brokerAddrTable = clusterInfo.getBrokerAddrTable();
    LinkedHashSet<String> brokerAddrs = new LinkedHashSet<>();
    for (String brokerName : brokerNames) {
      BrokerData brokerData = brokerAddrTable == null ? null : brokerAddrTable.get(brokerName);
      if (brokerData == null) {
        continue;
      }
      String addr = null;
      if (brokerData.getBrokerAddrs() != null) {
        addr = trimToNull(brokerData.getBrokerAddrs().get(MixAll.MASTER_ID));
      }
      if (addr == null) {
        addr = trimToNull(brokerData.selectBrokerAddr());
      }
      if (addr != null) {
        brokerAddrs.add(addr);
      }
    }
    if (brokerAddrs.isEmpty()) {
      throw new IllegalStateException("No broker address found in cluster: " + clusterName);
    }
    return brokerAddrs;
  }

  private TopicConfig buildTopicConfig(String topic, int readQueueNums, int writeQueueNums) {
    TopicConfig topicConfig =
        new TopicConfig(
            topic, readQueueNums, writeQueueNums, PermName.PERM_READ | PermName.PERM_WRITE);
    topicConfig.setOrder(false);
    return topicConfig;
  }

  private List<String> resolveRequiredTopics() {
    OpenLineageRuntimeConfig.Topic topic = runtimeProperties.getMq().getTopic();
    LinkedHashSet<String> topics = new LinkedHashSet<>();
    collectTopic(topics, topic.getEvents(), "openlineage.mq.topic.events");
    collectTopic(topics, topic.getEmbedding(), "openlineage.mq.topic.embedding");
    collectTopic(topics, topic.getRebuildCommand(), "openlineage.mq.topic.rebuild-command");
    collectTopic(topics, topic.getEventsDlq(), "openlineage.mq.topic.events-dlq");
    collectTopic(topics, topic.getEmbeddingDlq(), "openlineage.mq.topic.embedding-dlq");
    collectTopic(topics, topic.getRebuildCommandDlq(), "openlineage.mq.topic.rebuild-command-dlq");
    return new ArrayList<>(topics);
  }

  private void collectTopic(Set<String> topics, String topicName, String key) {
    String normalized = requireText(topicName, key);
    topics.add(normalized);
  }

  private String resolveAccessKey() {
    String producerAccessKey =
        trimToNull(
            rocketMQProperties.getProducer() == null
                ? null
                : rocketMQProperties.getProducer().getAccessKey());
    if (producerAccessKey != null) {
      return producerAccessKey;
    }
    return trimToNull(
        rocketMQProperties.getConsumer() == null
            ? null
            : rocketMQProperties.getConsumer().getAccessKey());
  }

  private String resolveSecretKey() {
    String producerSecretKey =
        trimToNull(
            rocketMQProperties.getProducer() == null
                ? null
                : rocketMQProperties.getProducer().getSecretKey());
    if (producerSecretKey != null) {
      return producerSecretKey;
    }
    return trimToNull(
        rocketMQProperties.getConsumer() == null
            ? null
            : rocketMQProperties.getConsumer().getSecretKey());
  }

  private String requireText(String value, String key) {
    String normalized = trimToNull(value);
    if (normalized == null) {
      throw new IllegalStateException(key + " is empty");
    }
    return normalized;
  }

  private int requirePositive(int value, String key) {
    if (value <= 0) {
      throw new IllegalStateException(key + " must be greater than 0");
    }
    return value;
  }

  private String trimToNull(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    return value.trim();
  }
}
