package com.sunny.datapillar.auth.service.login.method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sunny.datapillar.auth.dto.login.response.TenantOptionItem;
import com.sunny.datapillar.auth.entity.Tenant;
import com.sunny.datapillar.auth.entity.User;
import com.sunny.datapillar.auth.mapper.TenantMapper;
import com.sunny.datapillar.auth.mapper.TenantUserMapper;
import com.sunny.datapillar.auth.mapper.UserMapper;
import com.sunny.datapillar.auth.security.LoginAttemptTracker;
import com.sunny.datapillar.auth.service.login.LoginCommand;
import com.sunny.datapillar.auth.service.login.LoginSubject;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordLoginMethodTest {

  @Mock private UserMapper userMapper;
  @Mock private TenantMapper tenantMapper;
  @Mock private TenantUserMapper tenantUserMapper;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private LoginAttemptTracker loginAttemptTracker;

  @InjectMocks private PasswordLoginMethod passwordLoginMethod;

  @Test
  void authenticate_shouldDirectLoginWhenTenantCodeMissingAndOnlyOneTenantAvailable() {
    User user = buildUser(1L, "sunny", "hashed-password");
    Tenant tenant = buildTenant(10L, "tenant-one");

    when(userMapper.selectByUsername("sunny")).thenReturn(user);
    when(passwordEncoder.matches("123456asd", "hashed-password")).thenReturn(true);
    when(tenantUserMapper.selectTenantOptionsByUserId(1L))
        .thenReturn(List.of(new TenantOptionItem(10L, "tenant-one", "Tenant One", 1, 1)));
    when(tenantMapper.selectById(10L)).thenReturn(tenant);

    LoginCommand command = new LoginCommand();
    command.setLoginAlias("sunny");
    command.setPassword("123456asd");

    LoginSubject subject = passwordLoginMethod.authenticate(command);

    assertNotNull(subject.getTenant());
    assertEquals("tenant-one", subject.getTenant().getCode());
    assertNull(subject.getTenantOptions());
    verify(tenantMapper, never()).selectByCode(anyString());
    verify(loginAttemptTracker).assertLoginAllowed(null, "sunny", null);
    verify(loginAttemptTracker).clearFailures(null, "sunny", null);
  }

  @Test
  void authenticate_shouldReturnTenantOptionsWhenTenantCodeMissingAndMultipleTenantsAvailable() {
    User user = buildUser(1L, "sunny", "hashed-password");

    when(userMapper.selectByUsername("sunny")).thenReturn(user);
    when(passwordEncoder.matches("123456asd", "hashed-password")).thenReturn(true);
    when(tenantUserMapper.selectTenantOptionsByUserId(1L))
        .thenReturn(
            List.of(
                new TenantOptionItem(10L, "tenant-one", "Tenant One", 1, 1),
                new TenantOptionItem(20L, "tenant-two", "Tenant Two", 1, 0)));

    LoginCommand command = new LoginCommand();
    command.setLoginAlias("sunny");
    command.setPassword("123456asd");

    LoginSubject subject = passwordLoginMethod.authenticate(command);

    assertNull(subject.getTenant());
    assertNotNull(subject.getTenantOptions());
    assertEquals(2, subject.getTenantOptions().size());
    assertTrue(subject.requiresTenantSelection());
    verify(tenantMapper, never()).selectByCode(anyString());
    verify(tenantMapper, never()).selectById(any());
  }

  private User buildUser(Long userId, String username, String passwordHash) {
    User user = new User();
    user.setId(userId);
    user.setUsername(username);
    user.setPasswordHash(passwordHash);
    user.setStatus(1);
    return user;
  }

  private Tenant buildTenant(Long tenantId, String tenantCode) {
    Tenant tenant = new Tenant();
    tenant.setId(tenantId);
    tenant.setCode(tenantCode);
    tenant.setName(tenantCode);
    tenant.setStatus(1);
    return tenant;
  }
}
