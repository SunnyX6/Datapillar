package com.sunny.datapillar.studio.config;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.sunny.datapillar.studio.context.TenantContextHolder;
import java.util.Locale;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.schema.Column;
import org.springframework.stereotype.Component;

/**
 * 多租户租户ID注入
 */
@Component
public class StudioTenantLineHandler implements TenantLineHandler {

    @Override
    public Expression getTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("缺少租户上下文");
        }
        return new LongValue(tenantId);
    }

    @Override
    public String getTenantIdColumn() {
        return "tenant_id";
    }

    @Override
    public boolean ignoreTable(String tableName) {
        if (tableName == null) {
            return true;
        }
        String normalized = tableName.toLowerCase(Locale.ROOT);
        return "tenants".equals(normalized)
                || "user_invitation_orgs".equals(normalized)
                || "user_invitation_roles".equals(normalized)
                || "users".equals(normalized)
                || "feature_objects".equals(normalized)
                || "feature_object_categories".equals(normalized)
                || "permissions".equals(normalized);
    }

    @Override
    public boolean ignoreInsert(java.util.List<Column> columns, String tenantIdColumn) {
        return columns.stream().map(Column::getColumnName)
                .anyMatch(column -> column.equalsIgnoreCase(tenantIdColumn));
    }
}
