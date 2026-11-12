package com.eric.base.sys.process.properties;

import com.eric.base.common.utils.YmlResourceUtil;
import com.eric.base.sys.process.response.SysProcessTemplateModelVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程模板分类配置读取
 *
 * @author Eric
 * @date 2026/8/22
 * @description 扫描各模块 application.yml（classpath*:properties 目录下通配，模式见 PROCESS_TEMPLATE_RESOURCE_PATTERN）
 *              的 process_template 配置段，由各模块自行维护本模块的流程分类信息：
 *              <ul>
 *              <li>process_template 二级配置项 key 即为 sys_process_template.field_model_name 值；</li>
 *              <li>process_template.${fieldModelName}.name 为模板管理页分类显示名；</li>
 *              <li>process_template.${fieldModelName}.class 为该分类流程对应的文档实体类；</li>
 *              <li>process_template.${fieldModelName}.ops 为该分类可配置的「操作标识」字典
 *                  （key=操作标识，value=中文描述），节点配置按 key 开关；</li>
 *              <li>process_template.${fieldModelName}.fields 为该分类文档实体字段字典
 *                  （key=实体字段属性名，value=中文描述），供节点「审批来源=表单字段」选择审批人字段。</li>
 *              </ul>
 *              多个 yml 中重复定义同一分类 key 时启动报错（fail-fast），避免静默覆盖。
 *              依据 artificial/docs/设计文档/sys/process/流程核心业务.md「process_template 配置项 和 field_model_name 取值」。
 *              类加载时解析一次并缓存（配置为静态资源，变更需重启生效）。
 */
public final class SysProcessTemplateProperties {

    private static final Logger log = LoggerFactory.getLogger(SysProcessTemplateProperties.class);

    /** 各模块 application.yml 通配扫描模式（各模块自行维护 process_template 段） */
    private static final String PROCESS_TEMPLATE_RESOURCE_PATTERN = "classpath*:properties/**/application.yml";

    private static final String KEY_PROCESS_TEMPLATE = "process_template";

    /** 已解析的模板分类列表（保持 yml 配置顺序） */
    private static final List<SysProcessTemplateModelVo> MODELS = loadModels();

    private static final Map<String, SysProcessTemplateModelVo> MODEL_MAP = buildModelMap();

    private SysProcessTemplateProperties() {
    }

    /**
     * 模板分类列表（process_template 配置项，保持 yml 顺序）。
     *
     * @return 分类列表；配置缺失时返回空列表
     */
    public static List<SysProcessTemplateModelVo> listModels() {
        return new ArrayList<>(MODELS);
    }

    /**
     * 校验并返回指定 field_model_name 对应的文档实体类全限定名。
     *
     * @param fieldModelName 模板所属模块（process_template 分类 key）
     * @return 实体类全限定名；配置不存在时返回 null
     */
    public static String getClassName(String fieldModelName) {
        SysProcessTemplateModelVo vo = MODEL_MAP.get(fieldModelName);
        return null == vo ? null : vo.getClassName();
    }

    /**
     * 指定 field_model_name 的模板分类显示名（process_template.${fieldModelName}.name）。
     *
     * @param fieldModelName 模板所属模块（process_template 分类 key）
     * @return 显示名；配置不存在时返回 null
     */
    public static String getName(String fieldModelName) {
        SysProcessTemplateModelVo vo = MODEL_MAP.get(fieldModelName);
        return null == vo ? null : vo.getName();
    }

    /**
     * 指定 field_model_name 是否为已配置的模板分类。
     */
    public static boolean contains(String fieldModelName) {
        return MODEL_MAP.containsKey(fieldModelName);
    }

    /**
     * 指定 field_model_name 的「操作标识」字典（process_template.${fieldModelName}.ops：
     * key=操作标识，value=中文描述；未配置返回空 Map）。
     *
     * @param fieldModelName 模板所属模块（process_template 分类 key）
     * @return 操作标识字典（不可变）
     */
    public static Map<String, String> getOps(String fieldModelName) {
        SysProcessTemplateModelVo vo = MODEL_MAP.get(fieldModelName);
        return null == vo ? Map.of() : Map.copyOf(vo.getOps());
    }

    /**
     * 指定 field_model_name 的「文档实体字段」字典（process_template.${fieldModelName}.fields：
     * key=实体字段属性名，value=中文描述；未配置返回空 Map）。
     * 供节点「审批来源=表单字段」选择审批人字段（handlerField）。
     *
     * @param fieldModelName 模板所属模块（process_template 分类 key）
     * @return 文档实体字段字典（不可变）
     */
    public static Map<String, String> getFields(String fieldModelName) {
        SysProcessTemplateModelVo vo = MODEL_MAP.get(fieldModelName);
        return null == vo ? Map.of() : Map.copyOf(vo.getFields());
    }

    /**
     * 扫描所有模块 application.yml，聚合各模块自行维护的 process_template 段。
     * 重复分类 key 直接抛出异常（fail-fast，应用启动即暴露配置错误）。
     */
    private static List<SysProcessTemplateModelVo> loadModels() {
        Map<String, SysProcessTemplateModelVo> models = new LinkedHashMap<>();
        try {
            Resource[] resources = YmlResourceUtil.getResources(PROCESS_TEMPLATE_RESOURCE_PATTERN);
            if (null == resources || resources.length == 0) {
                log.warn("no application.yml found by pattern: {}", PROCESS_TEMPLATE_RESOURCE_PATTERN);
                return List.of();
            }
            for (Resource resource : resources) {
                Map<String, Object> data = YmlResourceUtil.loadYml(resource);
                Object processTemplate = data.get(KEY_PROCESS_TEMPLATE);
                if (processTemplate instanceof Map) {
                    mergeModels(models, (Map<?, ?>) processTemplate, resource.getDescription());
                }
            }
        } catch (Exception e) {
            log.error("failed to load process_template from {}: {}", PROCESS_TEMPLATE_RESOURCE_PATTERN, e.getMessage(), e);
        }
        return new ArrayList<>(models.values());
    }

    /**
     * 将单个 yml 的 process_template 段合并到分类表（包私有，便于单测）：
     * 同分类 key 重复定义时抛 {@link IllegalStateException}（fail-fast，避免静默覆盖）。
     *
     * @param models         分类表（key=fieldModelName，保持插入顺序）
     * @param processTemplate process_template 配置段
     * @param source          配置来源描述（用于报错信息，如资源路径）
     */
    static void mergeModels(Map<String, SysProcessTemplateModelVo> models, Map<?, ?> processTemplate, String source) {
        for (Map.Entry<?, ?> entry : processTemplate.entrySet()) {
            String modelName = String.valueOf(entry.getKey());
            if (!(entry.getValue() instanceof Map)) {
                continue;
            }
            if (models.containsKey(modelName)) {
                throw new IllegalStateException(
                        "重复的流程分类配置: " + modelName + "（来源: " + source + "）");
            }
            Map<?, ?> item = (Map<?, ?>) entry.getValue();
            SysProcessTemplateModelVo vo = new SysProcessTemplateModelVo();
            vo.setFieldModelName(modelName);
            vo.setName(valueOf(item.get("name")));
            vo.setClassName(valueOf(item.get("class")));
            vo.setDesc(valueOf(item.get("desc")));
            vo.setOps(parseStringMap(item.get("ops")));
            vo.setFields(parseStringMap(item.get("fields")));
            models.put(modelName, vo);
        }
    }

    private static Map<String, SysProcessTemplateModelVo> buildModelMap() {
        Map<String, SysProcessTemplateModelVo> map = new LinkedHashMap<>();
        for (SysProcessTemplateModelVo vo : MODELS) {
            map.put(vo.getFieldModelName(), vo);
        }
        return map;
    }

    /**
     * 解析 key:value 字典配置段（ops 操作标识字典 / fields 文档实体字段字典），
     * key=编码，value=中文描述，保持 yml 顺序。
     *
     * @param value 配置段（未配置或非 Map 时返回空字典）
     */
    private static Map<String, String> parseStringMap(Object value) {
        Map<String, String> map = new LinkedHashMap<>();
        if (value instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                map.put(String.valueOf(entry.getKey()), valueOf(entry.getValue()));
            }
        }
        return map;
    }

    private static String valueOf(Object value) {
        return null == value ? null : String.valueOf(value);
    }
}
