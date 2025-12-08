package com.sunny.admin.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.admin.module.user.entity.Menu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 菜单Mapper接口
 * 
 * @author sunny
 * @since 2024-01-01
 */
@Mapper
public interface MenuMapper extends BaseMapper<Menu> {
    
    /**
     * 根据用户ID查询可访问的菜单列表
     * 基于用户角色和菜单角色映射表进行过滤
     * 
     * @param userId 用户ID
     * @return 菜单列表
     */
    @Select("SELECT DISTINCT m.* FROM menus m " +
            "INNER JOIN menu_roles mr ON m.id = mr.menu_id " +
            "INNER JOIN user_roles ur ON mr.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId} " +
            "AND m.visible = 1 " +
            "ORDER BY m.sort ASC, m.id ASC")
    List<Menu> findByUserId(@Param("userId") Long userId);
    
    /**
     * 查询所有可见的菜单
     * 
     * @return 菜单列表
     */
    @Select("SELECT * FROM menus WHERE visible = 1 ORDER BY sort ASC, id ASC")
    List<Menu> findAllVisible();
}