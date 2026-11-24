package com.eric.base.sys.form.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 表单 Schema 字段对象协议文档对象
 *
 * @author Eric
 * @date 2026/11/24
 * @description Schema 字段对象（功能设计 §2.1 定稿结构，任务 F15-BE-001）：field / type /
 *              label / props / rules / children 六键 + 栅格列承载属性（span / fields，
 *              见下方「栅格列对象承载形态」）。由 Jackson 序列化 / 反序列化，向后兼容：
 *              未知键反序列化忽略（ignoreUnknown）、null 属性不输出（NON_NULL）。
 *              <ul>
 *              <li>field：字段码（数据载荷键），[a-zA-Z_][a-zA-Z0-9_]*、同一数据层级内唯一
 *                  （明细行内字段码在行上下文中求值，可与主表字段同码但行内必须唯一）；
 *                  仅数据类 12 组件与 detailTable 持有——group/grid/table/text/image
 *                  无 ID 不承载字段码（§1.1 固定属性项）；</li>
 *              <li>type：组件 type，取值域 = SysFormConstants.ALLOWED_FIELD_TYPES
 *                  （一期 18 个，R03）；</li>
 *              <li>props：组件属性 Map（固定属性项 + 扩展属性项混合承载——非固定结构
 *                  不建全量 POJO，对齐 SysTaskMain.fieldParams 口径）。约定键：
 *                  required（静态基础必填，动态必填由规则 setRequired 运行时覆盖）、
 *                  hidden（静态初始可见性）、tooltip（说明文本）、defaultValue（默认值）、
 *                  permission（字段级权限 editable/readonly/hidden，v1.8 BPM 消费）；
 *                  常用扩展键：maxlength/password（input）、dateType/format（date）、
 *                  multi/options（choice/select/cascade）、numberType/precision/min/max/
 *                  allowNegative/separator（number）、onText/offText（switch）、
 *                  parentField（cascade）、expression/expectedReturnType（formula）、
 *                  datasourceCode（datasource）、orgType/showRole（addressbook）、
 *                  maxCount/fileType（attachment）、rowMin/rowMax/summary（detailTable）、
 *                  content（text）、src/width（image）、columns/rows（table）；</li>
 *              <li>rules：以本字段为触发源的规则数组（DSL 见 SysFormRuleDo；triggerField
 *                  仍显式保留，服务端平铺索引求值不依赖嵌套位置）；</li>
 *              <li>children：仅容器类组件持有——group 为字段对象数组；grid 为列对象数组；
 *                  detailTable 为行内字段对象数组（仅数据类组件，R04）。</li>
 *              </ul>
 *              栅格列对象承载形态（任务开放点二选一定稿）：功能设计 §1.3 / §2.1 定稿 grid
 *              的 children 为列对象数组 { span, fields[] }。候选一「以 props 承载 span」
 *              会使列对象序列化为 { props: {span}, children: [...] }，与文档定稿形态不符，
 *              前端渲染器 / 设计器（F15-FE-005/007 按文档形态消费）无法直接读取；候选二
 *              「另设独立 SysFormGridColDo」在 children 保持强类型 List&lt;SysFormFieldDo&gt;
 *              时，列对象的 span/fields 键反序列化会被当未知属性丢弃（往返丢数据），改为
 *              List&lt;Object&gt; 则全树失去类型、结构校验器与下游引擎（F15-BE-003/004 遍历
 *              字段树）均需逐元素 convertValue，实现不便。故定稿：span/fields 作为本类的
 *              栅格列承载属性——type=grid 的 children 元素 = 仅 span/fields 赋值的列对象，
 *              配合 NON_NULL 恰好序列化为文档形态 { span, fields[] }，
 *              {@link #isGridColumn()} 判定列对象形态（Jackson 往返稳定 + 校验器实现便利）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "表单 Schema 字段对象：field/type/label/props/rules/children 六键"
        + "（功能设计 §2.1）；type=grid 的 children 为列对象数组 { span, fields[] }"
        + "（列对象复用本类以 span/fields 承载），detailTable 的 children 为行内字段对象数组")
public class SysFormFieldDo {

    /** 字段码（数据载荷键；[a-zA-Z_][a-zA-Z0-9_]*，同一数据层级内唯一；仅数据类 12 组件与 detailTable 持有，group/grid/table/text/image 无 ID 不承载） */
    @Schema(description = "字段码（数据载荷键，同一数据层级内唯一）；仅数据类组件与 detailTable 持有")
    private String field;

    /** 组件 type（取值域 = 一期注册表 + SysFormConstants.ALLOWED_FIELD_TYPES 白名单，R03） */
    @Schema(description = "组件 type（一期白名单 18 个：数据类 12 + 内容类 4 + 布局类 2）")
    private String type;

    /** 显示名称（分组即区块标题；全部字段对象必填） */
    @Schema(description = "显示名称（必填；布局类分组即区块标题）")
    private String label;

    /** 组件属性（固定属性项 + 扩展属性项混合承载，非固定结构以 Map 承载，对齐 SysTaskMain.fieldParams 口径；约定键 required/hidden/tooltip/defaultValue/permission） */
    @Schema(description = "组件属性 Map（约定键：required/hidden/tooltip/defaultValue/permission，"
            + "扩展键随组件类型，见功能设计 §1.3 属性项明细）")
    private Map<String, Object> props;

    /** 以本字段为触发源的规则数组（DSL 结构见 SysFormRuleDo；triggerField 显式保留，服务端平铺索引求值） */
    @Schema(description = "以本字段为触发源的规则数组（可空；ruleId/event/triggerField/condition/actions）")
    private List<SysFormRuleDo> rules;

    /** 子字段对象数组（仅容器类：group 为字段对象数组；grid 为列对象数组 { span, fields[] }；detailTable 为行内字段对象数组，仅数据类组件） */
    @Schema(description = "子字段对象数组（仅容器类组件；group/detailTable 为字段对象数组，grid 为列对象数组）")
    private List<SysFormFieldDo> children;

    /* ---------- 栅格列对象承载属性（仅 type=grid 的 children 列对象使用，见类注释定稿说明） ---------- */

    /** 栅格列跨度（1~24 的整数，24 栅格制 §1.3；仅 grid 的 children 列对象赋值） */
    @Schema(description = "栅格列跨度（1~24，24 栅格制）；仅 type=grid 的 children 列对象持有")
    private Integer span;

    /** 栅格列内字段对象数组（仅 grid 的 children 列对象赋值；列仅布局语义，列内字段与宿主栅格同处一个数据层级） */
    @Schema(description = "栅格列内字段对象数组；仅 type=grid 的 children 列对象持有（JSON 键为 fields）")
    private List<SysFormFieldDo> fields;

    /**
     * 判断是否栅格列对象形态（仅 span/fields 赋值、无字段码 / 类型 / 名称）：
     * 结构校验器据此分流 grid children 的列对象校验（span 合法性、列内字段递归）。
     * <p>标注 {@code @JsonIgnore}：本方法为只读派生判定（无对应字段），若不排除会被
     * Jackson 序列化为 {@code gridColumn} 键，破坏 Schema JSON 往返一致
     * （先例 {@code SysProcessTemplateNodeConfigDo#getNormalizedSkipMode}）。</p>
     */
    @JsonIgnore
    public boolean isGridColumn() {
        return null != span || null != fields;
    }
}
