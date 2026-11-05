package com.common.system.sys.i18n.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 多语言-翻译导入结果视图对象
 *
 * @author Eric
 * @date 2026/11/5
 * @description 导入结果反馈：updated 为按文件实际包含的 (key, language) 更新的条数，
 *              skippedKeys 为文件中出现但 sys_i18n_key 未登记而跳过的 key 列表，
 *              skippedLanguages 为语言码无效（非启用语言）而跳过的列头列表；
 *              越界项一律跳过且不插入资源项（D5 口径），导入记录写人工编辑标记
 *              （field_update_time），导入后刷新缓存。
 */
@Data
@Schema(description = "翻译导入结果：更新条数 + 跳过的 key / 语言列反馈")
public class SysI18nTranslationImportResultVo {

    /** 实际更新的翻译条数（仅文件实际包含且已登记的 (key, language)） */
    @Schema(description = "实际更新的翻译条数")
    private int updated;

    /** 文件中出现但 sys_i18n_key 未登记而跳过的 key 列表 */
    @Schema(description = "未登记而跳过的 key 列表")
    private List<String> skippedKeys;

    /** 语言码无效（非启用语言）而跳过的列头列表 */
    @Schema(description = "无效语言码而跳过的列头列表")
    private List<String> skippedLanguages;
}
