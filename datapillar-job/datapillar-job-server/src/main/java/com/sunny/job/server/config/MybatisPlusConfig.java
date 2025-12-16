package com.sunny.job.server.config;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.sunny.job.core.id.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置
 * <p>
 * 配置自定义 ID 生成器，使用 Snowflake 变种算法
 *
 * @author SunnyX6
 * @date 2025-12-15
 */
@Configuration
public class MybatisPlusConfig {

    private static final Logger log = LoggerFactory.getLogger(MybatisPlusConfig.class);

    /**
     * 自定义 ID 生成器
     * <p>
     * MyBatis-Plus 在插入数据时，如果 @TableId(type = IdType.ASSIGN_ID)，
     * 会调用此生成器生成 ID
     *
     * @param idGenerator 分布式 ID 生成器（来自 ClusterConfig）
     * @return MyBatis-Plus IdentifierGenerator
     */
    @Bean
    public IdentifierGenerator identifierGenerator(IdGenerator idGenerator) {
        log.info("配置 MyBatis-Plus ID 生成器: nodeId={}", idGenerator.getNodeId());
        return entity -> idGenerator.nextId();
    }
}
