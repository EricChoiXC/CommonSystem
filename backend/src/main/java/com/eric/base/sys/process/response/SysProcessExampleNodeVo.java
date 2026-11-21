package com.eric.base.sys.process.response;

import com.eric.base.sys.process.model.SysProcessExampleNodeRuntimeDo;
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

    /** 节点业务键（模板节点快照，模板内唯一），供实例详情流程图渲染节点定位与连线端点匹配 */
    private String fieldNodeKey;

    /** 节点运行态（FORK 激活分支集合与汇聚模式 / JOIN 到达分支集合，接口清单 §5.3 nodes[].runtime）；无运行态为 null */
    private SysProcessExampleNodeRuntimeDo runtime;
}
