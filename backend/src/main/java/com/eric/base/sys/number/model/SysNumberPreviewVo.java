package com.eric.base.sys.number.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 编号预览渲染结果文档对象
 *
 * @author Eric
 * @date 2026/11/13
 * @description 对应 preview/render 接口响应契约（方案 §3.2）：samples 为基于当前计数器
 *              顺延模拟的样例编号（绝不写库、不推进计数器），errors 为校验错误与容量提示
 *              （C1~C5 非法规则就地返回供前端红字提示，不抛异常——这是与 save/enable
 *              硬失败的差异；C6 为当前周期临近耗尽的越界提示）。
 */
@Data
@Schema(description = "编号预览渲染结果：samples 为顺延模拟样例编号，errors 为校验错误与容量提示")
public class SysNumberPreviewVo {

    /** 预览样例编号列表（校验失败时为空列表） */
    @Schema(description = "预览样例编号列表（基于当前计数顺延模拟，不写库；校验失败时为空列表）")
    private List<String> samples = new ArrayList<>();

    /** 校验错误与容量提示（C1~C5 非法规则或 C6 越界提示；无错误为空列表） */
    @Schema(description = "校验错误与容量提示（C1~C5 非法规则就地返回不抛异常，C6 为当前周期"
            + "临近耗尽的越界提示；无错误为空列表），单条结构 { code, itemId, message }")
    private List<SysNumberRuleErrorDo> errors = new ArrayList<>();
}
