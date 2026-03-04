package com.sunny.datapillar.studio.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * RedisConfiguration responsibleRedisConfigure assembly withBeaninitialization
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Configuration
@ConditionalOnProperty(prefix = "spring.data.redis", name = "host", matchIfMissing = false)
public class RedisConfig {

  /** RedisTemplateConfiguration */
  @Bean
  public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);

    // useJackson2JsonRedisSerializerto serialize and deserializeredisofvaluevalue
    Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer =
        new Jackson2JsonRedisSerializer<>(Object.class);
    ObjectMapper om = new ObjectMapper();
    om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
    om.activateDefaultTyping(
        LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL);
    jackson2JsonRedisSerializer.setObjectMapper(om);

    StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();

    // keyadoptStringserialization method
    template.setKeySerializer(stringRedisSerializer);
    // hashofkeyAlso usedStringserialization method
    template.setHashKeySerializer(stringRedisSerializer);
    // valueThe serialization method usesjackson
    template.setValueSerializer(jackson2JsonRedisSerializer);
    // hashofvalueThe serialization method usesjackson
    template.setHashValueSerializer(jackson2JsonRedisSerializer);
    template.afterPropertiesSet();

    return template;
  }
}
