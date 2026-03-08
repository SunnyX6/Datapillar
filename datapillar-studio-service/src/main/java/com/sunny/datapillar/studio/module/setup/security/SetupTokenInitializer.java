package com.sunny.datapillar.studio.module.setup.security;

import com.sunny.datapillar.studio.module.setup.entity.SystemBootstrap;
import com.sunny.datapillar.studio.module.setup.enums.SetupBootstrapStatus;
import com.sunny.datapillar.studio.module.setup.mapper.SystemBootstrapMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * initialization tokenInitializercomponents Responsible for initializing the tokenInitializerCore
 * logic implementation
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SetupTokenInitializer implements ApplicationRunner {

  private static final int SYSTEM_BOOTSTRAP_ID = 1;
  private final SystemBootstrapMapper systemBootstrapMapper;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    SystemBootstrap bootstrap;
    try {
      bootstrap = systemBootstrapMapper.selectByIdForUpdate(SYSTEM_BOOTSTRAP_ID);
    } catch (RuntimeException ex) {
      log.warn("setup Entry prompt skip：system_bootstrap Not available", ex);
      return;
    }

    if (bootstrap == null) {
      log.warn("setup Entry prompt skip：system_bootstrap does not exist");
      return;
    }

    if (SetupBootstrapStatus.COMPLETED.matches(bootstrap.getStatus())) {
      return;
    }

    log.warn(
        "SETUP_URL=/setup（System is not initialized，Direct access to the installation wizard）");
  }
}
