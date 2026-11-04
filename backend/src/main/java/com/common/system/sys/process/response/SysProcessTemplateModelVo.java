package com.common.system.sys.process.response;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 流程模板分类视图对象
 *
 * @author Eric
 * @date 2026/8/22
 * @description 模板管理页左侧分类列表项：来自各模块 application.yml（classpath*:properties 目录下通配扫描聚合）的 process_template
 *              配置（二级配置项即 sys_process_template.field_model_name 值），
 *              name 为分类显示名，class 为该分类流程对应的文档实体类。
 *              ops 为分类级「操作标识」字典（key=操作标识，value=中文描述），
 *              节点配置按 key 开关（见 SysProcessTemplateNodeConfigDo.ops）；
 *              fields 为分类级文档实体字段字典（key=实体字段属性名，value=中文描述），
 *              供节点「审批来源=表单字段」选择审批人字段（见 SysProcessTemplateNodeConfigDo.handlerField）。
 */
@Data
public class SysProcessTemplateModelVo {

    /** 所属模块（process_template 分类 key，如 sys_org） */
    private String fieldModelName;

    /** 分类显示名（process_template.${fieldModelName}.name） */
    private String name;

    /** 文档实体类全限定名（process_template.${fieldModelName}.class） */
    private String className;

    /** 描述（process_template.${fieldModelName}.desc，可为空） */
    private String desc;

    /** 操作标识字典（process_template.${fieldModelName}.ops：key=操作标识，value=中文描述；可为空） */
    private Map<String, String> ops = new LinkedHashMap<>();

    /** 文档实体字段字典（process_template.${fieldModelName}.fields：key=实体字段属性名，value=中文描述；供节点「审批来源=表单字段」选择审批人字段） */
    private Map<String, String> fields = new LinkedHashMap<>();
}
