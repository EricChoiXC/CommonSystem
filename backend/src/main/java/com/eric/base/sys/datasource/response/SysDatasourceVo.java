package com.eric.base.sys.datasource.response;

import com.eric.base.common.base.model.MainModel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 数据源详情/列表响应
 *
 * @author Eric
 * @date 2026/11/13
 * @description 对应 DOC-002 §4.10.4 /list、/load 响应契约：列表条目与 load 详情同构，
 *              差异为 fieldConditions / fieldFields 形态（列表原文 JSON 字符串、load 解析对象）；
 *              fieldTargetName / fieldTargetPath 由 Service 按 fieldTarget 从 Registry 带出
 *              （仅展示，不入库），目标失效（Provider 下线残留）时两者为 null，
 *              前端据此与 /targets 编码全集比对展示「目标失效」标记（R05）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "数据源响应：load 响应中 fieldConditions/fieldFields 为解析后的对象；"
        + "列表响应中为原文 JSON 字符串")
public class SysDatasourceVo extends MainModel {

    /** 目标查询能力编码 */
    @Schema(description = "目标查询能力编码（Provider code）")
    private String fieldTarget;

    /** 目标能力名称 */
    @Schema(description = "目标能力名称（由目标能力带出；目标失效时为 null）")
    private String fieldTargetName;

    /** 目标接口路径（由目标能力带出，仅展示） */
    @Schema(description = "目标接口路径（由目标能力带出，仅展示；目标失效时为 null）")
    private String fieldTargetPath;

    /** 固定条件（load 为解析后的对象数组 [{key,op,value}]，列表响应为原文 JSON 字符串） */
    @Schema(description = "固定条件：[{key,op,value}]，value 支持 ${CURRENT_USER_ID} 等运行时变量；"
            + "load 响应为解析后的对象数组，列表响应为原文 JSON 字符串；NULL=无固定条件")
    private Object fieldConditions;

    /** 返回字段白名单（load 为解析后的字符串数组，列表响应为原文 JSON 字符串）；NULL=默认全字段 */
    @Schema(description = "返回字段白名单：字段名字符串数组；load 响应为解析后的字符串数组，"
            + "列表响应为原文 JSON 字符串；NULL=目标能力默认全字段")
    private Object fieldFields;

    /** 备注 */
    @Schema(description = "备注")
    private String fieldRemark;
}
