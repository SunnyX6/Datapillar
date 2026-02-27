package com.sunny.datapillar.studio.dto.tenant.request;

import com.sunny.datapillar.studio.dto.tenant.response.SsoDingtalkConfigItem;
import com.sunny.datapillar.studio.validation.ValidSsoConfigUpdateRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@ValidSsoConfigUpdateRequest
@Schema(name = "SsoConfigUpdate")
public class SsoConfigUpdateRequest {

    @Size(max = 255, message = "基础URL长度不能超过255个字符")
    private String baseUrl;

    @Valid
    private SsoDingtalkConfigItem config;

    @Min(value = 0, message = "参数错误")
    @Max(value = 1, message = "参数错误")
    private Integer status;
}
