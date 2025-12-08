package com.sunny.job.admin.controller.openapi;
import com.sunny.job.admin.util.StringTool;
import com.sunny.job.admin.util.CollectionTool;

import com.sunny.job.admin.scheduler.conf.DatapillarJobAdminConfig;
import com.sunny.job.core.biz.AdminBiz;
import com.sunny.job.core.biz.model.HandleCallbackParam;
import com.sunny.job.core.biz.model.RegistryParam;
import com.sunny.job.core.biz.model.ReturnT;
import com.sunny.job.core.util.DatapillarJobRemotingUtil;
import com.sunny.job.admin.util.StringTool;
import com.sunny.job.admin.util.GsonTool;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/**
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */
@Controller
@RequestMapping("/datapillar-job")
public class JobApiController {

    @Resource
    private AdminBiz adminBiz;

    /**
     * api
     *
     * @param uri
     * @param data
     * @return
     */
    @RequestMapping("/{uri}")
    @ResponseBody
    public ReturnT<String> api(HttpServletRequest request, @PathVariable("uri") String uri, @RequestBody(required = false) String data) {

        // valid
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return ReturnT.ofFail("invalid request, HttpMethod not support.");
        }
        if (StringTool.isBlank(uri)) {
            return ReturnT.ofFail("invalid request, uri-mapping empty.");
        }
        if (StringTool.isNotBlank(DatapillarJobAdminConfig.getAdminConfig().getAccessToken())
                && !DatapillarJobAdminConfig.getAdminConfig().getAccessToken().equals(request.getHeader(DatapillarJobRemotingUtil.DATAPILLAR_JOB_ACCESS_TOKEN))) {
            return ReturnT.ofFail("The access token is wrong.");
        }

        // services mapping
        if ("callback".equals(uri)) {
            List<HandleCallbackParam> callbackParamList = GsonTool.fromJson(data, List.class, HandleCallbackParam.class);
            return adminBiz.callback(callbackParamList);
        } else if ("registry".equals(uri)) {
            RegistryParam registryParam = GsonTool.fromJson(data, RegistryParam.class);
            return adminBiz.registry(registryParam);
        } else if ("registryRemove".equals(uri)) {
            RegistryParam registryParam = GsonTool.fromJson(data, RegistryParam.class);
            return adminBiz.registryRemove(registryParam);
        } else {
            return ReturnT.ofFail("invalid request, uri-mapping("+ uri +") not found.");
        }

    }

}
