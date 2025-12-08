package com.sunny.job.admin.mapper;

import com.sunny.job.admin.model.DatapillarJobGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */
@Mapper
public interface DatapillarJobGroupMapper {

    public List<DatapillarJobGroup> findAll();

    public List<DatapillarJobGroup> findByAddressType(@Param("addressType") int addressType);

    public int save(DatapillarJobGroup datapillarJobGroup);

    public int update(DatapillarJobGroup datapillarJobGroup);

    public int remove(@Param("id") int id);

    public DatapillarJobGroup load(@Param("id") int id);

    public DatapillarJobGroup loadByAppName(@Param("appname") String appname);

    public List<DatapillarJobGroup> pageList(@Param("offset") int offset,
                                      @Param("pagesize") int pagesize,
                                      @Param("appname") String appname,
                                      @Param("title") String title);

    public int pageListCount(@Param("offset") int offset,
                             @Param("pagesize") int pagesize,
                             @Param("appname") String appname,
                             @Param("title") String title);

}
