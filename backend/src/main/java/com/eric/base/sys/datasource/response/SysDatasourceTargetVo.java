package com.eric.base.sys.datasource.response;

import com.eric.base.sys.datasource.spi.SysDatasourceFieldDo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 目标查询能力响应（targets）
 *
 * @author Eric
 * @date 2026/11/13
 * @description 自定义数据源配置表单的数据源（DOC-002 §4.10.4 /targets 契约）：
 *              能力清单 + 各自条件/返回字段定义；管理员据此选择能力、编排固定条件、
 *              勾选返回字段；code 即 field_target 取值。
 */
@Data
@Schema(description = "目标查询能力：Provider 声明的可挂接查询能力及其条件/返回字段定义")
public class SysDatasourceTargetVo {

    /** 能力编码 */
    @Schema(description = "能力编码（field_target 取值）")
    private String code;

    /** 能力名称 */
    @Schema(description = "能力名称")
    private String name;

    /** 目标接口标识 */
    @Schema(description = "目标接口标识（ACTION 常量值，仅展示）")
    private String targetAction;

    /** 目标接口路径 */
    @Schema(description = "目标接口路径（仅展示）")
    private String targetPath;

    /** 条件字段定义 */
    @Schema(description = "条件字段定义：key / label / type(STRING|NUMBER|DATE|DATETIME) / "
            + "ops(EQ|NE|LIKE|GT|GE|LT|LE|IN) / literalAllowed / sortable")
    private List<SysDatasourceFieldDo> conditionFields;

    /** 返回字段定义 */
    @Schema(description = "返回字段定义（默认全字段母集，返回字段定义忽略 ops）")
    private List<SysDatasourceFieldDo> resultFields;
}
