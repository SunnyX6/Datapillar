package com.sunny.datapillar.studio.module.tenant.service;

import com.sunny.datapillar.studio.module.user.dto.RoleDto;
import com.sunny.datapillar.studio.module.user.dto.UserDto;
import com.sunny.datapillar.studio.module.user.entity.User;
import java.util.List;

/**
 * 租户Member管理服务
 * 提供租户Member管理业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface TenantMemberAdminService {

    List<User> listUsers(Integer status);

    void updateMemberStatus(Long userId, Integer status);

    void updateUser(Long userId, UserDto.Update dto);

    List<RoleDto.Response> getRolesByUserId(Long userId);

    void assignRoles(Long userId, List<Long> roleIds);
}
