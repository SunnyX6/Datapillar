package com.sunny.datapillar.studio.module.tenant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.sunny.datapillar.common.exception.DatapillarRuntimeException;
import com.sunny.datapillar.studio.context.TenantContextHolder;
import com.sunny.datapillar.studio.module.tenant.dto.InvitationDto;
import com.sunny.datapillar.studio.module.tenant.entity.UserInvitation;
import com.sunny.datapillar.studio.module.tenant.entity.UserInvitationRole;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.UnauthorizedException;
import com.sunny.datapillar.common.exception.NotFoundException;
import com.sunny.datapillar.common.exception.AlreadyExistsException;
import com.sunny.datapillar.common.exception.InternalException;

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

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 12;

    private final UserInvitationMapper userInvitationMapper;
    private final UserInvitationRoleMapper userInvitationRoleMapper;
    private final RoleMapper roleMapper;
    private final UserMapper userMapper;
    private final TenantUserMapper tenantUserMapper;
    private final UserRoleMapper userRoleMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public InvitationDto.CreateResponse createInvitation(InvitationDto.Create dto) {
        if (dto == null) {
            throw new BadRequestException("参数错误");
        }
        Long tenantId = getRequiredTenantId();
        if (dto.getRoleIds() == null || dto.getRoleIds().isEmpty()) {
            throw new BadRequestException("参数错误");
        }
        Set<Long> uniqueRoles = new HashSet<>(dto.getRoleIds());
        if (uniqueRoles.isEmpty() || uniqueRoles.contains(null)) {
            throw new BadRequestException("参数错误");
        }

        String inviteeKey = buildInviteeKey(dto.getInviteeEmail(), dto.getInviteeMobile());
        if (inviteeKey == null) {
            throw new BadRequestException("参数错误");
        }

        LambdaQueryWrapper<UserInvitation> activeQuery = new LambdaQueryWrapper<>();
        activeQuery.eq(UserInvitation::getTenantId, tenantId)
                .eq(UserInvitation::getActiveInviteeKey, inviteeKey)
                .eq(UserInvitation::getStatus, STATUS_PENDING);
        if (userInvitationMapper.selectOne(activeQuery) != null) {
            throw new AlreadyExistsException("资源已存在");
        }

        Long inviterUserId = UserContextUtil.getRequiredUserId();
        UserInvitation invitation = new UserInvitation();
        invitation.setTenantId(tenantId);
        invitation.setInviterUserId(inviterUserId);
        invitation.setInviteeEmail(normalizeEmail(dto.getInviteeEmail()));
        invitation.setInviteeMobile(normalizePhone(dto.getInviteeMobile()));
        invitation.setInviteeKey(inviteeKey);
        invitation.setActiveInviteeKey(inviteeKey);
        invitation.setInviteCode(generateInviteCode());
        invitation.setStatus(STATUS_PENDING);
        invitation.setExpiresAt(dto.getExpiresAt());
        invitation.setCreatedAt(LocalDateTime.now());
        invitation.setUpdatedAt(LocalDateTime.now());
        userInvitationMapper.insert(invitation);

        for (Long roleId : uniqueRoles) {
            Role role = roleMapper.selectById(roleId);
            if (role == null || !tenantId.equals(role.getTenantId())) {
                throw new BadRequestException("参数错误");
            }
            UserInvitationRole relation = new UserInvitationRole();
            relation.setInvitationId(invitation.getId());
            relation.setRoleId(roleId);
            userInvitationRoleMapper.insert(relation);
        }

        InvitationDto.CreateResponse response = new InvitationDto.CreateResponse();
        response.setInvitationId(invitation.getId());
        response.setInviteCode(invitation.getInviteCode());
        response.setExpiresAt(invitation.getExpiresAt());
        return response;
    }

    @Override
    public List<UserInvitation> listInvitations(Integer status) {
        Long tenantId = getRequiredTenantId();
        LambdaQueryWrapper<UserInvitation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserInvitation::getTenantId, tenantId);
        if (status != null) {
            wrapper.eq(UserInvitation::getStatus, status);
        }
        wrapper.orderByDesc(UserInvitation::getCreatedAt).orderByDesc(UserInvitation::getId);
        return userInvitationMapper.selectList(wrapper);
    }

    @Override
    @Transactional
    public void cancelInvitation(Long invitationId) {
        if (invitationId == null) {
            throw new BadRequestException("参数错误");
        }
        Long tenantId = getRequiredTenantId();
        LambdaQueryWrapper<UserInvitation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserInvitation::getId, invitationId)
                .eq(UserInvitation::getTenantId, tenantId);
        UserInvitation invitation = userInvitationMapper.selectOne(wrapper);
        if (invitation == null) {
            throw new NotFoundException("资源不存在");
        }
        if (invitation.getStatus() == null || invitation.getStatus() != STATUS_PENDING) {
            throw new BadRequestException("参数错误");
        }
        LambdaUpdateWrapper<UserInvitation> update = new LambdaUpdateWrapper<>();
        update.eq(UserInvitation::getId, invitationId)
                .eq(UserInvitation::getTenantId, tenantId)
                .eq(UserInvitation::getStatus, STATUS_PENDING)
                .set(UserInvitation::getStatus, STATUS_CANCELLED)
                .set(UserInvitation::getActiveInviteeKey, null)
                .set(UserInvitation::getUpdatedAt, LocalDateTime.now());
        int updated = userInvitationMapper.update(null, update);
        if (updated == 0) {
            throw new InternalException("服务器内部错误");
        }
    }

    @Override
    @Transactional
    public void acceptInvitation(String inviteCode) {
        if (inviteCode == null || inviteCode.isBlank()) {
            throw new BadRequestException("参数错误");
        }
        Long tenantId = getRequiredTenantId();

        String normalizedInviteCode = inviteCode.trim().toUpperCase(Locale.ROOT);
        UserInvitation invitation = loadInvitation(tenantId, normalizedInviteCode);
        validateInvitationStatus(invitation);

        User currentUser = getRequiredCurrentUser();
        validateInviteeIdentity(invitation, currentUser);

        LocalDateTime now = LocalDateTime.now();
        if (invitation.getExpiresAt() != null && invitation.getExpiresAt().isBefore(now)) {
            markInvitationExpired(invitation.getId(), tenantId, now);
            throw new UnauthorizedException("邀请码已过期");
        }

        markInvitationAccepted(invitation.getId(), tenantId, currentUser.getId(), now);
        ensureTenantMember(tenantId, currentUser.getId(), now);
        grantInvitationRoles(invitation.getId(), tenantId, currentUser.getId(), now);
    }

    private UserInvitation loadInvitation(Long tenantId, String inviteCode) {
        LambdaQueryWrapper<UserInvitation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserInvitation::getTenantId, tenantId)
                .eq(UserInvitation::getInviteCode, inviteCode);
        UserInvitation invitation = userInvitationMapper.selectOne(wrapper);
        if (invitation == null) {
            throw new UnauthorizedException("邀请码无效");
        }
        return invitation;
    }

    private void validateInvitationStatus(UserInvitation invitation) {
        Integer status = invitation.getStatus();
        if (status == null || status == STATUS_CANCELLED) {
            throw new UnauthorizedException("邀请码无效");
        }
        if (status == STATUS_ACCEPTED) {
            throw new UnauthorizedException("邀请码已被使用");
        }
        if (status == STATUS_EXPIRED) {
            throw new UnauthorizedException("邀请码已过期");
        }
        if (status != STATUS_PENDING) {
            throw new UnauthorizedException("邀请码无效");
        }
    }

    private User getRequiredCurrentUser() {
        Long userId = UserContextUtil.getUserId();
        if (userId == null) {
            throw new UnauthorizedException("未授权访问");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new NotFoundException("用户不存在: %s", userId);
        }
        return user;
    }

    private void validateInviteeIdentity(UserInvitation invitation, User currentUser) {
        String inviteeKey = invitation.getInviteeKey();
        if (inviteeKey == null || inviteeKey.isBlank()) {
            throw new UnauthorizedException("邀请码无效");
        }

        Set<String> identityKeys = new HashSet<>();
        String emailKey = normalizeEmail(currentUser.getEmail());
        if (emailKey != null) {
            identityKeys.add(emailKey);
        }
        String phoneKey = normalizePhone(currentUser.getPhone());
        if (phoneKey != null) {
            identityKeys.add(phoneKey);
        }
        if (!identityKeys.contains(inviteeKey)) {
            throw new UnauthorizedException("邀请信息与登录身份不匹配");
        }
    }

    private void markInvitationExpired(Long invitationId, Long tenantId, LocalDateTime now) {
        UpdateWrapper<UserInvitation> update = new UpdateWrapper<>();
        update.eq("id", invitationId)
                .eq("tenant_id", tenantId)
                .eq("status", STATUS_PENDING)
                .set("status", STATUS_EXPIRED)
                .set("active_invitee_key", null)
                .set("updated_at", now);
        userInvitationMapper.update(null, update);
    }

    private void markInvitationAccepted(Long invitationId, Long tenantId, Long userId, LocalDateTime now) {
        UpdateWrapper<UserInvitation> update = new UpdateWrapper<>();
        update.eq("id", invitationId)
                .eq("tenant_id", tenantId)
                .eq("status", STATUS_PENDING)
                .set("status", STATUS_ACCEPTED)
                .set("accepted_user_id", userId)
                .set("accepted_at", now)
                .set("active_invitee_key", null)
                .set("updated_at", now);
        int updated = userInvitationMapper.update(null, update);
        if (updated == 0) {
            throw new UnauthorizedException("邀请码已被使用");
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
        List<UserInvitationRole> invitationRoles = userInvitationRoleMapper.selectList(roleQuery);
        if (invitationRoles == null || invitationRoles.isEmpty()) {
            return;
        }

        Set<Long> roleIds = new HashSet<>();
        for (UserInvitationRole invitationRole : invitationRoles) {
            if (invitationRole != null && invitationRole.getRoleId() != null) {
                roleIds.add(invitationRole.getRoleId());
            }
        }

        for (Long roleId : roleIds) {
            Role role = roleMapper.selectById(roleId);
            if (role == null || !tenantId.equals(role.getTenantId())) {
                throw new BadRequestException("参数错误");
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
            throw new UnauthorizedException("未授权访问");
        }
        return tenantId;
    }

    private String buildInviteeKey(String email, String mobile) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail != null) {
            return normalizedEmail;
        }
        String normalizedMobile = normalizePhone(mobile);
        if (normalizedMobile != null) {
            return normalizedMobile;
        }
        return null;
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizePhone(String phone) {
        if (phone == null) {
            return null;
        }
        String trimmed = phone.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String generateInviteCode() {
        StringBuilder builder = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            int index = secureRandom.nextInt(CODE_CHARS.length());
            builder.append(CODE_CHARS.charAt(index));
        }
        return builder.toString();
    }

}
