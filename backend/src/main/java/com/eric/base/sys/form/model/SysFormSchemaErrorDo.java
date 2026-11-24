package com.eric.base.sys.form.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 表单 Schema 结构校验错误项文档对象
 *
 * @author Eric
 * @date 2026/11/24
 * @description FormSchemaValidator 校验结果的单条错误描述（先例 SysFormulaErrorDo），
 *              元素 { code, message, field }：code ∈ SysFormConstants 的 CS_FORM_* 错误码
 *              （结构校验失败 CS_FORM_SCHEMA_INVALID / 资源上限超限
 *              CS_FORM_SCHEMA_LIMIT_EXCEEDED，API 分册 §7.1 第 6/7 项）；message 为中文
 *              可读原因（逐条附具体位置与违规说明）；field 为相关字段码（可空——顶层
 *              结构 / JSON 解析类错误无字段码）。发布校验链 P2/P3 与 define/save、
 *              define/update 的保存前校验共用本结构（功能设计 §5.3）。
 */
@Data
@Schema(description = "Schema 结构校验错误项：{ code, message, field }，"
        + "code ∈ CS_FORM_SCHEMA_INVALID / CS_FORM_SCHEMA_LIMIT_EXCEEDED")
public class SysFormSchemaErrorDo {

    /** 错误码（SysFormConstants.CS_FORM_*，供程序化匹配） */
    @Schema(description = "错误码（CS_FORM_*，供程序化匹配）")
    private String code;

    /** 中文可读错误原因（附具体位置与违规说明） */
    @Schema(description = "中文可读错误原因")
    private String message;

    /** 相关字段码（可空——顶层结构 / JSON 解析类错误无字段码） */
    @Schema(description = "相关字段码（可空）")
    private String field;

    public SysFormSchemaErrorDo() {
    }

    public SysFormSchemaErrorDo(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public SysFormSchemaErrorDo(String code, String message, String field) {
        this.code = code;
        this.message = message;
        this.field = field;
    }
}
