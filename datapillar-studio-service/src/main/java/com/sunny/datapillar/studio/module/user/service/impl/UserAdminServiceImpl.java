package com.sunny.datapillar.studio.module.user.service.impl;

import com.sunny.datapillar.studio.module.user.dto.UserDto;
import com.sunny.datapillar.studio.module.user.service.UserAdminService;
import com.sunny.datapillar.studio.module.user.service.UserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 用户管理服务实现
 * 实现用户管理业务流程与规则校验
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Service
@RequiredArgsConstructor
public class UserAdminServiceImpl implements UserAdminService {

    private final UserService userService;

    @Override
    public List<UserDto.Response> listUsers() {
        return userService.getUserList();
    }

    @Override
    public UserDto.Response getUser(Long userId) {
        return userService.getUserById(userId);
    }

    @Override
    public Long createUser(UserDto.Create request) {
        return userService.createUser(request);
    }

    @Override
    public void updateUser(Long userId, UserDto.Update request) {
        userService.updateUser(userId, request);
    }

    @Override
    public void deleteUser(Long userId) {
        userService.deleteUser(userId);
    }
}
