package com.eric.base.sys.process.model;

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
 * 流程审批记录实体
 *
 * @author Eric
 * @date 2026/8/23
 * @description 对应 sys_process_audit_note 表：起草人（提交/收回/废弃）、审批人（通过/驳回）、
 *              管理员（跳转/暂停/恢复/作废/办结）以及自动跳过（AUTO_PASS）的流转留痕。
 *              field_workitem_id 关联工作项（自动跳过等无工作项的操作可为空）；
 *              field_content 保存审批意见/说明。
 *              <p>2026/11/17 F19-DB-002 存量标注批二：类级 @TableSource / @TableIndex +
 *              业务列 @ColumnField，列定义与 dev 库 information_schema 逐列一致；
 *              表中存在的部分审计列 field_create_time 按业务列标注；
 *              公共列（field_id 主键）由引擎公共列模板内建，无需标注。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableSource(name = "sys_process_audit_note", comment = "流程审批记录表",
        indexes = {
                @TableIndex(name = "idx_audit_example", columns = "field_example_id"),
                @TableIndex(name = "idx_audit_workitem", columns = "field_workitem_id")})
public class SysProcessAuditNote extends BaseModel {

    /** 流程实例id（sys_process_example.field_id） */
    @ColumnField(name = "field_example_id", comment = "流程实例id", type = ColumnType.STRING, length = 32)
    private String fieldExampleId;

    /** 工作项id（sys_process_example_workitem.field_id，可为空） */
    @ColumnField(name = "field_workitem_id", comment = "工作项id(自动跳过等无工作项的操作可为空)",
            type = ColumnType.STRING, length = 32, nullable = true)
    private String fieldWorkitemId;

    /** 处理人id（起草人/审批人/管理员） */
    @ColumnField(name = "field_handler_id", comment = "处理人id(起草人/审批人/管理员)",
            type = ColumnType.STRING, length = 32, nullable = true)
    private String fieldHandlerId;

    /** 审批内容（意见/说明） */
    @ColumnField(name = "field_content", comment = "审批内容(意见/说明)", type = ColumnType.STRING, length = 200, nullable = true)
    private String fieldContent;

    /** 创建时间（表中存在的部分审计列，按业务列标注） */
    @ColumnField(name = "field_create_time", comment = "创建时间",
            type = ColumnType.TIMESTAMP, defaultFunction = DefaultFunction.CURRENT_TIMESTAMP)
    private Date fieldCreateTime;

    /**
     * 处理操作（全集 21 值 = 既有 11 + v1_7 新增 10，见《流程v1_7数据库设计分册》§7.7）：
     * DRAFTER_SUBMIT / DRAFTER_BACK / DRAFTER_ABANDON / HANDLER_PASS / HANDLER_RETURN / AUTO_PASS /
     * ADMIN_PAUSE / ADMIN_RESUME / ADMIN_JUMP / ADMIN_ABANDON / ADMIN_FINISH /
     * HANDLER_REJECT / CC_AUTO / DRAFTER_EXPEDITE / HANDLER_CC / HANDLER_TRANSFER /
     * HANDLER_TRANSFER_RETRACT / PROCESSED_RETRACT / PROCESSED_EXPEDITE / ADMIN_CHANGE_HANDLER /
     * SYSTEM_ERROR；
     * HANDLER_RETURN 保留兼容历史数据，新操作统一写 HANDLER_REJECT（可指定驳回目标 + resumeMode）。
     * 列宽 VARCHAR(20) → VARCHAR(50)：最长新值 HANDLER_TRANSFER_RETRACT 为 24 字符（L1 扩长）。
     */
    @ColumnField(name = "field_handle_type", comment = "处理操作: DRAFTER_SUBMIT=起草人提交 DRAFTER_BACK=起草人收回 DRAFTER_ABANDON=起草人废弃 HANDLER_PASS=处理人通过 HANDLER_RETURN=处理人驳回 AUTO_PASS=自动跳过 ADMIN_PAUSE=管理员暂停 ADMIN_RESUME=管理员恢复 ADMIN_JUMP=管理员跳转 ADMIN_ABANDON=管理员作废 ADMIN_FINISH=管理员办结 HANDLER_REJECT=处理人驳回(新语义:可指定驳回目标+resumeMode) CC_AUTO=抄送节点自动办结留痕 DRAFTER_EXPEDITE=起草人催办 HANDLER_CC=审批人抄送 HANDLER_TRANSFER=审批人转办 HANDLER_TRANSFER_RETRACT=转办收回 PROCESSED_RETRACT=已审批人收回 PROCESSED_EXPEDITE=已审批人催办 ADMIN_CHANGE_HANDLER=管理员更换审批人 SYSTEM_ERROR=流程异常(附错误摘要)",
            type = ColumnType.STRING, length = 50)
    private String fieldHandleType;
}
