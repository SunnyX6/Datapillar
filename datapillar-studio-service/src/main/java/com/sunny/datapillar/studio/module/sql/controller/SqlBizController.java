package com.sunny.datapillar.studio.module.sql.controller;

import com.sunny.datapillar.studio.module.sql.dto.SqlExecuteRequest;
import com.sunny.datapillar.studio.module.sql.dto.SqlExecuteResult;
import com.sunny.datapillar.studio.module.sql.service.SqlBizService;
import com.sunny.datapillar.common.response.ApiResponse;
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
 * SQL业务控制器
 * 负责SQL业务接口编排与请求处理
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Tag(name = "SQL", description = "SQL 执行接口")
@RestController
@RequestMapping("/biz/sql")
@RequiredArgsConstructor
public class SqlBizController {

    private final SqlBizService sqlBizService;

    @Operation(summary = "执行 SQL")
    @PostMapping("/execute")
    public ApiResponse<SqlExecuteResult> execute(@Valid @RequestBody SqlExecuteRequest request) {
        var executeResult = sqlBizService.executeSql(request);
        SqlExecuteResult result = new SqlExecuteResult();
        BeanUtils.copyProperties(executeResult, result);
        return ApiResponse.ok(result);
    }
}
