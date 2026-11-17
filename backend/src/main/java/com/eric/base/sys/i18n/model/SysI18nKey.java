package com.eric.base.sys.i18n.model;

import com.eric.base.common.base.model.BaseModel;
import com.eric.base.common.source.annotation.ColumnField;
import com.eric.base.common.source.annotation.TableIndex;
import com.eric.base.common.source.annotation.TableSource;
import com.eric.base.common.source.enums.ColumnType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 多语言-资源模型
 *
 * @author Eric
 * @date 2026/11/5
 * @description 对应 sys_i18n_key（V1_01_040）：资源项登记，key 仅随 resources/i18n/**
 *              资源文件扫描产生，不提供人工新增/删除入口（D5）。field_model 为相对 i18n/
 *              的资源子目录层级名（如 org、process/template）。本表无 field_name /
 *              field_code 列（loadModel 仅返回 field_id，见 SysTaskHistory 先例），
 *              不继承 MainModel、无公共审计列。列清单与附录 A DDL 保持一致。
 *              <p>2026/11/17 F19-DB-003 存量标注批三：类级 @TableSource / @TableIndex +
 *              业务列 @ColumnField，列定义与 dev 库 information_schema 逐列一致；
 *              公共列（field_id 主键）由引擎公共列模板内建，无需标注。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableSource(name = "sys_i18n_key", comment = "多语言-资源表",
        indexes = {
                @TableIndex(name = "idx_i18n_key_model_key", columns = {"field_model", "field_key"})})
public class SysI18nKey extends BaseModel {

    /** 关键字（资源 properties 的键，如 org.user.save） */
    @ColumnField(name = "field_key", comment = "关键字（资源 properties 的键）", type = ColumnType.STRING, length = 200)
    private String fieldKey;

    /** 所属模块（相对 i18n/ 的资源子目录层级名，如 org、process/template） */
    @ColumnField(name = "field_model", comment = "所属模块（相对 i18n/ 的资源子目录层级名，如 org、process/template）",
            type = ColumnType.STRING, length = 200)
    private String fieldModel;
}
