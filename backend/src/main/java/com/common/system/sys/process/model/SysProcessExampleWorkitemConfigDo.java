package com.common.system.sys.process.model;

import lombok.Data;

/**
 * 流程工作项配置文档对象
 *
 * @author Eric
 * @date 2026/8/23
 * @description 对应 sys_process_example_workitem.field_config（JSON 字符串）的结构化映射，
 *              由 Jackson 序列化/反序列化。当前承载驳回操作选择的「重新提交后流转方式」
 *              （依据 artificial/docs/设计文档/sys/process/流程核心业务.md「流程流转公共规范」第 4 条）。
 */
@Data
public class SysProcessExampleWorkitemConfigDo {

    /** 重新提交后流转方式：restart=重新流转 / backToNode=转回本节点 */
    private String resumeMode;
}
