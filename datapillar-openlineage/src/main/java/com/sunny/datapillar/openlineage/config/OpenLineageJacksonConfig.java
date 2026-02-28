package com.sunny.datapillar.openlineage.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openlineage.client.OpenLineageClientUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenLineage Jackson 配置。
 */
@Configuration
public class OpenLineageJacksonConfig {

    @Bean(name = "openLineageObjectMapper")
    public ObjectMapper openLineageObjectMapper() {
        return OpenLineageClientUtils.newObjectMapper();
    }
}
