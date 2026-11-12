package com.eric.base.sys.notify.model;

import com.eric.base.common.base.model.MainModel;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 待办主表模型
 *
 * @author Eric
 * @date 2026/8/12
 * @description 对应 sys_notify_todo；与 sys_notify_todo_target 通过 field_id
 *              一对多关联（一个待办发送给多个接收人，见通知待办表结构.md）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysNotifyTodo extends MainModel {

    /** 来源模块（存储 MODEL_KEY，如 SYS-ORG；展示时经 model:name:map 翻译为 MODEL_NAME） */
    private String fieldModelName;

    /** 文档id */
    private String fieldModelId;

    /** 通知类型: 1=待办 2=待阅 */
    private String fieldNotifyType;

    /** 链接 */
    private String fieldUrl;

    /** 内容 */
    private String fieldContent;
}
