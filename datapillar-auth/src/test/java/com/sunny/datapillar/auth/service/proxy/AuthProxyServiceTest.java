package com.sunny.datapillar.auth.service.proxy;

import com.sunny.datapillar.auth.config.AuthProxyProperties;
import com.sunny.datapillar.auth.dto.AuthDto;
import com.sunny.datapillar.auth.security.AuthAssertionSigner;
import com.sunny.datapillar.auth.service.AuthService;
import com.sunny.datapillar.common.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthProxyServiceTest {

    @Mock
    private DiscoveryClient discoveryClient;

    @Mock
    private RestTemplate authProxyRestTemplate;

    @Mock
    private AuthService authService;

    @Mock
    private AuthAssertionSigner assertionSigner;

    private AuthProxyProperties proxyProperties;
    private AuthProxyService authProxyService;

    @BeforeEach
    void setUp() {
        proxyProperties = new AuthProxyProperties();

        AuthProxyProperties.Route studioRoute = new AuthProxyProperties.Route();
        studioRoute.setPathPrefix("/api/studio");
        studioRoute.setServiceId("datapillar-studio-service");
        studioRoute.setTargetPrefix("/api/studio");
        studioRoute.setAssertionEnabled(true);

        AuthProxyProperties.Route oneMetaRoute = new AuthProxyProperties.Route();
        oneMetaRoute.setPathPrefix("/api/onemeta");
        oneMetaRoute.setServiceId("datapillar-gravitino");
        oneMetaRoute.setTargetPrefix("/api");

        proxyProperties.setRoutes(List.of(studioRoute, oneMetaRoute));

        authProxyService = new AuthProxyService(
                proxyProperties,
                discoveryClient,
                authProxyRestTemplate,
                authService,
                assertionSigner
        );
    }

    @Test
    void forward_shouldInjectAssertionForStudioRoute() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/proxy/api/studio/projects");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token");

        AuthDto.AccessContext accessContext = AuthDto.AccessContext.builder()
                .userId(1L)
                .tenantId(10L)
                .username("sunny")
                .email("sunny@datapillar.ai")
                .roles(List.of("ADMIN"))
                .impersonation(false)
                .build();

        when(authService.resolveAccessContext("test-token")).thenReturn(accessContext);
        when(discoveryClient.getInstances("datapillar-studio-service"))
                .thenReturn(List.of(new DefaultServiceInstance("studio-1", "datapillar-studio-service", "localhost", 7002, false)));
        when(assertionSigner.sign(any())).thenReturn("signed-assertion");
        when(assertionSigner.headerName()).thenReturn("X-Gateway-Assertion");
        when(authProxyRestTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok("ok".getBytes()));

        AuthProxyService.ForwardResponse response = authProxyService.forward(request, new byte[0]);

        assertEquals(200, response.statusCode());
        ArgumentCaptor<HttpEntity<byte[]>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        verify(authProxyRestTemplate).exchange(uriCaptor.capture(), eq(HttpMethod.GET), entityCaptor.capture(), eq(byte[].class));
        assertEquals("/api/studio/projects", uriCaptor.getValue().getPath());
        assertEquals("signed-assertion", entityCaptor.getValue().getHeaders().getFirst("X-Gateway-Assertion"));
    }

    @Test
    void forward_shouldRewriteOnemetaPath() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/proxy/api/onemeta/metalakes");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token");

        AuthDto.AccessContext accessContext = AuthDto.AccessContext.builder()
                .userId(1L)
                .tenantId(10L)
                .username("sunny")
                .email("sunny@datapillar.ai")
                .roles(List.of("ADMIN"))
                .impersonation(false)
                .build();

        when(authService.resolveAccessContext("test-token")).thenReturn(accessContext);
        when(discoveryClient.getInstances("datapillar-gravitino"))
                .thenReturn(List.of(new DefaultServiceInstance("meta-1", "datapillar-gravitino", "localhost", 8090, false)));
        when(authProxyRestTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok("ok".getBytes()));

        authProxyService.forward(request, new byte[0]);

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        verify(authProxyRestTemplate).exchange(uriCaptor.capture(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class));
        assertEquals("/api/metalakes", uriCaptor.getValue().getPath());
    }

    @Test
    void forward_shouldThrowWhenRouteMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/proxy/api/unknown/resources");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token");

        assertThrows(NotFoundException.class, () -> authProxyService.forward(request, new byte[0]));
    }
}
