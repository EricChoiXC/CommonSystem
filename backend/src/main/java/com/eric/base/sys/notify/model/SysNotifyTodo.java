package com.eric.base.sys.notify.model;

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
 * 待办主表模型
 *
 * @author Eric
 * @date 2026/8/12
 * @description 对应 sys_notify_todo；与 sys_notify_todo_target 通过 field_id
 *              一对多关联（一个待办发送给多个接收人，见通知待办表结构.md）。
 *              <p>2026/11/17 F19-DB-002 存量标注批二继承对齐（裁决 A：继承对齐表结构）：
 *              原继承 MainModel 与表结构不符（本表仅含部分审计列 field_creator_id /
 *              field_create_time，无 field_code / field_updater_id / field_update_time /
 *              field_enable 列），改继承 BaseModel 对齐公共列模板；表中存在的部分审计列
 *              按业务列 @ColumnField 标注（重声明字段 Java 类型与 MainModel 原声明一致），
 *              列定义与 dev 库 information_schema 逐列一致；公共列（field_id 主键）由
 *              引擎公共列模板内建，无需标注。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableSource(name = "sys_notify_todo", comment = "待办主表",
        indexes = {
                @TableIndex(name = "idx_notify_todo_create", columns = "field_create_time"),
                @TableIndex(name = "idx_notify_todo_model", columns = {"field_model_name", "field_model_id"}),
                @TableIndex(name = "idx_notify_todo_type", columns = "field_notify_type")})
public class SysNotifyTodo extends BaseModel {

    /** 重声明：标题（遮蔽 MainModel.fieldName） */
    @ColumnField(name = "field_name", comment = "标题", type = ColumnType.STRING, length = 200)
    private String fieldName;

    /** 来源模块（存储 MODEL_KEY，如 SYS-ORG；展示时经 model:name:map 翻译为 MODEL_NAME） */
    @ColumnField(name = "field_model_name", comment = "来源模块", type = ColumnType.STRING, length = 200, nullable = true)
    private String fieldModelName;

    /** 文档id */
    @ColumnField(name = "field_model_id", comment = "文档id", type = ColumnType.STRING, length = 200, nullable = true)
    private String fieldModelId;

    /** 重声明：创建人（表中存在的部分审计列，按业务列标注） */
    @ColumnField(name = "field_creator_id", comment = "创建人", type = ColumnType.STRING, length = 32, nullable = true)
    private String fieldCreatorId;

    /** 重声明：创建时间（表中存在的部分审计列，按业务列标注；类型与 MainModel 原声明一致） */
    @ColumnField(name = "field_create_time", comment = "创建时间",
            type = ColumnType.TIMESTAMP, defaultFunction = DefaultFunction.CURRENT_TIMESTAMP)
    private Date fieldCreateTime;

    /** 通知类型: 1=待办 2=待阅 */
    @ColumnField(name = "field_notify_type", comment = "通知类型: 1=待办 2=待阅", type = ColumnType.STRING, length = 1)
    private String fieldNotifyType;

    /** 链接 */
    @ColumnField(name = "field_url", comment = "链接", type = ColumnType.STRING, length = 200, nullable = true)
    private String fieldUrl;

    /** 内容 */
    @ColumnField(name = "field_content", comment = "内容", type = ColumnType.TEXT, nullable = true)
    private String fieldContent;
}
