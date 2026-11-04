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
 *              Service 返回统一响应。所有接口执行核心逻辑前先经 ElementResolver 完成鉴权。
 */
@RestController
@RequestMapping("/backend/sys/org/element")
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
     */
    @GetMapping("/load")
    public BaseResponse<SysOrgElementVo> load(@RequestParam("fieldId") String fieldId) {
        authorize(ACTION_LOAD, fieldId);
        SysOrgElementVo vo = elementService.load(fieldId);
        if (null == vo) {
            return BaseResponse.failure("E_NOT_FOUND", "未找到该组织架构元素");
        }
        return BaseResponse.success(vo, null);
    }

    /**
     * 新增：POST /backend/sys/org/element/save，请求体为组织架构元素对象（含类型专有字段）。
     */
    @PostMapping("/save")
    public BaseResponse<SysOrgElementVo> save(@RequestBody SysOrgElementVo body) {
        authorize(ACTION_SAVE);
        return BaseResponse.success(elementService.save(body), null);
    }

    /**
     * 编辑：PATCH /backend/sys/org/element/update，请求体为组织架构元素对象（必须携带 fieldId）。
     */
    @PatchMapping("/update")
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
     */
    @PatchMapping("/enable")
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
     */
    @DeleteMapping("/delete")
    public BaseResponse<Void> delete(@RequestParam("fieldId") String fieldId) {
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
