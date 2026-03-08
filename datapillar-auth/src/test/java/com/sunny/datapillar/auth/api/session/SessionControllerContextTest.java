package com.sunny.datapillar.auth.api.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.sunny.datapillar.auth.dto.auth.response.AuthenticationContextResponse;
import com.sunny.datapillar.auth.service.SessionAppService;
import com.sunny.datapillar.common.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class SessionControllerContextTest {

  @Mock private SessionAppService sessionAppService;

  @Test
  void shouldReturnAuthenticationContextFromAuthorizationHeader() {
    SessionController controller = new SessionController(sessionAppService);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer access-token");

    AuthenticationContextResponse context =
        AuthenticationContextResponse.builder()
            .userId(101L)
            .tenantId(1001L)
            .tenantCode("t-1001")
            .username("sunny")
            .sessionId("sid-1")
            .tokenId("jti-1")
            .build();

    when(sessionAppService.extractAuthorizationToken(request, null)).thenReturn("access-token");
    when(sessionAppService.context("access-token")).thenReturn(context);

    ApiResponse<AuthenticationContextResponse> response = controller.context(request, null);

    assertEquals(0, response.getCode());
    assertEquals(101L, response.getData().getUserId());
    assertEquals("sid-1", response.getData().getSessionId());
    assertEquals("jti-1", response.getData().getTokenId());
  }
}
