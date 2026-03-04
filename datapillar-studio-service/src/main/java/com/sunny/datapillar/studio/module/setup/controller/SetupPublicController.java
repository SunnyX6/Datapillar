package com.sunny.datapillar.studio.module.setup.controller;

import com.sunny.datapillar.common.response.ApiResponse;
import com.sunny.datapillar.studio.dto.llm.request.*;
import com.sunny.datapillar.studio.dto.llm.response.*;
import com.sunny.datapillar.studio.dto.project.request.*;
import com.sunny.datapillar.studio.dto.project.response.*;
import com.sunny.datapillar.studio.dto.setup.request.*;
import com.sunny.datapillar.studio.dto.setup.response.*;
import com.sunny.datapillar.studio.dto.sql.request.*;
import com.sunny.datapillar.studio.dto.sql.response.*;
import com.sunny.datapillar.studio.dto.tenant.request.*;
import com.sunny.datapillar.studio.dto.tenant.response.*;
import com.sunny.datapillar.studio.dto.user.request.*;
import com.sunny.datapillar.studio.dto.user.response.*;
import com.sunny.datapillar.studio.dto.workflow.request.*;
import com.sunny.datapillar.studio.dto.workflow.response.*;
import com.sunny.datapillar.studio.module.setup.service.SetupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Initialize public controller Responsible for initializing public interface orchestration and
 * request processing
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Tag(name = "System initialization", description = "First installation initialization")
@RestController
@RequestMapping("/setup")
@RequiredArgsConstructor
public class SetupPublicController {

  private final SetupService setupService;

  @Operation(summary = "Query initialization status")
  @GetMapping("/status")
  public ApiResponse<SetupStatusResponse> status() {
    return ApiResponse.ok(setupService.getStatus());
  }

  @Operation(summary = "Perform first initialization")
  @PostMapping
  public ApiResponse<SetupInitializeResponse> initialize(
      @Valid @RequestBody SetupInitializeRequest request) {
    return ApiResponse.ok(setupService.initialize(request));
  }
}
