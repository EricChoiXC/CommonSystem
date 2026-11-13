package com.eric.base.sys.number.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 编号规则配置文档对象
 *
 * @author Eric
 * @date 2026/11/13
 * @description 对应 sys_number_rule.field_rule（JSON 列）的结构化映射，由 Jackson
 *              序列化/反序列化，禁止手工拼串（先例 SysProcessTemplateNodeConfigDo）。
 *              结构契约见 docs/v1_5-编号功能/编号机制实现方案.md §2.1/§2.2：
 *              items 有序数组，渲染按数组顺序拼接；item.id 由前端新增项时生成
 *              （ci- 前缀 + 十六进制），是流水计数 sys_number_record.field_serial_id
 *              的取值来源，已保存规则中 seq 项 id 不可变更（C7）。
 *              四类规则项共用一个扁平 Item 结构，仅各自专属属性生效（方案 §2.2 表）。
 */
@Data
@Schema(description = "编号规则配置：sys_number_rule.field_rule 的结构化映射，items 有序数组按顺序拼接渲染")
public class SysNumberRuleConfigDo {

    /** 规则项类型：text 文本（text 常量内容） */
    public static final String TYPE_TEXT = "text";

    /** 规则项类型：datetime 日期时间（format，渲染取取号时刻） */
    public static final String TYPE_DATETIME = "datetime";

    /** 规则项类型：seq 流水号（start/length/padZero/resetPeriod，唯一性锚点） */
    public static final String TYPE_SEQ = "seq";

    /** 规则项类型：random 随机数（min/max/length/padZero/numericOnly，不参与计数） */
    public static final String TYPE_RANDOM = "random";

    /** 流水重置周期：不重置 */
    public static final String RESET_NONE = "NONE";

    /** 流水重置周期：按天 */
    public static final String RESET_DAY = "DAY";

    /** 流水重置周期：按 ISO-8601 周（含第一个周四的周为第 1 周） */
    public static final String RESET_WEEK = "WEEK";

    /** 流水重置周期：按月 */
    public static final String RESET_MONTH = "MONTH";

    /** 流水重置周期：按年 */
    public static final String RESET_YEAR = "YEAR";

    /** 拼接最大长度上限（C5，对齐业务单号列宽 varchar(64)） */
    public static final int MAX_CODE_LENGTH = 64;

    /** 规则项有序列表（渲染按数组顺序拼接）；为空即空规则，校验必不通过（C1） */
    @Schema(description = "规则项有序列表（渲染按数组顺序拼接）；为空即空规则，校验必不通过（C1）")
    private List<Item> items;

    /**
     * 规则项
     *
     * @description 公共属性 id（隐藏，前端生成）/ name（展示名）/ type；各类型专属属性
     *              按方案 §2.2 平铺，仅对应类型生效，其余属性为空。
     */
    @Data
    @Schema(description = "规则项：公共属性 id / name / type，各类型专属属性平铺，仅对应类型生效")
    public static class Item {

        /** 项 id（前端新增时生成 ci- 前缀 + 十六进制；seq 项 id 为计数器锚点，已保存规则不可变更 C7） */
        @Schema(description = "项 id（前端新增时生成 ci- 前缀；seq 项 id 为计数器锚点，已保存规则不可变更 C7）")
        private String id;

        /** 项类型：text / datetime / seq / random */
        @Schema(description = "项类型", allowableValues = {"text", "datetime", "seq", "random"})
        private String type;

        /** 展示名 */
        @Schema(description = "展示名")
        private String name;

        /** text：常量内容（非空，1..32 字符） */
        @Schema(description = "text：常量内容（非空，1..32 字符）")
        private String text;

        /** datetime：日期格式（白名单或仅含 y M d H m s 与 - / : 空格 的自定义组合） */
        @Schema(description = "datetime：日期格式（白名单或仅含 y M d H m s 与 - / : 空格 的自定义组合）")
        private String format;

        /** seq：起始值（≥ 0，首个流水号） */
        @Schema(description = "seq：起始值（≥ 0，首个流水号）")
        private Integer start;

        /** seq/random：长度（seq 1..9，容量 10^length−1；random 渲染宽度） */
        @Schema(description = "seq/random：长度（seq 1..9，容量 10^length−1；random 渲染宽度）")
        private Integer length;

        /** seq/random：是否补"0"（渲染用，长度不足左侧补零） */
        @Schema(description = "seq/random：是否补\"0\"（渲染用，长度不足左侧补零）")
        private Boolean padZero;

        /** seq：重置周期 DAY/WEEK/MONTH/YEAR/NONE（周期键 field_period 派生依据） */
        @Schema(description = "seq：重置周期", allowableValues = {"DAY", "WEEK", "MONTH", "YEAR", "NONE"})
        private String resetPeriod;

        /** random：最小值（numericOnly=true 时生效，min ≤ max） */
        @Schema(description = "random：最小值（numericOnly=true 时生效，min ≤ max）")
        private Integer min;

        /** random：最大值（numericOnly=true 时生效，min ≤ max） */
        @Schema(description = "random：最大值（numericOnly=true 时生效，min ≤ max）")
        private Integer max;

        /** random：仅数字（true 时 min/max/length 生效；false 时仅 length 生效，字符集 A-Za-z0-9） */
        @Schema(description = "random：仅数字（true 时 min/max/length 生效；false 时仅 length 生效，字符集 A-Za-z0-9）")
        private Boolean numericOnly;
    }
}
