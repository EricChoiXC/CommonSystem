package com.common.system.sys.i18n.controller;

import com.common.system.sys.i18n.service.SysI18nDeployService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 多语言-前端语言包接口
 *
 * @author Eric
 * @date 2026/11/5
 * @description 前端语言包动态接口（D2 决策，任务清单 I18N5-BE-001）：
 *              {@code GET /locales/{code}.json}，免鉴权（登录前即需加载语言包），
 *              由部署后的 DB / 缓存支撑，不依赖构建产物可写性。
 *              仅输出<b>启用语言</b>的全量翻译（扁平 {key: message}，vue-i18n
 *              setLocaleMessage 直用）；未启用或未知语言码返回错误，不泄露空包。
 *              注意：本接口不在 /backend 前缀下，不经过 CurrentUserFilter；
 *              带扩展名的路径由 SpaWebConfig 保持接口处理（不回退 index.html）。
 */
@RestController
@Tag(name = "多语言-语言包", description = "前端语言包动态接口 /locales/{code}.json（免鉴权，D2）；"
        + "仅输出启用语言的全量翻译，扁平 {key: message}")
public class SysI18nLocalesController {

    private final SysI18nDeployService deployService;

    public SysI18nLocalesController(SysI18nDeployService deployService) {
        this.deployService = deployService;
    }

    /**
     * 前端语言包：GET /locales/{code}.json
     * 免鉴权（登录前即需加载语言包）；路径参数为语言码（如 zh_CN）；
     * 仅输出启用语言的全量翻译（扁平 {key: message}）；未启用或未知语言码返回错误。
     *
     * @param code 语言码（如 zh_CN、en_US）
     * @return 扁平翻译 Map（{key: message}），直接作为 vue-i18n 语言包
     */
    @GetMapping("/locales/{code}.json")
    @SecurityRequirements
    @Operation(summary = "前端语言包", description = "无需鉴权（前端语言包动态接口，登录前即需加载，D2 决策）；"
            + "路径参数为语言码（如 zh_CN），仅输出启用语言的全量翻译（扁平 {key: message}）；"
            + "未启用或未知语言码返回错误，不泄露空包")
    public Map<String, String> locales(
            @Parameter(description = "语言码（如 zh_CN）", required = true) @PathVariable("code") String code) {
        return deployService.localePackage(code);
    }
}
