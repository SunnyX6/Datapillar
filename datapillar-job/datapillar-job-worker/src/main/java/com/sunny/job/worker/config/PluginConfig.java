package com.sunny.job.worker.config;

import org.pf4j.DefaultPluginManager;
import org.pf4j.JarPluginLoader;
import org.pf4j.ManifestPluginDescriptorFinder;
import org.pf4j.PluginDescriptorFinder;
import org.pf4j.PluginLoader;
import org.pf4j.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * PF4J 插件配置
 * <p>
 * 配置 PluginManager 以支持 JAR 格式插件加载
 * <p>
 * 插件目录：${datapillar.job.worker.plugins-dir:plugins}
 * <p>
 * 使用方式：将插件 jar 放到 plugins 目录，启动时自动加载
 *
 * @author SunnyX6
 * @date 2025-12-14
 */
@Configuration
public class PluginConfig {

    private static final Logger log = LoggerFactory.getLogger(PluginConfig.class);

    @Value("${datapillar.job.worker.plugins-dir:plugins}")
    private String pluginsDir;

    /**
     * 创建 PluginManager
     * <p>
     * 只加载 JAR 格式插件，从 MANIFEST.MF 读取插件元数据
     */
    @Bean
    public PluginManager pluginManager() {
        Path pluginsPath = Paths.get(pluginsDir).toAbsolutePath();
        log.info("初始化 PluginManager，插件目录: {}", pluginsPath);

        PluginManager pluginManager = new DefaultPluginManager(pluginsPath) {
            @Override
            protected PluginLoader createPluginLoader() {
                // 只加载 JAR 格式插件
                return new JarPluginLoader(this);
            }

            @Override
            protected PluginDescriptorFinder createPluginDescriptorFinder() {
                // 从 MANIFEST.MF 读取插件元数据
                return new ManifestPluginDescriptorFinder();
            }
        };

        // 加载并启动插件
        pluginManager.loadPlugins();
        pluginManager.startPlugins();

        log.info("插件加载完成，共加载 {} 个插件", pluginManager.getPlugins().size());
        pluginManager.getPlugins().forEach(plugin ->
                log.info("  - {} ({})", plugin.getPluginId(), plugin.getDescriptor().getVersion()));

        return pluginManager;
    }
}
