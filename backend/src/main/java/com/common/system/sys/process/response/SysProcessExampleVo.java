package com.common.system.sys.process.response;

import lombok.Data;

import java.util.Date;

/**
 * 流程实例列表视图对象
 *
 * @author Eric
 * @date 2026/8/23
 * @description 流程实例列表行数据：流程名称（模板名称）、流程编号、所属模块、发起人、
 *              创建时间、当前节点、流程状态；操作列详情跳转。
 */
@Data
public class SysProcessExampleVo {

    /** 流程实例 ID */
    private String fieldId;

    /** 流程名称（模板名称） */
    private String fieldName;

    /** 流程编号 */
    private String fieldCode;

    /** 所属模块（process_template 分类 key，如 sys_org） */
    private String fieldModelName;

    /** 所属模块id（业务文档 id） */
    private String fieldModelId;

    /** 模板 id */
    private String fieldTemplateId;

    /** 模板版本号（创建实例时的模板版本） */
    private Integer fieldTemplateVersion;

    /** 创建人（起草人）id */
    private String fieldCreatorId;

    /** 创建人（起草人）名称 */
    private String fieldCreatorName;

    /** 创建时间 */
    private Date fieldCreateTime;

    /** 完成时间 */
    private Date fieldFinishTime;

    /** 流程状态：ABANDON / DRAFT / PROCESSING / PAUSE / FINISH / RETURN */
    private String fieldStatus;

    /** 当前节点名称 */
    private String currentNodeName;
}
