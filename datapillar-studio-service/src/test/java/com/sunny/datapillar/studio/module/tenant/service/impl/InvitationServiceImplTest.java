package com.sunny.datapillar.studio.module.tenant.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.sunny.datapillar.common.exception.AlreadyExistsException;
import com.sunny.datapillar.common.exception.NotFoundException;
import com.sunny.datapillar.studio.context.TenantContextHolder;
import com.sunny.datapillar.studio.dto.tenant.request.InvitationRegisterRequest;
import com.sunny.datapillar.studio.dto.tenant.response.InvitationDetailResponse;
import com.sunny.datapillar.studio.exception.invitation.InvitationInternalException;
import com.sunny.datapillar.studio.exception.translator.StudioDbExceptionTranslator;
import com.sunny.datapillar.studio.module.tenant.entity.Tenant;
import com.sunny.datapillar.studio.module.tenant.entity.UserInvitation;
import com.sunny.datapillar.studio.module.tenant.entity.UserInvitationRole;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantMapper;
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
import com.sunny.datapillar.studio.module.user.service.UserService;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class InvitationServiceImplTest {

  @Mock private UserInvitationMapper userInvitationMapper;
  @Mock private UserInvitationRoleMapper userInvitationRoleMapper;
  @Mock private RoleMapper roleMapper;
  @Mock private UserMapper userMapper;
  @Mock private TenantUserMapper tenantUserMapper;
  @Mock private UserRoleMapper userRoleMapper;
  @Mock private TenantMapper tenantMapper;
  @Mock private UserService userService;
  @Mock private TransactionTemplate transactionTemplate;

  private InvitationServiceImpl invitationService;

  @BeforeEach
  void setUp() {
    lenient()
        .when(transactionTemplate.execute(any()))
        .thenAnswer(
            invocation -> {
              TransactionCallback<?> callback = invocation.getArgument(0);
              return callback.doInTransaction(null);
            });
    TableInfoHelper.initTableInfo(
        new MapperBuilderAssistant(new MybatisConfiguration(), ""), UserInvitation.class);
    TableInfoHelper.initTableInfo(
        new MapperBuilderAssistant(new MybatisConfiguration(), ""), UserInvitationRole.class);
    TableInfoHelper.initTableInfo(
        new MapperBuilderAssistant(new MybatisConfiguration(), ""), UserRole.class);
    User inviter = new User();
    inviter.setId(200L);
    inviter.setUsername("inviter_user");
    lenient().when(userMapper.selectById(200L)).thenReturn(inviter);
    invitationService =
        new InvitationServiceImpl(
            userInvitationMapper,
            userInvitationRoleMapper,
            roleMapper,
            userMapper,
            tenantUserMapper,
            userRoleMapper,
            tenantMapper,
            userService,
            new StudioDbExceptionTranslator(),
            transactionTemplate);
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
    invitation.setInviterUserId(200L);
    invitation.setInviteCode("INV-001");
    invitation.setStatus(0);
    invitation.setExpiresAt(LocalDateTime.now().plusDays(1));
    when(userInvitationMapper.selectByInviteCode("INV-001")).thenReturn(invitation);

    UserInvitationRole invitationRole = new UserInvitationRole();
    invitationRole.setInvitationId(1L);
    invitationRole.setRoleId(301L);
    when(userInvitationRoleMapper.selectOne(any(LambdaQueryWrapper.class)))
        .thenReturn(invitationRole);

    Role role = new Role();
    role.setId(301L);
    role.setTenantId(100L);
    role.setName("Data Analyst");
    when(roleMapper.selectById(301L)).thenReturn(role);

    Tenant tenant = new Tenant();
    tenant.setId(100L);
    tenant.setCode("t100");
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
    InvitationRegisterRequest request = buildRegisterRequest();
    request.setInviteCode("INV-404");
    when(userInvitationMapper.selectByInviteCodeForUpdate("INV-404")).thenReturn(null);

    NotFoundException exception =
        assertThrows(NotFoundException.class, () -> invitationService.registerInvitation(request));

    assertEquals("The invitation code does not exist", exception.getMessage());
    verify(userInvitationMapper).selectByInviteCodeForUpdate("INV-404");
  }

  @Test
  void registerInvitation_shouldPropagateCreateUserConflict() {
    InvitationRegisterRequest request = buildRegisterRequest();
    UserInvitation invitation = buildPendingInvitation();

    when(userInvitationMapper.selectByInviteCodeForUpdate("INV-EMAIL")).thenReturn(invitation);
    when(tenantMapper.selectById(100L)).thenReturn(tenant());
    when(userService.createUser(any(), eq(true), eq("inviter_user")))
        .thenThrow(new AlreadyExistsException("Resource already exists", "x@datapillar.ai"));

    AlreadyExistsException exception =
        assertThrows(
            AlreadyExistsException.class, () -> invitationService.registerInvitation(request));

    assertEquals("Resource already exists", exception.getMessage());
    verify(userService).createUser(any(), eq(true), eq("inviter_user"));
  }

  @Test
  void registerInvitation_shouldCreateUserAndAcceptInvitation() {
    InvitationRegisterRequest request = buildRegisterRequest();
    UserInvitation invitation = buildPendingInvitation();
    invitation.setInviteCode("INV-EMAIL");
    when(userInvitationMapper.selectByInviteCodeForUpdate("INV-EMAIL")).thenReturn(invitation);
    when(tenantMapper.selectById(100L)).thenReturn(tenant());
    when(userService.createUser(any(), eq(true), eq("inviter_user"))).thenReturn(501L);

    when(tenantUserMapper.selectByTenantIdAndUserId(100L, 501L)).thenReturn(null);
    when(tenantUserMapper.countByUserId(501L)).thenReturn(0);
    when(userRoleMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
    when(userInvitationMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1, 1);

    UserInvitationRole relation = new UserInvitationRole();
    relation.setInvitationId(1L);
    relation.setRoleId(301L);
    when(userInvitationRoleMapper.selectList(any(LambdaQueryWrapper.class)))
        .thenReturn(List.of(relation));

    Role role = new Role();
    role.setId(301L);
    role.setTenantId(100L);
    role.setName("ANALYST");
    when(roleMapper.selectById(301L)).thenReturn(role);

    invitationService.registerInvitation(request);

    verify(tenantUserMapper).insert(any(TenantUser.class));
    verify(userRoleMapper).insert(any(UserRole.class));
    verify(userInvitationMapper, times(2)).update(isNull(), any(LambdaUpdateWrapper.class));
  }

  @Test
  void registerInvitation_shouldMarkFailedWhenFinalizeFails() {
    InvitationRegisterRequest request = buildRegisterRequest();
    UserInvitation invitation = buildPendingInvitation();
    invitation.setInviteCode("INV-EMAIL");
    when(userInvitationMapper.selectByInviteCodeForUpdate("INV-EMAIL")).thenReturn(invitation);
    when(tenantMapper.selectById(100L)).thenReturn(tenant());
    when(userService.createUser(any(), eq(true), eq("inviter_user"))).thenReturn(502L);

    TenantUser tenantUser = new TenantUser();
    tenantUser.setTenantId(100L);
    tenantUser.setUserId(502L);
    tenantUser.setStatus(1);
    when(tenantUserMapper.selectByTenantIdAndUserId(100L, 502L)).thenReturn(tenantUser);
    when(userRoleMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
    doReturn(1)
        .doThrow(new RuntimeException("write failed"))
        .doReturn(1)
        .when(userInvitationMapper)
        .update(isNull(), any(LambdaUpdateWrapper.class));

    UserInvitationRole relation = new UserInvitationRole();
    relation.setInvitationId(1L);
    relation.setRoleId(301L);
    when(userInvitationRoleMapper.selectList(any(LambdaQueryWrapper.class)))
        .thenReturn(List.of(relation));

    Role role = new Role();
    role.setId(301L);
    role.setTenantId(100L);
    role.setName("ANALYST");
    when(roleMapper.selectById(301L)).thenReturn(role);

    InvitationInternalException exception =
        assertThrows(
            InvitationInternalException.class, () -> invitationService.registerInvitation(request));

    assertEquals("Invitation processing failed", exception.getMessage());
    verify(tenantUserMapper).updateById(any(TenantUser.class));
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
    invitation.setInviterUserId(200L);
    invitation.setStatus(0);
    invitation.setExpiresAt(LocalDateTime.now().plusDays(1));
    return invitation;
  }

  private Tenant tenant() {
    Tenant tenant = new Tenant();
    tenant.setId(100L);
    tenant.setCode("t100");
    return tenant;
  }
}
