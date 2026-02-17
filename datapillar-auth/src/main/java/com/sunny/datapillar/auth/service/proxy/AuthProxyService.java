package com.sunny.datapillar.auth.service.proxy;

import com.sunny.datapillar.auth.config.AuthProxyProperties;
import com.sunny.datapillar.auth.dto.AuthDto;
import com.sunny.datapillar.auth.security.AuthAssertionSigner;
import com.sunny.datapillar.auth.service.AuthService;
import com.sunny.datapillar.common.constant.HeaderConstants;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.NotFoundException;
import com.sunny.datapillar.common.exception.ServiceUnavailableException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 认证代理服务
 * 负责受保护请求统一鉴权并转发到目标微服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthProxyService {

    private static final String PROXY_PREFIX = "/proxy";
    private static final String AUTH_COOKIE_NAME = "auth-token";

    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailer",
            "transfer-encoding",
            "upgrade"
    );

    private static final Set<String> SANITIZED_CONTEXT_HEADERS = Set.of(
            HeaderConstants.HEADER_TENANT_ID,
            HeaderConstants.HEADER_USER_ID,
            HeaderConstants.HEADER_USERNAME,
            HeaderConstants.HEADER_EMAIL,
            HeaderConstants.HEADER_ACTOR_USER_ID,
            HeaderConstants.HEADER_ACTOR_TENANT_ID,
            HeaderConstants.HEADER_IMPERSONATION,
            HeaderConstants.HEADER_GATEWAY_ASSERTION
    );

    private final AuthProxyProperties proxyProperties;
    private final DiscoveryClient discoveryClient;
    private final RestTemplate authProxyRestTemplate;
    private final AuthService authService;
    private final AuthAssertionSigner assertionSigner;
    private final AtomicInteger roundRobin = new AtomicInteger(0);

    public ForwardResponse forward(HttpServletRequest request, byte[] requestBody) {
        String proxyPath = extractProxyPath(request);
        AuthProxyProperties.Route route = resolveRoute(proxyPath);
        AuthDto.AccessContext accessContext = authService.resolveAccessContext(extractAccessToken(request));

        String targetPath = mapTargetPath(proxyPath, normalizePathPrefix(route.getPathPrefix()), route.getTargetPrefix());
        ServiceInstance instance = selectInstance(route.getServiceId());
        URI targetUri = buildTargetUri(instance, targetPath, request.getQueryString());

        HttpMethod method = resolveMethod(request.getMethod());
        HttpHeaders headers = buildRequestHeaders(request, route, accessContext, method, targetPath);
        HttpEntity<byte[]> requestEntity = requiresRequestBody(method)
                ? new HttpEntity<>(requestBody, headers)
                : new HttpEntity<>(headers);

        try {
            ResponseEntity<byte[]> upstream = authProxyRestTemplate.exchange(targetUri, method, requestEntity, byte[].class);
            return new ForwardResponse(
                    upstream.getStatusCode().value(),
                    sanitizeResponseHeaders(upstream.getHeaders()),
                    upstream.getBody());
        } catch (HttpStatusCodeException exception) {
            return new ForwardResponse(
                    exception.getStatusCode().value(),
                    sanitizeResponseHeaders(exception.getResponseHeaders()),
                    exception.getResponseBodyAsByteArray());
        } catch (ResourceAccessException exception) {
            throw new ServiceUnavailableException(exception, "上游服务不可用: %s", route.getServiceId());
        } catch (Exception exception) {
            throw new ServiceUnavailableException(exception, "请求转发失败: %s", route.getServiceId());
        }
    }

    private String extractProxyPath(HttpServletRequest request) {
        String requestPath = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && requestPath.startsWith(contextPath)) {
            requestPath = requestPath.substring(contextPath.length());
        }

        if (!requestPath.startsWith(PROXY_PREFIX)) {
            throw new BadRequestException("代理路径非法: %s", requestPath);
        }

        String path = requestPath.substring(PROXY_PREFIX.length());
        if (!StringUtils.hasText(path)) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private AuthProxyProperties.Route resolveRoute(String proxyPath) {
        List<AuthProxyProperties.Route> routes = proxyProperties.getRoutes();
        if (routes == null || routes.isEmpty()) {
            throw new ServiceUnavailableException("代理路由未配置");
        }

        return routes.stream()
                .filter(route -> StringUtils.hasText(route.getPathPrefix()))
                .map(route -> new RouteCandidate(route, normalizePathPrefix(route.getPathPrefix())))
                .filter(candidate -> pathMatches(proxyPath, candidate.normalizedPrefix()))
                .max(Comparator.comparingInt(candidate -> candidate.normalizedPrefix().length()))
                .map(RouteCandidate::route)
                .orElseThrow(() -> new NotFoundException("未匹配代理路由: %s", proxyPath));
    }

    private String normalizePathPrefix(String prefix) {
        if (!StringUtils.hasText(prefix)) {
            return "/";
        }
        String normalized = prefix.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private boolean pathMatches(String path, String prefix) {
        if ("/".equals(prefix)) {
            return true;
        }
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private String mapTargetPath(String proxyPath, String routePrefix, String targetPrefix) {
        String normalizedTargetPrefix = normalizePathPrefix(targetPrefix);
        if ("/".equals(routePrefix)) {
            return proxyPath;
        }

        String suffix = proxyPath.equals(routePrefix) ? "" : proxyPath.substring(routePrefix.length());
        if (!StringUtils.hasText(suffix)) {
            return normalizedTargetPrefix;
        }
        if ("/".equals(normalizedTargetPrefix)) {
            return suffix.startsWith("/") ? suffix : "/" + suffix;
        }
        return suffix.startsWith("/")
                ? normalizedTargetPrefix + suffix
                : normalizedTargetPrefix + "/" + suffix;
    }

    private ServiceInstance selectInstance(String serviceId) {
        if (!StringUtils.hasText(serviceId)) {
            throw new ServiceUnavailableException("代理服务ID未配置");
        }
        List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
        if (instances == null || instances.isEmpty()) {
            throw new ServiceUnavailableException("上游服务不可用: %s", serviceId);
        }

        int index = Math.floorMod(roundRobin.getAndIncrement(), instances.size());
        return instances.get(index);
    }

    private URI buildTargetUri(ServiceInstance instance, String targetPath, String query) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(instance.getUri())
                .replacePath(targetPath);
        if (StringUtils.hasText(query)) {
            builder.replaceQuery(query);
        }
        return builder.build(true).toUri();
    }

    private HttpMethod resolveMethod(String method) {
        if (!StringUtils.hasText(method)) {
            throw new BadRequestException("HTTP 方法不能为空");
        }
        try {
            return HttpMethod.valueOf(method.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException(exception, "不支持的 HTTP 方法: %s", method);
        }
    }

    private HttpHeaders buildRequestHeaders(HttpServletRequest request,
                                            AuthProxyProperties.Route route,
                                            AuthDto.AccessContext accessContext,
                                            HttpMethod method,
                                            String targetPath) {
        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames != null && headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            if (!StringUtils.hasText(headerName)) {
                continue;
            }
            String lowerHeader = headerName.toLowerCase(Locale.ROOT);
            if (HOP_BY_HOP_HEADERS.contains(lowerHeader) || "host".equals(lowerHeader) || "content-length".equals(lowerHeader)) {
                continue;
            }
            Enumeration<String> headerValues = request.getHeaders(headerName);
            while (headerValues != null && headerValues.hasMoreElements()) {
                headers.add(headerName, headerValues.nextElement());
            }
        }

        SANITIZED_CONTEXT_HEADERS.forEach(headers::remove);
        if (Boolean.TRUE.equals(route.getAssertionEnabled())) {
            String assertion = assertionSigner.sign(new AuthAssertionSigner.AssertionPayload(
                    accessContext.getUserId(),
                    accessContext.getTenantId(),
                    accessContext.getUsername(),
                    accessContext.getEmail(),
                    accessContext.getRoles(),
                    Boolean.TRUE.equals(accessContext.getImpersonation()),
                    accessContext.getActorUserId(),
                    accessContext.getActorTenantId(),
                    method.name(),
                    targetPath
            ));
            headers.set(assertionSigner.headerName(), assertion);
        }
        return headers;
    }

    private HttpHeaders sanitizeResponseHeaders(HttpHeaders originalHeaders) {
        HttpHeaders sanitized = new HttpHeaders();
        if (originalHeaders == null || originalHeaders.isEmpty()) {
            return sanitized;
        }
        originalHeaders.forEach((headerName, values) -> {
            if (headerName == null) {
                return;
            }
            if (HOP_BY_HOP_HEADERS.contains(headerName.toLowerCase(Locale.ROOT))) {
                return;
            }
            sanitized.put(headerName, values);
        });
        return sanitized;
    }

    private boolean requiresRequestBody(HttpMethod method) {
        return method == HttpMethod.POST
                || method == HttpMethod.PUT
                || method == HttpMethod.PATCH
                || method == HttpMethod.DELETE;
    }

    private String extractAccessToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }

        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (AUTH_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public record ForwardResponse(int statusCode, HttpHeaders headers, byte[] body) {
    }

    private record RouteCandidate(AuthProxyProperties.Route route, String normalizedPrefix) {
    }
}
