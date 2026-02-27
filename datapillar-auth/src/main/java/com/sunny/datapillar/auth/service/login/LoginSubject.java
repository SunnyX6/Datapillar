package com.sunny.datapillar.auth.service.login;

import com.sunny.datapillar.auth.dto.auth.request.*;
import com.sunny.datapillar.auth.dto.auth.response.*;
import com.sunny.datapillar.auth.dto.login.request.*;
import com.sunny.datapillar.auth.dto.login.response.*;
import com.sunny.datapillar.auth.dto.oauth.request.*;
import com.sunny.datapillar.auth.dto.oauth.response.*;
import com.sunny.datapillar.auth.entity.Tenant;
import com.sunny.datapillar.auth.entity.User;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 登录Subject组件
 * 负责登录Subject核心逻辑实现
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Data
@Builder
public class LoginSubject {

    private User user;

    private Tenant tenant;

    private List<TenantOptionItem> tenantOptions;

    private String loginMethod;

    public boolean requiresTenantSelection() {
        return tenant == null && tenantOptions != null && !tenantOptions.isEmpty();
    }
}
