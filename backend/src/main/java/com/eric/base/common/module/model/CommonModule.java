package com.eric.base.common.module.model;

import com.eric.base.common.base.model.BaseModel;
import com.eric.base.common.source.annotation.ColumnField;
import com.eric.base.common.source.annotation.TableIndex;
import com.eric.base.common.source.annotation.TableSource;
import com.eric.base.common.source.enums.ColumnType;
import com.eric.base.common.source.enums.DefaultFunction;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 模块组开关状态模型
 *
 * @author Eric
 * @date 2026/11/12
 * @description 对应 common_module_group；模块组级接口启停的状态载体。
 *              field_key 为 kebab-case 全局唯一幂等同步键（一经发布不复用/不重命名，
 *              重命名视为新模块组），系统启动时按各模块 moduleGroup.yml 的
 *              module-group 段增量维护，新导入默认开启，已存在条目不回写开关状态；
 *              field_update_time 由数据库 DEFAULT CURRENT_TIMESTAMP ON UPDATE
 *              CURRENT_TIMESTAMP 自动维护。配置型表（与 common_setting 同），
 *              无创建人/创建时间/更新人字段。
 *              承载 /backend/common/module/enable 请求体（{ fieldId, fieldEnable }）。
 *              <p>2026/11/17 F19-DB-003 存量标注批三：类级 @TableSource / @TableIndex +
 *              业务列 @ColumnField（field_enable / field_update_time 为表中存在的部分
 *              审计列，按业务列标注），列定义与 dev 库 information_schema 逐列一致；
 *              公共列（field_id 主键）由引擎公共列模板内建，无需标注。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "模块组开关状态：列表返回与启停请求体（{ fieldId, fieldEnable }）")
@TableSource(name = "common_module_group", comment = "模块组状态表",
        indexes = {
                @TableIndex(name = "uk_module_key", columns = "field_key", unique = true)})
public class CommonModule extends BaseModel {

    /** 模块组标识（kebab-case，全局唯一，@ModuleGroup 注解引用键） */
    @ColumnField(name = "field_key", comment = "模块组标识（kebab-case，全局唯一）", type = ColumnType.STRING, length = 200)
    @Schema(description = "模块组标识（kebab-case，全局唯一，@ModuleGroup 注解引用键）", example = "calendar")
    private String fieldKey;

    /** 模块组名称 */
    @ColumnField(name = "field_name", comment = "模块组名称", type = ColumnType.STRING, length = 200)
    @Schema(description = "模块组名称", example = "日程")
    private String fieldName;

    /** 模块组状态 true 开启 false 关闭（表中存在的部分审计列，按业务列标注） */
    @ColumnField(name = "field_enable", comment = "模块组状态 0 关闭 1 开启",
            type = ColumnType.BOOLEAN, defaultValue = "b'1'")
    @Schema(description = "模块组状态：true 开启 / false 关闭；启停接口以本字段传目标状态", example = "true")
    private Boolean fieldEnable;

    /** 模块组描述 */
    @ColumnField(name = "field_remark", comment = "模块组描述", type = ColumnType.STRING, length = 200, nullable = true)
    @Schema(description = "模块组描述", example = "日程模块")
    private String fieldRemark;

    /** 更新时间（数据库自动维护；表中存在的部分审计列，按业务列标注） */
    @ColumnField(name = "field_update_time", comment = "更新时间",
            type = ColumnType.TIMESTAMP, defaultFunction = DefaultFunction.ON_UPDATE_CURRENT_TIMESTAMP)
    @Schema(description = "更新时间（数据库自动维护）")
    private Date fieldUpdateTime;
}
