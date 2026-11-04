package com.common.system.common.utils;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * YAML 资源读取工具
 *
 * <p>提供 classpath 资源通配扫描与 application.yml 解析能力，
 * 供读取 properties/ 下各模块配置（MODEL_KEY / MODEL_NAME 等）的场景复用，
 * 避免各业务类各自实现一份资源扫描与 YAML 解析逻辑。
 *
 * @author Eric
 * @date 2026/8/7
 */
public final class YmlResourceUtil {

    private static final ResourcePatternResolver RESOURCE_RESOLVER = new PathMatchingResourcePatternResolver();

    private YmlResourceUtil() {
    }

    /**
     * 按通配模式扫描 classpath 资源。
     *
     * @param pattern 如 classpath*:properties/**\/application.yml
     * @return 匹配的资源数组（无匹配时为空数组）
     * @throws IOException 资源扫描失败
     */
    public static Resource[] getResources(String pattern) throws IOException {
        return RESOURCE_RESOLVER.getResources(pattern);
    }

    /**
     * 解析 YAML 资源为键值 Map（UTF-8 编码）。
     *
     * @param resource YAML 资源
     * @return 顶层键值映射，空文件返回空 Map
     * @throws IOException 资源读取失败
     */
    public static Map<String, Object> loadYml(Resource resource) throws IOException {
        try (InputStream in = resource.getInputStream();
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            Map<String, Object> data = new Yaml().load(reader);
            return null == data ? Map.of() : data;
        }
    }
}
