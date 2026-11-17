package com.eric.base.sys.calendar.model;

import com.eric.base.common.base.model.BaseModel;
import com.eric.base.common.source.annotation.ColumnField;
import com.eric.base.common.source.annotation.TableIndex;
import com.eric.base.common.source.annotation.TableSource;
import com.eric.base.common.source.enums.ColumnType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 日程主表模型
 *
 * @author Eric
 * @date 2026/8/21
 * @description 对应 sys_calendar_main
 *              <p>2026/11/17 F19-DB-003 存量标注批三：类级 @TableSource / @TableIndex +
 *              业务列 @ColumnField（field_creator_id / field_create_time / field_update_time
 *              为表中存在的部分审计列，按业务列标注，datetime 类型保持 DATETIME 语义不做
 *              转换），列定义与 dev 库 information_schema 逐列一致；公共列（field_id 主键）
 *              由引擎公共列模板内建，无需标注。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableSource(name = "sys_calendar_main", comment = "日程主表",
        indexes = {
                @TableIndex(name = "idx_creator_id", columns = "field_creator_id"),
                @TableIndex(name = "idx_start_time", columns = "field_start_time"),
                @TableIndex(name = "idx_type", columns = "field_type")})
public class SysCalendarMain extends BaseModel {

    /** 创建人id（表中存在的部分审计列，按业务列标注） */
    @ColumnField(name = "field_creator_id", comment = "创建人id", type = ColumnType.STRING, length = 32)
    private String fieldCreatorId;

    /** 创建时间（表中存在的部分审计列，按业务列标注；datetime 保持 DATETIME 语义） */
    @ColumnField(name = "field_create_time", comment = "创建时间", type = ColumnType.DATETIME)
    private Date fieldCreateTime;

    /** 更新时间（表中存在的部分审计列，按业务列标注；datetime 保持 DATETIME 语义） */
    @ColumnField(name = "field_update_time", comment = "更新时间", type = ColumnType.DATETIME, nullable = true)
    private Date fieldUpdateTime;

    /** 开始时间 */
    @ColumnField(name = "field_start_time", comment = "开始时间", type = ColumnType.DATETIME)
    private Date fieldStartTime;

    /** 结束时间 */
    @ColumnField(name = "field_end_time", comment = "结束时间", type = ColumnType.DATETIME)
    private Date fieldEndTime;

    /** 标题 */
    @ColumnField(name = "field_name", comment = "标题", type = ColumnType.STRING, length = 200)
    private String fieldName;

    /** 内容 */
    @ColumnField(name = "field_content", comment = "内容", type = ColumnType.TEXT, nullable = true)
    private String fieldContent;

    /** 类型：PERSONAL=个人日程 TEAM=团队日程 */
    @ColumnField(name = "field_type", comment = "类型：PERSONAL=个人日程 TEAM=团队日程",
            type = ColumnType.STRING, length = 20, defaultValue = "PERSONAL")
    private String fieldType;
}