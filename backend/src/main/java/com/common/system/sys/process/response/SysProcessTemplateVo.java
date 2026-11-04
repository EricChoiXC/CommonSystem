package com.common.system.sys.process.response;

import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 流程模板响应对象
 *
 * @author Eric
 * @date 2026/8/22
 * @description 模板管理列表/详情/新建/编辑共用的响应与请求载体：
 *              list 仅返回基本字段（含版本号）；load 额外返回节点列表（nodes）；
 *              save/update 接收 nodes 并全量落库（编辑时历史版本归档到 sys_process_template_history）。
 */
@Data
public class SysProcessTemplateVo {

    /** 模板 ID */
    private String fieldId;

    /** 模板名称 */
    private String fieldName;

    /** 所属模块（process_template 二级配置项，左侧分类选中项） */
    private String fieldModelName;

    /** 模板编号 */
    private String fieldCode;

    /** 版本号 */
    private Integer fieldVersion;

    /** 备注 */
    private String fieldRemark;

    /** 是否默认模板（每分类最多一个，流程启动未指定模板时使用） */
    private Boolean fieldDefault;

    /** 创建人 ID */
    private String fieldCreatorId;

    /** 创建时间 */
    private Date fieldCreateTime;

    /** 修改人 ID */
    private String fieldUpdaterId;

    /** 修改时间 */
    private Date fieldUpdateTime;

    /** 是否启用 */
    private Boolean fieldEnable;

    /** 节点列表（按节点编号升序） */
    private List<SysProcessTemplateNodeVo> nodes;
}
