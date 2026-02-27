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
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sunny.datapillar.common.exception.db.DbStorageException;
import com.sunny.datapillar.common.exception.db.SQLExceptionUtils;
import com.sunny.datapillar.studio.context.TenantContext;
import com.sunny.datapillar.studio.context.TenantContextHolder;
import com.sunny.datapillar.studio.exception.translator.StudioDbExceptionTranslator;
import com.sunny.datapillar.studio.exception.translator.StudioDbScene;
import com.sunny.datapillar.studio.exception.invitation.InvalidInvitationRequestException;
import com.sunny.datapillar.studio.exception.invitation.InvitationAlreadyUsedException;
import com.sunny.datapillar.studio.exception.invitation.InvitationExpiredException;
import com.sunny.datapillar.studio.exception.invitation.InvitationInactiveException;
import com.sunny.datapillar.studio.exception.invitation.InvitationInternalException;
import com.sunny.datapillar.studio.exception.invitation.InvitationInviterNotFoundException;
import com.sunny.datapillar.studio.exception.invitation.InvitationNotFoundException;
import com.sunny.datapillar.studio.exception.invitation.InvitationTenantNotFoundException;
import com.sunny.datapillar.studio.exception.invitation.InvitationUnauthorizedException;
import com.sunny.datapillar.studio.module.tenant.entity.Tenant;
import com.sunny.datapillar.studio.module.tenant.entity.UserInvitation;
import com.sunny.datapillar.studio.module.tenant.entity.UserInvitationRole;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantMapper;
import com.sunny.datapillar.studio.module.tenant.mapper.UserInvitationMapper;
import com.sunny.datapillar.studio.module.tenant.mapper.UserInvitationRoleMapper;
import com.sunny.datapillar.studio.module.tenant.service.InvitationService;
import com.sunny.datapillar.studio.module.user.entity.Role;
import com.sunny.datapillar.studio.module.user.entity.TenantUser;
import com.sunny.datapillar.studio.module.user.entity.User;
import com.sunny.datapillar.studio.module.user.entity.UserRole;
import com.sunny.datapillar.studio.module.user.mapper.RoleMapper;
import com.sunny.datapillar.studio.module.user.mapper.TenantUserMapper;
import com.sunny.datapillar.studio.module.user.mapper.UserMapper;
import com.sunny.datapillar.studio.module.user.mapper.UserRoleMapper;
import com.sunny.datapillar.studio.util.UserContextUtil;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 邀请服务实现
 * 实现邀请业务流程与规则校验
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Service
@RequiredArgsConstructor
public class InvitationServiceImpl implements InvitationService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_ACCEPTED = 1;
    private static final int STATUS_EXPIRED = 2;
    private static final int STATUS_CANCELLED = 3;

    private static final int STATUS_ENABLED = 1;
    private static final int DELETED_NO = 0;
    private static final int USER_LEVEL_DEFAULT = 100;

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 12;
    private static final int MAX_CODE_RETRY = 10;

    private final UserInvitationMapper userInvitationMapper;
    private final UserInvitationRoleMapper userInvitationRoleMapper;
    private final RoleMapper roleMapper;
    private final UserMapper userMapper;
    private final TenantUserMapper tenantUserMapper;
    private final UserRoleMapper userRoleMapper;
    private final TenantMapper tenantMapper;
    private final PasswordEncoder passwordEncoder;
    private final StudioDbExceptionTranslator studioDbExceptionTranslator;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public InvitationCreateResponse createInvitation(InvitationCreateRequest dto) {
        if (dto == null) {
            throw new InvalidInvitationRequestException();
        }

        Long tenantId = getRequiredTenantId();
        Long roleId = dto.getRoleId();
        if (roleId == null) {
            throw new InvalidInvitationRequestException();
        }

        Role role = roleMapper.selectByIdForUpdate(roleId);
        if (role == null || !tenantId.equals(role.getTenantId())) {
            throw new InvitationNotFoundException();
        }

        OffsetDateTime expiresAt = dto.getExpiresAt();
        if (expiresAt == null) {
            throw new InvalidInvitationRequestException();
        }
        LocalDateTime expiresAtValue = expiresAt.toLocalDateTime();
        if (!expiresAtValue.isAfter(LocalDateTime.now())) {
            throw new InvalidInvitationRequestException();
        }

        Long inviterUserId = UserContextUtil.getRequiredUserId();
        User inviter = userMapper.selectById(inviterUserId);
        if (inviter == null) {
            throw new InvitationInviterNotFoundException("用户不存在: %s", inviterUserId);
        }
        Tenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new InvitationTenantNotFoundException("租户不存在: %s", tenantId);
        }

        LocalDateTime now = LocalDateTime.now();
        UserInvitation activeInvitation = findActiveInvitationForRole(tenantId, roleId, now);
        if (activeInvitation != null) {
            User activeInviter = userMapper.selectById(activeInvitation.getInviterUserId());
            LocalDateTime activeExpiresAt = activeInvitation.getExpiresAt();
            OffsetDateTime responseExpiresAt = activeExpiresAt == null
                    ? expiresAt
                    : activeExpiresAt.atZone(ZoneId.systemDefault()).toOffsetDateTime();
            return buildCreateResponse(activeInvitation, responseExpiresAt, tenant, role, activeInviter);
        }

        UserInvitation invitation = new UserInvitation();
        invitation.setTenantId(tenantId);
        invitation.setInviterUserId(inviterUserId);
        invitation.setInviteCode(generateUniqueInviteCode());
        invitation.setStatus(STATUS_PENDING);
        invitation.setExpiresAt(expiresAtValue);
        invitation.setCreatedAt(now);
        invitation.setUpdatedAt(now);
        try {
            userInvitationMapper.insert(invitation);

            UserInvitationRole relation = new UserInvitationRole();
            relation.setInvitationId(invitation.getId());
            relation.setRoleId(roleId);
            userInvitationRoleMapper.insert(relation);
        } catch (RuntimeException re) {
            throw translateDbException(re, StudioDbScene.STUDIO_GENERIC);
        }

        return buildCreateResponse(invitation, expiresAt, tenant, role, inviter);
    }

    @Override
    @Transactional(readOnly = true)
    public InvitationDetailResponse getInvitationByCode(String inviteCode) {
        String normalizedInviteCode = normalizeInviteCode(inviteCode);
        UserInvitation invitation = userInvitationMapper.selectByInviteCode(normalizedInviteCode);
        if (invitation == null) {
            throw new InvitationNotFoundException();
        }

        Long tenantId = invitation.getTenantId();
        Long roleId = getInvitationRoleId(invitation.getId());
        return withTenantContext(tenantId, () -> {
            Role role = roleMapper.selectById(roleId);
            if (role == null || !tenantId.equals(role.getTenantId())) {
                throw new InvitationNotFoundException();
            }

            Tenant tenant = tenantMapper.selectById(tenantId);
            if (tenant == null) {
                throw new InvitationNotFoundException();
            }

            User inviter = userMapper.selectById(invitation.getInviterUserId());
            InvitationDetailResponse response = new InvitationDetailResponse();
            response.setInviteCode(invitation.getInviteCode());
            response.setTenantName(tenant.getName());
            response.setRoleId(role.getId());
            response.setRoleName(role.getName());
            response.setInviterName(resolveInviterName(inviter));
            LocalDateTime expiresAt = invitation.getExpiresAt();
            response.setExpiresAt(expiresAt == null
                    ? null
                    : expiresAt.atZone(ZoneId.systemDefault()).toOffsetDateTime());
            response.setStatus(resolveInvitationStatus(invitation, LocalDateTime.now()));
            return response;
        });
    }

    @Override
    @Transactional
    public void registerInvitation(InvitationRegisterRequest request) {
        if (request == null) {
            throw new InvalidInvitationRequestException();
        }

        String inviteCode = normalizeInviteCode(request.getInviteCode());
        String username = normalizeRequiredUsername(request.getUsername());
        String email = normalizeRequiredEmail(request.getEmail());
        String password = normalizeRequiredText(request.getPassword());

        UserInvitation invitation = lockInvitation(inviteCode);
        if (invitation == null) {
            throw new InvitationNotFoundException();
        }
        Long tenantId = invitation.getTenantId();

        withTenantContext(tenantId, () -> {
            LocalDateTime now = LocalDateTime.now();
            validateInvitationStatus(invitation, now);

            Long userId = createInvitedUser(tenantId, username, email, password, now);

            ensureTenantMember(tenantId, userId, now);
            grantInvitationRoles(invitation.getId(), tenantId, userId, now);
            markInvitationAccepted(invitation.getId(), userId, now);
        });
    }

    private UserInvitation lockInvitation(String inviteCode) {
        return userInvitationMapper.selectByInviteCodeForUpdate(inviteCode);
    }

    private void withTenantContext(Long tenantId, Runnable action) {
        withTenantContext(tenantId, () -> {
            action.run();
            return null;
        });
    }

    private <T> T withTenantContext(Long tenantId, Supplier<T> action) {
        if (tenantId == null) {
            throw new InvitationInternalException();
        }
        if (action == null) {
            throw new InvitationInternalException();
        }

        TenantContext previous = TenantContextHolder.get();
        boolean switched = previous == null || !tenantId.equals(previous.getTenantId());
        if (switched) {
            String tenantCode = previous == null ? null : previous.getTenantCode();
            Long actorUserId = previous == null ? null : previous.getActorUserId();
            Long actorTenantId = previous == null ? null : previous.getActorTenantId();
            boolean impersonation = previous != null && previous.isImpersonation();
            TenantContextHolder.set(new TenantContext(tenantId, tenantCode, actorUserId, actorTenantId, impersonation));
        }

        try {
            return action.get();
        } finally {
            if (switched) {
                if (previous == null) {
                    TenantContextHolder.clear();
                } else {
                    TenantContextHolder.set(previous);
                }
            }
        }
    }

    private Long getInvitationRoleId(Long invitationId) {
        LambdaQueryWrapper<UserInvitationRole> roleQuery = new LambdaQueryWrapper<>();
        roleQuery.eq(UserInvitationRole::getInvitationId, invitationId)
                .orderByAsc(UserInvitationRole::getId)
                .last("LIMIT 1");
        UserInvitationRole relation = userInvitationRoleMapper.selectOne(roleQuery);
        if (relation == null || relation.getRoleId() == null) {
            throw new InvitationNotFoundException();
        }
        return relation.getRoleId();
    }

    private void validateInvitationStatus(UserInvitation invitation, LocalDateTime now) {
        Integer status = invitation.getStatus();
        if (status == null || status == STATUS_CANCELLED) {
            throw new InvitationInactiveException();
        }
        if (status == STATUS_ACCEPTED) {
            throw new InvitationAlreadyUsedException();
        }
        if (status == STATUS_EXPIRED) {
            throw new InvitationExpiredException();
        }
        if (status != STATUS_PENDING) {
            throw new InvitationInactiveException();
        }

        LocalDateTime expiresAt = invitation.getExpiresAt();
        if (expiresAt != null && expiresAt.isBefore(now)) {
            markInvitationExpired(invitation.getId(), now);
            throw new InvitationExpiredException();
        }
    }

    private Integer resolveInvitationStatus(UserInvitation invitation, LocalDateTime now) {
        if (invitation == null) {
            return STATUS_CANCELLED;
        }

        Integer status = invitation.getStatus();
        if (status == null) {
            return STATUS_CANCELLED;
        }
        if (status == STATUS_PENDING) {
            LocalDateTime expiresAt = invitation.getExpiresAt();
            if (expiresAt != null && !expiresAt.isAfter(now)) {
                return STATUS_EXPIRED;
            }
        }
        return status;
    }

    private Long createInvitedUser(Long tenantId,
                                   String username,
                                   String email,
                                   String password,
                                   LocalDateTime now) {
        User user = new User();
        user.setTenantId(tenantId);
        user.setUsername(username);
        user.setNickname(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setLevel(USER_LEVEL_DEFAULT);
        user.setStatus(STATUS_ENABLED);
        user.setDeleted(DELETED_NO);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        try {
            userMapper.insert(user);
        } catch (RuntimeException re) {
            throw translateDbException(re, StudioDbScene.STUDIO_INVITATION_REGISTER);
        }

        if (user.getId() == null) {
            throw new InvitationInternalException();
        }
        return user.getId();
    }

    private void markInvitationExpired(Long invitationId, LocalDateTime now) {
        LambdaUpdateWrapper<UserInvitation> update = new LambdaUpdateWrapper<>();
        update.eq(UserInvitation::getId, invitationId)
                .eq(UserInvitation::getStatus, STATUS_PENDING)
                .set(UserInvitation::getStatus, STATUS_EXPIRED)
                .set(UserInvitation::getUpdatedAt, now);
        userInvitationMapper.update(null, update);
    }

    private void markInvitationAccepted(Long invitationId, Long userId, LocalDateTime now) {
        LambdaUpdateWrapper<UserInvitation> update = new LambdaUpdateWrapper<>();
        update.eq(UserInvitation::getId, invitationId)
                .eq(UserInvitation::getStatus, STATUS_PENDING)
                .set(UserInvitation::getStatus, STATUS_ACCEPTED)
                .set(UserInvitation::getAcceptedUserId, userId)
                .set(UserInvitation::getAcceptedAt, now)
                .set(UserInvitation::getUpdatedAt, now);
        int updated = userInvitationMapper.update(null, update);
        if (updated == 0) {
            throw new InvitationAlreadyUsedException();
        }
    }

    private void ensureTenantMember(Long tenantId, Long userId, LocalDateTime now) {
        TenantUser tenantUser = tenantUserMapper.selectByTenantIdAndUserId(tenantId, userId);
        if (tenantUser == null) {
            TenantUser relation = new TenantUser();
            relation.setTenantId(tenantId);
            relation.setUserId(userId);
            relation.setStatus(STATUS_ENABLED);
            relation.setIsDefault(tenantUserMapper.countByUserId(userId) == 0 ? 1 : 0);
            relation.setJoinedAt(now);
            tenantUserMapper.insert(relation);
            return;
        }

        if (tenantUser.getStatus() == null || tenantUser.getStatus() != STATUS_ENABLED) {
            tenantUser.setStatus(STATUS_ENABLED);
            tenantUserMapper.updateById(tenantUser);
        }
    }

    private void grantInvitationRoles(Long invitationId, Long tenantId, Long userId, LocalDateTime now) {
        LambdaQueryWrapper<UserInvitationRole> roleQuery = new LambdaQueryWrapper<>();
        roleQuery.eq(UserInvitationRole::getInvitationId, invitationId);
        Set<Long> roleIds = new HashSet<>();
        for (UserInvitationRole invitationRole : userInvitationRoleMapper.selectList(roleQuery)) {
            if (invitationRole != null && invitationRole.getRoleId() != null) {
                roleIds.add(invitationRole.getRoleId());
            }
        }
        if (roleIds.isEmpty()) {
            throw new InvitationNotFoundException();
        }

        for (Long roleId : roleIds) {
            Role role = roleMapper.selectById(roleId);
            if (role == null || !tenantId.equals(role.getTenantId())) {
                throw new InvitationNotFoundException();
            }

            LambdaQueryWrapper<UserRole> query = new LambdaQueryWrapper<>();
            query.eq(UserRole::getTenantId, tenantId)
                    .eq(UserRole::getUserId, userId)
                    .eq(UserRole::getRoleId, roleId);
            Long count = userRoleMapper.selectCount(query);
            if (count != null && count > 0) {
                continue;
            }

            UserRole userRole = new UserRole();
            userRole.setTenantId(tenantId);
            userRole.setUserId(userId);
            userRole.setRoleId(roleId);
            userRole.setCreatedAt(now);
            userRoleMapper.insert(userRole);
        }
    }

    private Long getRequiredTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new InvitationUnauthorizedException();
        }
        return tenantId;
    }

    private String resolveInviterName(User inviter) {
        if (inviter == null) {
            return "";
        }
        if (StringUtils.hasText(inviter.getNickname())) {
            return inviter.getNickname().trim();
        }
        if (StringUtils.hasText(inviter.getUsername())) {
            return inviter.getUsername().trim();
        }
        return "";
    }

    private String normalizeInviteCode(String inviteCode) {
        if (inviteCode == null || inviteCode.isBlank()) {
            throw new InvalidInvitationRequestException();
        }
        return inviteCode.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeRequiredEmail(String email) {
        if (email == null) {
            throw new InvalidInvitationRequestException();
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new InvalidInvitationRequestException();
        }
        return normalized;
    }

    private String normalizeRequiredUsername(String username) {
        if (username == null) {
            throw new InvalidInvitationRequestException();
        }
        String normalized = username.trim();
        if (normalized.isEmpty()) {
            throw new InvalidInvitationRequestException();
        }
        return normalized;
    }

    private String normalizeRequiredText(String value) {
        if (value == null) {
            throw new InvalidInvitationRequestException();
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new InvalidInvitationRequestException();
        }
        return normalized;
    }

    private String generateUniqueInviteCode() {
        for (int i = 0; i < MAX_CODE_RETRY; i++) {
            String code = generateInviteCode();
            LambdaQueryWrapper<UserInvitation> query = new LambdaQueryWrapper<>();
            query.eq(UserInvitation::getInviteCode, code);
            Long count = userInvitationMapper.selectCount(query);
            if (count == null || count == 0L) {
                return code;
            }
        }
        throw new InvitationInternalException();
    }

    private String generateInviteCode() {
        StringBuilder builder = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            int index = secureRandom.nextInt(CODE_CHARS.length());
            builder.append(CODE_CHARS.charAt(index));
        }
        return builder.toString();
    }

    private UserInvitation findActiveInvitationForRole(Long tenantId, Long roleId, LocalDateTime now) {
        LambdaQueryWrapper<UserInvitationRole> roleQuery = new LambdaQueryWrapper<>();
        roleQuery.eq(UserInvitationRole::getRoleId, roleId);
        List<UserInvitationRole> invitationRoles = userInvitationRoleMapper.selectList(roleQuery);
        if (invitationRoles.isEmpty()) {
            return null;
        }

        Set<Long> invitationIds = new HashSet<>();
        for (UserInvitationRole invitationRole : invitationRoles) {
            if (invitationRole != null && invitationRole.getInvitationId() != null) {
                invitationIds.add(invitationRole.getInvitationId());
            }
        }
        if (invitationIds.isEmpty()) {
            return null;
        }

        LambdaQueryWrapper<UserInvitation> invitationQuery = new LambdaQueryWrapper<>();
        invitationQuery.eq(UserInvitation::getTenantId, tenantId)
                .eq(UserInvitation::getStatus, STATUS_PENDING)
                .gt(UserInvitation::getExpiresAt, now)
                .in(UserInvitation::getId, invitationIds)
                .orderByDesc(UserInvitation::getCreatedAt)
                .orderByDesc(UserInvitation::getId)
                .last("LIMIT 1");
        return userInvitationMapper.selectOne(invitationQuery);
    }

    private InvitationCreateResponse buildCreateResponse(UserInvitation invitation,
                                                             OffsetDateTime expiresAt,
                                                             Tenant tenant,
                                                             Role role,
                                                             User inviter) {
        InvitationCreateResponse response = new InvitationCreateResponse();
        response.setInvitationId(invitation.getId());
        response.setInviteCode(invitation.getInviteCode());
        response.setInviteUri(buildInviteUri(invitation.getInviteCode()));
        response.setExpiresAt(expiresAt);
        response.setTenantName(tenant.getName());
        response.setRoleId(role.getId());
        response.setRoleName(role.getName());
        response.setInviterName(resolveInviterName(inviter));
        return response;
    }

    private RuntimeException translateDbException(RuntimeException runtimeException, StudioDbScene scene) {
        DbStorageException dbException = SQLExceptionUtils.translate(runtimeException);
        if (dbException == null) {
            return runtimeException;
        }
        return studioDbExceptionTranslator.map(scene, dbException);
    }

    private String buildInviteUri(String inviteCode) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/invite")
                .queryParam("inviteCode", inviteCode);
        return builder.encode().build().toUriString();
    }
}
