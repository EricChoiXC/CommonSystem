package com.eric.base.sys.process.response;

import lombok.Data;

/**
 * 流程实例节点视图对象
 *
 * @author Eric
 * @date 2026/8/23
 * @description 流程实例详情页的节点行数据（创建实例时从模板节点快照），
 *              按节点编号升序展示，附节点状态。
 */
@Data
public class SysProcessExampleNodeVo {

    /** 节点 id */
    private String fieldId;

    /** 节点编号 */
    private Integer fieldNodeNo;

    /** 节点名称 */
    private String fieldNodeName;

    /** 节点类型：START / END / DRAFT / APPROVE / ROBORT */
    private String fieldNodeType;

    /** 节点状态：WAITING / PROCESSING / FINISH / PASS / REJECT */
    private String fieldStatus;
}
