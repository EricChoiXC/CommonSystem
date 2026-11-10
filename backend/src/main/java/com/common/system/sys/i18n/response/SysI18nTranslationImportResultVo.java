package com.common.system.sys.i18n.response;

import com.common.system.common.base.model.ImportResultVo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 多语言-翻译导入结果视图对象
 *
 * @author Eric
 * @date 2026/11/5
 * @description 导入结果反馈：公共字段 total / success / failed / errors 由父类
 *              {@link ImportResultVo} 承载（Q6 评审结论：继承扩展，此处不重复声明），
 *              口径见导入方案 9.1——total 为文件实际包含的 (key, language) 格数，
 *              success 为实际写入的翻译格数，failed 为跳过格数汇总（未登记 key 与
 *              无效语言列覆盖的格数），errors 为按跳过 key / 语言逐项生成的提示。
 *              自有字段：skippedKeys 为文件中出现但 sys_i18n_key 未登记而跳过的
 *              key 列表，skippedLanguages 为语言码无效（非启用语言）而跳过的列头列表；
 *              越界项一律跳过且不插入资源项（D5 口径），导入记录写人工编辑标记
 *              （field_update_time），导入后刷新缓存。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "翻译导入结果：公共导入结果（total/success/failed/errors）"
        + " + 跳过的 key / 语言列反馈")
public class SysI18nTranslationImportResultVo extends ImportResultVo {

    /** 文件中出现但 sys_i18n_key 未登记而跳过的 key 列表 */
    @Schema(description = "未登记而跳过的 key 列表")
    private List<String> skippedKeys;

    /** 语言码无效（非启用语言）而跳过的列头列表 */
    @Schema(description = "无效语言码而跳过的列头列表")
    private List<String> skippedLanguages;
}
