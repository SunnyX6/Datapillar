package com.sunny.datapillar.studio.module.governance.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Governance semantic service. */
public interface GovernanceSemanticService {

  JsonNode proxy(String method, String path, Map<String, String> query, JsonNode body);
}
