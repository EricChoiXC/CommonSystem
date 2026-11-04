package com.common.system.sys.process.response;

import lombok.Data;

import java.util.Date;

/**
 * 流程工作项视图对象
 *
 * @author Eric
 * @date 2026/8/23
 * @description 流程实例详情页的工作项行数据：所属节点、处理人（实际/期望）、状态与
 *              驳回后重新提交的流转方式（resumeMode）。
 */
@Data
public class SysProcessExampleWorkitemVo {

    /** 工作项 id */
    private String fieldId;

    /** 节点 id */
    private String fieldNodeId;

    /** 节点名称 */
    private String fieldNodeName;

    /** 处理人 id（实际处理人） */
    private String fieldHandlerId;

    /** 处理人名称 */
    private String fieldHandlerName;

    /** 期望处理人 id（节点配置审批人） */
    private String fieldExpectHandlerId;

    /** 期望处理人名称 */
    private String fieldExpectHandlerName;

    /** 创建时间 */
    private Date fieldCreateTime;

    /** 完成时间 */
    private Date fieldFinishTime;

    /** 工作项状态：PROCESSING / FINISH / ABANDON / PAUSE */
    private String fieldStatus;

    /** 重新提交后流转方式：restart=重新流转 / backToNode=转回本节点 */
    private String resumeMode;
}
