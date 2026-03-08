package com.sunny.datapillar.studio.integration.gravitino.service;

public interface GravitinoSetupService {

  void initializeResources(
      Long tenantId,
      String tenantCode,
      Long adminUserId,
      String adminUsername,
      String adminRoleName);
}
