package com.sunny.datapillar.studio.module.sso.service;

import com.sunny.datapillar.studio.module.sso.dto.SsoConfigDto;
import java.util.List;

/**
 * SSO 配置服务
 */
public interface SsoConfigService {

    List<SsoConfigDto.Response> listConfigs();

    Long createConfig(SsoConfigDto.Create dto);

    void updateConfig(Long configId, SsoConfigDto.Update dto);
}
