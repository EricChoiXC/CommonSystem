package com.eric.base.sys.process.response;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程审批面板操作按钮视图对象
 *
 * @author Eric
 * @date 2026/11/20
 * @description 审批面板「当前用户可执行操作按钮集」的按钮行（v1_7 升级，P17-BE-013，
 *              依据 docs/设计文档/sys/process/流程v1_7接口清单.md §5.4）：
 *              {@code panel.operations} 由「操作标识字符串数组」升级为「按钮对象数组」，
 *              旧操作标识语义由 {@code operateType} 承载，不新增并列字段。
 *              按钮集计算规则 = 节点 {@code operations} 配置 ∩ 用户身份 ∩ 状态前置
 *              （面板仅做展示预判，最终以操作时引擎身份校验为准）。
 *              <ul>
 *              <li>{@code operateType}：14 类小写操作标识（drafter_submit / handler_pass 等，
 *                  身份由前缀推导：drafter_*=起草人、handler_*=审批人、processed_*=已处理人）
 *                  或管理员特权操作大写标识（JUMP / FINISH / ABANDON / PAUSE / RESUME /
 *                  CHANGE_HANDLER）；</li>
 *              <li>{@code operateName}：节点配置的操作名称，缺省取操作类型中文名
 *                  （见 {@link #defaultOperateName(String)}）；</li>
 *              <li>{@code defaultContent}：节点配置的默认审批意见（面板选择该操作时回填，可改）；</li>
 *              <li>{@code rejectTargets}：驳回候选节点（仅 handler_reject），按允许目标口径
 *                  「节点配置 rejectTargets ∩ 已流经节点，空默认起草节点」输出；</li>
 *              <li>{@code flowChoices}：人工决策流向候选（仅 handler_pass / drafter_submit 且
 *                  存在决策来源关系时），输出决策节点全部出边目标。</li>
 *              </ul>
 */
@Data
public class SysProcessPanelOperationVo {

    /** 操作类型：14 类小写操作标识（身份按前缀推导）或 6 类管理员特权操作大写标识 */
    private String operateType;

    /** 操作名称（节点配置操作名，缺省取操作类型中文名；面板按钮显示名） */
    private String operateName;

    /** 默认审批意见（面板选择该操作时回填审批意见框，用户可改） */
    private String defaultContent;

    /** 驳回候选节点（nodeKey + nodeName；仅 handler_reject 按钮提供） */
    private List<SysProcessNodeOptionVo> rejectTargets;

    /** 人工决策流向候选（nodeKey + nodeName；仅 handler_pass / drafter_submit 且存在决策来源关系时提供） */
    private List<SysProcessNodeOptionVo> flowChoices;

    /** 操作类型默认中文名（14 类小写操作标识，键即 {@code SysProcessConstants.OPERATE_TYPE_*} 值） */
    private static final Map<String, String> DEFAULT_OPERATE_NAMES = buildDefaultOperateNames();

    /**
     * 操作类型默认中文名（缺省 operateName 时取用）：覆盖 14 类小写操作标识与 6 类管理员
     * 特权操作大写标识；未登记类型回退操作类型原文（展示名不为空）。
     *
     * @param operateType 操作类型（小写操作标识或大写特权操作标识）
     * @return 中文默认名；未登记时返回操作类型原文
     */
    public static String defaultOperateName(String operateType) {
        if (null == operateType || operateType.isBlank()) {
            return "";
        }
        String name = DEFAULT_OPERATE_NAMES.get(operateType);
        return null == name ? operateType : name;
    }

    private static Map<String, String> buildDefaultOperateNames() {
        Map<String, String> names = new LinkedHashMap<>();
        // 14 类小写操作标识（与 docs/设计文档/sys/process/流程核心业务.md「审批操作矩阵（14 类）」一致）
        names.put("drafter_submit", "起草人提交");
        names.put("drafter_cancel", "起草人取消");
        names.put("drafter_retract", "起草人收回");
        names.put("drafter_expedite", "起草人催办");
        names.put("handler_pass", "审批人通过");
        names.put("handler_reject", "审批人拒绝");
        names.put("handler_cc", "审批人抄送");
        names.put("handler_transfer", "审批人转办");
        names.put("handler_transfer_retract", "转办收回");
        names.put("processed_retract", "已审批人收回");
        names.put("processed_expedite", "已审批人催办");
        names.put("admin_abandon", "管理员废弃");
        names.put("admin_jump", "管理员跳转");
        names.put("admin_change_handler", "管理员更换审批人");
        // 6 类管理员特权操作大写标识（与 SysProcessConstants.ADMIN_OPERATE_* 同组）
        names.put("JUMP", "管理员跳转");
        names.put("FINISH", "管理员终审通过");
        names.put("ABANDON", "管理员作废");
        names.put("PAUSE", "管理员暂停");
        names.put("RESUME", "管理员恢复");
        names.put("CHANGE_HANDLER", "管理员更换审批人");
        return names;
    }
}
