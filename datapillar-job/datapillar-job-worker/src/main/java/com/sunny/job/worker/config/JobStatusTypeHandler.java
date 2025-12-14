package com.sunny.job.worker.config;

import com.sunny.job.core.enums.JobStatus;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * JobStatus 枚举类型处理器
 * <p>
 * 处理 JobStatus 枚举与数据库 int 之间的转换
 *
 * @author SunnyX6
 * @date 2025-12-14
 */
@MappedTypes(JobStatus.class)
@MappedJdbcTypes(JdbcType.INTEGER)
public class JobStatusTypeHandler extends BaseTypeHandler<JobStatus> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, JobStatus parameter, JdbcType jdbcType) throws SQLException {
        ps.setInt(i, parameter.getCode());
    }

    @Override
    public JobStatus getNullableResult(ResultSet rs, String columnName) throws SQLException {
        int code = rs.getInt(columnName);
        return rs.wasNull() ? null : JobStatus.of(code);
    }

    @Override
    public JobStatus getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        int code = rs.getInt(columnIndex);
        return rs.wasNull() ? null : JobStatus.of(code);
    }

    @Override
    public JobStatus getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        int code = cs.getInt(columnIndex);
        return cs.wasNull() ? null : JobStatus.of(code);
    }
}
