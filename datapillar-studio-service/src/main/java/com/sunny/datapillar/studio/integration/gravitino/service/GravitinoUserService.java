package com.sunny.datapillar.studio.integration.gravitino.service;

import java.util.List;

public interface GravitinoUserService {

  List<String> createUser(String username, Long externalUserId, String principalUsername);

  void deleteUser(String username, String principalUsername);

  void deleteUser(String metalake, String username, String principalUsername);
}
