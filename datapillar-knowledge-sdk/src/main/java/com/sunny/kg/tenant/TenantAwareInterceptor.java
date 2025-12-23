package com.sunny.kg.tenant;

import com.sunny.kg.spi.Interceptor;
import com.sunny.kg.spi.InterceptorContext;
import org.slf4j.MDC;

/**
 * 租户感知拦截器
 * <p>
 * 自动为数据打上租户标签
 *
 * @author Sunny
 * @since 2025-12-11
 */
public class TenantAwareInterceptor implements Interceptor {

    private static final String MDC_TENANT = "tenantId";
    private final String defaultTenant;

    public TenantAwareInterceptor() {
        this(null);
    }

    public TenantAwareInterceptor(String defaultTenant) {
        this.defaultTenant = defaultTenant;
    }

    @Override
    public boolean beforeEmit(InterceptorContext context) {
        String tenant = TenantContext.getTenant();
        if (tenant == null) {
            tenant = defaultTenant;
        }

        if (tenant != null) {
            context.setAttribute("tenantId", tenant);
            MDC.put(MDC_TENANT, tenant);
        }

        return true;
    }

    @Override
    public void afterEmit(InterceptorContext context) {
        MDC.remove(MDC_TENANT);
    }

    @Override
    public void onError(InterceptorContext context, Exception exception) {
        MDC.remove(MDC_TENANT);
    }

    @Override
    public int order() {
        return Integer.MIN_VALUE;
    }

}
