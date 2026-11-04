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
     */
    @PostMapping("/tree")
    public BaseResponse<List<SysOrgElement>> tree(@RequestBody SysOrgAddressBookQuery query) {
        authorize(ACTION_TREE);
        return BaseResponse.success(addressBookService.queryTree(query), null);
    }

    /**
     * 关键字列表分页查询：POST /backend/sys/org/addressbook/list
     * 请求体为 { body: { keyword, orgTypes, limitRange }, pages: { pageNo, pageSize, needPage } }，
     * 仅根据上层关键字输入框分页查询，总数通过 metadata.total 返回。
     */
    @PostMapping("/list")
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
     */
    @PostMapping("/load")
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
