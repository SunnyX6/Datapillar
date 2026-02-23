package com.sunny.datapillar.studio.module.tenant.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sunny.datapillar.common.exception.UnauthorizedException;
import com.sunny.datapillar.studio.context.TenantContext;
import com.sunny.datapillar.studio.context.TenantContextHolder;
import com.sunny.datapillar.studio.module.tenant.entity.UserInvitation;
import com.sunny.datapillar.studio.module.tenant.entity.UserInvitationRole;
import com.sunny.datapillar.studio.module.tenant.mapper.UserInvitationMapper;
import com.sunny.datapillar.studio.module.tenant.mapper.UserInvitationRoleMapper;
import com.sunny.datapillar.studio.module.user.entity.Role;
import com.sunny.datapillar.studio.module.user.entity.TenantUser;
import com.sunny.datapillar.studio.module.user.entity.User;
import com.sunny.datapillar.studio.module.user.entity.UserRole;
import com.sunny.datapillar.studio.module.user.mapper.RoleMapper;
import com.sunny.datapillar.studio.module.user.mapper.TenantUserMapper;
import com.sunny.datapillar.studio.module.user.mapper.UserMapper;
import com.sunny.datapillar.studio.module.user.mapper.UserRoleMapper;
import com.sunny.datapillar.studio.security.GatewayAssertionContext;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
class InvitationServiceImplTest {

    @Mock
    private UserInvitationMapper userInvitationMapper;
    @Mock
    private UserInvitationRoleMapper userInvitationRoleMapper;
    @Mock
    private RoleMapper roleMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private TenantUserMapper tenantUserMapper;
    @Mock
    private UserRoleMapper userRoleMapper;

    private InvitationServiceImpl invitationService;

    @BeforeEach
    void setUp() {
        invitationService = new InvitationServiceImpl(
                userInvitationMapper,
                userInvitationRoleMapper,
                roleMapper,
                userMapper,
                tenantUserMapper,
                userRoleMapper
        );
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        TenantContextHolder.clear();
    }

    @Test
    void acceptInvitation_shouldBindMemberAndRolesWhenInvitationValid() {
        Long tenantId = 100L;
        Long userId = 200L;
        bindCurrentUser(userId, tenantId, "alice@example.com");

        UserInvitation invitation = pendingInvitation(tenantId, "alice@example.com", LocalDateTime.now().plusDays(1));
        when(userInvitationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(invitation);
        when(userMapper.selectById(userId)).thenReturn(user(userId, "alice@example.com", "13900001234"));
        when(userInvitationMapper.update(isNull(), any())).thenReturn(1);
        when(tenantUserMapper.selectByTenantIdAndUserId(tenantId, userId)).thenReturn(null);
        when(tenantUserMapper.countByUserId(userId)).thenReturn(0);
        when(userInvitationRoleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                invitationRole(301L), invitationRole(302L)
        ));
        when(roleMapper.selectById(301L)).thenReturn(role(301L, tenantId));
        when(roleMapper.selectById(302L)).thenReturn(role(302L, tenantId));
        when(userRoleMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        invitationService.acceptInvitation(" code-001 ");

        verify(userInvitationMapper).update(isNull(), any());
        verify(tenantUserMapper).insert(any(TenantUser.class));
        verify(userRoleMapper, times(2)).insert(any(UserRole.class));
    }

    @Test
    void acceptInvitation_shouldRejectWhenInviteExpired() {
        Long tenantId = 100L;
        Long userId = 200L;
        bindCurrentUser(userId, tenantId, "alice@example.com");

        UserInvitation invitation = pendingInvitation(tenantId, "alice@example.com", LocalDateTime.now().minusMinutes(1));
        when(userInvitationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(invitation);
        when(userMapper.selectById(userId)).thenReturn(user(userId, "alice@example.com", "13900001234"));
        when(userInvitationMapper.update(isNull(), any())).thenReturn(1);

        UnauthorizedException exception = assertThrows(UnauthorizedException.class,
                () -> invitationService.acceptInvitation("EXPIRED-001"));

        assertEquals("邀请码已过期", exception.getMessage());
        verify(tenantUserMapper, never()).insert(any(TenantUser.class));
        verify(userRoleMapper, never()).insert(any(UserRole.class));
    }

    @Test
    void acceptInvitation_shouldRejectWhenInviteeDoesNotMatchCurrentUser() {
        Long tenantId = 100L;
        Long userId = 200L;
        bindCurrentUser(userId, tenantId, "bob@example.com");

        UserInvitation invitation = pendingInvitation(tenantId, "alice@example.com", LocalDateTime.now().plusDays(1));
        when(userInvitationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(invitation);
        when(userMapper.selectById(userId)).thenReturn(user(userId, "bob@example.com", "13900001234"));

        UnauthorizedException exception = assertThrows(UnauthorizedException.class,
                () -> invitationService.acceptInvitation("MISMATCH-001"));

        assertEquals("邀请信息与登录身份不匹配", exception.getMessage());
        verify(userInvitationMapper, never()).update(isNull(), any());
        verify(tenantUserMapper, never()).insert(any(TenantUser.class));
    }

    @Test
    void acceptInvitation_shouldRejectWhenInvitationAlreadyAccepted() {
        Long tenantId = 100L;
        Long userId = 200L;
        bindCurrentUser(userId, tenantId, "alice@example.com");

        UserInvitation invitation = pendingInvitation(tenantId, "alice@example.com", LocalDateTime.now().plusDays(1));
        invitation.setStatus(1);
        when(userInvitationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(invitation);

        UnauthorizedException exception = assertThrows(UnauthorizedException.class,
                () -> invitationService.acceptInvitation("USED-001"));

        assertEquals("邀请码已被使用", exception.getMessage());
        verify(userMapper, never()).selectById(any());
    }

    private void bindCurrentUser(Long userId, Long tenantId, String email) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        GatewayAssertionContext context = new GatewayAssertionContext(
                userId,
                tenantId,
                "tenant-" + tenantId,
                "tester",
                email,
                List.of("USER"),
                false,
                null,
                null,
                "token-1"
        );
        GatewayAssertionContext.attach(request, context);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        TenantContextHolder.set(new TenantContext(tenantId, "tenant-" + tenantId, null, null, false));
    }

    private UserInvitation pendingInvitation(Long tenantId, String inviteeEmail, LocalDateTime expiresAt) {
        UserInvitation invitation = new UserInvitation();
        invitation.setId(1L);
        invitation.setTenantId(tenantId);
        invitation.setInviteCode("INVITE-001");
        invitation.setInviteeEmail(inviteeEmail);
        invitation.setInviteeKey(inviteeEmail);
        invitation.setStatus(0);
        invitation.setExpiresAt(expiresAt);
        return invitation;
    }

    private User user(Long id, String email, String phone) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setPhone(phone);
        return user;
    }

    private UserInvitationRole invitationRole(Long roleId) {
        UserInvitationRole role = new UserInvitationRole();
        role.setInvitationId(1L);
        role.setRoleId(roleId);
        return role;
    }

    private Role role(Long roleId, Long tenantId) {
        Role role = new Role();
        role.setId(roleId);
        role.setTenantId(tenantId);
        return role;
    }
}
