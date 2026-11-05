package com.common.system.sys.i18n.controller;

import com.common.system.common.base.controller.BaseCrudController;
import com.common.system.common.base.model.BaseResponse;
import com.common.system.common.resolver.AuthorizationContext;
import com.common.system.sys.i18n.model.SysI18nLanguage;
import com.common.system.sys.i18n.request.SysI18nLanguageRequest;
import com.common.system.sys.i18n.resolver.I18nResolver;
import com.common.system.sys.i18n.service.SysI18nLanguageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.common.system.sys.i18n.properties.SysI18nProperties.*;

/**
 * 多语言-语言管理接口
 *
 * @author Eric
 * @date 2026/11/5
 * @description 语言管理页与登录页语言下拉的请求入口（任务清单 I18N3-BE-001，5 接口）：
 *              只负责接收请求、基础校验、执行最终鉴权并调用 Service 返回统一响应。
 *              <ul>
 *              <li>语言列表：免鉴权（登录页语言下拉需要，登录前可访问）；</li>
 *              <li>新增 / 编辑 / 删除 / 设默认：仅 SYS_I18N_ADMIN（超管 SYS_ADMIN 由
 *                  I18nResolver 统一放行）。</li>
 *              </ul>
 *              删除级联（D4）：同事务删除该语言全部翻译、用户语言登记改绑当前默认语言；
 *              默认语言禁止删除与停用（A2）；接口路径遵循 /backend/sys/i18n/language/*。
 */
@RestController
@RequestMapping("${project.request.prefix}/sys/i18n/language")
@Tag(name = "多语言-语言管理", description = "语言清单维护：语言列表（免鉴权，登录页下拉复用）、"
        + "新增/编辑/删除/设默认（仅 SYS_I18N_ADMIN）；默认语言禁止删除与停用")
public class SysI18nLanguageController extends BaseCrudController<SysI18nLanguage, SysI18nLanguageService, I18nResolver> {

    public SysI18nLanguageController(SysI18nLanguageService baseService, I18nResolver baseResolver,
                                     ApplicationEventPublisher eventPublisher) {
        super(baseService, baseResolver, eventPublisher);
    }

    /**
     * 语言列表：GET /backend/sys/i18n/language/list
     * 免鉴权（登录页语言下拉需要）：条件均可空，全量返回（按 field_order 升序、field_code 升序），
     * 登录页下拉传 fieldEnable=true 仅取启用语言；语言行数少，不分页。
     *
     * @param fieldName   名称模糊条件（可空）
     * @param fieldCode   语言码模糊条件（可空）
     * @param fieldEnable 启用状态精确条件（可空；"true"/"false"）
     * @return 统一响应，data 为语言列表
     */
    @GetMapping("/list")
    @SecurityRequirements
    @Operation(summary = "语言列表", description = "无需鉴权（登录页语言下拉需要）；条件均可空，"
            + "按 field_order 升序、field_code 升序全量返回；fieldEnable=true 仅取启用语言，不分页")
    public BaseResponse<List<SysI18nLanguage>> list(
            @Parameter(description = "名称模糊条件") @RequestParam(value = "fieldName", required = false) String fieldName,
            @Parameter(description = "语言码模糊条件") @RequestParam(value = "fieldCode", required = false) String fieldCode,
            @Parameter(description = "启用状态（true/false，缺省全部）") @RequestParam(value = "fieldEnable", required = false) Boolean fieldEnable) {
        return BaseResponse.success(getBaseService().list(fieldName, fieldCode, fieldEnable), null);
    }

    /**
     * 新增语言：POST /backend/sys/i18n/language/save
     * 请求体 { body: { fieldCode, fieldName, fieldEnable?, fieldOrder? } }；
     * fieldCode 唯一（重复返回参数错误）；field_default 不接受请求写入（新增一律非默认）。
     *
     * @param request 保存请求
     * @return 统一响应，data 为保存后的语言行
     */
    @PostMapping("/save")
    @Operation(summary = "新增语言", description = "鉴权动作 sys.i18n.language.save（仅 SYS_I18N_ADMIN）；"
            + "请求体 { body: { fieldCode, fieldName, fieldEnable?, fieldOrder? } }，"
            + "fieldCode 唯一且仅允许字母/数字/下划线；field_default 不接受请求写入（新增一律非默认）")
    public BaseResponse<SysI18nLanguage> save(@RequestBody SysI18nLanguageRequest request) {
        authorize(new AuthorizationContext(ACTION_LANGUAGE_SAVE));
        return BaseResponse.success(getBaseService().save(null == request ? null : request.getBody()), null);
    }

    /**
     * 编辑语言：PATCH /backend/sys/i18n/language/update
     * 请求体 { body: { fieldId, fieldName?, fieldEnable?, fieldOrder? } }；
     * fieldCode / field_default 不可修改；默认语言禁止停用（A2）。
     *
     * @param request 编辑请求
     * @return 统一响应，data 为更新后的语言行
     */
    @PatchMapping("/update")
    @Operation(summary = "编辑语言", description = "鉴权动作 sys.i18n.language.update（仅 SYS_I18N_ADMIN）；"
            + "请求体 { body: { fieldId, fieldName?, fieldEnable?, fieldOrder? } }，"
            + "fieldCode 与 field_default 不可修改；默认语言禁止停用")
    public BaseResponse<SysI18nLanguage> update(@RequestBody SysI18nLanguageRequest request) {
        authorize(new AuthorizationContext(ACTION_LANGUAGE_UPDATE));
        return BaseResponse.success(getBaseService().update(null == request ? null : request.getBody()), null);
    }

    /**
     * 删除语言：DELETE /backend/sys/i18n/language/delete?fieldId=${id}
     * 默认语言禁止删除（A2）；级联 D4：同事务删除该语言全部翻译、
     * 用户语言登记改绑当前默认语言（不删行）。
     *
     * @param fieldId 语言 ID
     * @return 统一响应
     */
    @DeleteMapping("/delete")
    @Operation(summary = "删除语言", description = "鉴权动作 sys.i18n.language.delete（仅 SYS_I18N_ADMIN）；"
            + "默认语言禁止删除；级联删除该语言全部翻译，用户语言登记改绑当前默认语言（D4，不删行）")
    public BaseResponse<Void> delete(
            @Parameter(description = "语言 ID", required = true) @RequestParam("fieldId") String fieldId) {
        authorize(ACTION_LANGUAGE_DELETE, fieldId);
        getBaseService().delete(fieldId);
        return BaseResponse.<Void>success("success", null, null);
    }

    /**
     * 设默认语言：PATCH /backend/sys/i18n/language/setDefault
     * 请求体 { body: { fieldId } }；互斥切换：同事务先清旧默认再设新默认，
     * 全局始终恰一条默认语言；停用语言不可设为默认（A2）。
     *
     * @param request 请求体，body.fieldId 为目标语言 ID
     * @return 统一响应，data 为切换后的默认语言行
     */
    @PatchMapping("/setDefault")
    @Operation(summary = "设默认语言", description = "鉴权动作 sys.i18n.language.setDefault（仅 SYS_I18N_ADMIN）；"
            + "请求体 { body: { fieldId } }；互斥切换：同事务先清全部默认标记再设新默认，"
            + "全局始终恰一条默认语言；停用语言不可设为默认")
    public BaseResponse<SysI18nLanguage> setDefault(@RequestBody SysI18nLanguageRequest request) {
        SysI18nLanguage body = null == request ? null : request.getBody();
        String fieldId = null == body ? null : body.getFieldId();
        authorize(ACTION_LANGUAGE_SET_DEFAULT, fieldId);
        return BaseResponse.success(getBaseService().setDefault(fieldId), null);
    }
}
