package com.eric.base.sys.process.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 流程模板响应对象
 *
 * @author Eric
 * @date 2026/8/22
 * @description 模板管理列表/详情/新建/编辑共用的响应与请求载体：
 *              list 仅返回基本字段（含版本号）；load 额外返回节点列表（nodes）与连线列表（lines）；
 *              save/update 接收 nodes + lines 并全量落库（节点与连线均全量替换，连线不做差量；
 *              编辑时历史版本归档到 sys_process_template_history，归档内容含节点与连线快照）。
 *              依据 docs/设计文档/sys/process/流程v1_7接口清单.md §4.2 / §4.3。
 */
@Data
@Schema(description = "流程模板：list 仅基本字段；load 含 nodes + lines；save/update 携带 nodes + lines（均全量替换）")
public class SysProcessTemplateVo {

    /** 模板 ID */
    @Schema(description = "模板 ID")
    private String fieldId;

    /** 模板名称 */
    @Schema(description = "模板名称（必填）")
    private String fieldName;

    /** 所属模块（process_template 二级配置项，左侧分类选中项） */
    @Schema(description = "所属模块（process_template 二级配置项 key，左侧分类选中项；编辑不可变更）")
    private String fieldModelName;

    /** 模板编号 */
    @Schema(description = "模板编号")
    private String fieldCode;

    /** 版本号 */
    @Schema(description = "版本号（每次编辑 +1）")
    private Integer fieldVersion;

    /** 备注 */
    @Schema(description = "备注")
    private String fieldRemark;

    /** 是否默认模板（每分类最多一个，流程启动未指定模板时使用） */
    @Schema(description = "是否默认模板（每分类最多一个；置为 true 时替换同分类现有默认模板）")
    private Boolean fieldDefault;

    /** 创建人 ID */
    @Schema(description = "创建人 ID")
    private String fieldCreatorId;

    /** 创建时间 */
    @Schema(description = "创建时间")
    private Date fieldCreateTime;

    /** 修改人 ID */
    @Schema(description = "修改人 ID")
    private String fieldUpdaterId;

    /** 修改时间 */
    @Schema(description = "修改时间")
    private Date fieldUpdateTime;

    /** 是否启用 */
    @Schema(description = "是否启用")
    private Boolean fieldEnable;

    /** 节点列表（按节点编号升序） */
    @Schema(description = "节点列表（按节点编号升序；含固定节点 START/DRAFT/END 与其余节点，节点键 nodeKey 模板内唯一）")
    private List<SysProcessTemplateNodeVo> nodes;

    /**
     * 连线列表（v1_7 新增，按起始节点键、分支序号升序）：
     * 流转唯一依据；save/update 时服务端先删该模板全部连线再按请求 lines 全量重建（不做差量），
     * {@code fieldFromKey}/{@code fieldToKey} 必须存在于请求 nodes 的 nodeKey 集合。
     */
    @Schema(description = "连线列表（v1_7 新增，流转唯一依据）：save/update 时全量替换（先删后建，不做差量），"
            + "fieldFromKey/fieldToKey 须存在于请求 nodes 的 nodeKey 集合")
    private List<SysProcessTemplateLineVo> lines;
}
