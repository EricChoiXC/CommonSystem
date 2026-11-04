package com.common.system.sys.org.controller;

import com.common.system.common.base.model.BaseResponse;
import com.common.system.common.base.model.Pages;
import com.common.system.common.exception.AuthorizationException;
import com.common.system.common.resolver.AuthorizationContext;
import com.common.system.common.utils.UserUtil;
import com.common.system.sys.org.model.SysOrgElement;
import com.common.system.sys.org.request.SysOrgAddressBookListRequest;
import com.common.system.sys.org.request.SysOrgAddressBookLoadRequest;
import com.common.system.sys.org.request.SysOrgAddressBookQuery;
import com.common.system.sys.org.resolver.AddressBookResolver;
import com.common.system.sys.org.service.SysOrgAddressBookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static com.common.system.sys.org.properties.SysOrgAddressBookProperties.*;

/**
 * 地址本选择接口
 *
 * @author Eric
 * @date 2026/8/9
 * @description 地址本组件(AddressBook.vue)的后端入口：只负责接收请求、基础校验、
 *              执行最终鉴权并调用 Service 返回统一响应。所有接口执行核心逻辑前先经
 *              AddressBookResolver 完成鉴权（已登录且账号启用）。
 */
@RestController
@RequestMapping("/backend/sys/org/addressbook")
@Tag(name = "组织架构-地址本", description = "地址本组件（AddressBook.vue）：树查询、关键字分页查询与回填；登录且账号启用即可访问")
public class SysOrgAddressBookController {

    private final SysOrgAddressBookService addressBookService;

    private final AddressBookResolver addressBookResolver;

    public SysOrgAddressBookController(SysOrgAddressBookService addressBookService,
                                       AddressBookResolver addressBookResolver) {
        this.addressBookService = addressBookService;
        this.addressBookResolver = addressBookResolver;
    }

    /**
     * 树查询：POST /backend/sys/org/addressbook/tree
     * 请求体平铺 { orgTypes, limitRange, parentId }；
     * parentId 为空返回根级（limitRange 非空时以范围节点为根），否则返回该节点的直接下级。
     *
     * @param query 树查询条件（orgTypes 组织类型、limitRange 范围、parentId 父节点）
     * @return 统一响应，data 为当前层的组织架构元素列表
     */
    @PostMapping("/tree")
    @Operation(summary = "地址本树查询", description = "鉴权动作 sys.org.addressbook.tree；请求体 "
            + "{ orgTypes, limitRange, parentId }，parentId 为空返回根级（limitRange 非空时以范围节点为根），"
            + "否则返回该节点的直接下级")
    public BaseResponse<List<SysOrgElement>> tree(@RequestBody SysOrgAddressBookQuery query) {
        authorize(ACTION_TREE);
        return BaseResponse.success(addressBookService.queryTree(query), null);
    }

    /**
     * 关键字列表分页查询：POST /backend/sys/org/addressbook/list
     * 请求体为 { body: { keyword, orgTypes, limitRange }, pages: { pageNo, pageSize, needPage } }，
     * 仅根据上层关键字输入框分页查询，总数通过 metadata.total 返回。
     *
     * @param request 关键字与分页请求
     * @return 统一响应，data 为命中的组织架构元素列表，metadata.total 为总数
     */
    @PostMapping("/list")
    @Operation(summary = "地址本关键字分页查询", description = "鉴权动作 sys.org.addressbook.list；请求体 "
            + "{ body: { keyword, orgTypes, limitRange }, pages }，总数经 metadata.total 返回")
    public BaseResponse<List<SysOrgElement>> list(@RequestBody SysOrgAddressBookListRequest request) {
        authorize(ACTION_LIST);
        SysOrgAddressBookQuery query = null == request ? null : request.getBody();
        Pages pages = null == request ? null : request.getPages();
        List<SysOrgElement> list = addressBookService.queryList(query, pages);
        long total = addressBookService.countList(query);
        return BaseResponse.success(list, Map.of("total", total));
    }

    /**
     * 目标回填：POST /backend/sys/org/addressbook/load
     * 请求体为 { body: { ids: string[] } }，按 id 列表读取名称等展示信息。
     *
     * @param request 回填请求，body.ids 为需要回填的元素 ID 列表
     * @return 统一响应，data 为回填所需的元素信息列表
     */
    @PostMapping("/load")
    @Operation(summary = "地址本目标回填", description = "鉴权动作 sys.org.addressbook.load；请求体 "
            + "{ body: { ids: string[] } }，按 id 列表返回名称等展示信息")
    public BaseResponse<List<SysOrgElement>> load(@RequestBody SysOrgAddressBookLoadRequest request) {
        authorize(ACTION_LOAD);
        List<String> ids = null == request || null == request.getBody() ? null : request.getBody().getIds();
        return BaseResponse.success(addressBookService.loadByIds(ids), null);
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
        if (!addressBookResolver.resolve(new AuthorizationContext(action))) {
            throw new AuthorizationException("FORBIDDEN", "no permission for action: " + action);
        }
    }
}
