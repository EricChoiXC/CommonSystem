package com.eric.base.sys.form.enums;

/**
 * 表单组件类别
 *
 * @author Eric
 * @date 2026/11/24
 * @description 组件物料的 category 分类（功能设计 §1.1 / §1.3 组件全集定稿清单 R03），
 *              对齐组件文档「数据 / 内容 / 控制」分类 + R03 一期新增布局类。类别仅
 *              Schema 内取值域、不入库列（数据库分册 §8.2 附注）；后端仅持 type 白名单
 *              常量 + 本枚举（功能设计 §7——组件物料是前端概念，后端不建组件注册表），
 *              物料栏按类别分组展示、固定属性项按类别裁剪（§1.1）。
 */
public enum SysFormComponentCategory {

    /** 数据类（12 组件）：input / textarea / number / choice / select / date / switch / cascade / formula / datasource / addressbook / attachment，承载表单数据（有字段码 / 必填 / 默认值） */
    DATA,
    /** 内容类（4 组件）：detailTable / table / text / image——detailTable 为数据容器特例（有字段码与行结构），table / text / image 不承载数据（无字段码 / 必填 / 默认值） */
    CONTENT,
    /** 布局类（2 组件，R03 一期新增）：group / grid，纯排版容器（无字段码 / 必填 / 说明 / 默认值，分组即区块标题） */
    LAYOUT,
    /** 控制类（1 组件）：permission 仅登记 type 与元数据协议位——不注册运行时物料、物料栏不展示、不进 Schema fields，消费形态随 v1.8 BPM 由流程侧下发字段权限覆盖（功能设计 §6.6） */
    CONTROL
}
