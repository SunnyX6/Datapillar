package com.sunny.datapillar.admin.module.user.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.admin.module.user.entity.Menu;

/**
 * 菜单 Mapper 接口
 *
 * @author sunny
 */
@Mapper
public interface MenuMapper extends BaseMapper<Menu> {

    /**
     * 根据用户ID查询可访问的菜单列表
     */
    List<Menu> findByUserId(@Param("userId") Long userId, @Param("location") String location);

    /**
     * 查询所有可见的菜单
     */
    List<Menu> findAllVisible();
}
