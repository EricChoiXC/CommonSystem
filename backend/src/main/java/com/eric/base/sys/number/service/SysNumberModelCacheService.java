package com.eric.base.sys.number.service;

import com.eric.base.common.utils.StringUtil;
import com.eric.base.common.utils.YmlResourceUtil;
import com.eric.base.sys.number.dao.SysNumberRuleDao;
import com.eric.base.sys.number.model.SysNumberModelDo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 编号接入项缓存服务
 *
 * @author Eric
 * @date 2026/11/13
 * @description 承载 numberRule.yml 接入项的启动期扫描与进程内缓存（任务 NB2-BE-001，
 *              方案 §1.1）：
 *              <ul>
 *              <li>启动任务入口 {@link #syncFromYml()}：通配扫描
 *                  {@code classpath*:properties/**\/numberRule.yml}（文件名全局统一，
 *                  YmlResourceUtil 已覆盖 properties/** 扫描），解析合并为「接入项编码 →
 *                  {name / modelName / modelField}」缓存，供编号规则编辑页「所属接入项」
 *                  下拉与 save/enable 接入项存在性校验使用；</li>
 *              <li>缓存失效告警：校验 sys_number_rule.field_model_id 是否仍存在于缓存
 *                  （yml 删除后规则残留场景，R6），失效输出 WARN 告警日志，不阻断启动；</li>
 *              <li>{@link #getModelOptions()} 为接入项下拉数据源读取入口
 *                  （NB3-BE-003 modelOptions 接口数据源）。</li>
 *              </ul>
 *              缓存为进程内单例不入 Redis（A1：yml 静态无运行期变更）；
 *              构建失败保留旧缓存、告警失败不阻断（启动任务失败不应影响应用启动，
 *              先例 CommonModuleService#syncFromYml）。
 */
@Service
public class SysNumberModelCacheService {

    private static final Logger log = LoggerFactory.getLogger(SysNumberModelCacheService.class);

    /** numberRule.yml 资源扫描路径（各模块 properties/ 下同名文件全局统一） */
    private static final String NUMBER_RULE_RESOURCE_PATTERN = "classpath*:properties/**/numberRule.yml";

    /** numberRule.yml 顶层段名 */
    private static final String KEY_NUMBER_RULE = "numberRule";

    /** 接入项条目键：名称 */
    private static final String KEY_NAME = "name";

    /** 接入项条目键：业务模型名 */
    private static final String KEY_MODEL_NAME = "modelName";

    /** 接入项条目键：承接编号的字段名 */
    private static final String KEY_MODEL_FIELD = "modelField";

    private final SysNumberRuleDao ruleDao;

    /** 进程内接入项缓存（key → 接入项，保持 yml 出现顺序；不可变，线程安全） */
    private volatile Map<String, SysNumberModelDo> localModels = Map.of();

    public SysNumberModelCacheService(SysNumberRuleDao ruleDao) {
        this.ruleDao = ruleDao;
    }

    /**
     * 系统启动任务入口（SysStartupTask 第十一个任务只调本方法）：构建接入项缓存 + 失效告警。
     * <p>扫描/构建各失败路径只记日志不抛出：缓存属增强读取，构建失败时保留旧缓存
     * （首次启动无旧缓存则为空 Map），不阻断应用启动。</p>
     */
    public void syncFromYml() {
        try {
            Map<String, SysNumberModelDo> models = parseNumberRuleYmls();
            localModels = Collections.unmodifiableMap(models);
            log.info("number model cache built from numberRule.yml: {} entries, keys={}",
                    models.size(), models.keySet());
        } catch (IOException e) {
            log.error("failed to load numberRule.yml, pattern={}, number model cache unchanged",
                    NUMBER_RULE_RESOURCE_PATTERN, e);
        } catch (Exception e) {
            log.warn("failed to build number model cache from numberRule.yml, startup continues", e);
        }
        // 失效接入项告警（R6）：sys_number_rule 引用的 field_model_id 不在缓存中时输出告警
        try {
            warnInvalidModelIds();
        } catch (Exception e) {
            log.warn("failed to check invalid number model ids, startup continues", e);
        }
    }

    /**
     * 接入项下拉数据源（NB3-BE-003 modelOptions 接口数据源）。
     * <p>按 yml 文件出现顺序返回全量接入项（数量级小，不做分页）。</p>
     *
     * @return 接入项列表（含 key / name / modelName / modelField）
     */
    public List<SysNumberModelDo> getModelOptions() {
        return List.copyOf(localModels.values());
    }

    /**
     * 按编码读取接入项。
     *
     * @param key 接入项编码（numberRule.yml key）
     * @return 接入项；编码为空或不存在时返回 null
     */
    public SysNumberModelDo getModel(String key) {
        if (StringUtil.isEmpty(key)) {
            return null;
        }
        return localModels.get(key);
    }

    /**
     * 接入项存在性判断（save / enable 时拦截失效接入项的依据，错误语义
     * CRM_NUMBER_RULE_MODEL_NOT_FOUND 随 NB3-BE-002 收口）。
     *
     * @param key 接入项编码（numberRule.yml key）
     * @return true 缓存中存在该编码
     */
    public boolean containsModel(String key) {
        if (StringUtil.isEmpty(key)) {
            return false;
        }
        return localModels.containsKey(key);
    }

    /**
     * 失效接入项告警：查询 sys_number_rule 全部引用的 field_model_id（去重），
     * 不在接入项缓存中的输出 WARN 告警（不阻断启动、不修改数据）。
     * <p>查询失败仅记 WARN 跳过（DB 暂不可用时告警能力顺延到下次启动）。</p>
     */
    void warnInvalidModelIds() {
        List<String> ruleModelIds;
        try {
            ruleModelIds = ruleDao.selectDistinctModelIds();
        } catch (Exception e) {
            log.warn("failed to query sys_number_rule field_model_id for invalid model check, skipped", e);
            return;
        }
        for (String modelId : ruleModelIds) {
            if (!localModels.containsKey(modelId)) {
                log.warn("编号规则引用的接入项已失效：field_model_id=[{}] 不在 numberRule.yml 接入项缓存中，"
                        + "相关规则保存/启停将被拦截（CRM_NUMBER_RULE_MODEL_NOT_FOUND），存量规则取号将失败，"
                        + "请补回接入项配置或停用相关规则", modelId);
            }
        }
    }

    /**
     * 扫描 classpath 上全部 numberRule.yml 的 numberRule 段，解析合并为接入项缓存。
     * <p>多文件同 key 时后加载文件覆盖先加载（与 ModelCacheService putAll 口径一致）并告警；
     * 覆盖顺序经 LinkedHashMap 保持文件内声明顺序，供下拉稳定展示。</p>
     */
    private Map<String, SysNumberModelDo> parseNumberRuleYmls() throws IOException {
        Map<String, SysNumberModelDo> models = new LinkedHashMap<>();
        Resource[] resources = YmlResourceUtil.getResources(NUMBER_RULE_RESOURCE_PATTERN);
        for (Resource resource : resources) {
            Map<String, Object> data = YmlResourceUtil.loadYml(resource);
            Object numberRule = data.get(KEY_NUMBER_RULE);
            if (!(numberRule instanceof Map)) {
                continue;
            }
            parseEntries((Map<?, ?>) numberRule, resource.getDescription(), models);
        }
        return models;
    }

    /**
     * 解析单个 numberRule 段为接入项条目：name / modelName / modelField 必填
     * （方案 §1.1 示例结构），key 为空或属性缺失（空白视为缺失）的条目告警并跳过。
     * <p>package-private 供单元测试直接传入构造的 Map 验证必填校验与告警
     * （先例 CommonModuleService#parseModuleGroups）。</p>
     *
     * @param numberRule    numberRule 段（key → {name, modelName, modelField}）
     * @param resource      所属资源描述（仅用于告警日志定位）
     * @param models        合并目标缓存（原位写入）
     */
    void parseEntries(Map<?, ?> numberRule, String resource, Map<String, SysNumberModelDo> models) {
        for (Map.Entry<?, ?> entry : numberRule.entrySet()) {
            String key = StringUtil.trimToNull(String.valueOf(entry.getKey()));
            if (StringUtil.isEmpty(key)) {
                log.warn("number model entry skipped, key is required: resource={}", resource);
                continue;
            }
            Object body = entry.getValue();
            if (!(body instanceof Map)) {
                log.warn("number model entry skipped, name/modelName/modelField are required: key={}, resource={}",
                        key, resource);
                continue;
            }
            Map<?, ?> entryBody = (Map<?, ?>) body;
            String name = StringUtil.trimToNull(textOf(entryBody.get(KEY_NAME)));
            String modelName = StringUtil.trimToNull(textOf(entryBody.get(KEY_MODEL_NAME)));
            String modelField = StringUtil.trimToNull(textOf(entryBody.get(KEY_MODEL_FIELD)));
            if (null == name || null == modelName || null == modelField) {
                log.warn("number model entry skipped, name/modelName/modelField are required: "
                                + "key={}, name={}, modelName={}, modelField={}, resource={}",
                        key, name, modelName, modelField, resource);
                continue;
            }
            if (models.containsKey(key)) {
                log.warn("duplicate number model key [{}], later entry wins: resource={}", key, resource);
            }
            SysNumberModelDo model = new SysNumberModelDo();
            model.setKey(key);
            model.setName(name);
            model.setModelName(modelName);
            model.setModelField(modelField);
            models.put(key, model);
        }
    }

    private static String textOf(Object value) {
        return null == value ? null : String.valueOf(value);
    }
}
