package com.sunny.datapillar.studio.module.tenant.service.sso;

import com.sunny.datapillar.studio.module.tenant.dto.SsoIdentityDto;
import java.util.List;

/**
 * 单点登录Identity服务
 * 提供单点登录Identity业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface SsoIdentityService {

    List<SsoIdentityDto.Item> list(String provider, Long userId);

    Long bindByCode(SsoIdentityDto.BindByCodeRequest request);

    void unbind(Long identityId);
}
