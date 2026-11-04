package com.common.system.common.cache;

import com.common.system.common.utils.StringUtil;
import com.common.system.common.utils.YmlResourceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模块信息缓存服务
 *
 * <p>应用启动时扫描 classpath 下 properties/**\/application.yml，
 * 读取各模块的 MODEL_KEY / MODEL_NAME，以 Redis Hash 结构（即一份 Map）缓存到 Redis：
 * key = model:name:map，field = MODEL_KEY，value = MODEL_NAME。
 * 例如 {COMMON: 公共, SYS-ORG: 组织架构和权限模块}。
 * 缓存写入失败仅记录日志，不阻断应用启动（缓存属增强能力，不影响核心业务）。</p>
 * <p>同时维护进程内兜底缓存：Redis 不可用、未部署或缓存被清理时，
 * 读取（getModelNames / resolveModelName）自动回退本地内存，保证来源模块下拉等
 * 功能不依赖 Redis 可用性（部署环境默认无 Redis，见 infra/docker-compose.yml）。</p>
 *
 * @author Eric
 * @date 2026/8/7
 */
@Service
public class ModelCacheService {

    private static final Logger log = LoggerFactory.getLogger(ModelCacheService.class);

    /** application.yml 资源扫描路径（提供 MODEL_KEY / MODEL_NAME） */
    private static final String APPLICATION_RESOURCE_PATTERN = "classpath*:properties/**/application.yml";

    /** 模块信息缓存 key（Redis Hash：field=MODEL_KEY，value=MODEL_NAME） */
    public static final String CACHE_KEY_MODEL_MAP = "model:name:map";

    private static final String KEY_MODEL_KEY = "MODEL_KEY";
    private static final String KEY_MODEL_NAME = "MODEL_NAME";

    private final RedisTemplate<String, Object> redisTemplate;

    /** 进程内兜底缓存：cacheModelNames 时更新，Redis 读取失败/为空时回退（不可变，线程安全） */
    private volatile Map<String, String> localModelNames = Map.of();

    public ModelCacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 扫描 properties/ 下所有 application.yml，将 MODEL_KEY → MODEL_NAME 映射缓存到 Redis。
     * <p>application.yml 缺失或 MODEL_KEY / MODEL_NAME 缺失的模块不进入缓存；
     * 使用 putAll 整份覆盖，保证与配置文件保持一致。
     * 无论 Redis 是否可用，都会先更新进程内兜底缓存，保证读取侧始终有值。</p>
     *
     * @return 实际写入缓存的模块数量；Redis 写入失败时返回 0
     */
    public int cacheModelNames() {
        Map<String, String> modelNames = loadModelNames();
        if (modelNames.isEmpty()) {
            log.warn("no MODEL_KEY/MODEL_NAME found from application.yml, skip cache");
            return 0;
        }
        // 先更新进程内兜底缓存（Redis 写入失败不影响读取侧有值）
        localModelNames = Map.copyOf(modelNames);
        try {
            redisTemplate.opsForHash().putAll(CACHE_KEY_MODEL_MAP, modelNames);
            log.info("cached {} model names to redis, key={}, data={}",
                    modelNames.size(), CACHE_KEY_MODEL_MAP, modelNames);
            return modelNames.size();
        } catch (Exception e) {
            log.error("failed to cache model names to redis, key={}, fallback to local cache",
                    CACHE_KEY_MODEL_MAP, e);
            return 0;
        }
    }

    /**
     * 读取模块信息 Map（MODEL_KEY → MODEL_NAME）。
     * <p>优先读 Redis；Redis 读取失败或缓存为空（未部署 Redis / key 被清理）时，
     * 回退进程内兜底缓存，避免来源模块等功能在 Redis 不可用时返回空。</p>
     *
     * @return 模块信息 Map；本地兜底亦为空时返回空 Map
     */
    public Map<String, String> getModelNames() {
        try {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(CACHE_KEY_MODEL_MAP);
            Map<String, String> result = new LinkedHashMap<>();
            entries.forEach((key, value) -> result.put(String.valueOf(key), String.valueOf(value)));
            if (!result.isEmpty()) {
                return result;
            }
            return localModelNames;
        } catch (Exception e) {
            log.warn("failed to read model names from redis, key={}, fallback to local cache",
                    CACHE_KEY_MODEL_MAP, e);
            return localModelNames;
        }
    }

    /**
     * 将 MODEL_KEY 翻译为 MODEL_NAME（模块中文名）。
     * <p>缓存缺失或 key 不在映射中时回退返回 key 原值，保证历史数据（旧值）仍可展示。</p>
     *
     * @param modelKey 模块标识（MODEL_KEY，如 SYS-ORG）
     * @return 模块中文名（MODEL_NAME）；空值/未知 key 回退原值
     */
    public String resolveModelName(String modelKey) {
        if (StringUtil.isEmpty(modelKey)) {
            return modelKey;
        }
        return getModelNames().getOrDefault(modelKey, modelKey);
    }

    /**
     * 扫描 properties/ 下所有 application.yml，构建 MODEL_KEY → MODEL_NAME（模块中文名）映射。
     * application.yml 缺失或 MODEL_KEY / MODEL_NAME 缺失时该模块不进入映射。
     */
    private Map<String, String> loadModelNames() {
        Map<String, String> modelNames = new HashMap<>();
        try {
            Resource[] resources = YmlResourceUtil.getResources(APPLICATION_RESOURCE_PATTERN);
            for (Resource resource : resources) {
                Map<String, Object> data = YmlResourceUtil.loadYml(resource);
                Object modelKey = data.get(KEY_MODEL_KEY);
                Object modelName = data.get(KEY_MODEL_NAME);
                if (null != modelKey && StringUtil.isNotEmpty(String.valueOf(modelKey))
                        && null != modelName && StringUtil.isNotEmpty(String.valueOf(modelName))) {
                    modelNames.put(String.valueOf(modelKey), String.valueOf(modelName));
                }
            }
        } catch (IOException e) {
            log.warn("failed to load application.yml model names: {}", e.getMessage());
        }
        return modelNames;
    }
}
