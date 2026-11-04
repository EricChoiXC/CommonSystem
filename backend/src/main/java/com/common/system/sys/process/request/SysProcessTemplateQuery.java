package com.common.system.sys.process.request;

import lombok.Data;

/**
 * 流程模板查询条件
 *
 * @author Eric
 * @date 2026/8/22
 * @description 模板管理列表页的查询条件：模板名称/编号模糊、启用状态筛选，
 *              field_model_name 为左侧分类选中项（固定放入查询）。
 */
@Data
public class SysProcessTemplateQuery {

    /** 所属模块（左侧分类选中项，固定条件） */
    private String fieldModelName;

    /** 模板名称模糊匹配 */
    private String fieldName;

    /** 模板编号模糊匹配 */
    private String fieldCode;

    /** 启用状态：为空表示全部 */
    private Boolean enable;
}
