package com.eric.base.common.module.service;

import com.eric.base.common.base.service.BaseService;
import com.eric.base.common.module.annotation.ModuleGroup;
import com.eric.base.common.module.dao.CommonModuleDao;
import com.eric.base.common.module.model.CommonModule;
import com.eric.base.common.module.properties.CommonModuleProperties;
import com.eric.base.common.utils.IDUtil;
import com.eric.base.common.utils.StringUtil;
import com.eric.base.common.utils.YmlResourceUtil;
import com.eric.base.sys.log.enums.SysLogResult;
import com.eric.base.sys.log.enums.SysLogType;
import com.eric.base.sys.log.service.LogRecord;
import com.eric.base.sys.log.service.LogService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 模块组开关状态业务服务
 *
 * @author Eric
 * @date 2026/11/12
 * @description 承载「模块组（module-group）」的启动期维护与管理面业务能力：
 *              <ul>
 *              <li>启动时扫描各模块 moduleGroup.yml 的 module-group 段，将模块组增量同步到
 *                  common_module_group（{@link #syncModules} 幂等三边界，见
 *                  docs/v1_1-common/05-增加接口开关/接口开关机制设计方案-修订稿.md 第 6 节）；</li>
 *              <li>同步后经 {@link RequestMappingHandlerMapping} 汇总全部 @ModuleGroup 注解 key
 *                  做启动期校验（方法级优先、类级回退），未知 key 仅记 WARN，不阻断启动；</li>
 *              <li>最后调 {@link ModuleGroupCacheService#refresh()} 全量预热状态缓存；</li>
 *              <li>管理面接口（模块组列表 / 启停，入口见 CommonModuleController）的业务逻辑：
 *                  {@link #listModules()} 全量查询、{@link #updateEnable} 启停并刷新缓存广播，
 *                  切换属低频敏感操作，经 {@link LogService} 记录操作日志（action 用
 *                  {@link CommonModuleProperties#ACTION_ENABLE}，日志失败不影响主流程）。</li>
 *              </ul>
 *              幂等三边界（机制成立前提）：
 *              <ol>
 *              <li>库中不存在该 key → 插入，field_enable 默认开启；</li>
 *              <li>已存在 → 只回写 name / remark（经 batchUpdateMeta 通道），field_enable
 *                  一律不动——否则管理员关闭后重启即被 yml 重置，机制失效；</li>
 *              <li>yml 中移除的 key → 库中记录保留，不删除、不改状态，可人工清理。</li>
 *              </ol>
 */
@Service
public class CommonModuleService extends BaseService<CommonModule, CommonModuleDao> {

    private static final Logger log = LoggerFactory.getLogger(CommonModuleService.class);

    /** moduleGroup.yml 资源扫描路径（提供 module-group 段） */
    private static final String MODULE_GROUP_RESOURCE_PATTERN = "classpath*:properties/**/moduleGroup.yml";

    private static final String KEY_MODULE_GROUP = "module-group";
    private static final String KEY_NAME = "name";
    private static final String KEY_REMARK = "remark";

    /** 操作日志 module 取值（sys_log_operation.field_module） */
    private static final String LOG_MODULE = "COMMON-MODULE";

    private static final ObjectMapper LOG_JSON_MAPPER = new ObjectMapper();

    private final ModuleGroupCacheService moduleGroupCacheService;
    private final RequestMappingHandlerMapping requestMappingHandlerMapping;
    private final LogService logService;

    public CommonModuleService(CommonModuleDao baseDao,
                               ModuleGroupCacheService moduleGroupCacheService,
                               RequestMappingHandlerMapping requestMappingHandlerMapping,
                               LogService logService) {
        super(baseDao);
        this.moduleGroupCacheService = moduleGroupCacheService;
        this.requestMappingHandlerMapping = requestMappingHandlerMapping;
        this.logService = logService;
    }

    /**
     * 模块组列表（管理面）：全量返回，按 field_key 排序（SQL 固定排序）。
     * <p>模块组量极小，不做分页裁剪；metadata.total 由调用方按需携带。</p>
     */
    public List<CommonModule> listModules() {
        return getBaseDao().selectAll();
    }

    /**
     * 启停用模块组（管理面 /enable 通道）：更新 field_enable 后刷新本实例状态缓存，
     * 并经 Redis 广播失效通知其他实例（best-effort，失败依赖 TTL 收敛，决策 D1）。
     * <p>校验与影响行数口径同构 {@code SysOrgElementService#updateEnable}：
     * fieldId 为空 / 目标状态缺失抛 {@link IllegalArgumentException}，
     * 记录不存在抛 {@link IllegalArgumentException}，影响行数非 1 抛 {@link IllegalStateException}。</p>
     * <p>操作日志：成功记 SUCCESS（变更前后启用状态写入 before / after），失败记 FAILED 及失败原因；
     * 日志记录失败仅记 WARN，绝不影响主流程。</p>
     *
     * @param fieldId     模块组 ID（必填）
     * @param fieldEnable 目标状态 true 开启 / false 关闭（必填）
     */
    @Transactional
    public void updateEnable(String fieldId, Boolean fieldEnable) {
        if (StringUtil.isEmpty(fieldId)) {
            throw new IllegalArgumentException("fieldId must not be blank");
        }
        if (null == fieldEnable) {
            throw new IllegalArgumentException("fieldEnable must not be null");
        }
        String documentId = fieldId.trim();
        CommonModule before = getBaseDao().selectById(documentId);
        if (null == before) {
            throw new IllegalArgumentException("模块组不存在或已删除");
        }
        try {
            int affected = getBaseDao().updateEnable(documentId, fieldEnable);
            if (affected != 1) {
                throw new IllegalStateException("updateEnable expected one affected row but got " + affected);
            }
            moduleGroupCacheService.refreshAndBroadcast();
            recordEnableLog(documentId, before.getFieldEnable(), fieldEnable,
                    SysLogResult.SUCCESS.getValue(), null);
        } catch (RuntimeException e) {
            recordEnableLog(documentId, before.getFieldEnable(), fieldEnable,
                    SysLogResult.FAILED.getValue(), e.getMessage());
            throw e;
        }
    }

    /**
     * 记录模块组启停操作日志：logType 用操作日志，action 用 {@link CommonModuleProperties#ACTION_ENABLE}，
     * businessId 为被操作模块组 ID，before / after 记录切换前后的启用状态。
     * <p>日志记录失败仅记 WARN（{@code LogService.record} 对非法参数抛
     * {@link IllegalArgumentException}），绝不影响主流程。</p>
     */
    private void recordEnableLog(String documentId, Boolean beforeEnable, Boolean afterEnable,
                                 String result, String failReason) {
        try {
            LogRecord record = new LogRecord();
            record.setLogType(SysLogType.OPERATION.getValue());
            record.setModule(LOG_MODULE);
            record.setAction(CommonModuleProperties.ACTION_ENABLE);
            record.setBusinessId(documentId);
            record.setBefore(enableStateJson(beforeEnable));
            record.setAfter(enableStateJson(afterEnable));
            record.setResult(result);
            record.setFailReason(failReason);
            logService.record(record);
        } catch (Exception e) {
            log.warn("failed to record module group enable log, businessId={}", documentId, e);
        }
    }

    /**
     * 启用状态的变更记录 JSON（{@code {"fieldEnable":true|false}}）：经 Jackson 序列化，
     * 空值原样返回 null
     */
    private static String enableStateJson(Boolean fieldEnable) {
        if (null == fieldEnable) {
            return null;
        }
        try {
            return LOG_JSON_MAPPER.writeValueAsString(Map.of("fieldEnable", fieldEnable));
        } catch (JsonProcessingException e) {
            return String.valueOf(fieldEnable);
        }
    }

    /**
     * 系统启动任务（第 10 个）：「同步 → 注解校验 → 缓存预热」全链路入口，
     * SysStartupTask 只调本方法（与既有任务一致）。
     * <p>同步/校验/预热各失败路径只记日志不抛出：启动任务失败不应阻断应用启动
     * （DB 不可用时记 WARN 并继续，缓存预热由 {@link ModuleGroupCacheService#refresh}
     * 内部 fail-open；注解 key 校验在运行期按「开启」放行兜底）。</p>
     */
    public void syncFromYml() {
        // 一、解析 moduleGroup.yml 并按幂等三边界增量同步
        try {
            List<CommonModule> modules = parseModuleGroupYmls();
            syncModules(modules);
            log.info("module groups synced from moduleGroup.yml: {} entries", modules.size());
        } catch (IOException e) {
            log.error("failed to load moduleGroup.yml, pattern={}, module group sync skipped", MODULE_GROUP_RESOURCE_PATTERN, e);
        } catch (Exception e) {
            log.warn("failed to sync module groups from moduleGroup.yml, module group sync skipped (startup continues)", e);
        }
        // 二、启动期注解校验：未知 key 仅 WARN，不阻断启动
        try {
            validateModuleGroupAnnotations();
        } catch (Exception e) {
            log.warn("failed to validate @ModuleGroup annotations, validation skipped (startup continues)", e);
        }
        // 三、缓存全量预热（refresh 内部 fail-open，不抛出）
        try {
            moduleGroupCacheService.refresh();
        } catch (Exception e) {
            log.warn("failed to warm up module group cache, warm-up skipped (startup continues)", e);
        }
    }

    /**
     * 幂等三边界同步核心：不存在 → 插入（field_enable 默认开启）；
     * 已存在 → 只回写 name / remark（batchUpdateMeta 通道，field_enable 一律不动）；
     * yml 移除的 key 不在本方法处理范围内 → 库中保留（不删除、不改状态）。
     * <p>package-private 供单元测试直接传入解析结果，避免依赖 classpath 上
     * moduleGroup.yml 的实际数量（先例：CommonSettingService.syncSetting）。</p>
     */
    void syncModules(List<CommonModule> modules) {
        if (null == modules || modules.isEmpty()) {
            return;
        }
        List<CommonModule> toInsert = new ArrayList<>();
        List<CommonModule> toUpdateMeta = new ArrayList<>();
        for (CommonModule module : modules) {
            CommonModule exist = getBaseDao().selectByKey(module.getFieldKey());
            if (null == exist) {
                // 幂等边界 1：不存在 → 插入，field_enable 默认开启
                module.setFieldId(IDUtil.initID());
                module.setFieldEnable(Boolean.TRUE);
                toInsert.add(module);
            } else {
                // 幂等边界 2：已存在 → 只回写元信息，field_enable 一律不动（不得经 update / updateEnable 通道）
                toUpdateMeta.add(module);
            }
        }
        for (CommonModule module : toInsert) {
            insert(module);
            log.info("module group inserted: key={}, name={}", module.getFieldKey(), module.getFieldName());
        }
        if (!toUpdateMeta.isEmpty()) {
            // 幂等边界 2 的实现通道：batchUpdateMeta 仅回写 field_name / field_remark，绝不动 field_enable
            getBaseDao().batchUpdateMeta(toUpdateMeta);
            log.info("module group meta updated: {} entries", toUpdateMeta.size());
        }
    }

    /**
     * 启动期注解校验：汇总全部 HandlerMethod 上的 @ModuleGroup key（方法级优先、类级回退，去重），
     * 用同步后的 DB 全量比对（不依赖缓存），未知 key 记 WARN（含非法 key 值），不抛异常。
     */
    void validateModuleGroupAnnotations() {
        Set<String> annotatedKeys = collectModuleGroupKeys();
        if (annotatedKeys.isEmpty()) {
            return;
        }
        Map<String, CommonModule> modulesByKey = new HashMap<>();
        for (CommonModule module : getBaseDao().selectAll()) {
            if (StringUtil.isNotEmpty(module.getFieldKey())) {
                modulesByKey.put(module.getFieldKey(), module);
            }
        }
        for (String key : annotatedKeys) {
            if (!modulesByKey.containsKey(key)) {
                log.warn("module group key [{}] referenced by @ModuleGroup not found in common_module_group, "
                        + "requests will pass through as enabled (fail-open); check moduleGroup.yml entries or annotation spelling", key);
            }
        }
    }

    /**
     * 汇总全部 HandlerMethod 上的 @ModuleGroup 注解 key：方法级优先、类级回退，去重后返回。
     * <p>与 ModuleGroupInterceptor（MOD2-BE-004）的注解读取规则保持一致。</p>
     */
    Set<String> collectModuleGroupKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (HandlerMethod handlerMethod : requestMappingHandlerMapping.getHandlerMethods().values()) {
            ModuleGroup moduleGroup = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), ModuleGroup.class);
            if (null == moduleGroup) {
                moduleGroup = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), ModuleGroup.class);
            }
            if (null == moduleGroup) {
                continue;
            }
            if (StringUtil.isEmpty(moduleGroup.moduleGroupKey())) {
                log.warn("@ModuleGroup annotation with blank moduleGroupKey on {}, skipped", handlerMethod);
                continue;
            }
            keys.add(moduleGroup.moduleGroupKey());
        }
        return keys;
    }

    /**
     * 扫描 classpath 上全部 moduleGroup.yml 的 module-group 段，解析为条目列表
     * （key + name + remark；field_enable 不在此预设，由同步核心按边界写入）。
     */
    private List<CommonModule> parseModuleGroupYmls() throws IOException {
        List<CommonModule> modules = new ArrayList<>();
        Resource[] resources = YmlResourceUtil.getResources(MODULE_GROUP_RESOURCE_PATTERN);
        for (Resource resource : resources) {
            Map<String, Object> data = YmlResourceUtil.loadYml(resource);
            Object moduleGroup = data.get(KEY_MODULE_GROUP);
            if (!(moduleGroup instanceof Map)) {
                continue;
            }
            modules.addAll(parseModuleGroups((Map<?, ?>) moduleGroup, resource.getDescription()));
        }
        return modules;
    }

    /**
     * 解析单个 module-group 段为条目列表：name / remark 必填（修订稿 5.1 节），
     * key 为空或 name / remark 缺失（空白视为缺失）的条目告警并跳过。
     * <p>package-private 供单元测试直接传入构造的 Map 验证必填校验与告警。</p>
     *
     * @param moduleGroups        module-group 段（key → {name, remark}）
     * @param moduleGroupResource 所属资源描述（仅用于告警日志定位）
     * @return 通过校验的条目列表
     */
    List<CommonModule> parseModuleGroups(Map<?, ?> moduleGroups, String moduleGroupResource) {
        List<CommonModule> modules = new ArrayList<>();
        for (Map.Entry<?, ?> entry : moduleGroups.entrySet()) {
            String fieldKey = StringUtil.trimToNull(String.valueOf(entry.getKey()));
            if (StringUtil.isEmpty(fieldKey)) {
                log.warn("module group entry skipped, key is required: resource={}", moduleGroupResource);
                continue;
            }
            Object body = entry.getValue();
            if (!(body instanceof Map)) {
                log.warn("module group entry skipped, name and remark are required: key={}, resource={}",
                        fieldKey, moduleGroupResource);
                continue;
            }
            Map<?, ?> groupBody = (Map<?, ?>) body;
            String fieldName = StringUtil.trimToNull(textOf(groupBody.get(KEY_NAME)));
            String fieldRemark = StringUtil.trimToNull(textOf(groupBody.get(KEY_REMARK)));
            if (null == fieldName || null == fieldRemark) {
                log.warn("module group entry skipped, name and remark are required: key={}, name={}, remark={}, resource={}",
                        fieldKey, fieldName, fieldRemark, moduleGroupResource);
                continue;
            }
            CommonModule module = new CommonModule();
            module.setFieldKey(fieldKey);
            module.setFieldName(fieldName);
            module.setFieldRemark(fieldRemark);
            modules.add(module);
        }
        return modules;
    }

    private static String textOf(Object value) {
        return null == value ? null : String.valueOf(value);
    }
}
