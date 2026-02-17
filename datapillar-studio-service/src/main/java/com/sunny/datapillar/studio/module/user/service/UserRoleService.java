package com.sunny.datapillar.studio.module.user.service;

import com.sunny.datapillar.studio.module.user.dto.RoleDto;
import java.util.List;

/**
 * 用户角色服务
 * 提供用户角色业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface UserRoleService {

    List<RoleDto.Response> listRolesByUser(Long userId);

    void assignRoles(Long userId, List<Long> roleIds);
}
