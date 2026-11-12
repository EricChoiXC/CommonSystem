package com.eric.base.sys.dict.controller;

import com.eric.base.common.base.controller.BaseCrudController;
import com.eric.base.common.base.model.BaseRequest;
import com.eric.base.common.base.model.BaseResponse;
import com.eric.base.common.base.model.ImportResultVo;
import com.eric.base.common.base.service.ImportExportService;
import com.eric.base.common.resolver.AuthorizationContext;
import com.eric.base.sys.dict.model.SysDictItem;
import com.eric.base.sys.dict.model.SysDictType;
import com.eric.base.sys.dict.request.SysDictItemImportVo;
import com.eric.base.sys.dict.request.SysDictItemsRequest;
import com.eric.base.sys.dict.resolver.DictResolver;
import com.eric.base.sys.dict.response.SysDictItemExportVo;
import com.eric.base.sys.dict.response.SysDictItemVo;
import com.eric.base.sys.dict.service.SysDictItemService;
import com.eric.base.sys.dict.service.SysDictService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static com.eric.base.sys.dict.properties.SysDictConstants.*;

/**
 * 码值接口
 *
 * @author Eric
 * @date 2026/9/2
 * @description 码值模块的请求入口：只负责接收请求、基础校验、执行最终鉴权并调用
 *              Service 返回统一响应。每个接口在执行核心业务逻辑前先通过 DictResolver
 *              完成鉴权：类型/码值项列表与「通过类型编号获取码值项」登录即可（页面全员可见），
 *              码值项新增/编辑/删除、导入/导出/模板下载仅 SYS_DICT_ADMIN（或超管 SYS_ADMIN，
 *              导入导出走公共导入导出通道）。
 *              接口路径遵循 /backend/{根模块}/{模块}/{次级模块}/{请求方法}。
 */
@RestController
@RequestMapping("${project.request.prefix}/sys/dict")
@Tag(name = "码值", description = "码值管理：类型与码值项查询（登录即可）、码值项维护（仅 SYS_DICT_ADMIN）")
public class SysDictController extends BaseCrudController<SysDictType, SysDictService, DictResolver> {

    /** 码值项行实体服务：公共导入导出通道的行数据查询与落库（行实体 ≠ Controller 泛型实体） */
    private final SysDictItemService itemService;

    public SysDictController(SysDictService baseService, SysDictItemService itemService, DictResolver baseResolver,
                             ApplicationEventPublisher eventPublisher, ImportExportService importExportService) {
        super(baseService, baseResolver, eventPublisher, importExportService);
        this.itemService = itemService;
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

    /**
     * 导出码值项：POST /backend/sys/dict/export
     * 请求体 { body, params }（条件与 /list 同构；导出恒全量，body 作筛选条件）；
     * 返回 xlsx 文件流，列 = 类型名称 / 显示值 / 码值 / 是否启用（yes_no 码值转换），
     * 文件名 dict_items_{yyyyMMddHHmmss}.xlsx，导出文件 round-trip 可回传导入。
     *
     * @param request 列表查询请求（body 为筛选条件；null 视为无条件全量）
     * @return 码值项 xlsx 文件流（attachment）
     */
    @PostMapping("/export")
    @Operation(summary = "导出码值项", description = "鉴权动作 " + ACTION_ITEM_EXPORT
            + "（仅 SYS_DICT_ADMIN）；请求体 { body, params }（条件与 itemList 同构，导出恒全量），"
            + "返回 xlsx 文件流：列 = 类型名称 / 显示值 / 码值 / 是否启用，"
            + "文件名 dict_items_{yyyyMMddHHmmss}.xlsx，round-trip 可回传导入")
    @ApiResponse(responseCode = "200", description = "码值项 xlsx 文件流",
            content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                    schema = @Schema(type = "string", format = "binary")))
    public ResponseEntity<Resource> export(@RequestBody(required = false) BaseRequest<SysDictItem> request) {
        authorize(new AuthorizationContext(ACTION_ITEM_EXPORT));
        return exportExcel("dict_items", SysDictItemExportVo.class,
                itemService.selectAll(null == request ? null : request.getBody()),
                getBaseService().exportConverter());
    }

    /**
     * 导入码值项：POST /backend/sys/dict/import（multipart/form-data）
     * file 必填（.xlsx，列 = 类型名称 / 显示值 / 码值 / 是否启用，与模板一致）；
     * params 可选 JSON（公共开关 ignoreError，默认 false：任一行失败整体抛出回滚）。
     * 行级校验：类型名称存在且启用 + 同类型下码值唯一（既有 saveItem 同款口径）；
     * 导入行固定人工码值 field_system = false、field_enable 空默认启用。
     *
     * @param file   码值项 xlsx 文件（列头与模板一致）
     * @param params 导入配置 JSON（如 {"ignoreError":true}；可选，缺省 ignoreError=false）
     * @return 统一响应，data 为导入结果（total / success / failed / errors）
     */
    @PostMapping("/import")
    @Operation(summary = "导入码值项", description = "鉴权动作 " + ACTION_ITEM_IMPORT
            + "（仅 SYS_DICT_ADMIN）；multipart/form-data：file（必填，.xlsx，列头与模板一致）+ "
            + "params（可选 JSON，公共开关 ignoreError 默认 false，任一行失败整体回滚）；"
            + "行级校验：类型名称存在且启用 + 同类型下码值唯一，导入行固定人工码值 field_system = false")
    public BaseResponse<ImportResultVo> importItems(
            @Parameter(description = "码值项 xlsx 文件（列头与模板一致）", required = true)
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "导入配置 JSON（如 {\"ignoreError\":true}）；可选，缺省 ignoreError=false")
            @RequestPart(value = "params", required = false) String params) {
        authorize(new AuthorizationContext(ACTION_ITEM_IMPORT));
        return importExcel(file, params, SysDictItemImportVo.class,
                getBaseService().importRowHandler(), getBaseService().importToEntity(), itemService);
    }

    /**
     * 下载码值项导入模板：POST /backend/sys/dict/template
     * 请求体 { body, params }（与 /export 同构，模板不消费其内容）；返回仅含标题行的
     * 空模板 xlsx 文件流，列 = 类型名称 / 显示值 / 码值 / 是否启用，
     * 文件名 dict_items_template.xlsx。
     *
     * @param request 请求体（与 /export 同构；内容不消费，允许 null）
     * @return 空模板 xlsx 文件流（attachment，仅标题行）
     */
    @PostMapping("/template")
    @Operation(summary = "下载码值项导入模板", description = "鉴权动作 " + ACTION_ITEM_TEMPLATE
            + "（仅 SYS_DICT_ADMIN）；请求体 { body, params }（与 /export 同构，内容不消费），"
            + "返回仅含标题行的空模板 xlsx 文件流，文件名 dict_items_template.xlsx")
    @ApiResponse(responseCode = "200", description = "码值项导入模板 xlsx 文件流",
            content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                    schema = @Schema(type = "string", format = "binary")))
    public ResponseEntity<Resource> template(@RequestBody(required = false) BaseRequest<SysDictItem> request) {
        authorize(new AuthorizationContext(ACTION_ITEM_TEMPLATE));
        return exportTemplate("dict_items", SysDictItemExportVo.class);
    }
}
