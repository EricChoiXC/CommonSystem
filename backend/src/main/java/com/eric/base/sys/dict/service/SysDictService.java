package com.eric.base.sys.dict.service;

import com.eric.base.common.base.service.BaseService;
import com.eric.base.common.base.service.ImportRowHandler;
import com.eric.base.common.utils.StringUtil;
import com.eric.base.common.utils.YmlResourceUtil;
import com.eric.base.sys.dict.dao.SysDictItemDao;
import com.eric.base.sys.dict.dao.SysDictTypeDao;
import com.eric.base.sys.dict.model.SysDictItem;
import com.eric.base.sys.dict.model.SysDictType;
import com.eric.base.sys.dict.request.SysDictItemImportVo;
import com.eric.base.sys.dict.response.SysDictItemExportVo;
import com.eric.base.sys.dict.response.SysDictItemVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 码值业务服务
 *
 * @author Eric
 * @date 2026/9/2
 * @description 承载码值模块核心业务（依据 artificial/docs/设计文档/sys/dict/码值功能核心业务.md）：
 *              <ul>
 *              <li>类型列表（按 field_code 排序，不可维护）与码值项列表（按 field_value 排序）；</li>
 *              <li>码值项维护（新增/编辑/删除）：仅非系统码值（field_system = false）可维护，
 *                  人工新增固定 field_system = false，同类型下码值唯一；</li>
 *              <li>通过类型编号获取码值项（供前端和其他模块服务快速使用）；</li>
 *              <li>启动时读取 properties/{MODEL}/dict.yml 增量导入系统码值（field_system = true）。</li>
 *              </ul>
 */
@Service
public class SysDictService extends BaseService<SysDictType, SysDictTypeDao> {

    private static final Logger log = LoggerFactory.getLogger(SysDictService.class);

    /** dict.yml 资源扫描路径（与 application.yml 同目录） */
    private static final String DICT_RESOURCE_PATTERN = "classpath*:properties/**/dict.yml";

    private static final String KEY_DICT = "dict";
    private static final String KEY_NAME = "name";
    private static final String KEY_DESC = "desc";

    private final SysDictItemDao itemDao;

    public SysDictService(SysDictTypeDao baseDao, SysDictItemDao itemDao) {
        super(baseDao);
        this.itemDao = itemDao;
    }

    /* ==================== 查询 ==================== */

    /**
     * 码值类型列表：按 field_code 升序，供左侧类型列表展示（前端按关键字过滤名称/编号）。
     */
    public List<SysDictType> typeList() {
        return getBaseDao().selectAll();
    }

    /**
     * 指定类型下的码值项列表：按 field_value 升序，供右侧码值项列表展示。
     *
     * @param fieldTypeId 码值类型 id
     */
    public List<SysDictItem> itemList(String fieldTypeId) {
        return itemDao.selectByTypeId(requireText(fieldTypeId, "fieldTypeId"));
    }

    /**
     * 通过类型编号获取该类型下已启用的码值项（供前端和其他模块服务快速使用）。
     *
     * @param fieldCode 码值类型编号（如 yes_no）
     * @return 启用码值项列表（按 field_value 升序）；类型不存在时返回空列表
     */
    public List<SysDictItemVo> itemsByTypeCode(String fieldCode) {
        String code = requireText(fieldCode, "fieldCode");
        if (null == getBaseDao().selectByCode(code)) {
            return List.of();
        }
        return itemDao.selectItemsByTypeCode(code);
    }

    /* ==================== 导入导出（公共通道，IE4-BE-001） ==================== */

    /** yes_no 字典（common/dict.yml）的码值：1=是 / 0=否，field_enable 列转换用 */
    private static final String ENABLE_CODE_YES = "1";
    private static final String ENABLE_CODE_NO = "0";
    private static final String ENABLE_NAME_YES = "是";
    private static final String ENABLE_NAME_NO = "否";

    /**
     * 导出转换器：码值项实体列表 → 导出 VO 列表（BaseCrudController 公共导出通道）。
     * <p>类型列写「类型名称」（人可读 + round-trip 按名称定位类型，见
     * {@link #typeIdByName}）；field_enable 写 yes_no 码值（"1"/"0"），
     * 显示值（是/否）转换由公共层 {@code @Export(dict = "yes_no")} 统一完成。</p>
     *
     * @return 转换函数（导出行实体列表 → 导出 VO 列表）
     */
    public Function<List<SysDictItem>, List<SysDictItemExportVo>> exportConverter() {
        return this::toExportVos;
    }

    /**
     * 导入行级业务校验（ImportRowHandler，公共导入第 4 步模块承接）：
     * @Export(required) 非空与 yes_no 码值回转已由公共层完成，此处按既有 saveItem
     * 同款口径校验模块业务规则——类型存在且启用 + 同类型下码值唯一（既有 save
     * 仅这两条业务规则，不再新增）。
     *
     * @return 校验不通过的消息；通过返回 null
     */
    public ImportRowHandler<SysDictItemImportVo> importRowHandler() {
        return this::validateImportRow;
    }

    /**
     * 导入 VO → 行实体映射（BaseCrudController 公共导入通道 toEntity 参数）：
     * 仅做列值搬运；field_id 由落库通道生成、field_system 由落库通道固定 false
     * （见 SysDictItemService#insert，saveItem 硬性规则）。
     *
     * @return 转换函数（导入 VO → 码值项实体）
     */
    public Function<SysDictItemImportVo, SysDictItem> importToEntity() {
        return this::toItemEntity;
    }

    /** 实体列表 → 导出 VO 列表（类型名称按 distinct 类型逐个回查） */
    private List<SysDictItemExportVo> toExportVos(List<SysDictItem> items) {
        List<SysDictItemExportVo> rows = new ArrayList<>(null == items ? 0 : items.size());
        if (null == items) {
            return rows;
        }
        Map<String, String> typeNames = typeNamesOf(items);
        for (SysDictItem item : items) {
            if (null == item) {
                continue;
            }
            SysDictItemExportVo row = new SysDictItemExportVo();
            row.setTypeName(typeNames.get(item.getFieldTypeId()));
            row.setFieldName(item.getFieldName());
            row.setFieldValue(item.getFieldValue());
            row.setFieldEnable(enableCode(item.getFieldEnable()));
            rows.add(row);
        }
        return rows;
    }

    /** 码值项类型名称映射（field_type_id → field_name）：已回查过的类型跳过 */
    private Map<String, String> typeNamesOf(List<SysDictItem> items) {
        Map<String, String> names = new HashMap<>();
        for (SysDictItem item : items) {
            String typeId = null == item ? null : item.getFieldTypeId();
            if (null == typeId || typeId.isEmpty() || names.containsKey(typeId)) {
                continue;
            }
            SysDictType type = getBaseDao().selectById(typeId);
            if (null != type) {
                names.put(typeId, type.getFieldName());
            }
        }
        return names;
    }

    /** 行级校验：类型存在且启用 + 同类型下码值唯一（saveItem 同款口径；事务内可查见本批已插行，文件内重复码值同样命中） */
    private String validateImportRow(SysDictItemImportVo row, int rowNo) {
        String typeId = typeIdByName(row.getTypeName());
        if (null == typeId) {
            return "类型名称不存在或未启用: " + row.getTypeName();
        }
        SysDictItem exist = itemDao.selectByTypeIdAndValue(typeId, row.getFieldValue());
        if (null != exist) {
            return "该类型下已存在相同码值: " + row.getFieldValue();
        }
        return null;
    }

    /** 导入 VO → 码值项实体（列值搬运；类型在行级校验已确认存在，此处兜底拒绝） */
    private SysDictItem toItemEntity(SysDictItemImportVo row) {
        String typeId = typeIdByName(row.getTypeName());
        if (null == typeId) {
            throw new IllegalArgumentException("类型名称不存在或未启用: " + row.getTypeName());
        }
        SysDictItem item = new SysDictItem();
        item.setFieldTypeId(typeId);
        item.setFieldName(row.getFieldName());
        item.setFieldValue(row.getFieldValue());
        item.setFieldEnable(enableBoolean(row.getFieldEnable()));
        return item;
    }

    /** 类型名称 → 类型 ID：精确匹配 field_name 且已启用；不存在或未启用返回 null */
    private String typeIdByName(String typeName) {
        if (StringUtil.isEmpty(typeName)) {
            return null;
        }
        for (SysDictType type : getBaseDao().selectAll()) {
            if (typeName.equals(type.getFieldName()) && Boolean.TRUE.equals(type.getFieldEnable())) {
                return type.getFieldId();
            }
        }
        return null;
    }

    /** field_enable 布尔 → yes_no 码值（"1"/"0"；null 防御写空串，列内容不缺列） */
    private static String enableCode(Boolean enable) {
        if (null == enable) {
            return "";
        }
        return Boolean.TRUE.equals(enable) ? ENABLE_CODE_YES : ENABLE_CODE_NO;
    }

    /** 启用列导入归一："0"/「否」→ false；"1"/「是」/空/其它 → true（既有 enable 口径：非「否」一律启用） */
    private static boolean enableBoolean(String value) {
        String text = null == value ? "" : value.trim();
        return !ENABLE_CODE_NO.equals(text) && !ENABLE_NAME_NO.equals(text);
    }

    /* ==================== 码值项维护（仅 SYS_DICT_ADMIN，非系统码值） ==================== */

    /**
     * 新增码值项：人工新增固定 field_system = false，同类型下码值唯一。
     *
     * @param item 码值项（fieldTypeId / fieldValue / fieldName / fieldEnable）
     * @return 保存后的码值项
     */
    @Transactional
    public SysDictItem saveItem(SysDictItem item) {
        requireItemBase(item);
        requireTypeExists(item.getFieldTypeId());
        requireValueUnique(item.getFieldTypeId(), item.getFieldValue(), null);
        item.initFieldId();
        // 人工新增的码值固定 field_system = false（文档硬性规则，不接受入参覆盖）
        item.setFieldSystem(false);
        if (null == item.getFieldEnable()) {
            item.setFieldEnable(true);
        }
        itemDao.insert(item);
        return item;
    }

    /**
     * 编辑码值项：field_system = true 的系统码值不可维护。
     *
     * @param item 码值项（fieldId 必填；fieldValue / fieldName / fieldEnable 传空表示不修改）
     * @return 更新后的码值项
     */
    @Transactional
    public SysDictItem updateItem(SysDictItem item) {
        String id = requireText(null == item ? null : item.getFieldId(), "fieldId");
        SysDictItem exist = itemDao.selectById(id);
        if (null == exist) {
            throw new IllegalArgumentException("码值项不存在");
        }
        requireNotSystem(exist);
        if (StringUtil.isEmpty(item.getFieldValue()) && StringUtil.isEmpty(item.getFieldName())
                && null == item.getFieldEnable()) {
            throw new IllegalArgumentException("没有需要修改的内容");
        }
        String targetTypeId = StringUtil.isEmpty(item.getFieldTypeId()) ? exist.getFieldTypeId() : item.getFieldTypeId();
        requireTypeExists(targetTypeId);
        if (StringUtil.isNotEmpty(item.getFieldValue())) {
            requireValueUnique(targetTypeId, item.getFieldValue(), id);
        }
        SysDictItem update = new SysDictItem();
        update.setFieldId(id);
        update.setFieldTypeId(StringUtil.isEmpty(item.getFieldTypeId()) ? null : item.getFieldTypeId());
        update.setFieldValue(StringUtil.isEmpty(item.getFieldValue()) ? null : item.getFieldValue());
        update.setFieldName(StringUtil.isEmpty(item.getFieldName()) ? null : item.getFieldName());
        update.setFieldEnable(item.getFieldEnable());
        // 编辑不改变 field_system 归属：系统码值在此前已被拦截，人工码值保持 false
        itemDao.update(update);
        return itemDao.selectById(id);
    }

    /**
     * 删除码值项：field_system = true 的系统码值不可删除。
     *
     * @param fieldId 码值项 id
     */
    @Transactional
    public void deleteItem(String fieldId) {
        String id = requireText(fieldId, "fieldId");
        SysDictItem exist = itemDao.selectById(id);
        if (null == exist) {
            throw new IllegalArgumentException("码值项不存在");
        }
        requireNotSystem(exist);
        itemDao.deleteById(id);
    }

    /* ==================== 启动：dict.yml 增量导入系统码值 ==================== */

    /**
     * 系统启动任务：扫描 properties/ 下各模块 dict.yml，将系统码值增量导入数据库。
     * <p>文件统一格式（见码值功能核心业务.md）：
     * <pre>
     * dict:
     *   ${type.field_code_1}:
     *     name: ${type.field_name}
     *     desc: ${type.field_desc}
     *     ${item.field_value_1}: ${item.field_name_1}
     *     ${item.field_value_2}: ${item.field_name_2}
     * </pre>
     * 增量规则：类型不存在则创建（field_enable = true）；已存在则同步 name/desc；
     * 码值项不存在则创建并固定 field_system = true（默认启用）；
     * 已存在的系统码值仅同步显示值（不反复启用被人工停用的项）；
     * 已存在的人工码值（field_system = false）跳过，保留人工维护结果。</p>
     *
     * @return 本次处理（新增 + 更新）的记录条数
     */
    @Transactional
    public int syncDictFromYml() {
        int handled = 0;
        try {
            Resource[] resources = YmlResourceUtil.getResources(DICT_RESOURCE_PATTERN);
            for (Resource resource : resources) {
                Map<String, Object> data = YmlResourceUtil.loadYml(resource);
                Object dict = data.get(KEY_DICT);
                if (!(dict instanceof Map)) {
                    continue;
                }
                handled += syncDicts((Map<?, ?>) dict);
            }
        } catch (IOException e) {
            log.error("failed to load dict resources, pattern={}", DICT_RESOURCE_PATTERN, e);
            throw new IllegalStateException("failed to load dict resources: " + DICT_RESOURCE_PATTERN, e);
        }
        log.info("system dict synced from dict.yml: handled {} records", handled);
        return handled;
    }

    /**
     * 同步单个 dict 段（dict.yml 的 dict: 根节点）下的全部码值类型与码值项。
     * <p>package-private 供单元测试直接传入构造的 Map 验证增量规则，
     * 避免测试依赖 classpath 上 dict.yml 的实际数量（先例：CommonSettingService.syncSetting）。</p>
     *
     * @return 本次处理（新增 + 更新）的记录条数
     */
    int syncDicts(Map<?, ?> dicts) {
        int handled = 0;
        for (Map.Entry<?, ?> typeEntry : dicts.entrySet()) {
            if (!(typeEntry.getValue() instanceof Map)) {
                continue;
            }
            String typeCode = String.valueOf(typeEntry.getKey());
            if (StringUtil.isEmpty(typeCode)) {
                continue;
            }
            Map<?, ?> typeBody = (Map<?, ?>) typeEntry.getValue();
            SysDictType type = getBaseDao().selectByCode(typeCode);
            if (null == type) {
                type = createType(typeCode, typeBody);
                handled++;
            } else if (updateTypeIfChanged(type, typeBody)) {
                handled++;
            }
            handled += syncItems(type, typeBody);
        }
        return handled;
    }

    /**
     * 创建码值类型（field_enable = true）。
     */
    private SysDictType createType(String typeCode, Map<?, ?> typeBody) {
        String name = textOf(typeBody.get(KEY_NAME));
        SysDictType type = new SysDictType();
        type.initFieldId();
        type.setFieldCode(typeCode);
        type.setFieldName(StringUtil.isEmpty(name) ? typeCode : name);
        type.setFieldDesc(textOf(typeBody.get(KEY_DESC)));
        type.setFieldEnable(true);
        getBaseDao().insert(type);
        log.info("dict type inserted: code={}, name={}", typeCode, type.getFieldName());
        return type;
    }

    /**
     * 同步已存在码值类型的 name / desc（desc 缺失时不改动）。
     *
     * @return true=实际更新；false=无需处理
     */
    private boolean updateTypeIfChanged(SysDictType exist, Map<?, ?> typeBody) {
        String name = textOf(typeBody.get(KEY_NAME));
        String desc = textOf(typeBody.get(KEY_DESC));
        boolean needName = StringUtil.isNotEmpty(name) && !name.equals(exist.getFieldName());
        boolean needDesc = StringUtil.isNotEmpty(desc) && !desc.equals(exist.getFieldDesc());
        if (!needName && !needDesc) {
            return false;
        }
        SysDictType update = new SysDictType();
        update.setFieldId(exist.getFieldId());
        update.setFieldName(needName ? name : null);
        update.setFieldDesc(needDesc ? desc : null);
        update(update);
        log.info("dict type updated: code={}, name={}", exist.getFieldCode(), name);
        return true;
    }

    /**
     * 同步单个码值类型下的码值项（增量：只补缺失，人工码值不覆盖）。
     *
     * @return 本次处理（新增 + 更新）的记录条数
     */
    private int syncItems(SysDictType type, Map<?, ?> typeBody) {
        int handled = 0;
        for (Map.Entry<?, ?> itemEntry : typeBody.entrySet()) {
            String key = String.valueOf(itemEntry.getKey());
            // name / desc 为类型保留字段，其余键为码值
            if (KEY_NAME.equals(key) || KEY_DESC.equals(key)) {
                continue;
            }
            String value = key;
            String name = textOf(itemEntry.getValue());
            if (StringUtil.isEmpty(value) || StringUtil.isEmpty(name)) {
                continue;
            }
            SysDictItem exist = itemDao.selectByTypeIdAndValue(type.getFieldId(), value);
            if (null == exist) {
                SysDictItem item = new SysDictItem();
                item.initFieldId();
                item.setFieldTypeId(type.getFieldId());
                item.setFieldValue(value);
                item.setFieldName(name);
                item.setFieldEnable(true);
                item.setFieldSystem(true);
                itemDao.insert(item);
                handled++;
                continue;
            }
            // 已存在的人工码值（field_system = false）跳过，保留人工维护结果
            if (!Boolean.TRUE.equals(exist.getFieldSystem())) {
                continue;
            }
            // 已存在的系统码值仅同步显示值，不反复启用被人工停用的项
            if (!name.equals(exist.getFieldName())) {
                SysDictItem update = new SysDictItem();
                update.setFieldId(exist.getFieldId());
                update.setFieldName(name);
                itemDao.update(update);
                handled++;
            }
        }
        return handled;
    }

    /* ==================== 私有校验 ==================== */

    /**
     * 新增/编辑公共基础校验：fieldTypeId / fieldValue / fieldName 非空。
     */
    private void requireItemBase(SysDictItem item) {
        if (null == item) {
            throw new IllegalArgumentException("码值项不能为空");
        }
        requireText(item.getFieldTypeId(), "fieldTypeId");
        requireText(item.getFieldValue(), "fieldValue");
        requireText(item.getFieldName(), "fieldName");
    }

    /**
     * 校验码值类型存在且启用。
     */
    private void requireTypeExists(String fieldTypeId) {
        String typeId = requireText(fieldTypeId, "fieldTypeId");
        SysDictType type = getBaseDao().selectById(typeId);
        if (null == type) {
            throw new IllegalArgumentException("码值类型不存在");
        }
        if (!Boolean.TRUE.equals(type.getFieldEnable())) {
            throw new IllegalArgumentException("码值类型未启用，无法维护码值项");
        }
    }

    /**
     * 校验同类型下码值唯一（编辑时排除自身）。
     */
    private void requireValueUnique(String fieldTypeId, String fieldValue, String excludeId) {
        SysDictItem exist = itemDao.selectByTypeIdAndValue(fieldTypeId, fieldValue);
        if (null != exist && !exist.getFieldId().equals(excludeId)) {
            throw new IllegalArgumentException("该类型下已存在相同码值: " + fieldValue);
        }
    }

    /**
     * 系统码值不可维护：field_system = true 时拒绝编辑/删除。
     */
    private void requireNotSystem(SysDictItem item) {
        if (Boolean.TRUE.equals(item.getFieldSystem())) {
            throw new IllegalArgumentException("系统码值不可维护");
        }
    }

    private String requireText(String value, String fieldName) {
        if (StringUtil.isEmpty(value)) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value.trim();
    }

    private static String textOf(Object value) {
        return null == value ? null : String.valueOf(value).trim();
    }
}
