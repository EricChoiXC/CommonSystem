package com.eric.base.common.setting.model;

import com.eric.base.common.base.model.BaseModel;
import com.eric.base.common.source.annotation.ColumnField;
import com.eric.base.common.source.annotation.TableIndex;
import com.eric.base.common.source.annotation.TableSource;
import com.eric.base.common.source.enums.ColumnType;
import com.eric.base.common.source.enums.DefaultFunction;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 公共配置信息模型
 *
 * @author Eric
 * @date 2026/8/19
 * @description 对应 common_setting；保存系统配置级信息，包含从公共配置到各模块专有配置在内的所有内容。
 *              配置以 (field_type, field_key) 为唯一键，field_default_value 来自各模块 application.yml 的
 *              SETTING 段（SETTING.${field_type}.${field_key}: ${field_default_value}），系统启动时增量维护；
 *              field_value 为当前生效的配置值，可由内部维护接口变更，变更后不受默认值同步覆盖。
 *              <p>2026/11/17 F19-DB-003 存量标注批三：类级 @TableSource / @TableIndex +
 *              业务列 @ColumnField（field_update_time 为表中存在的部分审计列，按业务列标注），
 *              列定义与 dev 库 information_schema 逐列一致；公共列（field_id 主键）由引擎
 *              公共列模板内建，无需标注。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableSource(name = "common_setting", comment = "公共配置信息表",
        indexes = {
                @TableIndex(name = "uk_setting_type_key", columns = {"field_type", "field_key"}, unique = true)})
public class CommonSetting extends BaseModel {

    /** 模块 */
    @ColumnField(name = "field_model_name", comment = "模块", type = ColumnType.STRING, length = 50)
    private String fieldModelName;

    /** 配置字段 */
    @ColumnField(name = "field_key", comment = "配置字段", type = ColumnType.STRING, length = 50)
    private String fieldKey;

    /** 配置值 */
    @ColumnField(name = "field_value", comment = "配置值", type = ColumnType.STRING, length = 200)
    private String fieldValue;

    /** 类型 */
    @ColumnField(name = "field_type", comment = "类型", type = ColumnType.STRING, length = 50)
    private String fieldType;

    /** 更新时间（表中存在的部分审计列，按业务列标注） */
    @ColumnField(name = "field_update_time", comment = "更新时间",
            type = ColumnType.TIMESTAMP, nullable = true, defaultFunction = DefaultFunction.ON_UPDATE_CURRENT_TIMESTAMP)
    private Date fieldUpdateTime;

    /** 默认值 */
    @ColumnField(name = "field_default_value", comment = "默认值", type = ColumnType.STRING, length = 200)
    private String fieldDefaultValue;
}
