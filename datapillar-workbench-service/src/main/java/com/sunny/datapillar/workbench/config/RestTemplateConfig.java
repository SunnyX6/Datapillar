package com.sunny.datapillar.workbench.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * RestTemplate 配置类
 *
 * @author sunny
 */
@Configuration
@RequiredArgsConstructor
public class RestTemplateConfig {

    private final AirflowConfig airflowConfig;
    private final ObjectMapper objectMapper;

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        RestTemplate restTemplate = builder
                .connectTimeout(Duration.ofMillis(airflowConfig.getConnectTimeout()))
                .readTimeout(Duration.ofMillis(airflowConfig.getReadTimeout()))
                .build();

        // 配置 Jackson 消息转换器
        List<HttpMessageConverter<?>> converters = new ArrayList<>(restTemplate.getMessageConverters());
        converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
        converters.add(new MappingJackson2HttpMessageConverter(objectMapper));
        restTemplate.setMessageConverters(converters);

        return restTemplate;
    }
}
