package com.common.system.sys.i18n.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 多语言-翻译导出视图对象（EasyExcel 行模型）
 *
 * @author Eric
 * @date 2026/11/5
 * @description 导出文件行 = 资源名 + 按 field_order 排序的各语言列；
 *              dynamicLanguages 供服务层用 EasyExcel 动态头写出（列数随启用语言数变化），
 *              固定字段 resourceName 为「模块 + key」资源名，messages 保持语言列顺序。
 *              导出文件可直接回传导入（round-trip）：导入按列头语言码反解析。
 */
@Data
@Schema(description = "翻译导出行：资源名 + 各语言列（EasyExcel 动态头）")
public class SysI18nTranslationExportVo {

    /** 资源名（格式：模块 + key，如 org | org.user.save） */
    @Schema(description = "资源名（模块 + key）")
    private String resourceName;

    /** 各语言翻译文本（与导出列头语言码顺序一致） */
    @Schema(description = "各语言翻译文本（与列头语言码顺序一致）")
    private List<String> messages;

    /** 语言码列头（与 messages 顺序一致，供 EasyExcel 动态头与导入反解析） */
    @Schema(description = "语言码列头（与 messages 顺序一致）", hidden = true)
    private List<String> languageCodes;

    /** key → 各语言文本映射（供 EasyExcel 动态头写出） */
    @Schema(description = "key → 各语言文本映射", hidden = true)
    private Map<String, String> messageMap;
}
