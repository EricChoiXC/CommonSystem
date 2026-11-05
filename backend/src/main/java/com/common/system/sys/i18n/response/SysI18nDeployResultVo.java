package com.common.system.sys.i18n.response;

import com.common.system.sys.i18n.model.SysI18nKey;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 多语言-部署结果视图对象
 *
 * @author Eric
 * @date 2026/11/5
 * @description 部署动作（/backend/sys/i18n/deploy/run）的返回对象：
 *              syncedModelCount 为本次同步的资源模块数，syncedKeyCount 为登记的资源项数，
 *              languageCounts 为各语言翻译条数（语言码 → 条数）；
 *              D2 决策下前端语言包为 /locales/{code}.json 动态接口，部署刷新缓存即生效、
 *              无构建产物落盘，故无产物路径字段。
 */
@Data
@Schema(description = "部署结果：同步资源数、各语言翻译条数（语言包为动态接口，无产物落盘）")
public class SysI18nDeployResultVo {

    /** 同步的资源模块数（扫描到的 i18n 子目录数） */
    @Schema(description = "同步的资源模块数（扫描到的 i18n 子目录数）")
    private int syncedModelCount;

    /** 登记的资源项数（sys_i18n_key 处理数） */
    @Schema(description = "登记的资源项数")
    private int syncedKeyCount;

    /** 各语言翻译条数（语言码 → 条数，含未启用语言的既有数据不输出） */
    @Schema(description = "各语言翻译条数（语言码 → 条数）")
    private Map<String, Integer> languageCounts;

    /** 资源行（供后续扩展；当前不使用，保留键顺序语义） */
    @Schema(description = "本次同步涉及的模块列表（field_model，升序）", hidden = true)
    private List<SysI18nKey> unusedKeys;
}
