package com.eric.base.sys.number.properties;

/**
 * 编号机制模块常量
 *
 * @author Eric
 * @date 2026/11/13
 * @description 编号机制模块接口标识（resolver 鉴权使用）与角色编码，供 Controller / Resolver 静态引用。
 *              权限设计依据 docs/v1_5-编号功能/编号机制实现方案.md「§4. 权限设计」：
 *              <ul>
 *              <li>编号规则列表 / 预览渲染 / 正式取号：登录即可——列表不设权、预览无副作用不落库
 *                  （NumberRuleComposer 宿主复用预览不 403）、取号的业务单据权限由调用方完成；</li>
 *              <li>编号规则维护五动作（load / save / update / enable / delete）：
 *                  SYS_NUMBER_ADMIN 或 SYS_NUMBER_RULE_EDITOR；</li>
 *              <li>取号明细与特权管理四动作（recordList / reset / void / reissue）：
 *                  仅 SYS_NUMBER_ADMIN（重置会造成计数器回退、作废 / 补号改变编号可用性，收敛管理员）。</li>
 *              </ul>
 */
public class SysNumberConstants {

    /* ==================== 接口标识（resolver 鉴权使用） ==================== */

    /** 编号规则列表（登录即可，需求 4.2.2「查询不设权」） */
    public static final String ACTION_RULE_LIST = "sys.number.rule.list";
    /** 编号规则完整读取，field_rule 解析为对象返回（SYS_NUMBER_ADMIN 或 SYS_NUMBER_RULE_EDITOR） */
    public static final String ACTION_RULE_LOAD = "sys.number.rule.load";
    /** 编号规则新建保存（默认启用并切换式启用同接入项原启用规则；SYS_NUMBER_ADMIN 或 SYS_NUMBER_RULE_EDITOR） */
    public static final String ACTION_RULE_SAVE = "sys.number.rule.save";
    /** 编号规则编辑（另校验 C7 seq 项 id 锁定；SYS_NUMBER_ADMIN 或 SYS_NUMBER_RULE_EDITOR） */
    public static final String ACTION_RULE_UPDATE = "sys.number.rule.update";
    /** 编号规则启停用（启用时切换式启用；SYS_NUMBER_ADMIN 或 SYS_NUMBER_RULE_EDITOR） */
    public static final String ACTION_RULE_ENABLE = "sys.number.rule.enable";
    /** 编号规则删除（存在取号明细时拒绝 CRM_NUMBER_RULE_IN_USE；SYS_NUMBER_ADMIN 或 SYS_NUMBER_RULE_EDITOR） */
    public static final String ACTION_RULE_DELETE = "sys.number.rule.delete";
    /** 编号预览渲染（不推进计数器、不落任何记录；登录即可，供宿主组件复用） */
    public static final String ACTION_PREVIEW_RENDER = "sys.number.preview.render";
    /** 正式取号（登录即可，业务单据权限由调用方在取号前完成，禁止替代业务鉴权） */
    public static final String ACTION_GENERATE_ACQUIRE = "sys.number.generate.acquire";
    /** 取号明细查询（仅 SYS_NUMBER_ADMIN） */
    public static final String ACTION_MANAGE_RECORD_LIST = "sys.number.manage.recordList";
    /** 重置计数器（封存当前行、新行从 start 起，会造成重号；仅 SYS_NUMBER_ADMIN） */
    public static final String ACTION_MANAGE_RESET = "sys.number.manage.reset";
    /** 作废取号明细（不回退计数器；仅 SYS_NUMBER_ADMIN） */
    public static final String ACTION_MANAGE_VOID = "sys.number.manage.void";
    /** 补号（对已作废明细对应单据按计数器顺延重新取号；仅 SYS_NUMBER_ADMIN） */
    public static final String ACTION_MANAGE_REISSUE = "sys.number.manage.reissue";

    /* ==================== 角色编码 ==================== */

    /** 编号管理员：拥有编号模块所有业务权限，含重置、补号、作废 */
    public static final String ROLE_SYS_NUMBER_ADMIN = "SYS_NUMBER_ADMIN";
    /** 编号规则维护者：拥有编号规则配置和启停用权限 */
    public static final String ROLE_SYS_NUMBER_RULE_EDITOR = "SYS_NUMBER_RULE_EDITOR";
}
