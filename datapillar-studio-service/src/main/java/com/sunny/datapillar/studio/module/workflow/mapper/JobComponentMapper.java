package com.sunny.datapillar.studio.module.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.studio.module.workflow.entity.JobComponent;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * TaskComponentMapper Responsible for tasksComponentData access and persistence mapping
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Mapper
public interface JobComponentMapper extends BaseMapper<JobComponent> {

  /** Query all available components */
  List<JobComponent> selectAllComponents();

  /** According to code Query component */
  JobComponent selectByCode(@Param("code") String code);

  /** Query components by type */
  List<JobComponent> selectByType(@Param("type") String type);
}
