package com.eric.base.sys.dict.service;

import com.eric.base.sys.dict.dao.SysDictItemDao;
import com.eric.base.sys.dict.dao.SysDictTypeDao;
import com.eric.base.sys.dict.model.SysDictItem;
import com.eric.base.sys.dict.model.SysDictType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 码值业务服务测试
 *
 * @author Eric
 * @date 2026/9/2
 * @description 覆盖码值项维护规则（人工新增固定 field_system=false、同类型码值唯一、
 *              系统码值不可维护）、通过类型编号获取码值项，以及启动 dict.yml 增量导入
 *              （类型/码值项新增、系统码值仅同步显示值、人工码值不被覆盖）。
 */
class SysDictServiceTest {

    private SysDictTypeDao typeDao;
    private SysDictItemDao itemDao;
    private SysDictService service;

    /** 32 位合法主键（BaseModel.setFieldId 会对非 32 位 id 重新生成） */
    private static final String ITEM_ID = "item" + "0".repeat(28);

    @BeforeEach
    void setUp() {
        typeDao = mock(SysDictTypeDao.class);
        itemDao = mock(SysDictItemDao.class);
        service = new SysDictService(typeDao, itemDao);
    }

    /* ==================== 码值项新增 ==================== */

    @Test
    void insertsManualItemWithSystemFalse() {
        SysDictType type = new SysDictType();
        type.setFieldId("type-1");
        type.setFieldEnable(true);
        when(typeDao.selectById("type-1")).thenReturn(type);
        when(itemDao.selectByTypeIdAndValue("type-1", "active")).thenReturn(null);
        when(itemDao.insert(any(SysDictItem.class))).thenReturn(1);

        SysDictItem item = new SysDictItem();
        item.setFieldTypeId("type-1");
        item.setFieldValue("active");
        item.setFieldName("启用");
        SysDictItem saved = service.saveItem(item);

        assertEquals("active", saved.getFieldValue());
        // 人工新增的码值固定 field_system = false，不接受入参覆盖
        assertFalse(saved.getFieldSystem());
        assertTrue(saved.getFieldEnable());
        assertEquals(32, saved.getFieldId().length());
        verify(itemDao).insert(argThat(inserted ->
                "type-1".equals(inserted.getFieldTypeId())
                        && "active".equals(inserted.getFieldValue())
                        && Boolean.FALSE.equals(inserted.getFieldSystem())));
    }

    @Test
    void rejectsItemWhenTypeMissing() {
        when(typeDao.selectById("type-1")).thenReturn(null);

        SysDictItem item = new SysDictItem();
        item.setFieldTypeId("type-1");
        item.setFieldValue("active");
        item.setFieldName("启用");

        assertThrows(IllegalArgumentException.class, () -> service.saveItem(item));
        verify(itemDao, never()).insert(any(SysDictItem.class));
    }

    @Test
    void rejectsDuplicateValueInSameType() {
        SysDictType type = new SysDictType();
        type.setFieldId("type-1");
        type.setFieldEnable(true);
        when(typeDao.selectById("type-1")).thenReturn(type);
        SysDictItem exist = new SysDictItem();
        exist.setFieldId("item-1");
        when(itemDao.selectByTypeIdAndValue("type-1", "active")).thenReturn(exist);

        SysDictItem item = new SysDictItem();
        item.setFieldTypeId("type-1");
        item.setFieldValue("active");
        item.setFieldName("启用");

        assertThrows(IllegalArgumentException.class, () -> service.saveItem(item));
        verify(itemDao, never()).insert(any(SysDictItem.class));
    }

    /* ==================== 码值项编辑 ==================== */

    @Test
    void rejectsUpdatingSystemItem() {
        SysDictItem exist = new SysDictItem();
        exist.setFieldId(ITEM_ID);
        exist.setFieldSystem(true);
        when(itemDao.selectById(ITEM_ID)).thenReturn(exist);

        SysDictItem item = new SysDictItem();
        item.setFieldId(ITEM_ID);
        item.setFieldName("改名");

        assertThrows(IllegalArgumentException.class, () -> service.updateItem(item));
        verify(itemDao, never()).update(any(SysDictItem.class));
    }

    @Test
    void updatesManualItemKeepingSystemFalse() {
        SysDictItem exist = new SysDictItem();
        exist.setFieldId(ITEM_ID);
        exist.setFieldTypeId("type-1");
        exist.setFieldSystem(false);
        when(itemDao.selectById(ITEM_ID)).thenReturn(exist);
        SysDictType type = new SysDictType();
        type.setFieldId("type-1");
        type.setFieldEnable(true);
        when(typeDao.selectById("type-1")).thenReturn(type);
        when(itemDao.selectByTypeIdAndValue("type-1", "active")).thenReturn(exist);
        SysDictItem updated = new SysDictItem();
        updated.setFieldId(ITEM_ID);
        updated.setFieldSystem(false);
        when(itemDao.selectById(ITEM_ID)).thenReturn(exist, updated);
        when(itemDao.update(any(SysDictItem.class))).thenReturn(1);

        SysDictItem item = new SysDictItem();
        item.setFieldId(ITEM_ID);
        item.setFieldValue("active");
        item.setFieldName("启用中");
        item.setFieldEnable(true);
        SysDictItem result = service.updateItem(item);

        assertEquals(ITEM_ID, result.getFieldId());
        verify(itemDao).update(argThat(update ->
                ITEM_ID.equals(update.getFieldId())
                        && "启用中".equals(update.getFieldName())
                        && "active".equals(update.getFieldValue())
                        && null == update.getFieldSystem()));
    }

    /* ==================== 码值项删除 ==================== */

    @Test
    void rejectsDeletingSystemItem() {
        SysDictItem exist = new SysDictItem();
        exist.setFieldId("item-1");
        exist.setFieldSystem(true);
        when(itemDao.selectById("item-1")).thenReturn(exist);

        assertThrows(IllegalArgumentException.class, () -> service.deleteItem("item-1"));
        verify(itemDao, never()).deleteById(anyString());
    }

    @Test
    void deletesManualItem() {
        SysDictItem exist = new SysDictItem();
        exist.setFieldId("item-1");
        exist.setFieldSystem(false);
        when(itemDao.selectById("item-1")).thenReturn(exist);
        when(itemDao.deleteById("item-1")).thenReturn(1);

        service.deleteItem("item-1");

        verify(itemDao).deleteById("item-1");
    }

    /* ==================== 通过类型编号获取码值项 ==================== */

    @Test
    void returnsEmptyWhenTypeCodeMissing() {
        when(typeDao.selectByCode("not_exist")).thenReturn(null);

        assertEquals(Collections.emptyList(), service.itemsByTypeCode("not_exist"));
        verify(itemDao, never()).selectItemsByTypeCode(anyString());
    }

    @Test
    void returnsItemsByTypeCode() {
        SysDictType type = new SysDictType();
        type.setFieldId("type-1");
        when(typeDao.selectByCode("yes_no")).thenReturn(type);
        when(itemDao.selectItemsByTypeCode("yes_no")).thenReturn(Collections.emptyList());

        assertEquals(Collections.emptyList(), service.itemsByTypeCode("yes_no"));
        verify(itemDao).selectItemsByTypeCode("yes_no");
    }

    /* ==================== 启动 dict.yml 增量导入 ==================== */

    @Test
    void syncsDictFromClasspathYml() {
        // 烟雾测试：扫描真实 classpath 资源 properties/**/dict.yml（common/org/att/log/notify/task/process 等），
        // 验证公共模块的 yes_no 类型与「1:是 / 0:否」码值项被创建并固定 system=true；
        // 断言数量随各模块新增 dict.yml 会变化，故仅验证下限与关键插入。
        when(typeDao.selectByCode("yes_no")).thenReturn(null);
        when(itemDao.selectByTypeIdAndValue(anyString(), anyString())).thenReturn(null);
        when(typeDao.insert(any(SysDictType.class))).thenReturn(1);
        when(itemDao.insert(any(SysDictItem.class))).thenReturn(1);

        int handled = service.syncDictFromYml();

        verify(typeDao).insert(argThat(type ->
                "yes_no".equals(type.getFieldCode())
                        && "是否".equals(type.getFieldName())
                        && "通用是否码值（field_enable / field_is_star / field_is_del 等布尔字段共用）".equals(type.getFieldDesc())
                        && Boolean.TRUE.equals(type.getFieldEnable())));
        verify(itemDao).insert(argThat(item ->
                "1".equals(item.getFieldValue()) && "是".equals(item.getFieldName())
                        && Boolean.TRUE.equals(item.getFieldSystem())
                        && Boolean.TRUE.equals(item.getFieldEnable())));
        verify(itemDao).insert(argThat(item ->
                "0".equals(item.getFieldValue()) && "否".equals(item.getFieldName())
                        && Boolean.TRUE.equals(item.getFieldSystem())));
        // 至少包含 yes_no 类型 1 条 + 码值项 2 条（实际为各模块全部系统码值）
        assertTrue(handled >= 3);
    }

    @Test
    void skipsManualItemDuringYmlSync() {
        // 直接以构造的 dict 段验证增量规则（不依赖 classpath 资源数量）：
        // 类型已存在；码值项已存在但为人工码值（field_system=false）→ 跳过，不覆盖
        SysDictType type = new SysDictType();
        type.setFieldId("type-1");
        type.setFieldCode("yes_no");
        type.setFieldName("是否");
        type.setFieldDesc("通用是否码值（示例）");
        when(typeDao.selectByCode("yes_no")).thenReturn(type);
        SysDictItem manual = new SysDictItem();
        manual.setFieldId("item-1");
        manual.setFieldTypeId("type-1");
        manual.setFieldValue("1");
        manual.setFieldName("人工显示值");
        manual.setFieldSystem(false);
        // type.getFieldId() 为随机生成的 32 位 id，按码值参数匹配即可
        when(itemDao.selectByTypeIdAndValue(anyString(), eq("1"))).thenReturn(manual);
        when(itemDao.selectByTypeIdAndValue(anyString(), eq("0"))).thenReturn(null);
        when(itemDao.insert(any(SysDictItem.class))).thenReturn(1);

        Map<String, Object> dictData = Map.of("yes_no",
                Map.of("name", "是否", "desc", "通用是否码值（示例）", "1", "人工显示值", "0", "否"));
        int handled = service.syncDicts(dictData);

        // 人工码值「1」未被修改（insert/update 均不触发），仅补缺失码值「0」
        verify(itemDao, never()).update(any(SysDictItem.class));
        verify(itemDao).insert(argThat(item -> "0".equals(item.getFieldValue())));
        assertEquals(1, handled);
    }

    @Test
    void syncsSystemItemNameDuringYmlSync() {
        // 类型与码值项已存在且为系统码值：仅同步显示值，不反复启用被人工停用的项
        SysDictType type = new SysDictType();
        type.setFieldId("type-1");
        type.setFieldCode("yes_no");
        type.setFieldName("是否");
        type.setFieldDesc("通用是否码值（示例）");
        when(typeDao.selectByCode("yes_no")).thenReturn(type);
        SysDictItem disabled = new SysDictItem();
        disabled.setFieldId("item-1");
        disabled.setFieldTypeId("type-1");
        disabled.setFieldValue("1");
        disabled.setFieldName("旧显示值");
        disabled.setFieldEnable(false); // 人工停用
        disabled.setFieldSystem(true);
        // type.getFieldId() 为随机生成的 32 位 id，按码值参数匹配即可
        when(itemDao.selectByTypeIdAndValue(anyString(), eq("1"))).thenReturn(disabled);
        when(itemDao.selectByTypeIdAndValue(anyString(), eq("0"))).thenReturn(null);
        when(itemDao.update(any(SysDictItem.class))).thenReturn(1);
        when(itemDao.insert(any(SysDictItem.class))).thenReturn(1);

        Map<String, Object> dictData = Map.of("yes_no",
                Map.of("name", "是否", "desc", "通用是否码值（示例）", "1", "是", "0", "否"));
        int handled = service.syncDicts(dictData);

        // 系统码值「1」仅更新显示值（不触碰 field_enable）；码值「0」缺失补插入
        verify(itemDao).update(argThat(update ->
                null != update.getFieldId() && "是".equals(update.getFieldName())
                        && null == update.getFieldEnable()));
        verify(itemDao).insert(argThat(item -> "0".equals(item.getFieldValue())));
        assertEquals(2, handled);
    }

    /* ==================== 类型/码值项列表 ==================== */

    @Test
    void listsTypesAndItems() {
        SysDictType type = new SysDictType();
        type.setFieldId("type-1");
        when(typeDao.selectAll()).thenReturn(Arrays.asList(type));
        SysDictItem item = new SysDictItem();
        item.setFieldId("item-1");
        when(itemDao.selectByTypeId("type-1")).thenReturn(List.of(item));

        assertEquals(1, service.typeList().size());
        assertEquals(1, service.itemList("type-1").size());
    }
}
