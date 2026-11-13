package com.eric.base.sys.datasource.response;

import com.eric.base.sys.datasource.spi.SysDatasourceFieldDo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 数据源下拉选项响应（options）
 *
 * @author Eric
 * @date 2026/11/13
 * @description 消费方数据源下拉条目（DOC-002 §4.10.4 /options 契约）：默认（CODE）+
 *              启用的自定义（CUSTOM）；只暴露消费所需元数据（编码、名称、能力、
 *              有效返回字段定义与固定条件数），不暴露固定条件原文（A1：固定条件属
 *              服务端强制语义，不是可协商参数）。
 */
@Data
@Schema(description = "数据源下拉选项：默认数据源 + 启用的自定义数据源；不暴露固定条件原文")
public class SysDatasourceOptionVo {

    /** 数据源编码 */
    @Schema(description = "数据源编码")
    private String code;

    /** 数据源名称 */
    @Schema(description = "数据源名称")
    private String name;

    /** 来源类型：CODE=默认（代码 Provider 注册）/ CUSTOM=自定义（管理人员配置） */
    @Schema(description = "来源类型：CODE=默认 / CUSTOM=自定义",
            allowableValues = {"CODE", "CUSTOM"})
    private String sourceType;

    /** 目标查询能力编码 */
    @Schema(description = "目标查询能力编码")
    private String target;

    /** 目标接口路径（展示用） */
    @Schema(description = "目标接口路径（展示用）")
    private String targetPath;

    /** 返回字段定义（解析后的有效字段集：自定义按 fieldFields 裁剪，默认为全字段） */
    @Schema(description = "返回字段定义（有效字段集，与 /query 响应 metadata.fields 输出列一致）")
    private List<SysDatasourceFieldDo> fields;

    /** 固定条件数量（仅提示，不暴露条件原文） */
    @Schema(description = "固定条件数量（不暴露条件原文）")
    private int conditionCount;
}
