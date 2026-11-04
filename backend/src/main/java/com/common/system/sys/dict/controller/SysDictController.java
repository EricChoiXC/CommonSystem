package com.common.system.sys.dict.controller;

import com.common.system.common.base.controller.BaseCrudController;
import com.common.system.common.base.model.BaseResponse;
import com.common.system.common.resolver.AuthorizationContext;
import com.common.system.sys.dict.model.SysDictItem;
import com.common.system.sys.dict.model.SysDictType;
import com.common.system.sys.dict.request.SysDictItemsRequest;
import com.common.system.sys.dict.resolver.DictResolver;
import com.common.system.sys.dict.response.SysDictItemVo;
import com.common.system.sys.dict.service.SysDictService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.common.system.sys.dict.properties.SysDictConstants.*;

/**
 * 码值接口
 *
 * @author Eric
 * @date 2026/9/2
 * @description 码值模块的请求入口：只负责接收请求、基础校验、执行最终鉴权并调用
 *              Service 返回统一响应。每个接口在执行核心业务逻辑前先通过 DictResolver
 *              完成鉴权：类型/码值项列表与「通过类型编号获取码值项」登录即可（页面全员可见），
 *              码值项新增/编辑/删除仅 SYS_DICT_ADMIN（或超管 SYS_ADMIN）。
 *              接口路径遵循 /backend/{根模块}/{模块}/{次级模块}/{请求方法}。
 */
@RestController
@RequestMapping("/backend/sys/dict")
@Tag(name = "码值", description = "码值管理：类型与码值项查询（登录即可）、码值项维护（仅 SYS_DICT_ADMIN）")
public class SysDictController extends BaseCrudController<SysDictType, SysDictService, DictResolver> {

    public SysDictController(SysDictService baseService, DictResolver baseResolver,
                             ApplicationEventPublisher eventPublisher) {
        super(baseService, baseResolver, eventPublisher);
    }

    /**
     * 码值类型列表：GET /backend/sys/dict/typeList
     * 按 field_code 升序返回全部码值类型（页面左侧列表，前端按关键字过滤名称/编号）；
     * 码值类型不可维护。
     *
     * @return 统一响应，data 为按 field_code 升序的码值类型列表
     */
    @GetMapping("/typeList")
    @Operation(summary = "码值类型列表", description = "鉴权动作 sys.dict.typeList（登录即可）；"
            + "按 field_code 升序返回全部码值类型，页面左侧列表由前端按关键字过滤名称/编号")
    public BaseResponse<List<SysDictType>> typeList() {
        authorize(new AuthorizationContext(ACTION_TYPE_LIST));
        return BaseResponse.success(getBaseService().typeList(), null);
    }

    /**
     * 码值项列表：GET /backend/sys/dict/itemList?fieldTypeId=${typeId}
     * 返回指定类型下的码值项（无筛选区、无排序区，固定按 field_value 升序）。
     *
     * @param fieldTypeId 码值类型 ID
     * @return 统一响应，data 为该类型下按 field_value 升序的码值项
     */
    @GetMapping("/itemList")
    @Operation(summary = "码值项列表", description = "鉴权动作 sys.dict.itemList（登录即可）；"
            + "返回指定类型下的码值项，固定按 field_value 升序")
    public BaseResponse<List<SysDictItem>> itemList(
            @Parameter(description = "码值类型 ID", required = true) @RequestParam("fieldTypeId") String fieldTypeId) {
        authorize(new AuthorizationContext(ACTION_ITEM_LIST));
        return BaseResponse.success(getBaseService().itemList(fieldTypeId), null);
    }

    /**
     * 新增码值项：POST /backend/sys/dict/itemSave
     * 请求体 { fieldTypeId, fieldValue, fieldName, fieldEnable }；
     * 仅 SYS_DICT_ADMIN 权限账号调用；人工新增的码值固定 field_system = false。
     *
     * @param body 码值项 { fieldTypeId, fieldValue, fieldName, fieldEnable }
     * @return 统一响应，data 为保存后的码值项
     */
    @PostMapping("/itemSave")
    @Operation(summary = "新增码值项", description = "鉴权动作 sys.dict.itemSave（仅 SYS_DICT_ADMIN）；请求体 "
            + "{ fieldTypeId, fieldValue, fieldName, fieldEnable }，人工新增的码值固定 field_system = false")
    public BaseResponse<SysDictItem> itemSave(@RequestBody SysDictItem body) {
        authorize(new AuthorizationContext(ACTION_ITEM_SAVE));
        return BaseResponse.success(getBaseService().saveItem(body), null);
    }

    /**
     * 编辑码值项：PATCH /backend/sys/dict/itemUpdate
     * 请求体 { fieldId, fieldTypeId?, fieldValue?, fieldName?, fieldEnable? }；
     * field_system = true 的系统码值不可维护（Service 层拦截）。
     *
     * @param body 码值项，必须携带 fieldId；其余字段可选
     * @return 统一响应，data 为更新后的码值项；系统码值由 Service 拒绝
     */
    @PatchMapping("/itemUpdate")
    @Operation(summary = "编辑码值项", description = "鉴权动作 sys.dict.itemUpdate（仅 SYS_DICT_ADMIN）；请求体 "
            + "{ fieldId, fieldTypeId?, fieldValue?, fieldName?, fieldEnable? }，field_system = true 的系统码值不可维护")
    public BaseResponse<SysDictItem> itemUpdate(@RequestBody SysDictItem body) {
        authorize(new AuthorizationContext(ACTION_ITEM_UPDATE));
        return BaseResponse.success(getBaseService().updateItem(body), null);
    }

    /**
     * 删除码值项：DELETE /backend/sys/dict/itemDelete?fieldId=${id}
     * field_system = true 的系统码值不可删除（Service 层拦截）。
     *
     * @param fieldId 码值项 ID
     * @return 统一响应；系统码值由 Service 拒绝
     */
    @DeleteMapping("/itemDelete")
    @Operation(summary = "删除码值项", description = "鉴权动作 sys.dict.itemDelete（仅 SYS_DICT_ADMIN）；"
            + "field_system = true 的系统码值不可删除")
    public BaseResponse<Void> itemDelete(
            @Parameter(description = "码值项 ID", required = true) @RequestParam("fieldId") String fieldId) {
        authorize(ACTION_ITEM_DELETE, fieldId);
        getBaseService().deleteItem(fieldId);
        return BaseResponse.<Void>success("success", null, null);
    }

    /**
     * 通过类型编号获取码值项：POST /backend/sys/dict/items
     * 请求体 { typeCode }；返回该类型下已启用的码值项（按 field_value 升序），
     * 供前端下拉和其他模块服务快速使用；类型不存在时返回空列表。
     *
     * @param request 请求体 { typeCode }
     * @return 统一响应，data 为该类型下已启用的码值项（按 field_value 升序）
     */
    @PostMapping("/items")
    @Operation(summary = "按类型编号取码值项", description = "鉴权动作 sys.dict.items（登录即可）；请求体 "
            + "{ typeCode }，返回该类型下已启用的码值项（按 field_value 升序），类型不存在返回空列表")
    public BaseResponse<List<SysDictItemVo>> items(@RequestBody SysDictItemsRequest request) {
        authorize(new AuthorizationContext(ACTION_ITEMS));
        String typeCode = null == request ? null : request.getTypeCode();
        return BaseResponse.success(getBaseService().itemsByTypeCode(typeCode), null);
    }
}
