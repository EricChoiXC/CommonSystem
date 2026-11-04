package com.common.system.sys.org.controller;

import com.common.system.common.base.model.BaseResponse;
import com.common.system.common.base.model.Pages;
import com.common.system.common.exception.AuthorizationException;
import com.common.system.common.resolver.AuthorizationContext;
import com.common.system.common.utils.StringUtil;
import com.common.system.common.utils.UserUtil;
import com.common.system.sys.org.request.SysOrgElementQuery;
import com.common.system.sys.org.request.SysOrgElementRequest;
import com.common.system.sys.org.resolver.ElementResolver;
import com.common.system.sys.org.response.SysOrgElementVo;
import com.common.system.sys.org.service.SysOrgElementService;
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

import static com.common.system.sys.org.properties.SysOrgElementProperties.*;

/**
 * 组织架构元素维护接口
 *
 * @author Eric
 * @date 2026/8/10
 * @description 组织架构管理页的后端入口：只负责接收请求、基础校验、执行最终鉴权并调用
 *              Service 返回统一响应。所有接口执行核心逻辑前先经 ElementResolver 完成鉴权
 *              （要求 SYS_ORG_ELEMENT_ADMIN，SYS_ORG_ADMIN / SYS_ADMIN 自动放行）。
 */
@RestController
@RequestMapping("/backend/sys/org/element")
@Tag(name = "组织架构元素", description = "组织架构管理页：机构/部门/人员/岗位的统一元素维护；需要 SYS_ORG_ELEMENT_ADMIN（SYS_ORG_ADMIN / SYS_ADMIN 自动放行）")
public class SysOrgElementController {

    private final SysOrgElementService elementService;

    private final ElementResolver elementResolver;

    public SysOrgElementController(SysOrgElementService elementService, ElementResolver elementResolver) {
        this.elementService = elementService;
        this.elementResolver = elementResolver;
    }

    /**
     * 列表查询：POST /backend/sys/org/element/list
     * 请求体为 { body: { parentId, fieldName, fieldCode, orgType, enable }, pages: { pageNo, pageSize, needPage },
     *           sortField, sortOrder }；parentId 为空表示根级，总数经 metadata.total 返回。
     */
    @PostMapping("/list")
    @Operation(summary = "组织架构元素列表", description = "鉴权动作 sys.org.element.list；请求体 "
            + "{ body: { parentId, fieldName, fieldCode, orgType, enable }, pages, sortField, sortOrder }，"
            + "parentId 为空表示根级，总数经 metadata.total 返回")
    public BaseResponse<List<SysOrgElementVo>> list(@RequestBody SysOrgElementRequest request) {
        authorize(ACTION_LIST);
        SysOrgElementQuery query = null == request ? null : request.getBody();
        Pages pages = null == request ? null : request.getPages();
        List<SysOrgElementVo> list = elementService.list(query, pages,
                null == request ? null : request.getSortField(),
                null == request ? null : request.getSortOrder());
        long total = elementService.count(query);
        return BaseResponse.success(list, Map.of("total", total));
    }

    /**
     * 详情：GET /backend/sys/org/element/load?fieldId=${id}
     *
     * @param fieldId 组织架构元素 ID
     * @return 统一响应，data 为元素详情（含类型专有字段）；不存在时返回 E_NOT_FOUND
     */
    @GetMapping("/load")
    @Operation(summary = "组织架构元素详情", description = "鉴权动作 sys.org.element.load；返回元素详情，"
            + "并按组织类型归一化负责人、电话、邮箱、地址等专有字段；不存在时返回 E_NOT_FOUND")
    public BaseResponse<SysOrgElementVo> load(
            @Parameter(description = "组织架构元素 ID", required = true) @RequestParam("fieldId") String fieldId) {
        authorize(ACTION_LOAD, fieldId);
        SysOrgElementVo vo = elementService.load(fieldId);
        if (null == vo) {
            return BaseResponse.failure("E_NOT_FOUND", "未找到该组织架构元素");
        }
        return BaseResponse.success(vo, null);
    }

    /**
     * 新增：POST /backend/sys/org/element/save，请求体为组织架构元素对象（含类型专有字段）。
     *
     * @param body 组织架构元素对象，父级由 fieldParentId 指定，层级由 Service 重建
     * @return 统一响应，data 为保存后的元素
     */
    @PostMapping("/save")
    @Operation(summary = "新增组织架构元素", description = "鉴权动作 sys.org.element.save；请求体为元素对象"
            + "（含类型专有字段），保存时由 Service 计算 fieldHierarchy / fieldLevel")
    public BaseResponse<SysOrgElementVo> save(@RequestBody SysOrgElementVo body) {
        authorize(ACTION_SAVE);
        return BaseResponse.success(elementService.save(body), null);
    }

    /**
     * 编辑：PATCH /backend/sys/org/element/update，请求体为组织架构元素对象（必须携带 fieldId）。
     *
     * @param body 组织架构元素对象，必须携带 fieldId；父级变更时递归修正子树层级
     * @return 统一响应，data 为更新后的元素；缺 fieldId 返回 BAD_REQUEST
     */
    @PatchMapping("/update")
    @Operation(summary = "编辑组织架构元素", description = "鉴权动作 sys.org.element.update；请求体为元素对象"
            + "（必须携带 fieldId）；父级变更时校验不能指向自身或后代，并递归修正子树 hierarchy")
    public BaseResponse<SysOrgElementVo> update(@RequestBody SysOrgElementVo body) {
        if (null == body || StringUtil.isEmpty(body.getFieldId())) {
            return BaseResponse.failure("BAD_REQUEST", "fieldId is required");
        }
        authorize(ACTION_UPDATE, body.getFieldId());
        return BaseResponse.success(elementService.update(body), null);
    }

    /**
     * 启停用：PATCH /backend/sys/org/element/enable
     * 请求体为 { fieldId, fieldEnable }，仅更新启用状态与修改人/修改时间。
     *
     * @param body 请求体 { fieldId, fieldEnable }，fieldId 必填
     * @return 统一响应；缺 fieldId 返回 BAD_REQUEST
     */
    @PatchMapping("/enable")
    @Operation(summary = "启停用组织架构元素", description = "鉴权动作 sys.org.element.enable；请求体 "
            + "{ fieldId, fieldEnable }，仅更新启用状态与修改人/修改时间，不级联后代")
    public BaseResponse<Void> enable(@RequestBody SysOrgElementVo body) {
        if (null == body || StringUtil.isEmpty(body.getFieldId())) {
            return BaseResponse.failure("BAD_REQUEST", "fieldId is required");
        }
        authorize(ACTION_ENABLE, body.getFieldId());
        elementService.updateEnable(body.getFieldId(), body.getFieldEnable());
        return BaseResponse.success(null, null);
    }

    /**
     * 删除（置为无效）：DELETE /backend/sys/org/element/delete?fieldId=${id}
     * 仅允许删除无直接下级的节点，删除前二次确认由前端承担。
     *
     * @param fieldId 组织架构元素 ID
     * @return 统一响应；存在直接下级时由 Service 抛出参数异常
     */
    @DeleteMapping("/delete")
    @Operation(summary = "删除组织架构元素", description = "鉴权动作 sys.org.element.delete；仅允许删除"
            + "无直接下级的节点，删除前二次确认由前端承担")
    public BaseResponse<Void> delete(
            @Parameter(description = "组织架构元素 ID", required = true) @RequestParam("fieldId") String fieldId) {
        authorize(ACTION_DELETE, fieldId);
        elementService.delete(fieldId);
        return BaseResponse.success(null, null);
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
        if (!elementResolver.resolve(context)) {
            throw new AuthorizationException("FORBIDDEN", "no permission for action: " + context.getAction());
        }
    }
}
