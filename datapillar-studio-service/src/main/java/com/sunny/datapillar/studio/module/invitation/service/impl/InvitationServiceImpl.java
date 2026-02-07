package com.sunny.datapillar.studio.module.invitation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunny.datapillar.common.error.ErrorCode;
import com.sunny.datapillar.common.exception.BusinessException;
import com.sunny.datapillar.studio.context.TenantContextHolder;
import com.sunny.datapillar.studio.module.invitation.dto.InvitationDto;
import com.sunny.datapillar.studio.module.invitation.entity.UserInvitation;
import com.sunny.datapillar.studio.module.invitation.entity.UserInvitationRole;
import com.sunny.datapillar.studio.module.invitation.mapper.UserInvitationMapper;
import com.sunny.datapillar.studio.module.invitation.mapper.UserInvitationRoleMapper;
import com.sunny.datapillar.studio.module.invitation.service.InvitationService;
import com.sunny.datapillar.studio.module.user.entity.Role;
import com.sunny.datapillar.studio.module.user.mapper.RoleMapper;
import com.sunny.datapillar.studio.util.UserContextUtil;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 邀请服务实现
 */
@Service
@RequiredArgsConstructor
public class InvitationServiceImpl implements InvitationService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_CANCELLED = 3;

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 12;

    private final UserInvitationMapper userInvitationMapper;
    private final UserInvitationRoleMapper userInvitationRoleMapper;
    private final RoleMapper roleMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public InvitationDto.CreateResponse createInvitation(InvitationDto.Create dto) {
        if (dto == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT);
        }
        Long tenantId = getRequiredTenantId();
        if (dto.getRoleIds() == null || dto.getRoleIds().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT);
        }
        Set<Long> uniqueRoles = new HashSet<>(dto.getRoleIds());
        if (uniqueRoles.isEmpty() || uniqueRoles.contains(null)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT);
        }

        String inviteeKey = buildInviteeKey(dto.getInviteeEmail(), dto.getInviteeMobile());
        if (inviteeKey == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT);
        }

        LambdaQueryWrapper<UserInvitation> activeQuery = new LambdaQueryWrapper<>();
        activeQuery.eq(UserInvitation::getTenantId, tenantId)
                .eq(UserInvitation::getActiveInviteeKey, inviteeKey)
                .eq(UserInvitation::getStatus, STATUS_PENDING);
        if (userInvitationMapper.selectOne(activeQuery) != null) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
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
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT);
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
    public IPage<UserInvitation> listInvitations(Integer status, int limit, int offset) {
        Long tenantId = getRequiredTenantId();
        long current = resolveCurrent(limit, offset);
        Page<UserInvitation> page = Page.of(current, limit);
        LambdaQueryWrapper<UserInvitation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserInvitation::getTenantId, tenantId);
        if (status != null) {
            wrapper.eq(UserInvitation::getStatus, status);
        }
        wrapper.orderByDesc(UserInvitation::getCreatedAt).orderByDesc(UserInvitation::getId);
        return userInvitationMapper.selectPage(page, wrapper);
    }

    @Override
    @Transactional
    public void cancelInvitation(Long invitationId) {
        if (invitationId == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT);
        }
        Long tenantId = getRequiredTenantId();
        LambdaQueryWrapper<UserInvitation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserInvitation::getId, invitationId)
                .eq(UserInvitation::getTenantId, tenantId);
        UserInvitation invitation = userInvitationMapper.selectOne(wrapper);
        if (invitation == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (invitation.getStatus() == null || invitation.getStatus() != STATUS_PENDING) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT);
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
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private Long getRequiredTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
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

    private long resolveCurrent(int limit, int offset) {
        if (limit <= 0) {
            return 1;
        }
        return offset / limit + 1;
    }
}
