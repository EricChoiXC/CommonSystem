package com.common.system.sys.org.controller;

import com.common.system.common.base.model.BaseResponse;
import com.common.system.common.exception.AuthorizationException;
import com.common.system.common.resolver.AuthorizationContext;
import com.common.system.common.utils.StringUtil;
import com.common.system.common.utils.UserUtil;
import com.common.system.sys.org.request.PreviewTreeChildrenRequest;
import com.common.system.sys.org.request.RolePreviewRequest;
import com.common.system.sys.org.resolver.RoleResolver;
import com.common.system.sys.org.response.PreviewTreeChildVo;
import com.common.system.sys.org.response.RolePreviewVo;
import com.common.system.sys.org.service.SysOrgRolePreviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.common.system.sys.org.properties.SysOrgRoleProperties.*;

/**
 * 权限预览接口
 *
 * @author Eric
 * @date 2026/8/10
 * @description 权限预览页的后端入口：只负责接收请求、基础校验、执行最终鉴权并调用
 *              Service 返回统一响应。所有接口执行核心逻辑前先经 RoleResolver 完成鉴权
 *              （可访问权限为 SYS_ORG_ROLE_ADMIN，SYS_ORG_ADMIN / SYS_ADMIN 自动放行）。
 */
@RestController
@RequestMapping("/backend/sys/org/preview")
@Tag(name = "组织架构-权限预览", description = "权限预览页：按组织架构查看关联角色与权限并集；需要 SYS_ORG_ROLE_ADMIN（SYS_ORG_ADMIN / SYS_ADMIN 自动放行）")
public class SysOrgRolePreviewController {

    private final SysOrgRolePreviewService previewService;

    private final RoleResolver roleResolver;

    public SysOrgRolePreviewController(SysOrgRolePreviewService previewService, RoleResolver roleResolver) {
        this.previewService = previewService;
        this.roleResolver = roleResolver;
    }

    /**
     * 概览数据：POST /backend/sys/org/preview/preview
     * 请求体为 { elementId }，返回选中组织架构信息、关联组织架构、直接关联角色列表、
     * 权限并集；权限树由 treeChildren 接口懒加载。
     *
     * @param request 预览请求，elementId 为选中的组织架构 ID
     * @return 统一响应，data 为权限概览；elementId 为空返回 BAD_REQUEST
     */
    @PostMapping("/preview")
    @Operation(summary = "权限预览概览", description = "鉴权动作 sys.org.preview.preview；请求体 "
            + "{ elementId }，返回选中组织架构信息、关联组织架构、直接关联角色与权限并集；"
            + "权限树由 treeChildren 懒加载")
    public BaseResponse<RolePreviewVo> preview(@RequestBody RolePreviewRequest request) {
        authorize(ACTION_PREVIEW);
        String elementId = null == request ? null : request.getElementId();
        if (StringUtil.isEmpty(elementId)) {
            return BaseResponse.failure("BAD_REQUEST", "elementId is required");
        }
        return BaseResponse.success(previewService.preview(elementId), null);
    }

    /**
     * 权限树子节点：POST /backend/sys/org/preview/treeChildren
     * 请求体为 { type, id }；type=element 组织架构、type=role 角色，auth 为叶子不加载。
     *
     * @param request 树节点请求，type 为节点类型、id 为节点 ID
     * @return 统一响应，data 为该节点的直接子节点；id 为空返回 BAD_REQUEST
     */
    @PostMapping("/treeChildren")
    @Operation(summary = "权限树子节点", description = "鉴权动作 sys.org.preview.treeChildren；请求体 "
            + "{ type, id }，type=element 为组织架构、type=role 为角色，auth 为叶子不加载")
    public BaseResponse<List<PreviewTreeChildVo>> treeChildren(@RequestBody PreviewTreeChildrenRequest request) {
        authorize(ACTION_PREVIEW_TREE);
        String type = null == request ? null : request.getType();
        String id = null == request ? null : request.getId();
        if (StringUtil.isEmpty(id)) {
            return BaseResponse.failure("BAD_REQUEST", "id is required");
        }
        return BaseResponse.success(previewService.treeChildren(type, id), null);
    }

    /**
     * 执行最终鉴权：失败时抛出 {@link AuthorizationException}，由全局异常处理器统一转换。
     * <p>未登录或凭证已失效（当前用户为空）时抛出 {@code UNAUTHORIZED}，
     * 已登录但缺少权限时抛出 {@code FORBIDDEN}。</p>
     */
    private void authorize(String action) {
        if (null == UserUtil.getCurrentUser()) {
            throw new AuthorizationException("UNAUTHORIZED", "登录已过期，请重新登录");
        }
        if (!roleResolver.resolve(new AuthorizationContext(action))) {
            throw new AuthorizationException("FORBIDDEN", "no permission for action: " + action);
        }
    }
}
