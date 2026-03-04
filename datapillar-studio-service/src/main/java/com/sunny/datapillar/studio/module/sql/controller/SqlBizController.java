package com.sunny.datapillar.studio.module.sql.controller;

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
import com.sunny.datapillar.studio.module.sql.service.SqlBizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SQLBusiness controller responsibleSQLBusiness interface orchestration and request processing
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Tag(name = "SQL", description = "SQL execution interface")
@RestController
@RequestMapping("/biz/sql")
@RequiredArgsConstructor
public class SqlBizController {

  private final SqlBizService sqlBizService;

  @Operation(summary = "execute SQL")
  @PostMapping("/execute")
  public ApiResponse<SqlExecuteResponse> execute(@Valid @RequestBody SqlExecuteRequest request) {
    var executeResult = sqlBizService.executeSql(request);
    SqlExecuteResponse result = new SqlExecuteResponse();
    BeanUtils.copyProperties(executeResult, result);
    return ApiResponse.ok(result);
  }
}
