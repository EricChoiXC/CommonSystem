package com.eric.base.sys.number.request;

import com.eric.base.sys.number.model.SysNumberRuleConfigDo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 编号预览渲染请求对象
 *
 * @author Eric
 * @date 2026/11/13
 * @description 对应 preview/render 接口请求契约（方案 §3.2）：已保存规则传
 *              {@code fieldId}，未保存 JSON 传 {@code rule}，二选一——两者都传以
 *              {@code fieldId} 优先（已保存规则按当前计数顺延，作用域 = 规则 field_id）；
 *              两者都空由预览服务抛 IllegalArgumentException（命中全局 BAD_REQUEST 分支）。
 *              {@code count} 为样例数量，缺省 3、上限 10、小于 1 归一为 1（服务侧归一）。
 */
@Data
@Schema(description = "编号预览渲染请求：fieldId（已保存规则）与 rule（未保存规则 JSON）二选一，"
        + "两者都传以 fieldId 优先；count 为样例数量（缺省 3，上限 10）")
public class SysNumberPreviewRequest {

    /** 已保存规则 ID（与 rule 二选一，同时传入以 fieldId 优先） */
    @Schema(description = "已保存规则 ID（sys_number_rule.field_id）；与 rule 二选一，两者都传以 fieldId 优先")
    private String fieldId;

    /** 未保存规则配置对象（无作用域，计数视同不存在，从 start 起渲染） */
    @Schema(description = "未保存规则配置对象（SysNumberRuleConfigDo）；与 fieldId 二选一，"
            + "预览无作用域计数、视同不存在，样例从 start 起渲染")
    private SysNumberRuleConfigDo rule;

    /** 样例数量：缺省 3，上限 10，小于 1 归一为 1 */
    @Schema(description = "样例数量：缺省 3，上限 10，小于 1 归一为 1")
    private Integer count;
}
