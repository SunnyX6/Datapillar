package com.sunny.datapillar.studio.module.setup.security;

import com.sunny.datapillar.studio.module.setup.entity.SystemBootstrap;
import com.sunny.datapillar.studio.module.setup.mapper.SystemBootstrapMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 初始化令牌Initializer组件
 * 负责初始化令牌Initializer核心逻辑实现
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SetupTokenInitializer implements ApplicationRunner {

    private static final int SYSTEM_BOOTSTRAP_ID = 1;
    private static final int SETUP_COMPLETED = 1;

    private final SystemBootstrapMapper systemBootstrapMapper;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        SystemBootstrap bootstrap;
        try {
            bootstrap = systemBootstrapMapper.selectByIdForUpdate(SYSTEM_BOOTSTRAP_ID);
        } catch (RuntimeException ex) {
            log.warn("setup 入口提示跳过：system_bootstrap 不可用", ex);
            return;
        }

        if (bootstrap == null) {
            log.warn("setup 入口提示跳过：system_bootstrap 不存在");
            return;
        }

        if (bootstrap.getSetupCompleted() != null && bootstrap.getSetupCompleted() == SETUP_COMPLETED) {
            return;
        }

        log.warn("SETUP_URL=/setup（系统未初始化，直接访问安装向导）");
    }
}
