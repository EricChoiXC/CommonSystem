package com.common.system.sys.i18n.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 多语言-模块聚合视图对象
 *
 * @author Eric
 * @date 2026/11/5
 * @description 翻译维护模块聚合下拉项：field_model 为相对 i18n/ 的子目录层级名；
 *              field_model_name 为显示名，按「末段路径」匹配各模块 application.yml 的
 *              MODEL_NAME 取得（MODEL_KEY 形如 SYS-ORG 与 field_model=org 不同源，
 *              禁止等值匹配），取不到回退 field_model。
 */
@Data
@Schema(description = "翻译维护模块聚合项：field_model + 显示名（末段匹配 MODEL_NAME，回退 field_model）")
public class SysI18nModelVo {

    /** 所属模块（相对 i18n/ 的资源子目录层级名，如 org、process/template） */
    @Schema(description = "所属模块（相对 i18n/ 的资源子目录层级名）")
    private String fieldModel;

    /** 模块显示名（按末段路径匹配 MODEL_NAME；取不到回退 field_model） */
    @Schema(description = "模块显示名（末段匹配 MODEL_NAME，取不到回退 field_model）")
    private String fieldModelName;
}
