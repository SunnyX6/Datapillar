package com.sunny.datapillar.studio.module.tenant.service.sso;

import com.sunny.datapillar.studio.module.tenant.dto.SsoConfigDto;
import java.util.List;

/**
 * 单点登录配置服务
 * 提供单点登录配置业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface SsoConfigService {

    List<SsoConfigDto.Response> listConfigs();

    Long createConfig(SsoConfigDto.Create dto);

    void updateConfig(Long configId, SsoConfigDto.Update dto);
}
