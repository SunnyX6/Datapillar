package com.sunny.datapillar.studio.module.workflow.service;

/**
 * Airflow网关服务
 * 提供Airflow网关业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface AirflowGatewayService {

    <T> T get(String path, Class<T> responseType);

    <T, R> T post(String path, R body, Class<T> responseType);

    <T, R> T patch(String path, R body, Class<T> responseType);

    <T> T delete(String path, Class<T> responseType);
}
