package com.eric.base.sys.form.controller;

import com.eric.base.common.base.model.BaseResponse;
import com.eric.base.common.base.model.Pages;
import com.eric.base.common.module.annotation.ModuleGroup;
import com.eric.base.common.resolver.AuthorizationContext;
import com.eric.base.common.resolver.AuthorizationHelper;
import com.eric.base.common.utils.StringUtil;
import com.eric.base.sys.form.model.SysForm;
import com.eric.base.sys.form.request.SysFormDefineSaveRequest;
import com.eric.base.sys.form.request.SysFormDefineUpdateRequest;
import com.eric.base.sys.form.request.SysFormListRequest;
import com.eric.base.sys.form.request.SysFormPublishRequest;
import com.eric.base.sys.form.request.SysFormRollbackRequest;
import com.eric.base.sys.form.request.SysFormUpdateRequest;
import com.eric.base.sys.form.resolver.FormResolver;
import com.eric.base.sys.form.response.SysFormVersionCreatedVo;
import com.eric.base.sys.form.response.SysFormVersionVo;
import com.eric.base.sys.form.response.SysFormVo;
import com.eric.base.sys.form.service.SysFormService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static com.eric.base.sys.form.properties.SysFormConstants.*;

/**
 * 表单定义管理接口
 *
 * @author Eric
 * @date 2026/11/24
 * @description 自定义表单定义面（表单定义与版本管理九动作组）的请求入口（任务 F15-BE-006，
 *              API 分册 §3，Controller 拆分定稿见分册 §9——按次级模块拆两个 Controller，
 *              先例流程）：只负责接收请求、基础校验、执行最终鉴权并调用
 *              {@link SysFormService} 返回统一响应，业务编排（编码查重、Schema 结构校验
 *              阻断、草稿行承载、发布校验链 P1~P4、快照冻结与指针切换、版本状态机）全部
 *              在 Service。
 *              本模块 Service 为非 IBaseService 形态，不继承 BaseCrudController 骨架，
 *              最终鉴权委托 {@link AuthorizationHelper}（先例 SysFormulaController）：
 *              定义面九动作仅表单管理员 SYS_FORM_ADMIN（SYS_ADMIN 由 BaseResolver 自动
 *              放行，经 {@link FormResolver} 按动作判定）。
 *              documentId 传递按分册 §2.3：load / update / enable / delete / publish /
 *              rollback / versions 传表单主档 fieldId（update / enable / publish / rollback
 *              从请求体取出后先做存在性校验再鉴权），list / save 不传。
 *              请求体包装口径：列表与 save / update 走仓库统一 { body } 包装（分册 §2.2
 *              与 §3.3 / §3.5 定稿，先例 SysFormulaSaveRequest / SysCalendarSaveRequest，
 *              前端 api/form.js 同口径）；enable / publish / rollback 为动作型请求、
 *              载荷直传不包装（分册 §3.6 / §3.8 / §3.10）。
 *              接口路径遵循 /backend/{根模块}/{模块}/{次级模块}/{请求方法}；
 *              类级 {@code @ModuleGroup(moduleGroupKey = "form")} 使 9 接口受模块组
 *              开关统一控制（key 登记于 properties/sys/form/moduleGroup.yml，F15-OPS-001）。
 */
@RestController
@RequestMapping("${project.request.prefix}/sys/form/define")
@ModuleGroup(moduleGroupKey = "form")
@Tag(name = "自定义表单-表单定义", description = "表单定义与版本管理：草稿 CRUD、发布、"
        + "版本列表与回滚；需 SYS_FORM_ADMIN")
public class SysFormDefineController {

    private final SysFormService formService;

    private final FormResolver formResolver;

    private final AuthorizationHelper authorizationHelper;

    public SysFormDefineController(SysFormService formService, FormResolver formResolver,
                                   AuthorizationHelper authorizationHelper) {
        this.formService = formService;
        this.formResolver = formResolver;
        this.authorizationHelper = authorizationHelper;
    }

    /**
     * 表单定义列表：POST /backend/sys/form/define/list
     * 请求体 { body, pages: { pageNo, pageSize, needPage }, sortField, sortOrder }（分册
     * §2.2 统一包装）；条件 fieldFormCode / fieldFormName 模糊、fieldStatus / fieldEnable
     * 精确（分册 §3.2 的创建时间区间筛选一期未实现——Mapper QueryConditions 未承载，
     * 请求传入忽略不报错，见分册 §3.2 编码期登记）；排序固定创建时间降序（Mapper 固定序，
     * sortField / sortOrder 受理不参与动态排序）；needPage=false 返回全部时仍随
     * metadata.total 返回总数（对齐 formula / org 先例）。
     *
     * @param request 列表请求（条件 + 分页）
     * @return 统一响应，data 为表单定义列表，metadata.total 为满足条件的总数
     */
    @PostMapping("/list")
    @Operation(summary = "表单定义列表", description = "鉴权动作 " + ACTION_FORM_DEFINE_LIST
            + "（仅 " + ROLE_SYS_FORM_ADMIN + "）；请求体 { body: { fieldFormCode, "
            + "fieldFormName, fieldStatus, fieldEnable }, pages, sortField, sortOrder }，"
            + "fieldFormCode / fieldFormName 模糊、fieldStatus / fieldEnable 精确，"
            + "排序固定创建时间降序，行字段含 hasDraft 预判与 fieldCurrentVersion，"
            + "不携带 Schema 内容（经 /define/load 获取），总数经 metadata.total 返回")
    public BaseResponse<List<SysFormVo>> list(@RequestBody SysFormListRequest request) {
        authorize(new AuthorizationContext(ACTION_FORM_DEFINE_LIST));
        SysForm body = null == request ? null : request.getBody();
        Pages pages = null == request ? null : request.getPages();
        List<SysFormVo> list = formService.list(body, pages);
        long total = formService.count(body);
        return BaseResponse.success(list, Map.of("total", total));
    }

    /**
     * 新建表单：POST /backend/sys/form/define/save
     * 请求体 body 承载保存体 { fieldId, fieldFormCode, fieldFormName, schema? }（fieldId
     * 由前端经 /backend/common/initID 预生成）；服务端动作（事务内）：编码全局查重
     * （重复 → CS_FORM_CODE_DUPLICATED）→ schema 携带时结构校验（失败阻断，缺省时以
     * 空白草稿初始化 fields:[]）→ 创建主档行（field_status = DRAFT）+ 至多一行的 DRAFT
     * 版本行，返回初始化后的详情。
     *
     * @param request 保存请求（body 承载保存体）
     * @return 统一响应，data 为保存后的表单详情（含初始化后的 draftSchema）
     */
    @PostMapping("/save")
    @Operation(summary = "新建表单", description = "鉴权动作 " + ACTION_FORM_DEFINE_SAVE
            + "（仅 " + ROLE_SYS_FORM_ADMIN + "）；请求体 { body: { fieldId, fieldFormCode, "
            + "fieldFormName, schema } }，fieldId 经 /backend/common/initID 预生成；编码"
            + "全局查重（CS_FORM_CODE_DUPLICATED），schema 缺省时以空白草稿初始化、携带时"
            + "结构校验失败阻断（CS_FORM_SCHEMA_INVALID / CS_FORM_SCHEMA_LIMIT_EXCEEDED），"
            + "创建主档行（DRAFT）+ 初始草稿行，返回保存后的详情（含 draftSchema）")
    public BaseResponse<SysFormVo> save(@RequestBody SysFormDefineSaveRequest request) {
        authorize(new AuthorizationContext(ACTION_FORM_DEFINE_SAVE));
        return BaseResponse.success(
                formService.save(null == request ? null : request.getBody()), null);
    }

    /**
     * 表单详情：GET /backend/sys/form/define/load?fieldId=${id}
     * 返回元数据行字段 + 草稿 Schema（draftSchema，设计器编辑对象）+ 当前发布版本 Schema
     * （publishedSchema，再编辑画布初始化对象），供元数据表单页与设计器页回填。
     *
     * @param fieldId 表单主档 ID
     * @return 统一响应，data 为表单详情；不存在返回参数异常（CS_FORM_NOT_FOUND）
     */
    @GetMapping("/load")
    @Operation(summary = "表单详情", description = "鉴权动作 " + ACTION_FORM_DEFINE_LOAD
            + "（仅 " + ROLE_SYS_FORM_ADMIN + "）；按 ID 读取元数据行字段 + 草稿 Schema"
            + "（draftSchema，设计器以之为编辑对象）+ 当前发布版本 Schema（publishedSchema，"
            + "draftSchema 为 null（再编辑场景）时以其初始化画布，首次 /update 由服务端创建"
            + "草稿行）；协议头 formId / formCode / formName 以主档元数据为权威归一（分册 §2.4）")
    public BaseResponse<SysFormVo> load(
            @Parameter(description = "表单主档 ID", required = true)
            @RequestParam("fieldId") String fieldId) {
        authorize(ACTION_FORM_DEFINE_LOAD, fieldId);
        return BaseResponse.success(formService.load(fieldId), null);
    }

    /**
     * 更新表单（元数据 + 草稿 Schema）：PATCH /backend/sys/form/define/update
     * 请求体 body 必须携带 fieldId；fieldFormName 与 schema 至少携带一项（全空拒绝，
     * Service 校验）；只写 DRAFT 版本行——主档 PUBLISHED 且无草稿行时服务端自动创建草稿行
     * 承载本次提交（「再编辑」入口），PUBLISHED / DEPRECATED 终态行内容永不更新（发布
     * 快照不可变）；编码不可变更（本接口不含 fieldFormCode），停用不阻断；返回更新后的详情。
     *
     * @param request 更新请求（body 必须携带 fieldId）
     * @return 统一响应，data 为更新后的表单详情；缺 fieldId 返回 BAD_REQUEST
     */
    @PatchMapping("/update")
    @Operation(summary = "更新表单", description = "鉴权动作 " + ACTION_FORM_DEFINE_UPDATE
            + "（仅 " + ROLE_SYS_FORM_ADMIN + "）；请求体 { body: { fieldId, fieldFormName, "
            + "schema } }，fieldId 必填、fieldFormName 与 schema 至少携带一项（全空拒绝）；"
            + "只写 DRAFT 版本行——已发布且无草稿行时自动创建草稿行承载（「再编辑」入口），"
            + "发布快照（PUBLISHED / DEPRECATED 行）内容永不更新；编码不可变更（不含 "
            + "fieldFormCode）、停用不阻断，返回更新后的详情（服务端归一后的 Schema 刷新画布），"
            + "缺 fieldId 返回 BAD_REQUEST")
    public BaseResponse<SysFormVo> update(@RequestBody SysFormDefineUpdateRequest request) {
        SysFormUpdateRequest body = null == request ? null : request.getBody();
        if (null == body || StringUtil.isEmpty(body.getFieldId())) {
            return BaseResponse.failure("BAD_REQUEST", "fieldId is required");
        }
        authorize(ACTION_FORM_DEFINE_UPDATE, body.getFieldId());
        return BaseResponse.success(formService.update(body), null);
    }

    /**
     * 启停用：PATCH /backend/sys/form/define/enable
     * 请求体仅为 { fieldId, fieldEnable } 两字段（对齐 org element / formula enable 以
     * 实体作 body 的先例，其余字段不消费）：仅更新启用状态与修改人/修改时间（自定义表单
     * 无子级，不级联）；停用关闭填写入口与数据提交（数据面 schema / save / validate 拒绝
     * CS_FORM_DISABLED），定义面与数据查询保留。
     *
     * @param body 请求体 { fieldId, fieldEnable }，两字段均必填
     * @return 统一响应；缺 fieldId 返回 BAD_REQUEST
     */
    @PatchMapping("/enable")
    @Operation(summary = "启停用表单", description = "鉴权动作 " + ACTION_FORM_DEFINE_ENABLE
            + "（仅 " + ROLE_SYS_FORM_ADMIN + "）；请求体 { fieldId, fieldEnable }，仅携带"
            + "这两个字段：只更新主档自身一行（自定义表单无子级，不级联）；停用关闭填写入口"
            + "与数据提交（数据面 schema / save / validate 拒绝 CS_FORM_DISABLED），定义面"
            + "与数据查询保留，缺 fieldId 返回 BAD_REQUEST")
    public BaseResponse<Void> enable(@RequestBody SysForm body) {
        if (null == body || StringUtil.isEmpty(body.getFieldId())) {
            return BaseResponse.failure("BAD_REQUEST", "fieldId is required");
        }
        authorize(ACTION_FORM_DEFINE_ENABLE, body.getFieldId());
        formService.updateEnable(body.getFieldId(), body.getFieldEnable());
        return BaseResponse.success(null, null);
    }

    /**
     * 删除表单：DELETE /backend/sys/form/define/delete?fieldId=${id}
     * 删除前置校验（分册 §7.4 定稿②）：sys_form_data 中该 formId 存在任意实例数据
     * （不分版本、不分启用状态）→ 拒绝删除（CS_FORM_HAS_DATA），不提供级联删除——
     * 需保留数据时用「停用」达成「不再使用」的管理目的；通过校验后（事务内）删除主档行
     * + 该表单全部版本行（含 DRAFT / PUBLISHED / DEPRECATED）。
     *
     * @param fieldId 表单主档 ID
     * @return 统一响应
     */
    @DeleteMapping("/delete")
    @Operation(summary = "删除表单", description = "鉴权动作 " + ACTION_FORM_DEFINE_DELETE
            + "（仅 " + ROLE_SYS_FORM_ADMIN + "）；删除前置校验——sys_form_data 中该 formId"
            + " 存在任意实例数据（不分版本、不分启用状态）即拒绝删除（CS_FORM_HAS_DATA），"
            + "不提供级联删除（需保留数据时用「停用」达成「不再使用」的管理目的）；通过校验后"
            + "删除主档行与该表单全部版本行，前端删除须二次确认（列表页通用口径）")
    public BaseResponse<Void> delete(
            @Parameter(description = "表单主档 ID", required = true)
            @RequestParam("fieldId") String fieldId) {
        authorize(ACTION_FORM_DEFINE_DELETE, fieldId);
        formService.delete(fieldId);
        return BaseResponse.success(null, null);
    }

    /**
     * 发布表单：POST /backend/sys/form/define/publish
     * 发布校验链（分册 §3.8）按序全部通过方可发布：P1 草稿行存在（CS_FORM_NO_DRAFT）→
     * P2/P3 Schema 结构与资源上限（CS_FORM_SCHEMA_INVALID / CS_FORM_SCHEMA_LIMIT_EXCEEDED）
     * → P4 公式静态校验（复用 CS_FORMULA_* 族）；发布成功动作（事务内）：草稿行原地冻结
     * （版本号回填 = 现存最大 + 1）→ 原当前发布版本转 DEPRECATED → 主档指针切换 + 主档
     * 状态 DRAFT → PUBLISHED（首次发布），表单停用不阻断发布。
     *
     * @param request 发布请求（body 承载 { fieldId }）
     * @return 统一响应，data = { fieldVersionId, fieldVersion }（新发布版本行 ID 与版本号）；
     *         缺 fieldId 返回 BAD_REQUEST
     */
    @PostMapping("/publish")
    @Operation(summary = "发布表单", description = "鉴权动作 " + ACTION_FORM_DEFINE_PUBLISH
            + "（仅 " + ROLE_SYS_FORM_ADMIN + "）；请求体 { fieldId }；发布校验链按序全部"
            + "通过方可发布：无草稿行 CS_FORM_NO_DRAFT → Schema 结构 CS_FORM_SCHEMA_INVALID /"
            + " 资源上限 CS_FORM_SCHEMA_LIMIT_EXCEEDED → 公式静态校验（复用 CS_FORMULA_* 族）；"
            + "草稿行原地冻结为不可变版本行（版本号 = 现存最大 + 1）→ 原当前发布版本转 "
            + "DEPRECATED → 主档指针切换 + 状态 DRAFT → PUBLISHED（首次发布），表单停用不"
            + "阻断发布，缺 fieldId 返回 BAD_REQUEST")
    public BaseResponse<SysFormVersionCreatedVo> publish(@RequestBody SysFormPublishRequest request) {
        if (null == request || StringUtil.isEmpty(request.getFieldId())) {
            return BaseResponse.failure("BAD_REQUEST", "fieldId is required");
        }
        authorize(ACTION_FORM_DEFINE_PUBLISH, request.getFieldId());
        return BaseResponse.success(formService.publish(request.getFieldId()), null);
    }

    /**
     * 版本列表：GET /backend/sys/form/define/versions?fieldId=${id}
     * 版本行不返回 Schema 内容（体积大；回滚由服务端复制，历史版本 Schema 预览一期不单设
     * 接口）；排序定稿（分册 §3.9）：草稿行恒排最前（每表单至多一行），其余按 fieldVersion
     * 降序（最新在前）。
     *
     * @param fieldId 表单主档 ID
     * @return 统一响应，data 为版本行清单（含草稿行与全部历史发布行）
     */
    @GetMapping("/versions")
    @Operation(summary = "表单版本列表", description = "鉴权动作 " + ACTION_FORM_DEFINE_VERSIONS
            + "（仅 " + ROLE_SYS_FORM_ADMIN + "）；按表单 ID 返回版本行清单，版本行不返回"
            + " Schema 内容（体积大）；排序：草稿行恒排最前（每表单至多一行），其余按 "
            + "fieldVersion 降序（最新在前）；fieldVersion 草稿行为 null（版本号仅发布动作分配）")
    public BaseResponse<List<SysFormVersionVo>> versions(
            @Parameter(description = "表单主档 ID", required = true)
            @RequestParam("fieldId") String fieldId) {
        authorize(ACTION_FORM_DEFINE_VERSIONS, fieldId);
        return BaseResponse.success(formService.versions(fieldId), null);
    }

    /**
     * 回滚版本：POST /backend/sys/form/define/rollback
     * 回滚 = 复制目标历史版本内容（合法目标 PUBLISHED / DEPRECATED，DRAFT 目标拒绝）产生
     * 新发布版本行（版本号 = 现存最大 + 1，不经草稿态一步发布）→ 原当前发布版本转
     * DEPRECATED → 主档指针指向新行——不产生指针回拨、不影响既有草稿行，表单停用不阻断。
     *
     * @param request 回滚请求（body 承载 { fieldId, fieldVersionId }，两字段均必填）
     * @return 统一响应，data = { fieldVersionId, fieldVersion }（回滚产生的新版本行 ID 与
     *         版本号）；缺 fieldId / fieldVersionId 返回 BAD_REQUEST
     */
    @PostMapping("/rollback")
    @Operation(summary = "回滚表单版本", description = "鉴权动作 " + ACTION_FORM_DEFINE_ROLLBACK
            + "（仅 " + ROLE_SYS_FORM_ADMIN + "）；请求体 { fieldId, fieldVersionId }；"
            + "回滚 = 复制目标版本内容产生新发布版本行（不经草稿态一步发布、不产生指针回拨），"
            + "合法目标为 PUBLISHED / DEPRECATED 行（DRAFT 目标拒绝 "
            + "CS_FORM_VERSION_STATE_INVALID、目标不存在 CS_FORM_VERSION_NOT_FOUND），"
            + "不影响既有草稿行、表单停用不阻断回滚，缺 fieldId / fieldVersionId 返回 BAD_REQUEST")
    public BaseResponse<SysFormVersionCreatedVo> rollback(@RequestBody SysFormRollbackRequest request) {
        if (null == request || StringUtil.isEmpty(request.getFieldId())
                || StringUtil.isEmpty(request.getFieldVersionId())) {
            return BaseResponse.failure("BAD_REQUEST", "fieldId and fieldVersionId are required");
        }
        authorize(ACTION_FORM_DEFINE_ROLLBACK, request.getFieldId());
        return BaseResponse.success(formService.rollback(request), null);
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
