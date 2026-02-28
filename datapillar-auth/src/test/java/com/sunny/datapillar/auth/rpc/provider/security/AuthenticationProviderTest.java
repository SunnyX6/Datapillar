package com.sunny.datapillar.auth.rpc.provider.security;

import com.sunny.datapillar.auth.dto.auth.request.*;
import com.sunny.datapillar.auth.dto.auth.response.*;
import com.sunny.datapillar.auth.dto.login.request.*;
import com.sunny.datapillar.auth.dto.login.response.*;
import com.sunny.datapillar.auth.dto.oauth.request.*;
import com.sunny.datapillar.auth.dto.oauth.response.*;
import com.sunny.datapillar.auth.security.AuthAssertionSigner;
import com.sunny.datapillar.auth.service.AuthService;
import com.sunny.datapillar.common.exception.UnauthorizedException;
import com.sunny.datapillar.common.rpc.security.v1.CheckAuthenticationRequest;
import com.sunny.datapillar.common.rpc.security.v1.CheckAuthenticationResponse;
import com.sunny.datapillar.common.rpc.security.v1.DenyCode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationProviderTest {

    @Mock
    private AuthService authService;

    @Mock
    private AuthAssertionSigner assertionSigner;

    @InjectMocks
    private AuthenticationProvider provider;

    @Test
    void shouldAllowAndBuildPrincipalForStudio() {
        AuthenticationContextResponse context = AuthenticationContextResponse.builder()
                .userId(1L)
                .tenantId(2L)
                .username("sunny")
                .email("sunny@qq.com")
                .roles(List.of("ADMIN"))
                .impersonation(false)
                .sessionId("sid-1")
                .tokenId("jti-1")
                .build();

        when(authService.resolveAuthenticationContext("token-1")).thenReturn(context);
        when(assertionSigner.sign(any(), eq("datapillar-studio-service"))).thenReturn("gw-assertion");

        CheckAuthenticationResponse response = provider.checkAuthentication(CheckAuthenticationRequest.newBuilder()
                .setToken("token-1")
                .setMethod("GET")
                .setPath("/api/studio/admin/llms/models")
                .build());

        Assertions.assertTrue(response.getAuthenticated());
        Assertions.assertEquals(DenyCode.DENY_CODE_UNSPECIFIED, response.getDenyCode());
        Assertions.assertEquals("gw-assertion", response.getGatewayAssertion());
        Assertions.assertNotNull(response.getPrincipal());
        Assertions.assertEquals(1L, response.getPrincipal().getUserId());
        Assertions.assertEquals(2L, response.getPrincipal().getTenantId());
    }

    @Test
    void shouldRejectWhenTokenMissing() {
        CheckAuthenticationResponse response = provider.checkAuthentication(CheckAuthenticationRequest.newBuilder()
                .setToken(" ")
                .setPath("/api/studio/admin/llms/models")
                .setMethod("GET")
                .build());

        Assertions.assertFalse(response.getAuthenticated());
        Assertions.assertEquals(DenyCode.TOKEN_MISSING, response.getDenyCode());
    }

    @Test
    void shouldBuildAiAssertionWhenAiResourceRequested() {
        AuthenticationContextResponse context = AuthenticationContextResponse.builder()
                .userId(8L)
                .tenantId(9L)
                .username("ai-user")
                .roles(List.of("DEVELOPER"))
                .sessionId("sid-8")
                .tokenId("jti-8")
                .build();

        when(authService.resolveAuthenticationContext("token-ai")).thenReturn(context);
        when(assertionSigner.sign(any(), eq("datapillar-ai"))).thenReturn("ai-assertion");

        CheckAuthenticationResponse response = provider.checkAuthentication(CheckAuthenticationRequest.newBuilder()
                .setToken("token-ai")
                .setMethod("POST")
                .setPath("/api/ai/knowledge/wiki/namespaces")
                .build());

        Assertions.assertTrue(response.getAuthenticated());
        Assertions.assertEquals("ai-assertion", response.getGatewayAssertion());
        Assertions.assertEquals(8L, response.getPrincipal().getUserId());
        Assertions.assertEquals(9L, response.getPrincipal().getTenantId());
    }

    @Test
    void shouldMapExpiredTokenToDenyCode() {
        when(authService.resolveAuthenticationContext("expired-token"))
                .thenThrow(new com.sunny.datapillar.common.exception.UnauthorizedException("Token已过期"));

        CheckAuthenticationResponse response = provider.checkAuthentication(CheckAuthenticationRequest.newBuilder()
                .setToken("expired-token")
                .setMethod("GET")
                .setPath("/api/ai/knowledge/wiki/namespaces")
                .build());

        Assertions.assertFalse(response.getAuthenticated());
        Assertions.assertEquals(DenyCode.TOKEN_EXPIRED, response.getDenyCode());
    }

    @Test
    void shouldBuildOnemetaAssertionWhenOnemetaResourceRequested() {
        AuthenticationContextResponse context = AuthenticationContextResponse.builder()
                .userId(5L)
                .tenantId(6L)
                .tenantCode("tenant-6")
                .tenantName("Tenant 6")
                .username("meta-user")
                .roles(List.of("ANALYST"))
                .sessionId("sid-5")
                .tokenId("jti-5")
                .build();

        when(authService.resolveAuthenticationContext("token-meta")).thenReturn(context);
        when(assertionSigner.sign(any(), eq("datapillar-gravitino"))).thenReturn("meta-assertion");

        CheckAuthenticationResponse response = provider.checkAuthentication(CheckAuthenticationRequest.newBuilder()
                .setToken("token-meta")
                .setMethod("GET")
                .setPath("/api/onemeta/metalakes/OneMeta/catalogs")
                .build());

        Assertions.assertTrue(response.getAuthenticated());
        Assertions.assertEquals("meta-assertion", response.getGatewayAssertion());
        Assertions.assertEquals(5L, response.getPrincipal().getUserId());
        Assertions.assertEquals(6L, response.getPrincipal().getTenantId());
    }

    @Test
    void shouldBuildOpenLineageAssertionWhenOpenLineageResourceRequested() {
        AuthenticationContextResponse context = AuthenticationContextResponse.builder()
                .userId(7L)
                .tenantId(8L)
                .tenantCode("tenant-8")
                .tenantName("Tenant 8")
                .username("lineage-user")
                .roles(List.of("DEVELOPER"))
                .sessionId("sid-7")
                .tokenId("jti-7")
                .build();

        when(authService.resolveAuthenticationContext("token-ol")).thenReturn(context);
        when(assertionSigner.sign(any(), eq("datapillar-openlineage"))).thenReturn("openlineage-assertion");

        CheckAuthenticationResponse response = provider.checkAuthentication(CheckAuthenticationRequest.newBuilder()
                .setToken("token-ol")
                .setMethod("POST")
                .setPath("/api/openlineage")
                .build());

        Assertions.assertTrue(response.getAuthenticated());
        Assertions.assertEquals("openlineage-assertion", response.getGatewayAssertion());
        Assertions.assertEquals(7L, response.getPrincipal().getUserId());
        Assertions.assertEquals(8L, response.getPrincipal().getTenantId());
    }
}
