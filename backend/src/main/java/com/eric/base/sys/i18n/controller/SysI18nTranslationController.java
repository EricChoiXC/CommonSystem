package com.eric.base.sys.i18n.controller;

import com.eric.base.common.base.model.BaseResponse;
import com.eric.base.common.base.model.ImportResultVo;
import com.eric.base.common.resolver.AuthorizationContext;
import com.eric.base.common.resolver.AuthorizationHelper;
import com.eric.base.sys.i18n.properties.SysI18nProperties;
import com.eric.base.sys.i18n.request.SysI18nTranslationListRequest;
import com.eric.base.sys.i18n.request.SysI18nTranslationResetRequest;
import com.eric.base.sys.i18n.request.SysI18nTranslationUpdateRequest;
import com.eric.base.sys.i18n.resolver.I18nResolver;
import com.eric.base.sys.i18n.service.SysI18nTranslationService;
import com.eric.base.sys.i18n.response.SysI18nModelVo;
import com.eric.base.sys.i18n.response.SysI18nTranslationMatrixVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static com.eric.base.sys.i18n.properties.SysI18nProperties.ACTION_MODEL_LIST;
import static com.eric.base.sys.i18n.properties.SysI18nProperties.ACTION_TRANSLATION_EXPORT;
import static com.eric.base.sys.i18n.properties.SysI18nProperties.ACTION_TRANSLATION_IMPORT;
import static com.eric.base.sys.i18n.properties.SysI18nProperties.ACTION_TRANSLATION_LIST;
import static com.eric.base.sys.i18n.properties.SysI18nProperties.ACTION_TRANSLATION_RESET;
import static com.eric.base.sys.i18n.properties.SysI18nProperties.ACTION_TRANSLATION_UPDATE;

/**
 * 多语言-翻译维护接口
 *
 * @author Eric
 * @date 2026/11/5
 * @description 翻译维护页的请求入口（任务清单 I18N4-BE-001，4 接口）：
 *              只负责接收请求、执行最终鉴权并调用 Service 返回统一响应。
 *              <ul>
 *              <li>模块聚合列表：登录即可（翻译矩阵页模块下拉）；</li>
 *              <li>矩阵查询 / 批量更新 / 重置：仅 SYS_I18N_ADMIN（超管 SYS_ADMIN 由
 *                  I18nResolver 统一放行）。</li>
 *              </ul>
 *              批量更新仅对提交的变更项生效并置人工编辑标记（field_update_time = now），
 *              重置还原资源文件值或删除无资源值的翻译；接口路径遵循 /backend/sys/i18n/*。
 */
@RestController
@RequestMapping("${project.request.prefix}/sys/i18n")
@Tag(name = "多语言-翻译维护", description = "模块聚合列表（登录即可）、翻译矩阵查询/批量更新/重置"
        + "（仅 SYS_I18N_ADMIN）；人工编辑标记 field_update_time 非空即受启动同步保护")
public class SysI18nTranslationController {

    private final SysI18nTranslationService translationService;
    private final I18nResolver baseResolver;
    private final AuthorizationHelper authorizationHelper;

    public SysI18nTranslationController(SysI18nTranslationService translationService,
                                        I18nResolver baseResolver,
                                        AuthorizationHelper authorizationHelper) {
        this.translationService = translationService;
        this.baseResolver = baseResolver;
        this.authorizationHelper = authorizationHelper;
    }

    /**
     * 模块聚合列表：GET /backend/sys/i18n/model/list
     * 登录即可：聚合 sys_i18n_key 去重 field_model，显示名按末段路径匹配 MODEL_NAME（取不到回退 field_model），
     * 供翻译矩阵页「所属模块」下拉。
     *
     * @return 统一响应，data 为模块聚合项列表
     */
    @GetMapping("/model/list")
    @Operation(summary = "模块聚合列表", description = "鉴权动作 sys.i18n.model.list（登录即可）；"
            + "聚合 sys_i18n_key 去重 field_model，显示名按末段路径匹配各模块 application.yml 的"
            + " MODEL_NAME（禁止 MODEL_KEY 等值匹配），取不到回退 field_model")
    public BaseResponse<List<SysI18nModelVo>> modelList() {
        authorizationHelper.authorize(baseResolver, new AuthorizationContext(ACTION_MODEL_LIST));
        return BaseResponse.success(translationService.listModels(), null);
    }

    /**
     * 翻译矩阵：POST /backend/sys/i18n/translation/list
     * 请求体 { fieldModel }；行 = field_key 升序，列 = 启用语言按 field_order 升序，
     * matrix[key][language] 为翻译文本（无翻译的格缺省），总数（资源行数）经 metadata.total 返回。
     *
     * @param request 矩阵查询请求
     * @return 统一响应，data 为矩阵响应
     */
    @PostMapping("/translation/list")
    @Operation(summary = "翻译矩阵", description = "鉴权动作 " + SysI18nProperties.ACTION_TRANSLATION_LIST
            + "（仅 SYS_I18N_ADMIN）；请求体 { fieldModel }，行 = field_key 升序、列 = 启用语言按"
            + " field_order 升序，matrix[key][language] 为翻译文本，资源行数经 metadata.total 返回")
    public BaseResponse<SysI18nTranslationMatrixVo> translationList(@RequestBody SysI18nTranslationListRequest request) {
        authorizationHelper.authorize(baseResolver, new AuthorizationContext(ACTION_TRANSLATION_LIST));
        SysI18nTranslationMatrixVo vo = translationService.translationList(null == request ? null : request.getFieldModel());
        int total = null == vo || null == vo.getKeys() ? 0 : vo.getKeys().size();
        return BaseResponse.success(vo, Map.of("total", total));
    }

    /**
     * 批量更新翻译：PATCH /backend/sys/i18n/translation/update
     * 请求体 { details: [{ fieldId, fieldMessage }] }（仅提交本次 diff 出的改动项）；
     * 按记录主键定位写 field_message 并置人工编辑标记，完成后刷新受影响语言缓存。
     *
     * @param request 批量更新请求
     * @return 统一响应，data 为更新条数
     */
    @PatchMapping("/translation/update")
    @Operation(summary = "批量更新翻译", description = "鉴权动作 " + SysI18nProperties.ACTION_TRANSLATION_UPDATE
            + "（仅 SYS_I18N_ADMIN）；请求体 { details: [{ fieldId, fieldMessage }] }（仅提交本次改动项），"
            + "写 field_message 并置人工编辑标记（field_update_time = now），完成后刷新受影响语言缓存")
    public BaseResponse<Integer> translationUpdate(@RequestBody SysI18nTranslationUpdateRequest request) {
        authorizationHelper.authorize(baseResolver, new AuthorizationContext(ACTION_TRANSLATION_UPDATE));
        return BaseResponse.success(translationService.updateBatch(request), null);
    }

    /**
     * 重置翻译：PATCH /backend/sys/i18n/translation/reset
     * 请求体 { fieldKey, fieldLanguage }；清人工编辑标记并回写资源文件当前值；
     * 该语言无对应资源文件（或文件中无此 key）→ 直接删除该翻译记录。
     *
     * @param request 重置请求
     * @return 统一响应
     */
    @PatchMapping("/translation/reset")
    @Operation(summary = "重置翻译", description = "鉴权动作 " + SysI18nProperties.ACTION_TRANSLATION_RESET
            + "（仅 SYS_I18N_ADMIN）；请求体 { fieldKey, fieldLanguage }，清人工编辑标记并回写资源文件当前值；"
            + "该语言无对应资源文件（或文件中无此 key）时直接删除该翻译记录")
    public BaseResponse<Void> translationReset(@RequestBody SysI18nTranslationResetRequest request) {
        authorizationHelper.authorize(baseResolver, new AuthorizationContext(ACTION_TRANSLATION_RESET));
        String fieldKey = null == request ? null : request.getFieldKey();
        String fieldLanguage = null == request ? null : request.getFieldLanguage();
        translationService.reset(fieldKey, fieldLanguage);
        return BaseResponse.<Void>success("success", null, null);
    }

    /**
     * 导出翻译：POST /backend/sys/i18n/translation/export
     * 请求体 { fieldModel }；返回 xlsx 文件流：列 = 资源名（模块 + key）+ 启用语言按
     * field_order 升序，首行为语言码列头，round-trip 可回传导入。
     *
     * @param request 导出请求（body.fieldModel 指定模块）
     * @return xlsx 文件流（Content-Disposition: attachment）
     */
    @PostMapping("/translation/export")
    @Operation(summary = "导出翻译", description = "鉴权动作 " + SysI18nProperties.ACTION_TRANSLATION_EXPORT
            + "（仅 SYS_I18N_ADMIN）；请求体 { fieldModel }，返回 xlsx 文件流：列 = 资源名 + 启用语言"
            + "按 field_order 升序，首行为语言码列头，round-trip 可回传导入")
    @ApiResponse(responseCode = "200", description = "翻译 xlsx 文件流",
            content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                    schema = @Schema(type = "string", format = "binary")))
    public ResponseEntity<Resource> translationExport(@RequestBody SysI18nTranslationListRequest request) {
        authorizationHelper.authorize(baseResolver, new AuthorizationContext(ACTION_TRANSLATION_EXPORT));
        String fieldModel = null == request ? null : request.getFieldModel();
        byte[] bytes = translationService.export(fieldModel);
        String fileName = URLEncoder.encode(translationService.buildExportFileName(fieldModel),
                StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new ByteArrayResource(bytes));
    }

    /**
     * 导入翻译：POST /backend/sys/i18n/translation/import（multipart 上传 xlsx）
     * 固定覆盖导入：仅按文件实际包含的 (key, language) 写入；未登记 key、无效语言码列
     * 跳过且不插入资源项（D5）；导入记录写人工编辑标记，导入后刷新缓存。
     * 结果结构迁移公共契约（Q6）：声明 BaseResponse<ImportResultVo>，实际返回子类
     * SysI18nTranslationImportResultVo（公共字段 total/success/failed/errors + 扩展字段）。
     *
     * @param file 翻译 xlsx 文件（列头与导出一致）
     * @return 统一响应，data 为导入结果（ImportResultVo 子类实例）
     */
    @PostMapping("/translation/import")
    @Operation(summary = "导入翻译", description = "鉴权动作 " + SysI18nProperties.ACTION_TRANSLATION_IMPORT
            + "（仅 SYS_I18N_ADMIN）；multipart 上传 xlsx（列头与导出一致，支持 round-trip），"
            + "固定覆盖导入：仅按文件实际包含的 (key, language) 写入并置人工编辑标记；"
            + "未登记 key 与无效语言码列跳过且不插入资源项；导入后刷新缓存；"
            + "返回公共导入结果 total/success/failed/errors（实际为子类，含 skippedKeys/skippedLanguages 扩展）")
    @ApiResponse(responseCode = "200", description = "导入结果：data 为 SysI18nTranslationImportResultVo"
            + "（ImportResultVo 子类，公共字段 total/success/failed/errors + skippedKeys/skippedLanguages 扩展）",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ImportResultVo.class)))
    public BaseResponse<ImportResultVo> translationImport(
            @Parameter(description = "翻译 xlsx 文件", required = true) @RequestParam("file") MultipartFile file) {
        authorizationHelper.authorize(baseResolver, new AuthorizationContext(ACTION_TRANSLATION_IMPORT));
        return BaseResponse.success(translationService.importTranslations(file), null);
    }
}
