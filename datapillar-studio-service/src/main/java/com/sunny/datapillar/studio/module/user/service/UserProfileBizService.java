package com.sunny.datapillar.studio.module.user.service;

import com.sunny.datapillar.studio.module.user.dto.UserDto;

/**
 * 用户Profile业务服务
 * 提供用户Profile业务业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface UserProfileBizService {

    UserDto.Response getProfile(Long userId);

    void updateProfile(Long userId, UserDto.UpdateProfile request);
}
