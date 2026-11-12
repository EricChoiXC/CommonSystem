package com.eric.base.sys.att.controller;

import com.eric.base.common.base.controller.BaseCrudController;
import com.eric.base.common.base.model.BaseResponse;
import com.eric.base.common.base.model.Pages;
import com.eric.base.common.resolver.AuthorizationContext;
import com.eric.base.sys.att.enums.SysAttOperateType;
import com.eric.base.sys.att.model.SysAttMain;
import com.eric.base.sys.att.request.SysAttMainRequest;
import com.eric.base.sys.att.resolver.AttResolver;
import com.eric.base.sys.att.response.SysAttMainResponse;
import com.eric.base.sys.att.service.SysAttMainService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static com.eric.base.sys.att.properties.SysAttConstants.*;

/**
 * 附件主表接口
 *
 * @author Eric
 * @date 2026/8/7
 * @description 请求进入后端后的入口：只负责接收请求、基础校验、调用 Service 并返回统一响应。
 *              每个接口在执行核心业务逻辑前先通过 AttResolver 完成最终鉴权；
 *              上传/下载/预览为公共附件组件接口，后台管理接口统一要求 SYS_ATT_MAIN 权限。
 */
@RestController
@RequestMapping("${project.request.prefix}/sys/att/main")
@Tag(name = "附件-主表", description = "附件上传下载预览与后台管理；公共组件接口登录即可用，后台管理需要 SYS_ATT_MAIN")
public class SysAttMainController extends BaseCrudController<SysAttMain, SysAttMainService, AttResolver> {

    public SysAttMainController(SysAttMainService baseService, AttResolver baseResolver,
                                ApplicationEventPublisher eventPublisher) {
        super(baseService, baseResolver, eventPublisher);
    }

    /**
     * 上传：POST /backend/sys/att/main/upload
     * multipart 参数：file、modelName、modelId、key(可空)
     *
     * @param file      multipart 文件
     * @param modelName 业务模块名（如 sys_org_user）
     * @param modelId   业务数据 ID
     * @param key       同一业务下附件分组键，可为空
     * @return 统一响应，data 为保存后的附件主表记录（含生成的 fieldId）
     */
    @PostMapping("/upload")
    @Operation(summary = "上传附件", description = "鉴权动作 sys.att.main.upload；multipart 参数 "
            + "file / modelName / modelId / key(可空)，保存物理文件并写入附件主表")
    public SysAttMainResponse upload(
            @Parameter(description = "上传的文件", required = true) @RequestParam("file") MultipartFile file,
            @Parameter(description = "业务模块名，如 sys_org_user", required = true) @RequestParam("modelName") String modelName,
            @Parameter(description = "业务数据 ID", required = true) @RequestParam("modelId") String modelId,
            @Parameter(description = "同一业务下附件分组键，可空") @RequestParam(value = "key", required = false) String key) {
        authorize(new AuthorizationContext(ACTION_UPLOAD));
        return SysAttMainResponse.success(getBaseService().upload(file, modelName, modelId, key));
    }

    /**
     * 下载：GET /backend/sys/att/main/download?fieldId=${id}
     * 公共组件查看状态对所有人显示；下载次数 +1 并记录操作日志。
     *
     * @param fieldId 附件 ID
     * @return 附件二进制流（Content-Disposition: attachment，文件名按 UTF-8 编码）
     */
    @GetMapping("/download")
    @Operation(summary = "下载附件", description = "鉴权动作 sys.att.main.download；下载次数 +1 并记录操作日志，"
            + "以 attachment 方式返回文件流")
    @ApiResponse(responseCode = "200", description = "附件二进制流",
            content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                    schema = @Schema(type = "string", format = "binary")))
    public ResponseEntity<Resource> download(
            @Parameter(description = "附件 ID", required = true) @RequestParam("fieldId") String fieldId) {
        authorize(ACTION_DOWNLOAD, fieldId);
        return stream(fieldId, SysAttOperateType.DOWNLOAD, false);
    }

    /**
     * 预览：GET /backend/sys/att/main/view?fieldId=${id}
     * 公共组件查看状态对所有人显示；预览次数 +1 并记录操作日志，浏览器内联打开。
     *
     * @param fieldId 附件 ID
     * @return 附件二进制流（Content-Disposition: inline，浏览器内联打开）
     */
    @GetMapping("/view")
    @Operation(summary = "预览附件", description = "鉴权动作 sys.att.main.view；预览次数 +1 并记录操作日志，"
            + "以 inline 方式返回文件流供浏览器内联打开")
    @ApiResponse(responseCode = "200", description = "附件二进制流（inline）",
            content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                    schema = @Schema(type = "string", format = "binary")))
    public ResponseEntity<Resource> view(
            @Parameter(description = "附件 ID", required = true) @RequestParam("fieldId") String fieldId) {
        authorize(ACTION_VIEW, fieldId);
        return stream(fieldId, SysAttOperateType.VIEW, true);
    }

    /**
     * 删除（公共组件）：DELETE /backend/sys/att/main/delete?fieldId=${id}
     * 仅上传者本人或 SYS_ATT_MAIN；软删除并记录「删除」操作日志。
     *
     * @param fieldId 附件 ID
     * @return 统一响应
     */
    @DeleteMapping("/delete")
    @Operation(summary = "删除附件（公共组件）", description = "鉴权动作 sys.att.main.delete；仅上传者本人或 "
            + "SYS_ATT_MAIN；软删除并记录删除操作日志")
    public SysAttMainResponse delete(
            @Parameter(description = "附件 ID", required = true) @RequestParam("fieldId") String fieldId) {
        authorize(ACTION_DELETE, fieldId);
        getBaseService().delete(fieldId, SysAttOperateType.DELETE);
        return SysAttMainResponse.success(null);
    }

    /**
     * 后台管理 - 附件列表：POST /backend/sys/att/main/list
     * 请求体为 { body: { fieldName, fieldModelName, fieldModelId, fieldKey, fieldEnable },
     *           pages: { pageNo, pageSize, needPage }, sortField, sortOrder }，
     * 总数经 metadata.total 返回；排序字段取 createTime / download / view。
     *
     * @param request 附件列表请求（条件 + 分页 + 单条件排序），需要 SYS_ATT_MAIN
     * @return 统一响应，data 为附件列表，metadata.total 为总数
     */
    @PostMapping("/list")
    @Operation(summary = "附件列表（后台管理）", description = "鉴权动作 sys.att.main.list（需要 SYS_ATT_MAIN）；"
            + "请求体 { body: { fieldName, fieldModelName, fieldModelId, fieldKey, fieldEnable }, pages, "
            + "sortField, sortOrder }，排序字段取 createTime / download / view")
    public BaseResponse<List<SysAttMain>> selectList(@RequestBody SysAttMainRequest request) {
        authorize(new AuthorizationContext(ACTION_LIST));
        SysAttMain criteria = null == request ? null : request.getBody();
        if (null == criteria) {
            criteria = new SysAttMain();
        }
        Pages pages = null == request ? null : request.getPages();
        List<SysAttMain> list = getBaseService().list(criteria, pages,
                null == request ? null : request.getSortField(),
                null == request ? null : request.getSortOrder());
        long total = getBaseService().count(criteria);
        return BaseResponse.success(list, Map.of("total", total));
    }

    /**
     * 公共组件 - 附件列表查询：POST /backend/sys/att/main/componentList
     * 供业务页面（Attachment 组件）按 modelName + modelId + key 查询启用附件，
     * 登录且账号启用即可访问（不要求 SYS_ATT_MAIN）。
     * 请求体为 { body: { fieldModelName, fieldModelId, fieldKey }, pages: { pageNo, pageSize, needPage } }，
     * 固定按创建时间升序，仅返回启用（field_enable=true）附件；总数经 metadata.total 返回。
     *
     * @param request 附件查询请求，body 需含 fieldModelName / fieldModelId，fieldKey 可空
     * @return 统一响应，data 为启用附件列表（固定按创建时间升序），metadata.total 为总数
     */
    @PostMapping("/componentList")
    @Operation(summary = "附件列表（公共组件）", description = "鉴权动作 sys.att.main.componentList；"
            + "请求体 { body: { fieldModelName, fieldModelId, fieldKey }, pages }，仅返回启用附件，"
            + "固定按创建时间升序；登录且账号启用即可访问")
    public BaseResponse<List<SysAttMain>> componentList(@RequestBody SysAttMainRequest request) {
        authorize(new AuthorizationContext(ACTION_COMPONENT_LIST));
        SysAttMain criteria = null == request ? null : request.getBody();
        if (null == criteria) {
            criteria = new SysAttMain();
        }
        criteria.setFieldEnable(true);
        Pages pages = null == request ? null : request.getPages();
        if (null == pages) {
            pages = new Pages();
        }
        if (pages.getPageNo() < 1) {
            pages.setPageNo(1);
        }
        if (pages.getPageSize() < 1) {
            pages.setPageSize(100);
        }
        List<SysAttMain> list = getBaseService().list(criteria, pages, "createTime", "asc");
        long total = getBaseService().count(criteria);
        return BaseResponse.success(list, Map.of("total", total));
    }

    /**
     * 后台管理 - 附件详情：GET /backend/sys/att/main/load?fieldId=${id}
     *
     * @param fieldId 附件 ID
     * @return 统一响应，data 为附件主表记录；不存在返回 E_NOT_FOUND
     */
    @GetMapping("/load")
    @Operation(summary = "附件详情（后台管理）", description = "鉴权动作 sys.att.main.load（需要 SYS_ATT_MAIN）")
    public SysAttMainResponse load(
            @Parameter(description = "附件 ID", required = true) @RequestParam("fieldId") String fieldId) {
        authorize(ACTION_LOAD, fieldId);
        SysAttMain att = getBaseService().selectById(fieldId);
        if (null == att) {
            return SysAttMainResponse.failure("E_NOT_FOUND", "未找到该附件");
        }
        return SysAttMainResponse.success(att);
    }

    /**
     * 后台管理 - 删除：DELETE /backend/sys/att/main/adminDelete?fieldId=${id}
     * 操作日志类型区别于公共组件删除（4=后台删除）。
     *
     * @param fieldId 附件 ID
     * @return 统一响应
     */
    @DeleteMapping("/adminDelete")
    @Operation(summary = "删除附件（后台管理）", description = "鉴权动作 sys.att.main.adminDelete（需要 "
            + "SYS_ATT_MAIN）；操作日志类型为后台删除，区别于公共组件删除")
    public SysAttMainResponse adminDelete(
            @Parameter(description = "附件 ID", required = true) @RequestParam("fieldId") String fieldId) {
        authorize(ACTION_ADMIN_DELETE, fieldId);
        getBaseService().delete(fieldId, SysAttOperateType.ADMIN_DELETE);
        return SysAttMainResponse.success(null);
    }

    /**
     * 后台管理 - 下载：GET /backend/sys/att/main/adminDownload?fieldId=${id}
     *
     * @param fieldId 附件 ID
     * @return 附件二进制流（Content-Disposition: attachment）
     */
    @GetMapping("/adminDownload")
    @Operation(summary = "下载附件（后台管理）", description = "鉴权动作 sys.att.main.adminDownload（需要 "
            + "SYS_ATT_MAIN）；以 attachment 方式返回文件流")
    @ApiResponse(responseCode = "200", description = "附件二进制流",
            content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                    schema = @Schema(type = "string", format = "binary")))
    public ResponseEntity<Resource> adminDownload(
            @Parameter(description = "附件 ID", required = true) @RequestParam("fieldId") String fieldId) {
        authorize(ACTION_ADMIN_DOWNLOAD, fieldId);
        return stream(fieldId, SysAttOperateType.DOWNLOAD, false);
    }

    /**
     * 组装文件流响应：加载附件 → 校验物理文件 → 记录操作 → 返回流
     */
    private ResponseEntity<Resource> stream(String fieldId, SysAttOperateType type, boolean inline) {
        SysAttMain att = getBaseService().loadStreamable(fieldId);
        File file = getBaseService().resolveStorageFile(att.getFieldFilePath());
        if (!file.exists() || !file.isFile()) {
            throw new IllegalStateException("附件物理文件不存在: " + att.getFieldFilePath());
        }
        getBaseService().recordOperate(fieldId, type);
        String encodedName = URLEncoder.encode(att.getFieldName(), StandardCharsets.UTF_8).replace("+", "%20");
        String disposition = (inline ? "inline" : "attachment") + "; filename*=UTF-8''" + encodedName;
        MediaType mediaType = MediaTypeFactory.getMediaType(att.getFieldName())
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(mediaType)
                .body(new FileSystemResource(file));
    }
}
