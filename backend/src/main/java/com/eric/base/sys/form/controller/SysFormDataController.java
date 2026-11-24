package com.eric.base.sys.form.controller;

import com.eric.base.common.base.model.BaseResponse;
import com.eric.base.common.base.model.Pages;
import com.eric.base.common.module.annotation.ModuleGroup;
import com.eric.base.common.resolver.AuthorizationContext;
import com.eric.base.common.resolver.AuthorizationHelper;
import com.eric.base.sys.form.model.SysFormSchemaDo;
import com.eric.base.sys.form.request.SysFormDataListRequest;
import com.eric.base.sys.form.request.SysFormDataQuery;
import com.eric.base.sys.form.request.SysFormDataSaveRequest;
import com.eric.base.sys.form.request.SysFormDataValidateRequest;
import com.eric.base.sys.form.resolver.FormResolver;
import com.eric.base.sys.form.response.SysFormDataDetailVo;
import com.eric.base.sys.form.response.SysFormDataSavedVo;
import com.eric.base.sys.form.response.SysFormDataValidateVo;
import com.eric.base.sys.form.response.SysFormDataVo;
import com.eric.base.sys.form.service.SysFormDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static com.eric.base.sys.form.properties.SysFormConstants.*;

/**
 * 表单数据接口
 *
 * @author Eric
 * @date 2026/11/24
 * @description 自定义表单数据面（表单数据引擎六动作组）的请求入口（任务 F15-BE-006，
 *              API 分册 §4，Controller 拆分定稿见分册 §9——按次级模块拆两个 Controller，
 *              先例流程）：只负责接收请求、基础校验、执行最终鉴权并调用
 *              {@link SysFormDataService} 返回统一响应，业务编排（保存校验链 S0~S7、
 *              显隐复算裁剪、级联复核、公式重算、数据归属判定与强制过滤）全部在 Service。
 *              本模块 Service 为非 IBaseService 形态，不继承 BaseCrudController 骨架，
 *              最终鉴权委托 {@link AuthorizationHelper}（先例 SysFormulaDesignerController）：
 *              数据面六动作登录且账号启用即可（R06 定稿，分册 §4.8——数据归属「本人 /
 *              管理员全量」由 Service 层按会话强制判定与过滤，不信任请求体传入的用户标识，
 *              经 {@link FormResolver} 按动作判定）。
 *              documentId 传递按分册 §2.3：load / delete 传数据行 fieldId，schema / save /
 *              validate / list 不传。
 *              接口路径遵循 /backend/{根模块}/{模块}/{次级模块}/{请求方法}；
 *              类级 {@code @ModuleGroup(moduleGroupKey = "form")} 使 6 接口受模块组
 *              开关统一控制（key 登记于 properties/sys/form/moduleGroup.yml，F15-OPS-001）。
 */
@RestController
@RequestMapping("${project.request.prefix}/sys/form/data")
@ModuleGroup(moduleGroupKey = "form")
@Tag(name = "自定义表单-表单数据", description = "表单数据引擎：填写 Schema 获取、保存、"
        + "校验、回显、列表与删除；登录即可，数据归属按创建人判定")
public class SysFormDataController {

    private final SysFormDataService formDataService;

    private final FormResolver formResolver;

    private final AuthorizationHelper authorizationHelper;

    public SysFormDataController(SysFormDataService formDataService, FormResolver formResolver,
                                 AuthorizationHelper authorizationHelper) {
        this.formDataService = formDataService;
        this.formResolver = formResolver;
        this.authorizationHelper = authorizationHelper;
    }

    /**
     * 获取填写用 Schema：GET /backend/sys/form/data/schema?formId=${formId}
     * 返回 field_current_version_id 指向版本行的完整 Schema（协议头 version 回填发布版本号），
     * 填写页（/form/fill/:formId）渲染新实例必需——定义面 /define/load 仅 SYS_FORM_ADMIN
     * 可用，填写人（登录即可）不可经其取 Schema；前置拒绝：表单不存在（CS_FORM_NOT_FOUND）、
     * 表单停用（CS_FORM_DISABLED，填写入口关闭）、无已发布版本（CS_FORM_NO_PUBLISHED_VERSION）。
     *
     * @param formId 表单主档 ID
     * @return 统一响应，data 为当前发布版本 Schema
     */
    @GetMapping("/schema")
    @Operation(summary = "获取填写用 Schema", description = "鉴权动作 " + ACTION_FORM_DATA_SCHEMA
            + "（登录即可）；按 formId 返回 field_current_version_id 指向版本行的完整 Schema"
            + "（协议头 version 回填发布版本号），填写页渲染新实例必需——定义面 /define/load 仅 "
            + "SYS_FORM_ADMIN 可用，填写人不可经其取 Schema；前置拒绝：表单不存在 "
            + "CS_FORM_NOT_FOUND、停用 CS_FORM_DISABLED（填写入口关闭）、无已发布版本 "
            + "CS_FORM_NO_PUBLISHED_VERSION；数据详情页不经本接口取 Schema（经 /data/load 取"
            + "实例锁定版本 Schema）")
    public BaseResponse<SysFormSchemaDo> schema(
            @Parameter(description = "表单主档 ID", required = true)
            @RequestParam("formId") String formId) {
        authorize(new AuthorizationContext(ACTION_FORM_DATA_SCHEMA));
        return BaseResponse.success(formDataService.schema(formId), null);
    }

    /**
     * 保存表单数据：POST /backend/sys/form/data/save
     * 一期仅新增语义（无实例更新接口）；请求体 { fieldId, formId, businessId?, formData }
     * （fieldId 经 /backend/common/initID 预生成，formData 不含 formVersionId——一期恒取
     * 当前发布版本）；服务端执行保存校验链（S0~S7，分册 §4.3）：S1 未知字段拒绝 → S3 显隐
     * 裁剪 → S2/S4 合并裁决 → S5 级联复核 → S6 公式重算（以服务端结果为准落库，ADR-09）；
     * field_creator_id 绑定会话用户（R06）。
     *
     * @param request 保存请求
     * @return 统一响应，data = { fieldId, formData }（formData 为服务端最终落库形态）
     */
    @PostMapping("/save")
    @Operation(summary = "保存表单数据", description = "鉴权动作 " + ACTION_FORM_DATA_SAVE
            + "（登录即可，数据归属按创建人判定）；请求体 { fieldId, formId, businessId, "
            + "formData }（fieldId 经 /backend/common/initID 预生成，formData 不含 "
            + "formVersionId——一期恒取当前发布版本）；服务端按当前发布版本 Schema 执行保存"
            + "校验链（S0~S7）：未知字段拒绝 CS_FORM_UNKNOWN_FIELD → 显隐复算与提交裁剪 →"
            + " 基础 + 动态校验合并裁决（失败 CS_FORM_DATA_INVALID 首条中文摘要）→ 级联选项"
            + "复核 CS_FORM_CASCADE_OPTION_INVALID → 公式以服务端重算为准落库（ADR-09 差额"
            + "不报错）；一期仅新增（无实例更新接口），data = { fieldId, formData }"
            + "（服务端最终落库形态，宿主以此刷新展示）")
    public BaseResponse<SysFormDataSavedVo> save(@RequestBody SysFormDataSaveRequest request) {
        authorize(new AuthorizationContext(ACTION_FORM_DATA_SAVE));
        return BaseResponse.success(formDataService.save(request), null);
    }

    /**
     * 独立校验入口：POST /backend/sys/form/data/validate
     * 与 save 完全共用同一校验引擎与校验链（S0~S6，同序同语义）、不落库；字段级结果以
     * data.errors 结构化承载（HTTP 200、code 为成功，对齐公式 validate / simulate 先例）；
     * S0/S1/S5/S6 资源与确定性拒绝仍以 CS_FORM_* / CS_FORMULA_* 错误码异常返回。
     *
     * @param request 校验请求（与 save 同构，无 fieldId）
     * @return 统一响应，data = { pass, errors, formData }
     */
    @PostMapping("/validate")
    @Operation(summary = "表单数据独立校验", description = "鉴权动作 " + ACTION_FORM_DATA_VALIDATE
            + "（登录即可）；与 data/save 共用校验引擎的独立预检入口，不落库，请求体与 save "
            + "同构（formId / businessId / formData，无 fieldId）；字段级结果经 data.errors "
            + "结构化承载（{ fieldCode, rowIndex, ruleId, message }，HTTP 200、code 为成功），"
            + "pass = true 时 formData 回带服务端重算/裁剪后的最终数据预览；S0/S1/S5/S6 资源与"
            + "确定性拒绝仍以 CS_FORM_* / CS_FORMULA_* 错误码异常返回（与 save 同语义）")
    public BaseResponse<SysFormDataValidateVo> validate(@RequestBody SysFormDataValidateRequest request) {
        authorize(new AuthorizationContext(ACTION_FORM_DATA_VALIDATE));
        return BaseResponse.success(formDataService.validate(request), null);
    }

    /**
     * 数据详情 / 回显：GET /backend/sys/form/data/load?fieldId=${id}
     * 返回数据本体（formData 读取层裁剪后形态）+ 实例锁定版本 Schema（原则 2「在途实例用
     * 原版本」，渲染器详情态 / 编辑态复用）；前置拒绝：数据行不存在（CS_FORM_DATA_NOT_FOUND）、
     * 非本人且非管理员（CS_FORM_DATA_FORBIDDEN，归属判定见分册 §4.8）。
     *
     * @param fieldId 数据行 ID
     * @return 统一响应，data 为数据详情（含实例锁定版本 Schema）
     */
    @GetMapping("/load")
    @Operation(summary = "表单数据详情", description = "鉴权动作 " + ACTION_FORM_DATA_LOAD
            + "（登录即可 + 数据归属）；按数据行 ID 返回数据本体 + 实例锁定版本 Schema"
            + "（原则 2「在途实例用原版本」，渲染器详情态 / 编辑态复用）；formData 为读取层"
            + "裁剪后形态（提交时刻被裁剪的隐藏字段不下发、permission=hidden 字段不下发）；"
            + "前置拒绝：数据行不存在 CS_FORM_DATA_NOT_FOUND、非本人且非管理员 "
            + "CS_FORM_DATA_FORBIDDEN（归属判定按创建人，分册 §4.8）")
    public BaseResponse<SysFormDataDetailVo> load(
            @Parameter(description = "数据行 ID", required = true)
            @RequestParam("fieldId") String fieldId) {
        authorize(ACTION_FORM_DATA_LOAD, fieldId);
        return BaseResponse.success(formDataService.load(fieldId), null);
    }

    /**
     * 数据列表：POST /backend/sys/form/data/list
     * 请求体 { body, pages, sortField, sortOrder }；单一表单维度（body.fieldFormId 必填，
     * 表单不存在 → CS_FORM_NOT_FOUND）；归属强制过滤（R06）：非管理员由 Service 层强制
     * 覆盖 fieldCreatorId 为会话用户（不信任请求体用户标识），管理员保留传入筛选；列表行
     * 不携带 formData 字段值内容（详情经 /load）。
     *
     * @param request 列表请求（条件 + 分页）
     * @return 统一响应，data 为数据列表行，metadata.total 为满足条件的总数
     */
    @PostMapping("/list")
    @Operation(summary = "表单数据列表", description = "鉴权动作 " + ACTION_FORM_DATA_LIST
            + "（登录即可 + 归属强制过滤）；请求体 { body: { fieldFormId（必填）, "
            + "fieldBusinessId, fieldProcessInstanceId（v1.8 预留筛选一期恒空）, "
            + "fieldCreatorId（仅管理员生效）, fieldCreateTimeBegin/End }, pages, "
            + "sortField, sortOrder }；单一表单维度（表单不存在 CS_FORM_NOT_FOUND），非管理员"
            + "由 Service 层强制过滤为本人数据（不信任请求体用户标识，分册 §4.8）；列表行"
            + "不携带 formData 字段值内容（详情经 /data/load），总数经 metadata.total 返回，"
            + "排序固定创建时间降序")
    public BaseResponse<List<SysFormDataVo>> list(@RequestBody SysFormDataListRequest request) {
        authorize(new AuthorizationContext(ACTION_FORM_DATA_LIST));
        SysFormDataQuery body = null == request ? null : request.getBody();
        Pages pages = null == request ? null : request.getPages();
        List<SysFormDataVo> list = formDataService.list(body, pages);
        long total = formDataService.count(body);
        return BaseResponse.success(list, Map.of("total", total));
    }

    /**
     * 删除数据：DELETE /backend/sys/form/data/delete?fieldId=${id}
     * 创建人或管理员可删（R06 定稿）；前置拒绝：数据行不存在（CS_FORM_DATA_NOT_FOUND）、
     * 非创建人且非管理员（CS_FORM_DATA_FORBIDDEN）；物理删除（sys_form_data 流水结构，
     * 一期只增不改 + 物理删除）。
     *
     * @param fieldId 数据行 ID
     * @return 统一响应
     */
    @DeleteMapping("/delete")
    @Operation(summary = "删除表单数据", description = "鉴权动作 " + ACTION_FORM_DATA_DELETE
            + "（登录即可 + 数据归属）；创建人或管理员可删（非创建人且非管理员拒绝 "
            + "CS_FORM_DATA_FORBIDDEN，归属判定按创建人）；数据行不存在 "
            + "CS_FORM_DATA_NOT_FOUND；物理删除（sys_form_data 流水结构，一期只增不改 + "
            + "物理删除），前端删除须二次确认（列表页通用口径）")
    public BaseResponse<Void> delete(
            @Parameter(description = "数据行 ID", required = true)
            @RequestParam("fieldId") String fieldId) {
        authorize(ACTION_FORM_DATA_DELETE, fieldId);
        formDataService.delete(fieldId);
        return BaseResponse.success(null, null);
    }

    /* ==================== 鉴权（AuthorizationHelper 统一入口） ==================== */

    /**
     * 执行最终鉴权：未登录或凭证已失效抛 UNAUTHORIZED，已登录但缺少权限时
     * 发布鉴权拒绝事件并抛 FORBIDDEN，由全局异常处理器统一转换。
     */
    private void authorize(AuthorizationContext context) {
        authorizationHelper.authorize(formResolver, context);
    }

    private void authorize(String action, String documentId) {
        authorizationHelper.authorize(formResolver, action, documentId);
    }
}
