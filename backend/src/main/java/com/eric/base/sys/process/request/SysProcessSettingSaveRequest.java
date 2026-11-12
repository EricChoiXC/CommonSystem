package com.eric.base.sys.process.request;

import lombok.Data;

import java.util.List;

/**
 * 流程公共配置提交请求对象
 *
 * @author Eric
 * @date 2026/8/21
 * @description 提交仅将有更新（或重置）的配置项保存：请求体 { items: [{ fieldId, fieldValue }] }，
 *              fieldValue 为提交后的配置值（重置场景即默认值）。
 */
@Data
public class SysProcessSettingSaveRequest {

    /** 有变更（或重置）的配置项列表 */
    private List<SysProcessSettingItem> items;

    /** 单个配置项变更 */
    @Data
    public static class SysProcessSettingItem {

        /** 配置项id（common_setting.field_id） */
        private String fieldId;

        /** 配置值 */
        private String fieldValue;
    }
}
