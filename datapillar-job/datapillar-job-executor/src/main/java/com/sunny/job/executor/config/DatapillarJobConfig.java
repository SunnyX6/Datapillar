package com.sunny.job.executor.config;

import com.sunny.job.core.executor.impl.DatapillarJobSpringExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * datapillar-job config
 *
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */
@Configuration
public class DatapillarJobConfig {
    private Logger logger = LoggerFactory.getLogger(DatapillarJobConfig.class);

    @Value("${datapillar.job.admin.addresses}")
    private String adminAddresses;

    @Value("${datapillar.job.admin.accessToken}")
    private String accessToken;

    @Value("${datapillar.job.admin.timeout}")
    private int timeout;

    @Value("${datapillar.job.executor.appname}")
    private String appname;

    @Value("${datapillar.job.executor.address}")
    private String address;

    @Value("${datapillar.job.executor.ip}")
    private String ip;

    @Value("${datapillar.job.executor.port}")
    private int port;

    @Value("${datapillar.job.executor.logpath}")
    private String logPath;

    @Value("${datapillar.job.executor.logretentiondays}")
    private int logRetentionDays;


    @Bean
    public DatapillarJobSpringExecutor datapillarJobExecutor() {
        logger.info(">>>>>>>>>>> datapillar-job config init.");
        DatapillarJobSpringExecutor datapillarJobSpringExecutor = new DatapillarJobSpringExecutor();
        datapillarJobSpringExecutor.setAdminAddresses(adminAddresses);
        datapillarJobSpringExecutor.setAppname(appname);
        datapillarJobSpringExecutor.setAddress(address);
        datapillarJobSpringExecutor.setIp(ip);
        datapillarJobSpringExecutor.setPort(port);
        datapillarJobSpringExecutor.setAccessToken(accessToken);
        datapillarJobSpringExecutor.setTimeout(timeout);
        datapillarJobSpringExecutor.setLogPath(logPath);
        datapillarJobSpringExecutor.setLogRetentionDays(logRetentionDays);

        return datapillarJobSpringExecutor;
    }

    /**
     * 针对多网卡、容器内部署等情况，可借助 "spring-cloud-commons" 提供的 "InetUtils" 组件灵活定制注册IP；
     *
     *      1、引入依赖：
     *          <dependency>
     *             <groupId>org.springframework.cloud</groupId>
     *             <artifactId>spring-cloud-commons</artifactId>
     *             <version>${version}</version>
     *         </dependency>
     *
     *      2、配置文件，或者容器启动变量
     *          spring.cloud.inetutils.preferred-networks: 'xxx.xxx.xxx.'
     *
     *      3、获取IP
     *          String ip_ = inetUtils.findFirstNonLoopbackHostInfo().getIpAddress();
     */





     
}