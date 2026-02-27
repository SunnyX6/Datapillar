package com.sunny.datapillar.studio.module.tenant.service.impl;

import com.sunny.datapillar.studio.dto.llm.request.*;
import com.sunny.datapillar.studio.dto.llm.response.*;
import com.sunny.datapillar.studio.dto.project.request.*;
import com.sunny.datapillar.studio.dto.project.response.*;
import com.sunny.datapillar.studio.dto.setup.request.*;
import com.sunny.datapillar.studio.dto.setup.response.*;
import com.sunny.datapillar.studio.dto.sql.request.*;
import com.sunny.datapillar.studio.dto.sql.response.*;
import com.sunny.datapillar.studio.dto.tenant.request.*;
import com.sunny.datapillar.studio.dto.tenant.response.*;
import com.sunny.datapillar.studio.dto.user.request.*;
import com.sunny.datapillar.studio.dto.user.response.*;
import com.sunny.datapillar.studio.dto.workflow.request.*;
import com.sunny.datapillar.studio.dto.workflow.response.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sunny.datapillar.common.exception.AlreadyExistsException;
import com.sunny.datapillar.common.exception.NotFoundException;
import com.sunny.datapillar.studio.context.TenantContextHolder;
import com.sunny.datapillar.studio.exception.translator.StudioDbExceptionTranslator;
import com.sunny.datapillar.studio.module.tenant.entity.Tenant;
import com.sunny.datapillar.studio.module.tenant.entity.UserInvitation;
import com.sunny.datapillar.studio.module.tenant.entity.UserInvitationRole;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantMapper;
import com.sunny.datapillar.studio.module.tenant.mapper.UserInvitationMapper;
import com.sunny.datapillar.studio.module.tenant.mapper.UserInvitationRoleMapper;
import com.sunny.datapillar.studio.module.user.entity.Role;
import com.sunny.datapillar.studio.module.user.entity.User;
import com.sunny.datapillar.studio.module.user.mapper.RoleMapper;
import com.sunny.datapillar.studio.module.user.mapper.TenantUserMapper;
import com.sunny.datapillar.studio.module.user.mapper.UserMapper;
import com.sunny.datapillar.studio.module.user.mapper.UserRoleMapper;
import java.sql.SQLException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

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
    @Mock
    private TenantMapper tenantMapper;
    @Mock
    private PasswordEncoder passwordEncoder;

    private InvitationServiceImpl invitationService;

    @BeforeEach
    void setUp() {
        invitationService = new InvitationServiceImpl(
                userInvitationMapper,
                userInvitationRoleMapper,
                roleMapper,
                userMapper,
                tenantUserMapper,
                userRoleMapper,
                tenantMapper,
                passwordEncoder,
                new StudioDbExceptionTranslator()
        );
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void getInvitationByCode_shouldReturnDetailWhenInvitationExists() {
        UserInvitation invitation = new UserInvitation();
        invitation.setId(1L);
        invitation.setTenantId(100L);
        invitation.setInviterUserId(200L);
        invitation.setInviteCode("INV-001");
        invitation.setStatus(0);
        invitation.setExpiresAt(LocalDateTime.now().plusDays(1));
        when(userInvitationMapper.selectByInviteCode("INV-001")).thenReturn(invitation);

        UserInvitationRole invitationRole = new UserInvitationRole();
        invitationRole.setInvitationId(1L);
        invitationRole.setRoleId(301L);
        when(userInvitationRoleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(invitationRole);

        Role role = new Role();
        role.setId(301L);
        role.setTenantId(100L);
        role.setName("Data Analyst");
        when(roleMapper.selectById(301L)).thenReturn(role);

        Tenant tenant = new Tenant();
        tenant.setId(100L);
        tenant.setName("Data Engineering Core");
        when(tenantMapper.selectById(100L)).thenReturn(tenant);

        User inviter = new User();
        inviter.setId(200L);
        inviter.setNickname("Sarah Chen");
        when(userMapper.selectById(200L)).thenReturn(inviter);

        InvitationDetailResponse response = invitationService.getInvitationByCode(" inv-001 ");

        assertEquals("INV-001", response.getInviteCode());
        assertEquals("Data Engineering Core", response.getTenantName());
        assertEquals(301L, response.getRoleId());
        assertEquals("Data Analyst", response.getRoleName());
        assertEquals("Sarah Chen", response.getInviterName());
        assertEquals(0, response.getStatus());
        assertNull(TenantContextHolder.getTenantId());
        verify(userInvitationMapper).selectByInviteCode("INV-001");
    }

    @Test
    void registerInvitation_shouldThrowNotFoundWhenInvitationMissing() {
        InvitationRegisterRequest request = new InvitationRegisterRequest();
        request.setInviteCode("INV-404");
        request.setUsername("member_user");
        request.setEmail("new.user@datapillar.ai");
        request.setPassword("123456");

        when(userInvitationMapper.selectByInviteCodeForUpdate("INV-404")).thenReturn(null);

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> invitationService.registerInvitation(request));

        assertEquals("邀请码不存在", exception.getMessage());
        verify(userInvitationMapper).selectByInviteCodeForUpdate("INV-404");
    }

    @Test
    void registerInvitation_shouldReturnEmailExistsWhenInsertDuplicateEmail() {
        InvitationRegisterRequest request = buildRegisterRequest();
        UserInvitation invitation = buildPendingInvitation();

        when(userInvitationMapper.selectByInviteCodeForUpdate("INV-EMAIL")).thenReturn(invitation);
        when(passwordEncoder.encode("123456")).thenReturn("encoded-password");
        RuntimeException sqlWrapped = new RuntimeException(
                new SQLException("Duplicate entry x@datapillar.ai for key uq_user_email", "23000", 1062));
        when(userMapper.insert(any(User.class))).thenThrow(sqlWrapped);

        AlreadyExistsException exception = assertThrows(AlreadyExistsException.class,
                () -> invitationService.registerInvitation(request));

        assertEquals("邮箱已存在", exception.getMessage());
        verify(userMapper).insert(any(User.class));
    }

    @Test
    void registerInvitation_shouldReturnUsernameExistsWhenInsertDuplicateUsername() {
        InvitationRegisterRequest request = buildRegisterRequest();
        UserInvitation invitation = buildPendingInvitation();

        when(userInvitationMapper.selectByInviteCodeForUpdate("INV-EMAIL")).thenReturn(invitation);
        when(passwordEncoder.encode("123456")).thenReturn("encoded-password");
        RuntimeException sqlWrapped = new RuntimeException(
                new SQLException("Duplicate entry member_user for key uq_user_username", "23000", 1062));
        when(userMapper.insert(any(User.class))).thenThrow(sqlWrapped);

        AlreadyExistsException exception = assertThrows(AlreadyExistsException.class,
                () -> invitationService.registerInvitation(request));

        assertEquals("用户名已存在", exception.getMessage());
        verify(userMapper).insert(any(User.class));
    }

    private InvitationRegisterRequest buildRegisterRequest() {
        InvitationRegisterRequest request = new InvitationRegisterRequest();
        request.setInviteCode("INV-EMAIL");
        request.setUsername("member_user");
        request.setEmail("x@datapillar.ai");
        request.setPassword("123456");
        return request;
    }

    private UserInvitation buildPendingInvitation() {
        UserInvitation invitation = new UserInvitation();
        invitation.setId(1L);
        invitation.setTenantId(100L);
        invitation.setStatus(0);
        invitation.setExpiresAt(LocalDateTime.now().plusDays(1));
        return invitation;
    }
}
