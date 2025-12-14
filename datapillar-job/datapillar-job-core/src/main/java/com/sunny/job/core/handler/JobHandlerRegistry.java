package com.sunny.job.core.handler;

import org.pf4j.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务处理器注册表
 * <p>
 * 支持两种注册方式：
 * 1. Spring 扫描：自动发现带 @DatapillarJob 注解的 Bean 方法
 * 2. PF4J 插件：通过 PluginManager 加载 JobHandlerProvider 扩展
 * <p>
 * PF4J 插件使用方式：
 * 1. 实现 JobHandlerProvider 接口并添加 @Extension 注解
 * 2. 在 MANIFEST.MF 中配置 Plugin-Id、Plugin-Version
 * 3. 将 jar 放到 plugins 目录，启动时自动加载
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Component
public class JobHandlerRegistry implements ApplicationContextAware, SmartInitializingSingleton, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(JobHandlerRegistry.class);

    private ApplicationContext applicationContext;

    /**
     * PF4J 插件管理器（可选注入，由 worker 模块提供）
     */
    @Autowired(required = false)
    private PluginManager pluginManager;

    /**
     * handler 名称 -> MethodJobHandler 映射
     */
    private final ConcurrentHashMap<String, MethodJobHandler> handlerMap = new ConcurrentHashMap<>();

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterSingletonsInstantiated() {
        // 1. Spring 扫描
        scanJobHandlers();
        // 2. PF4J 插件加载
        loadFromPlugins();
    }

    @Override
    public void destroy() {
        destroyAllHandlers();
    }

    /**
     * 通过 PF4J 插件加载处理器
     */
    private void loadFromPlugins() {
        if (pluginManager == null) {
            log.info("PluginManager 未配置，跳过插件加载");
            return;
        }

        log.info("开始通过 PF4J 加载 JobHandlerProvider 扩展...");

        List<JobHandlerProvider> providers = pluginManager.getExtensions(JobHandlerProvider.class);
        for (JobHandlerProvider provider : providers) {
            log.info("发现 JobHandlerProvider 扩展: {}", provider.getClass().getName());
            provider.registerHandlers(this);
        }

        log.info("PF4J 插件加载完成，共发现 {} 个 Provider", providers.size());
    }

    /**
     * 扫描所有 Bean 中的 @DatapillarJob 方法
     */
    private void scanJobHandlers() {
        log.info("开始扫描 @DatapillarJob 注解的方法...");

        String[] beanNames = applicationContext.getBeanNamesForType(Object.class, false, true);

        for (String beanName : beanNames) {
            Object bean = applicationContext.getBean(beanName);
            Class<?> beanClass = bean.getClass();

            // 查找所有带 @DatapillarJob 注解的方法
            Map<Method, DatapillarJob> annotatedMethods;
            try {
                annotatedMethods = MethodIntrospector.selectMethods(
                        beanClass,
                        (MethodIntrospector.MetadataLookup<DatapillarJob>) method ->
                                AnnotatedElementUtils.findMergedAnnotation(method, DatapillarJob.class)
                );
            } catch (Throwable ex) {
                log.debug("扫描 Bean [{}] 失败: {}", beanName, ex.getMessage());
                continue;
            }

            // 注册处理器
            for (Map.Entry<Method, DatapillarJob> entry : annotatedMethods.entrySet()) {
                Method method = entry.getKey();
                DatapillarJob annotation = entry.getValue();
                registerHandler(bean, method, annotation);
            }
        }

        log.info("@DatapillarJob 扫描完成，共注册 {} 个处理器", handlerMap.size());
    }

    /**
     * 注册单个处理器（内部使用）
     */
    private void registerHandler(Object bean, Method method, DatapillarJob annotation) {
        String handlerName = annotation.value();

        if (handlerName.isBlank()) {
            log.warn("@DatapillarJob value 为空，忽略: {}.{}", bean.getClass().getName(), method.getName());
            return;
        }

        // 检查方法签名
        Class<?>[] paramTypes = method.getParameterTypes();
        if (paramTypes.length > 1) {
            log.warn("@DatapillarJob 方法参数过多，忽略: {}.{}", bean.getClass().getName(), method.getName());
            return;
        }

        if (paramTypes.length == 1) {
            Class<?> paramType = paramTypes[0];
            if (paramType != String.class && paramType != JobContext.class) {
                log.warn("@DatapillarJob 方法参数类型不支持，忽略: {}.{}", bean.getClass().getName(), method.getName());
                return;
            }
        }

        // 检查是否重复
        if (handlerMap.containsKey(handlerName)) {
            log.error("Handler 名称重复: {}, 已存在于 {}", handlerName, handlerMap.get(handlerName));
            return;
        }

        // 解析 init/destroy 方法
        Method initMethod = resolveMethod(bean.getClass(), annotation.init());
        Method destroyMethod = resolveMethod(bean.getClass(), annotation.destroy());

        // 创建并注册
        MethodJobHandler handler = new MethodJobHandler(bean, method, initMethod, destroyMethod);
        handlerMap.put(handlerName, handler);

        log.info("注册 JobHandler: {} -> {}.{}", handlerName, bean.getClass().getSimpleName(), method.getName());
    }

    /**
     * 注册处理器（供 SPI Provider 使用）
     *
     * @param handlerName 处理器名称（对应 jobType）
     * @param handler     处理器实例
     */
    public void register(String handlerName, MethodJobHandler handler) {
        if (handlerMap.containsKey(handlerName)) {
            log.error("Handler 名称重复: {}, 已存在", handlerName);
            return;
        }
        handlerMap.put(handlerName, handler);
        log.info("注册 JobHandler (SPI): {}", handlerName);
    }

    /**
     * 解析方法名到 Method 对象
     */
    private Method resolveMethod(Class<?> clazz, String methodName) {
        if (methodName == null || methodName.isBlank()) {
            return null;
        }
        try {
            return clazz.getDeclaredMethod(methodName);
        } catch (NoSuchMethodException e) {
            log.warn("找不到方法: {}.{}", clazz.getName(), methodName);
            return null;
        }
    }

    /**
     * 销毁所有处理器
     */
    private void destroyAllHandlers() {
        log.info("开始销毁 JobHandler...");
        for (Map.Entry<String, MethodJobHandler> entry : handlerMap.entrySet()) {
            try {
                entry.getValue().destroy();
            } catch (Exception e) {
                log.error("销毁 JobHandler 失败: {}", entry.getKey(), e);
            }
        }
        handlerMap.clear();
        log.info("JobHandler 销毁完成");
    }

    /**
     * 获取处理器
     *
     * @param handlerName 处理器名称
     * @return 处理器实例，不存在返回 null
     */
    public MethodJobHandler getHandler(String handlerName) {
        return handlerMap.get(handlerName);
    }

    /**
     * 检查处理器是否存在
     */
    public boolean hasHandler(String handlerName) {
        return handlerMap.containsKey(handlerName);
    }

    /**
     * 获取所有处理器名称
     */
    public java.util.Set<String> getHandlerNames() {
        return handlerMap.keySet();
    }

    /**
     * 获取处理器数量
     */
    public int size() {
        return handlerMap.size();
    }
}
