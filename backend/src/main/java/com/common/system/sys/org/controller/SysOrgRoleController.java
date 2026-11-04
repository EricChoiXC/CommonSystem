package com.common.system.sys.org.controller;

import com.common.system.common.base.model.BaseResponse;
import com.common.system.common.base.model.Pages;
import com.common.system.common.exception.AuthorizationException;
import com.common.system.common.resolver.AuthorizationContext;
import com.common.system.common.utils.StringUtil;
import com.common.system.common.utils.UserUtil;
import com.common.system.sys.org.request.SysOrgRoleQuery;
import com.common.system.sys.org.request.SysOrgRoleRequest;
import com.common.system.sys.org.resolver.RoleResolver;
import com.common.system.sys.org.response.AuthGroupVo;
import com.common.system.sys.org.response.SysOrgRoleVo;
import com.common.system.sys.org.service.SysOrgAuthService;
import com.common.system.sys.org.service.SysOrgRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static com.common.system.sys.org.properties.SysOrgRoleProperties.*;

/**
 * 角色管理接口
 *
 * @author Eric
 * @date 2026/8/10
 * @description 角色管理页的后端入口：只负责接收请求、基础校验、执行最终鉴权并调用
 *              Service 返回统一响应。所有接口执行核心逻辑前先经 RoleResolver 完成鉴权
 *              （要求 SYS_ORG_ROLE_ADMIN，SYS_ORG_ADMIN / SYS_ADMIN 自动放行）。
 */
@RestController
@RequestMapping("/backend/sys/org/role")
@Tag(name = "组织架构-角色", description = "角色管理页：角色增改查与权限可选项；需要 SYS_ORG_ROLE_ADMIN（SYS_ORG_ADMIN / SYS_ADMIN 自动放行）")
public class SysOrgRoleController {

    private final SysOrgRoleService roleService;

    private final SysOrgAuthService authService;

    private final RoleResolver roleResolver;

    public SysOrgRoleController(SysOrgRoleService roleService, SysOrgAuthService authService,
                                RoleResolver roleResolver) {
        this.roleService = roleService;
        this.authService = authService;
        this.roleResolver = roleResolver;
    }

    /**
     * 列表查询：POST /backend/sys/org/role/list
     * 请求体为 { body: { fieldName, fieldCode, enable }, pages: { pageNo, pageSize, needPage },
     *           sortField, sortOrder }，总数经 metadata.total 返回；列表仅返回基本字段，
     * 禁止批量操作由前端承担。
     *
     * @param request 列表请求（查询条件 + 分页 + 单条件排序）
     * @return 统一响应，data 为角色列表，metadata.total 为满足条件的总数
     */
    @PostMapping("/list")
    @Operation(summary = "角色列表查询", description = "鉴权动作 sys.org.role.list；请求体 "
            + "{ body: { fieldName, fieldCode, enable }, pages, sortField, sortOrder }，"
            + "总数经 metadata.total 返回")
    public BaseResponse<List<SysOrgRoleVo>> list(@RequestBody SysOrgRoleRequest request) {
        authorize(ACTION_LIST);
        SysOrgRoleQuery query = null == request ? null : request.getBody();
        Pages pages = null == request ? null : request.getPages();
        List<SysOrgRoleVo> list = roleService.list(query, pages,
                null == request ? null : request.getSortField(),
                null == request ? null : request.getSortOrder());
        long total = roleService.count(query);
        return BaseResponse.success(list, Map.of("total", total));
    }

    /**
     * 详情：GET /backend/sys/org/role/load?fieldId=${id}
     * 返回基本字段 + 关联组织架构（elementIds/elementNames）+ 已选权限（authIds），供编辑页回显。
     *
     * @param fieldId 角色 ID
     * @return 统一响应，data 为角色详情；不存在时返回 E_NOT_FOUND
     */
    @GetMapping("/load")
    @Operation(summary = "角色详情", description = "鉴权动作 sys.org.role.load；返回基本字段、关联组织架构"
            + "（elementIds/elementNames）与已选权限（authIds），供编辑页回显")
    public BaseResponse<SysOrgRoleVo> load(
            @Parameter(description = "角色 ID", required = true) @RequestParam("fieldId") String fieldId) {
        authorize(ACTION_LOAD, fieldId);
        SysOrgRoleVo vo = roleService.load(fieldId);
        if (null == vo) {
            return BaseResponse.failure("E_NOT_FOUND", "未找到该角色");
        }
        return BaseResponse.success(vo, null);
    }

    /**
     * 新增：POST /backend/sys/org/role/save
     * 请求体为角色对象（含 elementIds/authIds），关联表全量写入。
     *
     * @param body 角色对象，含关联组织架构 elementIds 与权限 authIds
     * @return 统一响应，data 为保存后的角色（含回填的关联信息）
     */
    @PostMapping("/save")
    @Operation(summary = "新增角色", description = "鉴权动作 sys.org.role.save；请求体为角色对象"
            + "（含 elementIds / authIds），关联表全量写入")
    public BaseResponse<SysOrgRoleVo> save(@RequestBody SysOrgRoleVo body) {
        authorize(ACTION_SAVE);
        return BaseResponse.success(roleService.saveRole(body), null);
    }

    /**
     * 编辑：PATCH /backend/sys/org/role/update
     * 请求体为角色对象（必须携带 fieldId），关联表先删后插全量替换。
     *
     * @param body 角色对象，必须携带 fieldId
     * @return 统一响应，data 为更新后的角色；缺 fieldId 返回 BAD_REQUEST
     */
    @PatchMapping("/update")
    @Operation(summary = "编辑角色", description = "鉴权动作 sys.org.role.update；请求体为角色对象"
            + "（必须携带 fieldId），关联表先删后插全量替换")
    public BaseResponse<SysOrgRoleVo> update(@RequestBody SysOrgRoleVo body) {
        if (null == body || StringUtil.isEmpty(body.getFieldId())) {
            return BaseResponse.failure("BAD_REQUEST", "fieldId is required");
        }
        authorize(ACTION_UPDATE, body.getFieldId());
        return BaseResponse.success(roleService.updateRole(body), null);
    }

    /**
     * 权限可选项：GET /backend/sys/org/role/authOptions
     * 按 sys_org_auth.field_model 分组返回全部权限，分组标题优先取 application.yml 的 MODEL_NAME。
     *
     * @return 统一响应，data 为按模块分组的权限列表
     */
    @GetMapping("/authOptions")
    @Operation(summary = "权限可选项", description = "鉴权动作 sys.org.role.authOptions；按 "
            + "sys_org_auth.field_model 分组返回全部权限，分组标题优先取 application.yml 的 MODEL_NAME")
    public BaseResponse<List<AuthGroupVo>> authOptions() {
        authorize(ACTION_AUTH_OPTIONS);
        return BaseResponse.success(authService.listAuthOptions(), null);
    }

    /**
     * 执行最终鉴权：失败时抛出 {@link AuthorizationException}，由全局异常处理器统一转换。
     * <p>未登录或凭证已失效（当前用户为空）时抛出 {@code UNAUTHORIZED}，
     * 已登录但缺少权限时抛出 {@code FORBIDDEN}。</p>
     */
    private void authorize(String action) {
        authorize(new AuthorizationContext(action));
    }

    private void authorize(String action, String documentId) {
        authorize(new AuthorizationContext(action, documentId));
    }

    private void authorize(AuthorizationContext context) {
        if (null == UserUtil.getCurrentUser()) {
            throw new AuthorizationException("UNAUTHORIZED", "登录已过期，请重新登录");
        }
        if (!roleResolver.resolve(context)) {
            throw new AuthorizationException("FORBIDDEN", "no permission for action: " + context.getAction());
        }
    }
}
