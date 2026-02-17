package com.sunny.datapillar.studio.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.sunny.datapillar.studio.context.TenantLinePolicy;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/**
 * MyBatisPlus配置
 * 负责MyBatisPlus配置装配与Bean初始化
 *
 * @author Sunny
 * @date 2026-01-01
 */

@Configuration
@MapperScan(basePackages = "com.sunny.datapillar.studio.module", annotationClass = Mapper.class)
public class MyBatisPlusConfig {

    private final TenantLinePolicy tenantLinePolicy;

    public MyBatisPlusConfig(TenantLinePolicy tenantLinePolicy) {
        this.tenantLinePolicy = tenantLinePolicy;
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        TenantLineInnerInterceptor tenantInterceptor = new TenantLineInnerInterceptor();
        tenantInterceptor.setTenantLineHandler(tenantLinePolicy);
        interceptor.addInnerInterceptor(tenantInterceptor);
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
