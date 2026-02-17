package com.sunny.datapillar.studio.module.workflow.service.impl;

import com.sunny.datapillar.studio.module.workflow.service.AirflowGatewayService;
import com.sunny.datapillar.studio.module.workflow.service.client.AirflowClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Airflow网关服务实现
 * 实现Airflow网关业务流程与规则校验
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Service
@RequiredArgsConstructor
public class AirflowGatewayServiceImpl implements AirflowGatewayService {

    private final AirflowClient airflowClient;

    @Override
    public <T> T get(String path, Class<T> responseType) {
        return airflowClient.get(path, responseType);
    }

    @Override
    public <T, R> T post(String path, R body, Class<T> responseType) {
        return airflowClient.post(path, body, responseType);
    }

    @Override
    public <T, R> T patch(String path, R body, Class<T> responseType) {
        return airflowClient.patch(path, body, responseType);
    }

    @Override
    public <T> T delete(String path, Class<T> responseType) {
        return airflowClient.delete(path, responseType);
    }
}
