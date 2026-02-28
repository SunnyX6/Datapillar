/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.gravitino.datapillar.storage.relational;

import java.sql.Connection;
import java.util.Objects;
import java.util.Properties;
import org.apache.gravitino.datapillar.context.TenantContext;
import org.apache.gravitino.datapillar.context.TenantContextHolder;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

/** MyBatis interceptor that enforces tenant-aware SQL rewriting. */
@Intercepts({
  @Signature(
      type = StatementHandler.class,
      method = "prepare",
      args = {Connection.class, Integer.class})
})
public class TenantSqlInterceptor implements Interceptor {

  private final TenantSqlPatchRegistry patchRegistry;

  public TenantSqlInterceptor() {
    this(new TenantSqlPatchRegistry());
  }

  TenantSqlInterceptor(TenantSqlPatchRegistry patchRegistry) {
    this.patchRegistry = Objects.requireNonNull(patchRegistry, "patchRegistry");
  }

  @Override
  public Object intercept(Invocation invocation) throws Throwable {
    MetaObject metaObject = SystemMetaObject.forObject(invocation.getTarget());
    BoundSql boundSql = (BoundSql) metaObject.getValue("delegate.boundSql");
    if (boundSql == null) {
      return invocation.proceed();
    }

    MappedStatement mappedStatement =
        (MappedStatement) metaObject.getValue("delegate.mappedStatement");
    String statementId = mappedStatement == null ? "unknown" : mappedStatement.getId();

    String originalSql = boundSql.getSql();
    Long tenantId = resolveTenantId();
    String rewrittenSql =
        TenantSqlRewriter.rewrite(statementId, originalSql, tenantId, patchRegistry);

    if (!Objects.equals(originalSql, rewrittenSql)) {
      metaObject.setValue("delegate.boundSql.sql", rewrittenSql);
    }
    return invocation.proceed();
  }

  @Override
  public Object plugin(Object target) {
    return Plugin.wrap(target, this);
  }

  @Override
  public void setProperties(Properties properties) {
    // No-op.
  }

  private Long resolveTenantId() {
    TenantContext tenantContext = TenantContextHolder.get();
    if (tenantContext == null || tenantContext.tenantId() <= 0) {
      throw new IllegalStateException("Missing tenant context for relational SQL rewrite");
    }
    return tenantContext.tenantId();
  }
}
