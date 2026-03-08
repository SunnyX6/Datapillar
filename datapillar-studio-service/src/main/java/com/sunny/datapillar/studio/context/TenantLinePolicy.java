package com.sunny.datapillar.studio.context;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import java.util.Locale;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.schema.Column;
import org.springframework.stereotype.Component;

/**
 * tenantLineStrategy component Responsible for tenantsLineStrategy core logic implementation
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Component
public class TenantLinePolicy implements TenantLineHandler {

  @Override
  public Expression getTenantId() {
    Long tenantId = TenantContextHolder.getTenantId();
    if (tenantId == null) {
      throw new IllegalStateException("Missing tenant context");
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
        || "system_bootstrap".equals(normalized)
        || "user_identities".equals(normalized)
        || "ai_provider".equals(normalized)
        || "user_invitation_orgs".equals(normalized)
        || "user_invitation_roles".equals(normalized)
        || "users".equals(normalized)
        || "feature_objects".equals(normalized)
        || "feature_object_categories".equals(normalized)
        || "permissions".equals(normalized);
  }

  @Override
  public boolean ignoreInsert(java.util.List<Column> columns, String tenantIdColumn) {
    return columns.stream()
        .map(Column::getColumnName)
        .anyMatch(column -> column.equalsIgnoreCase(tenantIdColumn));
  }
}
