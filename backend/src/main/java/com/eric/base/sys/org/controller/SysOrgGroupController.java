package com.eric.base.sys.org.controller;

import com.eric.base.common.base.model.BaseResponse;
import com.eric.base.common.base.model.Pages;
import com.eric.base.common.resolver.AuthorizationHelper;
import com.eric.base.common.utils.StringUtil;
import com.eric.base.sys.org.request.SysOrgGroupQuery;
import com.eric.base.sys.org.request.SysOrgGroupRequest;
import com.eric.base.sys.org.resolver.GroupResolver;
import com.eric.base.sys.org.response.SysOrgGroupVo;
import com.eric.base.sys.org.service.SysOrgGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
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

import static com.eric.base.sys.org.properties.SysOrgGroupProperties.*;

/**
 * 群组维护接口
 *
 * @author Eric
 * @date 2026/11/6
 * @description 群组管理页（GroupManageView）的后端入口：只负责接收请求、基础校验、
 *              执行最终鉴权并调用 Service 返回统一响应（写法与 SysOrgElementController /
 *              SysOrgRoleController 等组织架构先例一致，不继承 BaseCrudController 骨架：
 *              本模块 Service 为非 IBaseService 形态，最终鉴权委托 AuthorizationHelper，
 *              与 BaseCrudController 的鉴权口径一致并附鉴权拒绝审计事件）。群组「不设权 + 数据级过滤」
 *              （04-API改动清单 1.2）：接口级鉴权经 GroupResolver 仅要求已登录且账号启用，
 *              普通用户全部放行；归属/可见性/维护权限等数据级校验由 SysOrgGroupService 承担
 *              （群组不可见 FORBIDDEN「无权查看该群组」、无权维护 FORBIDDEN「无权维护该群组」、
 *              编号不存在 E_NOT_FOUND「未找到该群组」）。鉴权失败统一抛
 *              AuthorizationException 由全局异常处理器转换为 BaseResponse。
 */
@RestController
@RequestMapping("${project.request.prefix}/sys/org/group")
@Tag(name = "组织架构-群组", description = "群组维护：列表/详情/新建/编辑/删除；登录且账号启用即可访问（不设权），"
        + "归属与可见性等数据级过滤由 Service 按当前登录人承担")
public class SysOrgGroupController {

    private final SysOrgGroupService groupService;

    private final GroupResolver groupResolver;

    private final AuthorizationHelper authorizationHelper;

    public SysOrgGroupController(SysOrgGroupService groupService, GroupResolver groupResolver,
                                 AuthorizationHelper authorizationHelper) {
        this.groupService = groupService;
        this.groupResolver = groupResolver;
        this.authorizationHelper = authorizationHelper;
    }

    /**
     * 列表查询：POST /backend/sys/org/group/list
     * 请求体为 { body: { fieldName, fieldType, fieldIsAllViewer, fieldOwnerId },
     * pages: { pageNo, pageSize, needPage }, sortField, sortOrder }，总数经 metadata.total 返回；
     * 列表按当前登录人可见性过滤（个人群组仅所有人、公共群组全员或命中可见范围），管理员不过滤。
     *
     * @param request 列表请求（条件 + 分页 + 单条件排序），排序字段取 createTime / updateTime / name
     * @return 统一响应，data 为群组列表，metadata.total 为满足条件的总数
     */
    @PostMapping("/list")
    @Operation(summary = "群组列表查询", description = "鉴权动作 sys.org.group.list（登录且账号启用即可，"
            + "不设权，归属与可见性过滤由 Service 承担）；请求体 { body: { fieldName, fieldType, "
            + "fieldIsAllViewer, fieldOwnerId }, pages, sortField, sortOrder }，排序字段取 "
            + "createTime / updateTime / name（默认创建时间降序），总数经 metadata.total 返回")
    public BaseResponse<List<SysOrgGroupVo>> list(@RequestBody GroupListRequest request) {
        authorize(ACTION_LIST);
        SysOrgGroupQuery query = null == request ? null : request.getBody();
        Pages pages = null == request ? null : request.getPages();
        List<SysOrgGroupVo> list = groupService.list(query, pages,
                null == request ? null : request.getSortField(),
                null == request ? null : request.getSortOrder());
        long total = groupService.count(query);
        return BaseResponse.success(list, Map.of("total", total));
    }

    /**
     * 详情：GET /backend/sys/org/group/load?fieldId=${id}
     * 返回基本字段 + 成员（userIds/userNames）+ 可见范围（viewerIds/viewerNames），供编辑/详情页回显。
     *
     * @param fieldId 群组 ID
     * @return 统一响应，data 为群组详情；不存在时返回 E_NOT_FOUND，不可见时返回 FORBIDDEN
     */
    @GetMapping("/load")
    @Operation(summary = "群组详情", description = "鉴权动作 sys.org.group.load（登录且账号启用即可，"
            + "不设权，群组不可见返回 FORBIDDEN「无权查看该群组」，编号不存在返回 E_NOT_FOUND「未找到该群组」）；"
            + "返回群组基本字段、成员 userIds/userNames 与可见范围 viewerIds/viewerNames，供编辑/详情页回显")
    public BaseResponse<SysOrgGroupVo> load(
            @Parameter(description = "群组 ID", required = true) @RequestParam("fieldId") String fieldId) {
        authorize(ACTION_LOAD, fieldId);
        return BaseResponse.success(groupService.load(fieldId), null);
    }

    /**
     * 新增：POST /backend/sys/org/group/save，请求体为群组对象（含成员与可见范围明细数组）。
     *
     * @param body 群组对象，fieldOwnerId 由 Service 取当前登录人、fieldIsAllViewer 按可见范围派生
     * @return 统一响应，data 为保存后的群组
     */
    @PostMapping("/save")
    @Operation(summary = "新增群组", description = "鉴权动作 sys.org.group.save（登录且账号启用即可，"
            + "不设权，普通用户强制个人群组，指定公共类型返回 BAD_REQUEST「普通用户仅可创建个人群组」）；"
            + "请求体为群组对象 { fieldId, fieldName, fieldType, userIds: string[], viewerIds: string[], "
            + "fieldEnable }，fieldId 经 /backend/common/initID 预取；fieldOwnerId 取当前登录人、"
            + "fieldIsAllViewer 按 viewerIds 是否为空派生（前端传值无效）；成员须为启用人员"
            + "（BAD_REQUEST「群组成员必须为启用的人员」），可见范围限机构/部门/岗位/人员"
            + "（BAD_REQUEST「可见范围仅支持机构/部门/岗位/人员」），返回保存后的群组")
    public BaseResponse<SysOrgGroupVo> save(@RequestBody SysOrgGroupRequest body) {
        authorize(ACTION_SAVE);
        return BaseResponse.success(groupService.save(body), null);
    }

    /**
     * 编辑：PATCH /backend/sys/org/group/update，请求体为群组对象（必须携带 fieldId），
     * 成员与可见范围明细全量替换（先删后插），fieldOwnerId 恒保持原值。
     *
     * @param body 群组对象，必须携带 fieldId
     * @return 统一响应，data 为更新后的群组；缺 fieldId 返回 BAD_REQUEST
     */
    @PatchMapping("/update")
    @Operation(summary = "编辑群组", description = "鉴权动作 sys.org.group.update（登录且账号启用即可，"
            + "不设权，普通用户仅可维护自己的个人群组且不可改类型，无权返回 FORBIDDEN「无权维护该群组」，"
            + "群组不可见返回 FORBIDDEN「无权查看该群组」，编号不存在返回 E_NOT_FOUND「未找到该群组」）；"
            + "请求体为群组对象（必须携带 fieldId，成员与可见范围明细全量替换），fieldOwnerId 恒保持原值，"
            + "缺 fieldId 返回 BAD_REQUEST，返回更新后的群组")
    public BaseResponse<SysOrgGroupVo> update(@RequestBody SysOrgGroupRequest body) {
        if (null == body || StringUtil.isEmpty(body.getFieldId())) {
            return BaseResponse.failure("BAD_REQUEST", "fieldId is required");
        }
        authorize(ACTION_UPDATE, body.getFieldId());
        return BaseResponse.success(groupService.update(body), null);
    }

    /**
     * 删除（物理删除四表）：DELETE /backend/sys/org/group/delete?fieldId=${id}
     * 同事务删除成员明细、可见范围明细、群组扩展行与主表行，删除前二次确认由前端承担。
     *
     * @param fieldId 群组 ID
     * @return 统一响应；无权维护时由 Service 抛 FORBIDDEN，不存在时抛 E_NOT_FOUND
     */
    @DeleteMapping("/delete")
    @Operation(summary = "删除群组", description = "鉴权动作 sys.org.group.delete（登录且账号启用即可，"
            + "不设权，普通用户仅可删除自己的个人群组，无权返回 FORBIDDEN「无权维护该群组」，"
            + "编号不存在返回 E_NOT_FOUND「未找到该群组」）；物理删除群组及其成员/可见范围明细与主表行，"
            + "删除前二次确认由前端承担")
    public BaseResponse<Void> delete(
            @Parameter(description = "群组 ID", required = true) @RequestParam("fieldId") String fieldId) {
        authorize(ACTION_DELETE, fieldId);
        groupService.delete(fieldId);
        return BaseResponse.success(null, null);
    }

    /**
     * 执行最终鉴权：委托 {@link AuthorizationHelper} 统一处理——未登录或凭证已失效时抛出
     * {@code UNAUTHORIZED}，已登录但无权限时发布鉴权拒绝事件（安全审计）并抛出 {@code FORBIDDEN}，
     * 由全局异常处理器统一转换为 BaseResponse（与 BaseCrudController 的最终鉴权口径一致）。
     */
    private void authorize(String action) {
        authorizationHelper.authorize(groupResolver, action);
    }

    private void authorize(String action, String documentId) {
        authorizationHelper.authorize(groupResolver, action, documentId);
    }

    /**
     * 群组列表请求体（04-API改动清单 1.1）：{ body: 查询条件, pages: 分页, sortField, sortOrder }。
     * <p>本任务约束不修改既有 request 类、不新增第三个文件，故以嵌套类承载列表请求体；
     * 查询条件复用 {@link SysOrgGroupQuery}（fieldName / fieldType / fieldIsAllViewer / fieldOwnerId），
     * userId / admin 为服务端可见性过滤入参（@JsonIgnore，前端传值无效）。</p>
     */
    @Schema(description = "群组列表请求体：body 承载查询条件，pages 承载分页，sortField/sortOrder 承载单条件排序")
    public static class GroupListRequest {

        /** 群组查询条件 */
        @Schema(description = "查询条件：fieldName（名称模糊）、fieldType（COMMON/PERSONAL）、"
                + "fieldIsAllViewer（是否全员可见）、fieldOwnerId（所有人）")
        private SysOrgGroupQuery body;

        /** 分页参数 */
        @Schema(description = "分页参数：pageNo / pageSize / needPage")
        private Pages pages;

        /** 排序字段：createTime / updateTime / name，默认 createTime */
        @Schema(description = "排序字段：createTime / updateTime / name，默认 createTime",
                allowableValues = {"createTime", "updateTime", "name"}, defaultValue = "createTime")
        private String sortField;

        /** 排序方向：asc / desc，默认 desc */
        @Schema(description = "排序方向：asc / desc，默认 desc", allowableValues = {"asc", "desc"},
                defaultValue = "desc")
        private String sortOrder;

        public SysOrgGroupQuery getBody() {
            return body;
        }

        public void setBody(SysOrgGroupQuery body) {
            this.body = body;
        }

        public Pages getPages() {
            return pages;
        }

        public void setPages(Pages pages) {
            this.pages = pages;
        }

        public String getSortField() {
            return sortField;
        }

        public void setSortField(String sortField) {
            this.sortField = sortField;
        }

        public String getSortOrder() {
            return sortOrder;
        }

        public void setSortOrder(String sortOrder) {
            this.sortOrder = sortOrder;
        }
    }
}
