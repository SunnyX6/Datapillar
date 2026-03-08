package com.sunny.datapillar.studio.integration.gravitino.service;

import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoOwnerResponse;

public interface GravitinoOwnerService {

  GravitinoOwnerResponse getOwner(String domain, String objectType, String fullName);

  GravitinoOwnerResponse getOwner(
      String metalake, String objectType, String fullName, String principalUsername);

  void setOwner(
      String metalake,
      String objectType,
      String fullName,
      String ownerName,
      String ownerType,
      String principalUsername);
}
