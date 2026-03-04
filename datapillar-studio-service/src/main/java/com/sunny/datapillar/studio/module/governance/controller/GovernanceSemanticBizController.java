package com.sunny.datapillar.studio.module.governance.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.sunny.datapillar.studio.module.governance.service.GovernanceSemanticService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/** Governance semantic proxy controller. */
@Tag(name = "GovernanceSemantic", description = "Governance semantic proxy interface")
@RestController
@RequestMapping("/biz/governance/semantic")
@RequiredArgsConstructor
public class GovernanceSemanticBizController {

  private static final String BASE_PATH = "/biz/governance/semantic";

  private final GovernanceSemanticService governanceSemanticService;

  @Operation(summary = "Proxy governance semantic request")
  @RequestMapping(
      value = {"", "/", "/**"},
      method = {
        RequestMethod.GET,
        RequestMethod.POST,
        RequestMethod.PUT,
        RequestMethod.PATCH,
        RequestMethod.DELETE
      })
  public JsonNode proxy(HttpServletRequest request, @RequestBody(required = false) JsonNode body) {
    return governanceSemanticService.proxy(
        request.getMethod(), resolvePath(request), resolveQuery(request), body);
  }

  private String resolvePath(HttpServletRequest request) {
    String contextPath = request.getContextPath();
    String uri = request.getRequestURI();
    String withinApplication =
        uri.startsWith(contextPath) ? uri.substring(contextPath.length()) : uri;
    if (withinApplication.equals(BASE_PATH) || withinApplication.equals(BASE_PATH + "/")) {
      return "/";
    }
    if (!withinApplication.startsWith(BASE_PATH + "/")) {
      return "/";
    }
    return withinApplication.substring(BASE_PATH.length());
  }

  private Map<String, String> resolveQuery(HttpServletRequest request) {
    Map<String, String> query = new LinkedHashMap<>();
    request
        .getParameterMap()
        .forEach(
            (key, values) -> {
              if (values == null || values.length == 0 || values[0] == null) {
                return;
              }
              query.put(key, values[0]);
            });
    return query;
  }
}
