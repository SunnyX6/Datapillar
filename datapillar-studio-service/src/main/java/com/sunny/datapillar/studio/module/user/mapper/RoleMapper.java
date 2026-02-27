package com.sunny.datapillar.studio.module.user.mapper;

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
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.studio.module.user.entity.Role;
import com.sunny.datapillar.studio.module.user.entity.RolePermission;

/**
 * 角色Mapper
 * 负责角色数据访问与持久化映射
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Mapper
public interface RoleMapper extends BaseMapper<Role> {

    /**
     * 根据用户ID查询角色列表
     */
    List<Role> findByUserId(@Param("tenantId") Long tenantId,
                            @Param("userId") Long userId);

    /**
     * 根据角色名称查询角色
     */
    Role findByName(@Param("tenantId") Long tenantId,
                    @Param("name") String name);

    /**
     * 根据角色ID查询并加锁
     */
    Role selectByIdForUpdate(@Param("id") Long id);

    /**
     * 删除角色权限关联
     */
    void deleteRolePermissions(@Param("tenantId") Long tenantId, @Param("roleId") Long roleId);

    /**
     * 根据角色ID删除用户角色关联
     */
    void deleteUserRolesByRoleId(@Param("tenantId") Long tenantId, @Param("roleId") Long roleId);

    /**
     * 插入角色权限关联
     */
    void insertRolePermission(RolePermission rolePermission);

    /**
     * 查询租户角色列表（含成员数量）
     */
    List<RoleResponse> selectRoleListWithMemberCount(@Param("tenantId") Long tenantId);

    /**
     * 统计角色下成员数量
     */
    long countUsersByRoleId(@Param("tenantId") Long tenantId, @Param("roleId") Long roleId);

    /**
     * 查询租户最大排序值
     */
    Integer selectMaxSortByTenant(@Param("tenantId") Long tenantId);

    /**
     * 查询角色成员明细
     */
    List<RoleMemberItem> selectRoleMembers(@Param("tenantId") Long tenantId,
                                               @Param("roleId") Long roleId,
                                               @Param("status") Integer status);

    /**
     * 按角色批量删除用户角色关联
     */
    void deleteRoleMembersByUserIds(@Param("tenantId") Long tenantId,
                                    @Param("roleId") Long roleId,
                                    @Param("userIds") List<Long> userIds);
}
