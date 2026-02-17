package com.sunny.datapillar.studio.module.user.service.impl;

import com.sunny.datapillar.studio.module.user.dto.UserDto;
import com.sunny.datapillar.studio.module.user.service.UserProfileBizService;
import com.sunny.datapillar.studio.module.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 用户Profile业务服务实现
 * 实现用户Profile业务业务流程与规则校验
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Service
@RequiredArgsConstructor
public class UserProfileBizServiceImpl implements UserProfileBizService {

    private final UserService userService;

    @Override
    public UserDto.Response getProfile(Long userId) {
        return userService.getUserById(userId);
    }

    @Override
    public void updateProfile(Long userId, UserDto.UpdateProfile request) {
        userService.updateProfile(userId, request);
    }
}
