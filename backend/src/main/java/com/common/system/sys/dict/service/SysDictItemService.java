package com.common.system.sys.dict.service;

import com.common.system.common.base.service.BaseService;
import com.common.system.sys.dict.dao.SysDictItemDao;
import com.common.system.sys.dict.model.SysDictItem;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 码值项行实体业务服务（导入导出公共通道专用）
 *
 * @author Eric
 * @date 2026/11/9
 * @description {@link SysDictService} 的泛型实体是 sys_dict_type（模块页签实体），
 *              而导入导出行实体是 sys_dict_item，两者不同表；BaseCrudController
 *              公共导出（全量查询）与公共导入（分批 insert）各需要一个「行实体
 *              类型」的 Service 通道，故建立本极小 Service（IE4-BE-001 R3 契约
 *              缺口增量）。业务校验仍集中在 {@link SysDictService}：导入行级校验
 *              由 SysDictService 的 rowHandler 调 {@link SysDictService#saveItem}
 *              同款口径（类型存在 + 同类型下码值唯一）完成，本类不重复校验。
 */
@Service
public class SysDictItemService extends BaseService<SysDictItem, SysDictItemDao> {

    public SysDictItemService(SysDictItemDao baseDao) {
        super(baseDao);
    }

    /**
     * 码值项全量条件查询（BaseCrudController 公共导出通道）：
     * 复用既有 SysDictItemMapper.xml#selectList（动态 if 条件 + field_value 升序），
     * pages 传 null 即全量。
     *
     * @param criteria 查询条件（null 视为无条件）
     * @return 码值项列表（field_value 升序）
     */
    public List<SysDictItem> selectAll(SysDictItem criteria) {
        return getBaseDao().selectList(criteria, null);
    }

    /**
     * 码值项落库（BaseCrudController 公共导入通道，经公共层分批 insert 调用）：
     * 生成 field_id；人工导入码值固定 field_system = false（saveItem 硬性规则，
     * 不接受文件内容覆盖）；field_enable 为空时默认 true（对齐 saveItem）。
     *
     * @param item 已通过公共校验与行级业务校验的码值项
     * @return 落库后的码值项
     */
    @Override
    public SysDictItem insert(SysDictItem item) {
        item.initFieldId();
        // 人工导入的码值固定 field_system = false（saveItem 同款硬性规则）
        item.setFieldSystem(false);
        if (null == item.getFieldEnable()) {
            item.setFieldEnable(true);
        }
        return super.insert(item);
    }
}
