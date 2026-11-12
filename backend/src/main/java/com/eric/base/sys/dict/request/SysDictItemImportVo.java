package com.eric.base.sys.dict.request;

import com.eric.base.common.base.annotation.Export;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 码值项导入请求视图对象（EasyExcel 行模型，导入导出公共通道）
 *
 * @author Eric
 * @date 2026/11/9
 * @description 码值项导入文件的行模型，列元数据由 {@link Export} 注解声明，
 *              required 口径与 sys_dict_item 表结构一致（field_name / field_value
 *              数据库必填）。类型列与导出文件同构（写「类型名称」，导入按名称
 *              定位码值类型，见 {@link com.eric.base.sys.dict.response.SysDictItemExportVo}）；
 *              是否启用走 yes_no 码值回转（是→1 / 否→0），空值默认启用。
 */
@Data
@Schema(description = "码值项导入行：类型名称 / 显示值 / 码值 / 是否启用")
public class SysDictItemImportVo {

    /** 类型名称（sys_dict_type.field_name，按名称定位码值类型） */
    @Export(name = "类型名称", required = true, order = 1)
    @Schema(description = "类型名称（sys_dict_type.field_name）", example = "是否")
    private String typeName;

    /** 显示值（sys_dict_item.field_name，表字段 NOT NULL） */
    @Export(name = "显示值", required = true, order = 2)
    @Schema(description = "显示值（sys_dict_item.field_name）", example = "是")
    private String fieldName;

    /** 码值（sys_dict_item.field_value，表字段 NOT NULL，同类型下唯一） */
    @Export(name = "码值", required = true, order = 3)
    @Schema(description = "码值（sys_dict_item.field_value）", example = "1")
    private String fieldValue;

    /** 是否启用（yes_no 码值回转：是→1 / 否→0；空值默认启用） */
    @Export(name = "是否启用", dict = "yes_no", order = 4)
    @Schema(description = "是否启用（yes_no 码值：是/否，空值默认启用）", example = "是")
    private String fieldEnable;
}
