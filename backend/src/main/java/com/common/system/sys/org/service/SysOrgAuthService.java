package com.common.system.sys.org.service;

import com.common.system.common.base.service.BaseService;
import com.common.system.common.utils.StringUtil;
import com.common.system.sys.org.dao.SysOrgAuthDao;
import com.common.system.sys.org.model.SysOrgAuth;
import com.common.system.sys.org.response.AuthGroupVo;
import com.common.system.sys.org.response.AuthItemVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 权限业务服务
 *
 * @author Eric
 * @date 2026/8/7
 * @description 承载「读取 properties/ 下各模块 roles.yml 增量同步权限」等业务逻辑。
 *              权限所属模块 field_model 取同目录 application.yml 的 MODEL_KEY，
 *              分组标题 modelName 取同目录 application.yml 的 MODEL_NAME；
 *              field_name/field_description 仅当 roles.yml 中为有效中文时才写入，
 *              读取不到中文（空值或占位符）时保持为空，等待配置文件补充后再次同步补全。
 */
@Service
public class SysOrgAuthService extends BaseService<SysOrgAuth, SysOrgAuthDao> {

    private static final Logger log = LoggerFactory.getLogger(SysOrgAuthService.class);

    /** roles.yml 资源扫描路径（与 application.yml 同目录，无 /roles 层级） */
    private static final String ROLES_RESOURCE_PATTERN = "classpath*:properties/**/roles.yml";

    /** application.yml 资源扫描路径（提供 MODEL_KEY / MODEL_NAME） */
    private static final String APPLICATION_RESOURCE_PATTERN = "classpath*:properties/**/application.yml";

    private static final String KEY_MODEL_KEY = "MODEL_KEY";
    private static final String KEY_MODEL_NAME = "MODEL_NAME";
    private static final String KEY_ROLE_LIST = "ROLE_LIST";
    private static final String KEY_ROLE_MAP = "ROLE_MAP";
    private static final String KEY_NAME = "NAME";
    private static final String KEY_DESC = "DESC";

    private final ResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();

    public SysOrgAuthService(SysOrgAuthDao baseDao) {
        super(baseDao);
    }

    /**
     * 第二个启动任务：读取 properties/ 下各模块 roles.yml，将权限增量同步到 sys_org_auth。
     * <p>field_model 取同目录 application.yml 的 MODEL_KEY（缺失时回退按目录层级推导，如 common → COMMON）；
     * 已存在的权限不重复插入，仅当库中名称/描述为空时用 roles.yml 的有效中文补全。</p>
     */
    @Transactional
    public void syncAuthsFromRolesYml() {
        try {
            Resource[] resources = resourceResolver.getResources(ROLES_RESOURCE_PATTERN);
            for (Resource resource : resources) {
                String model = resolveModel(resource);
                log.info("sync auths from roles resource: url={}, model={}", resource.getURL(), model);
                Map<String, Object> data = loadYml(resource);
                List<String> codes = roleCodes(data);
                if (codes.isEmpty()) {
                    log.warn("roles resource has empty ROLE_LIST, skip: {}", resource.getURL());
                    continue;
                }
                Map<String, Map<String, Object>> roleMap = roleMap(data);
                for (String code : codes) {
                    syncAuth(code, model, nameOf(roleMap, code), descOf(roleMap, code));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("failed to load roles resources: " + ROLES_RESOURCE_PATTERN, e);
        }
    }

    /**
     * 确保指定编码的权限存在（幂等）：存在则直接返回，不存在则创建。
     *
     * @param code  权限编码
     * @param name  权限名称（可为空）
     * @param model 权限所属模块
     * @return 已存在或新建的权限
     */
    @Transactional
    public SysOrgAuth ensureAuth(String code, String name, String model) {
        SysOrgAuth exist = getBaseDao().selectByCode(code);
        if (exist != null) {
            return exist;
        }
        SysOrgAuth auth = new SysOrgAuth();
        auth.setFieldCode(code);
        auth.setFieldModel(model);
        if (StringUtil.isNotEmpty(name)) {
            auth.setFieldName(name);
        }
        insert(auth);
        log.info("auth ensured: code={}, model={}, name={}", code, model, auth.getFieldName());
        return auth;
    }

    /**
     * 角色编辑页「权限」字段可选项：按 sys_org_auth.field_model 分组返回。
     * 分组标题 modelName 优先读取 properties/ 下 application.yml 的 MODEL_NAME
     * （按 MODEL_KEY 匹配 field_model，如 sys/org/application.yml 的 MODEL_NAME=组织架构和权限模块），
     * 取不到时回退 field_model 原值。
     */
    public List<AuthGroupVo> listAuthOptions() {
        Map<String, String> modelNames = loadModelNames();
        List<SysOrgAuth> all = getBaseDao().selectList(null, null);
        Map<String, List<AuthItemVo>> grouped = new LinkedHashMap<>();
        for (SysOrgAuth auth : all) {
            String model = StringUtil.isEmpty(auth.getFieldModel()) ? "" : auth.getFieldModel();
            grouped.computeIfAbsent(model, k -> new ArrayList<>()).add(toAuthItem(auth));
        }
        List<AuthGroupVo> result = new ArrayList<>();
        grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    AuthGroupVo group = new AuthGroupVo();
                    group.setModel(entry.getKey());
                    group.setModelName(modelNames.getOrDefault(entry.getKey(), entry.getKey()));
                    group.setAuths(entry.getValue());
                    result.add(group);
                });
        return result;
    }

    /**
     * 扫描 properties/ 下所有 application.yml，构建 MODEL_KEY → MODEL_NAME（模块中文名）映射。
     * application.yml 缺失或 MODEL_NAME 缺失时该模块不进入映射，分组标题回退 field_model 原值。
     */
    private Map<String, String> loadModelNames() {
        Map<String, String> modelNames = new HashMap<>();
        try {
            Resource[] resources = resourceResolver.getResources(APPLICATION_RESOURCE_PATTERN);
            for (Resource resource : resources) {
                Map<String, Object> data = loadYml(resource);
                Object modelKey = data.get(KEY_MODEL_KEY);
                Object modelName = data.get(KEY_MODEL_NAME);
                if (null != modelKey && StringUtil.isNotEmpty(String.valueOf(modelKey))
                        && null != modelName && StringUtil.isNotEmpty(String.valueOf(modelName))) {
                    modelNames.put(String.valueOf(modelKey), String.valueOf(modelName));
                }
            }
        } catch (IOException e) {
            log.warn("failed to load application.yml model names, fallback to field_model: {}", e.getMessage());
        }
        return modelNames;
    }

    private static AuthItemVo toAuthItem(SysOrgAuth auth) {
        AuthItemVo item = new AuthItemVo();
        item.setFieldId(auth.getFieldId());
        item.setFieldName(auth.getFieldName());
        item.setFieldCode(auth.getFieldCode());
        return item;
    }

    /**
     * 同步单个权限：不存在则插入；已存在且库中名称/描述为空时，用 roles.yml 有效值补全。
     */
    void syncAuth(String code, String model, String name, String desc) {
        SysOrgAuth exist = getBaseDao().selectByCode(code);
        if (exist == null) {
            SysOrgAuth auth = new SysOrgAuth();
            auth.setFieldCode(code);
            auth.setFieldModel(model);
            if (isValidChinese(name)) {
                auth.setFieldName(name);
            }
            if (isValidChinese(desc)) {
                auth.setFieldDescription(desc);
            }
            insert(auth);
            log.info("auth inserted: code={}, model={}, name={}", code, model, auth.getFieldName());
            return;
        }
        boolean changed = false;
        if (isValidChinese(name) && StringUtil.isEmpty(exist.getFieldName())) {
            exist.setFieldName(name);
            changed = true;
        }
        if (isValidChinese(desc) && StringUtil.isEmpty(exist.getFieldDescription())) {
            exist.setFieldDescription(desc);
            changed = true;
        }
        if (changed) {
            update(exist);
            log.info("auth filled: code={}, name={}", code, exist.getFieldName());
        }
    }

    /**
     * 计算权限所属模块：优先取同目录 application.yml 的 MODEL_KEY；
     * 缺失时回退按 /properties/ 下的文件夹层级推导，全大写并用 '-' 拼接。
     * 例如 common/roles.yml → COMMON；sys/org/roles.yml → SYS-ORG。
     */
    private String resolveModel(Resource rolesResource) throws IOException {
        String modelKey = readApplicationValue(rolesResource, KEY_MODEL_KEY);
        if (StringUtil.isNotEmpty(modelKey)) {
            return modelKey;
        }
        String url = rolesResource.getURL().toString();
        int idx = url.indexOf("properties/");
        if (idx < 0) {
            throw new IllegalStateException("unexpected roles resource url: " + url);
        }
        String path = url.substring(idx + "properties/".length());
        int lastSlash = path.lastIndexOf('/');
        String dir = lastSlash >= 0 ? path.substring(0, lastSlash) : "";
        return dir.toUpperCase(Locale.ROOT).replace('/', '-');
    }

    /**
     * 读取与 roles.yml 同目录 application.yml 的顶层字段值；
     * application.yml 不存在或字段缺失时返回 null。
     */
    private String readApplicationValue(Resource rolesResource, String key) throws IOException {
        String url = rolesResource.getURL().toString();
        int lastSlash = url.lastIndexOf('/');
        if (lastSlash < 0) {
            return null;
        }
        Resource appResource = new UrlResource(url.substring(0, lastSlash) + "/application.yml");
        if (!appResource.exists()) {
            return null;
        }
        Map<String, Object> data = loadYml(appResource);
        Object value = data.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Map<String, Object> loadYml(Resource resource) throws IOException {
        try (InputStream in = resource.getInputStream();
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            Object data = new Yaml().load(reader);
            return data instanceof Map ? (Map<String, Object>) data : Collections.emptyMap();
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> roleCodes(Map<String, Object> data) {
        Object list = data.get(KEY_ROLE_LIST);
        return list instanceof List ? (List<String>) list : Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> roleMap(Map<String, Object> data) {
        Object map = data.get(KEY_ROLE_MAP);
        return map instanceof Map ? (Map<String, Map<String, Object>>) map : Collections.emptyMap();
    }

    private String nameOf(Map<String, Map<String, Object>> roleMap, String code) {
        return valueOf(roleMap, code, KEY_NAME);
    }

    private String descOf(Map<String, Map<String, Object>> roleMap, String code) {
        return valueOf(roleMap, code, KEY_DESC);
    }

    private String valueOf(Map<String, Map<String, Object>> roleMap, String code, String key) {
        Map<String, Object> item = roleMap.get(code);
        if (item == null) {
            return null;
        }
        Object value = item.get(key);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 是否为有效中文文案：非空且至少包含一个汉字。
     * 当前 roles.yml 中文缺失（空值/占位符）时返回 false，保持库中字段为空，待配置补充。
     */
    private boolean isValidChinese(String text) {
        return StringUtil.isNotEmpty(text) && text.matches(".*[\\u4e00-\\u9fa5].*");
    }
}
