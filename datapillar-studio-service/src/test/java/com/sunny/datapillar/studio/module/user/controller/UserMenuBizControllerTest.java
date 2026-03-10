package com.sunny.datapillar.studio.module.user.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sunny.datapillar.common.response.ApiResponse;
import com.sunny.datapillar.common.security.PrincipalType;
import com.sunny.datapillar.studio.dto.user.response.UserMenuItem;
import com.sunny.datapillar.studio.module.user.service.UserPermissionService;
import com.sunny.datapillar.studio.security.TrustedIdentityContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
class UserMenuBizControllerTest {

  @Mock private UserPermissionService userPermissionService;

  @AfterEach
  void tearDown() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void listMyMenus_shouldUseTrustedIdentityContextWithoutParsingAuthorizationHeader() {
    UserMenuItem menuItem = new UserMenuItem();
    menuItem.setId(1L);
    menuItem.setName("Projects");
    menuItem.setPath("/projects");
    menuItem.setChildren(List.of());
    when(userPermissionService.listMenus(101L)).thenReturn(List.of(menuItem));

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/biz/users/me/menu");
    request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer invalid.external.token");
    TrustedIdentityContext.attach(
        request,
        new TrustedIdentityContext(
            PrincipalType.USER,
            "user:101",
            101L,
            1001L,
            "tenant-a",
            "sunny",
            "sunny@datapillar.ai",
            List.of("ADMIN"),
            false,
            null,
            null,
            "access-jti-1"));
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    UserMenuBizController controller = new UserMenuBizController(userPermissionService);
    ApiResponse<List<UserMenuItem>> response = controller.listMyMenus();

    assertEquals(0, response.getCode());
    assertNotNull(response.getData());
    assertEquals(1, response.getData().size());
    assertEquals("Projects", response.getData().get(0).getName());
    verify(userPermissionService).listMenus(101L);
  }
}
