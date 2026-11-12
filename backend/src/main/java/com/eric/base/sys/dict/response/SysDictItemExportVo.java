package com.eric.base.sys.dict.response;

import com.eric.base.common.base.annotation.Export;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 码值项导出视图对象（EasyExcel 行模型，导入导出公共通道）
 *
 * @author Eric
 * @date 2026/11/9
 * @description 码值项导出文件的行模型，列元数据由 {@link Export} 注解声明
 *              （列序 order 升序）。类型列写「类型名称」而非 field_type_id，
 *              导出文件人可读且可直接回传导入（round-trip）：导入按名称定位
 *              码值类型；是否启用走 yes_no 码值转换（1=是 / 0=否）。
 */
@Data
@Schema(description = "码值项导出行：类型名称 / 显示值 / 码值 / 是否启用")
public class SysDictItemExportVo {

    /** 类型名称（sys_dict_type.field_name，导入时按名称定位码值类型） */
    @Export(name = "类型名称", order = 1)
    @Schema(description = "类型名称（sys_dict_type.field_name）", example = "是否")
    private String typeName;

    /** 显示值（sys_dict_item.field_name） */
    @Export(name = "显示值", order = 2)
    @Schema(description = "显示值（sys_dict_item.field_name）", example = "是")
    private String fieldName;

    /** 码值（sys_dict_item.field_value） */
    @Export(name = "码值", order = 3)
    @Schema(description = "码值（sys_dict_item.field_value）", example = "1")
    private String fieldValue;

    /** 是否启用（sys_dict_item.field_enable，yes_no 码值转换：1=是 / 0=否） */
    @Export(name = "是否启用", dict = "yes_no", order = 4)
    @Schema(description = "是否启用（yes_no 码值：是/否）", example = "是")
    private String fieldEnable;
}
