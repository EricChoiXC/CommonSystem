package com.eric.base.sys.datasource.controller;

import com.eric.base.common.base.model.BaseResponse;
import com.eric.base.common.base.model.Pages;
import com.eric.base.common.module.annotation.ModuleGroup;
import com.eric.base.common.resolver.AuthorizationContext;
import com.eric.base.common.resolver.AuthorizationHelper;
import com.eric.base.common.utils.StringUtil;
import com.eric.base.sys.datasource.model.SysDatasource;
import com.eric.base.sys.datasource.request.SysDatasourceExecuteRequest;
import com.eric.base.sys.datasource.request.SysDatasourceListRequest;
import com.eric.base.sys.datasource.request.SysDatasourceSaveRequest;
import com.eric.base.sys.datasource.resolver.DatasourceResolver;
import com.eric.base.sys.datasource.response.SysDatasourceOptionVo;
import com.eric.base.sys.datasource.response.SysDatasourceTargetVo;
import com.eric.base.sys.datasource.response.SysDatasourceVo;
import com.eric.base.sys.datasource.service.SysDatasourceService;
import com.eric.base.sys.datasource.service.SysDatasourceService.SysDatasourceExecution;
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

import static com.eric.base.sys.datasource.properties.SysDatasourceConstants.*;

/**
 * 数据源接口
 *
 * @author Eric
 * @date 2026/11/13
 * @description 数据源模块请求入口（任务 F18-BE-005，方案 §5 / DOC-002 §4.10，样例代码 §2.6）：
 *              只负责接收请求、基础校验、执行最终鉴权并调用 Service 返回统一响应，
 *              管理校验与统一执行编排（含执行期委托目标 action 鉴权）全部在
 *              {@link SysDatasourceService}。本模块 Service 为非 IBaseService 形态，
 *              不继承 BaseCrudController 骨架，最终鉴权委托 {@link AuthorizationHelper}
 *              （与 BaseCrudController 鉴权口径一致并附鉴权拒绝审计事件，
 *              先例 SysNumberRuleController / SysOrgGroupController）：
 *              <ul>
 *              <li>管理六动作（list / load / save / update / enable / delete）：
 *                  仅数据源管理员 SYS_DATASOURCE_ADMIN；</li>
 *              <li>options / targets / query：登录且账号启用即可（v1.8 自定义数据源全员可用）；
 *                  query 的目标接口鉴权由 Service#execute 在解析目标能力后经
 *                  AuthorizationHelper 委托目标 action（两段式，方案 §5.2）——
 *                  「能通过数据源取数」的前提是「有目标接口权限」。</li>
 *              </ul>
 *              接口路径遵循 /backend/{根模块}/{模块}/{次级模块}/{请求方法}；
 *              类级 {@code @ModuleGroup(moduleGroupKey = "datasource")} 使 9 接口
 *              受模块组开关统一控制。
 */
@RestController
@RequestMapping("${project.request.prefix}/sys/datasource")
@ModuleGroup(moduleGroupKey = "datasource")
@Tag(name = "数据源", description = "数据源管理：可复用取数配置（查询能力 + 固定条件 + 返回字段白名单）；"
        + "默认数据源由各模块代码注册，自定义数据源由管理员配置；统一执行入口 query 登录即可，"
        + "执行期委托目标接口 action 鉴权")
public class SysDatasourceController {

    private final SysDatasourceService datasourceService;

    private final DatasourceResolver datasourceResolver;

    private final AuthorizationHelper authorizationHelper;

    public SysDatasourceController(SysDatasourceService datasourceService, DatasourceResolver datasourceResolver,
                                   AuthorizationHelper authorizationHelper) {
        this.datasourceService = datasourceService;
        this.datasourceResolver = datasourceResolver;
        this.authorizationHelper = authorizationHelper;
    }

    /**
     * 自定义数据源管理列表：POST /backend/sys/datasource/list
     * 请求体 { body, pages: { pageNo, pageSize, needPage } }；条件 fieldName / fieldCode 模糊、
     * fieldTarget / fieldEnable 精确，排序固定创建时间降序；fieldConditions / fieldFields
     * 原文 JSON 字符串返回（列表页不解析 JSON，A4 先例），总数经 metadata.total 返回。
     *
     * @param request 列表请求（条件 + 分页）
     * @return 统一响应，data 为数据源列表，metadata.total 为满足条件的总数
     */
    @PostMapping("/list")
    @Operation(summary = "自定义数据源管理列表", description = "鉴权动作 " + ACTION_LIST + "（仅 "
            + ROLE_SYS_DATASOURCE_ADMIN + "）；请求体 { body: { fieldName, fieldCode, fieldTarget, "
            + "fieldEnable }, pages }，fieldName / fieldCode 模糊、fieldTarget / fieldEnable 精确，"
            + "默认创建时间降序，fieldConditions / fieldFields 原文 JSON 字符串返回（列表页不解析），"
            + "总数经 metadata.total 返回")
    public BaseResponse<List<SysDatasourceVo>> list(@RequestBody SysDatasourceListRequest request) {
        authorize(new AuthorizationContext(ACTION_LIST));
        SysDatasource body = null == request ? null : request.getBody();
        Pages pages = null == request ? null : request.getPages();
        List<SysDatasourceVo> list = datasourceService.list(body, pages);
        long total = datasourceService.count(body);
        return BaseResponse.success(list, Map.of("total", total));
    }

    /**
     * 数据源详情：GET /backend/sys/datasource/load?fieldId=${id}
     * fieldConditions 解析为条件对象数组 [{key,op,value}]、fieldFields 解析为字段名字符串数组
     * （NULL=目标能力默认全字段），供表单 / 详情页回显。
     *
     * @param fieldId 数据源 ID
     * @return 统一响应，data 为数据源详情（解析后形态）；不存在返回参数异常
     */
    @GetMapping("/load")
    @Operation(summary = "数据源详情", description = "鉴权动作 " + ACTION_LOAD + "（仅 "
            + ROLE_SYS_DATASOURCE_ADMIN + "）；按 ID 读取数据源完整配置，fieldConditions 解析为"
            + "条件对象数组 [{key,op,value}]、fieldFields 解析为字段名字符串数组（NULL=目标能力默认全字段），"
            + "供表单 / 详情页回显，fieldTargetName / fieldTargetPath 由目标能力带出（目标失效时为 null）")
    public BaseResponse<SysDatasourceVo> load(
            @Parameter(description = "数据源 ID", required = true) @RequestParam("fieldId") String fieldId) {
        authorize(ACTION_LOAD, fieldId);
        return BaseResponse.success(datasourceService.load(fieldId), null);
    }

    /**
     * 新建自定义数据源：POST /backend/sys/datasource/save
     * 请求体 { body: { fieldId, fieldCode, fieldName, fieldTarget, fieldConditions,
     * fieldFields, fieldRemark } }；编码全局查重（含默认数据源/目标能力编码占用集）、
     * fieldTarget 须为已注册目标能力（保存后锁定）、固定条件与返回字段白名单按目标能力
     * 定义静态校验，返回保存后的数据源详情（fieldConditions / fieldFields 已解析）。
     *
     * @param request 保存请求（body 承载保存体，fieldId 由前端经 /backend/common/initID 预取）
     * @return 统一响应，data 为保存后的数据源详情
     */
    @PostMapping("/save")
    @Operation(summary = "新建自定义数据源", description = "鉴权动作 " + ACTION_SAVE + "（仅 "
            + ROLE_SYS_DATASOURCE_ADMIN + "）；请求体 { body: { fieldId, fieldCode, fieldName, "
            + "fieldTarget, fieldConditions, fieldFields, fieldRemark } }，fieldId 经 "
            + "/backend/common/initID 预取；编码全局查重（含默认数据源与目标能力编码占用集），"
            + "fieldTarget 须为已注册目标能力且保存后锁定，固定条件 / 返回字段白名单按目标能力"
            + "定义静态校验，返回保存后的数据源详情")
    public BaseResponse<SysDatasourceVo> save(@RequestBody SysDatasourceSaveRequest request) {
        authorize(new AuthorizationContext(ACTION_SAVE));
        return BaseResponse.success(datasourceService.save(null == request ? null : request.getBody()), null);
    }

    /**
     * 编辑自定义数据源：PATCH /backend/sys/datasource/update
     * 请求体 { body: { fieldId, fieldCode, fieldName, fieldConditions, fieldFields, fieldRemark } }，
     * fieldId 必填；fieldTarget 保存后锁定——以库值为准不受理变更（按库值目标能力校验条件与
     * 字段白名单），编码可改但须查重（排除自身），启用态保持不变（启停走 enable 通道）。
     *
     * @param request 保存请求（body 必须携带 fieldId）
     * @return 统一响应，data 为更新后的数据源详情
     */
    @PatchMapping("/update")
    @Operation(summary = "编辑自定义数据源", description = "鉴权动作 " + ACTION_UPDATE + "（仅 "
            + ROLE_SYS_DATASOURCE_ADMIN + "）；请求体 { body: { fieldId, fieldCode, fieldName, "
            + "fieldConditions, fieldFields, fieldRemark } }，fieldId 必填；fieldTarget 以库值为准"
            + "不受理变更（保存后锁定，按库值目标能力校验条件与字段白名单），编码可改但须查重"
            + "（排除自身），启用态保持不变，返回更新后的数据源详情")
    public BaseResponse<SysDatasourceVo> update(@RequestBody SysDatasourceSaveRequest request) {
        authorize(new AuthorizationContext(ACTION_UPDATE));
        return BaseResponse.success(datasourceService.update(null == request ? null : request.getBody()), null);
    }

    /**
     * 启停用：PATCH /backend/sys/datasource/enable
     * 请求体仅为 { fieldId, fieldEnable } 两字段（对齐 org element / number rule enable
     * 以实体作 body 的先例，其余字段不消费）：仅更新启用状态与修改人/修改时间；
     * 停用后 /query 即报 CS_DATASOURCE_NOT_FOUND、/options 不再返回该源。
     *
     * @param body 请求体 { fieldId, fieldEnable }，fieldId 必填
     * @return 统一响应；缺 fieldId 返回 BAD_REQUEST
     */
    @PatchMapping("/enable")
    @Operation(summary = "启停用自定义数据源", description = "鉴权动作 " + ACTION_ENABLE + "（仅 "
            + ROLE_SYS_DATASOURCE_ADMIN + "）；请求体 { fieldId, fieldEnable }，仅携带这两个字段："
            + "仅更新启用状态与修改人/修改时间，停用后 /query 即报 CS_DATASOURCE_NOT_FOUND、"
            + "/options 不再返回该源，缺 fieldId 返回 BAD_REQUEST")
    public BaseResponse<Void> enable(@RequestBody SysDatasource body) {
        authorize(new AuthorizationContext(ACTION_ENABLE));
        if (null == body || StringUtil.isEmpty(body.getFieldId())) {
            return BaseResponse.failure("BAD_REQUEST", "fieldId is required");
        }
        datasourceService.updateEnable(body.getFieldId(), body.getFieldEnable());
        return BaseResponse.success(null, null);
    }

    /**
     * 删除自定义数据源：DELETE /backend/sys/datasource/delete?fieldId=${id}
     * 按 ID 物理删除，无附加在用校验——数据源属取数配置元数据，误删影响面为消费方取数失败
     * （CS_DATASOURCE_NOT_FOUND），启用中的数据源删除由前端二次确认；
     * 默认数据源由代码注册不入库，天然不可删。
     *
     * @param fieldId 数据源 ID
     * @return 统一响应
     */
    @DeleteMapping("/delete")
    @Operation(summary = "删除自定义数据源", description = "鉴权动作 " + ACTION_DELETE + "（仅 "
            + ROLE_SYS_DATASOURCE_ADMIN + "）；按 ID 物理删除，无附加在用校验——误删影响面为消费方"
            + "取数失败（CS_DATASOURCE_NOT_FOUND），启用中的数据源删除由前端二次确认；"
            + "默认数据源由代码注册不入库，天然不可删")
    public BaseResponse<Void> delete(
            @Parameter(description = "数据源 ID", required = true) @RequestParam("fieldId") String fieldId) {
        authorize(ACTION_DELETE, fieldId);
        datasourceService.delete(fieldId);
        return BaseResponse.success(null, null);
    }

    /**
     * 可用数据源下拉：GET /backend/sys/datasource/options
     * 返回默认数据源（CODE，代码 Provider 注册）+ 启用的自定义数据源（CUSTOM），
     * 元素 { code, name, sourceType, target, targetPath, fields, conditionCount }；
     * 只暴露消费所需元数据，不暴露固定条件原文（A1：固定条件属服务端强制语义）。
     *
     * @return 统一响应，data 为数据源下拉条目（默认源在前，自定义启用源按创建时间降序）
     */
    @GetMapping("/options")
    @Operation(summary = "可用数据源下拉", description = "鉴权动作 " + ACTION_OPTIONS + "（登录即可）；"
            + "返回默认数据源（CODE，代码 Provider 注册）+ 启用的自定义数据源（CUSTOM），元素 "
            + "{ code, name, sourceType, target, targetPath, fields(有效字段定义), conditionCount }；"
            + "只暴露消费所需元数据，不暴露固定条件原文（A1）；目标失效的启用行不在此暴露")
    public BaseResponse<List<SysDatasourceOptionVo>> options() {
        authorize(new AuthorizationContext(ACTION_OPTIONS));
        return BaseResponse.success(datasourceService.options(), null);
    }

    /**
     * 目标查询能力清单：GET /backend/sys/datasource/targets
     * 返回 Registry 目标能力全集 { code, name, targetAction, targetPath, conditionFields,
     * resultFields }，code 即 field_target 取值；字段定义元素统一结构
     * { key, label, type, ops, literalAllowed, sortable }，供配置表单选能力、
     * 编排固定条件、勾选返回字段。
     *
     * @return 统一响应，data 为能力列表（Registry 注册顺序）
     */
    @GetMapping("/targets")
    @Operation(summary = "目标查询能力清单", description = "鉴权动作 " + ACTION_TARGETS + "（登录即可）；"
            + "返回 Registry 目标能力全集 { code, name, targetAction, targetPath, conditionFields, "
            + "resultFields }，code 即 field_target 取值；字段定义元素统一结构 "
            + "{ key, label, type, ops, literalAllowed, sortable }，供配置表单选能力、"
            + "编排固定条件、勾选返回字段")
    public BaseResponse<List<SysDatasourceTargetVo>> targets() {
        authorize(new AuthorizationContext(ACTION_TARGETS));
        return BaseResponse.success(datasourceService.targets(), null);
    }

    /**
     * 数据源统一执行：POST /backend/sys/datasource/query
     * 请求体 { code, body, pages, sortField, sortOrder }（body 为调用方条件，固定条件同名
     * 覆盖之；sortField 须 ∈ 目标能力可排序白名单，非法值回退默认排序）；执行期由 Service
     * 委托目标 action 两段式鉴权；响应 data 为投影后行列表（动态列），metadata = { total, fields }，
     * 列头以 metadata.fields / options.fields 为准。
     *
     * @param request 执行请求
     * @return 统一响应，data 为投影后行列表，metadata 为 { total, fields }
     */
    @PostMapping("/query")
    @Operation(summary = "数据源统一执行", description = "鉴权动作 " + ACTION_QUERY + "（登录即可，"
            + "执行期委托目标接口 action 鉴权，如 sys.calendar.list）；请求体 { code, body(调用方条件，"
            + "固定条件同名覆盖之), pages, sortField, sortOrder }，sortField 须 ∈ 目标能力可排序白名单"
            + "（非法值回退目标能力默认排序）；响应 data 为投影后行列表（动态列，取数为原始值不翻译码值），"
            + "metadata = { total, fields }，列头以 metadata.fields / options.fields 为准")
    public BaseResponse<List<Map<String, Object>>> query(@RequestBody SysDatasourceExecuteRequest request) {
        authorize(new AuthorizationContext(ACTION_QUERY));
        SysDatasourceExecution execution = datasourceService.execute(
                null == request ? null : request.getCode(),
                null == request ? null : request.getBody(),
                null == request ? null : request.getPages(),
                null == request ? null : request.getSortField(),
                null == request ? null : request.getSortOrder());
        return BaseResponse.success(execution.rows(),
                Map.of("total", execution.total(), "fields", execution.fields()));
    }

    /* ==================== 鉴权（AuthorizationHelper 统一入口） ==================== */

    /**
     * 执行最终鉴权：委托 {@link AuthorizationHelper} 统一处理——未登录或凭证已失效时抛出
     * {@code UNAUTHORIZED}，已登录但无权限时发布鉴权拒绝事件（安全审计）并抛出 {@code FORBIDDEN}，
     * 由全局异常处理器统一转换为 BaseResponse（与 BaseCrudController 的最终鉴权口径一致）。
     */
    private void authorize(AuthorizationContext context) {
        authorizationHelper.authorize(datasourceResolver, context);
    }

    private void authorize(String action, String documentId) {
        authorizationHelper.authorize(datasourceResolver, action, documentId);
    }
}
