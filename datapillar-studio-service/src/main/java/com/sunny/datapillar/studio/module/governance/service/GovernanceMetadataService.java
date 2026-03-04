package com.sunny.datapillar.studio.module.governance.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Governance metadata service. */
public interface GovernanceMetadataService {

  JsonNode proxy(String method, String path, Map<String, String> query, JsonNode body);
}
