package com.sunny.datapillar.studio.module.user.service;

import com.sunny.datapillar.studio.module.user.dto.UserDto;
import java.util.List;

/**
 * 用户管理服务
 * 提供用户管理业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface UserAdminService {

    List<UserDto.Response> listUsers();

    UserDto.Response getUser(Long userId);

    Long createUser(UserDto.Create request);

    void updateUser(Long userId, UserDto.Update request);

    void deleteUser(Long userId);
}
