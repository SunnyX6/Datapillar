package com.sunny.datapillar.admin.module.sql.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sunny.datapillar.admin.module.sql.dto.SqlDto;
import com.sunny.datapillar.admin.module.sql.service.SqlService;
import com.sunny.datapillar.admin.web.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * SQL 执行控制器
 *
 * @author sunny
 */
@Tag(name = "SQL", description = "SQL 执行接口")
@RestController
@RequestMapping("/sql")
@RequiredArgsConstructor
public class SqlController {

    private final SqlService sqlService;

    @Operation(summary = "执行 SQL")
    @PostMapping("/execute")
    public ApiResponse<SqlDto.ExecuteResult> execute(
            @Valid @RequestBody SqlDto.ExecuteRequest request) {
        SqlDto.ExecuteResult result = sqlService.executeSql(request);
        return ApiResponse.ok(result);
    }
}
