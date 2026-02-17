package com.sunny.datapillar.auth.controller;

import com.sunny.datapillar.auth.service.proxy.AuthProxyService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证代理控制器
 * 负责统一受保护接口的鉴权代理与转发
 *
 * @author Sunny
 * @date 2026-01-01
 */
@RestController
@Hidden
@RequestMapping("/proxy")
@RequiredArgsConstructor
public class AuthProxyController {

    private final AuthProxyService authProxyService;

    @RequestMapping(value = "/**", method = {
            RequestMethod.GET,
            RequestMethod.POST,
            RequestMethod.PUT,
            RequestMethod.PATCH,
            RequestMethod.DELETE,
            RequestMethod.OPTIONS,
            RequestMethod.HEAD
    })
    public ResponseEntity<byte[]> proxy(HttpServletRequest request,
                                        @RequestBody(required = false) byte[] requestBody) {
        AuthProxyService.ForwardResponse forwardResponse = authProxyService.forward(
                request,
                requestBody == null ? new byte[0] : requestBody);
        return ResponseEntity.status(forwardResponse.statusCode())
                .headers(forwardResponse.headers())
                .body(forwardResponse.body());
    }
}
