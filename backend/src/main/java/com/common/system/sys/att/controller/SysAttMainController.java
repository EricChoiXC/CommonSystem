package com.common.system.sys.att.controller;

import com.common.system.common.base.controller.BaseCrudController;
import com.common.system.common.base.model.BaseResponse;
import com.common.system.common.base.model.Pages;
import com.common.system.common.resolver.AuthorizationContext;
import com.common.system.sys.att.enums.SysAttOperateType;
import com.common.system.sys.att.model.SysAttMain;
import com.common.system.sys.att.request.SysAttMainRequest;
import com.common.system.sys.att.resolver.AttResolver;
import com.common.system.sys.att.response.SysAttMainResponse;
import com.common.system.sys.att.service.SysAttMainService;
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

import static com.common.system.sys.att.properties.SysAttConstants.*;

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
@RequestMapping("/backend/sys/att/main")
public class SysAttMainController extends BaseCrudController<SysAttMain, SysAttMainService, AttResolver> {

    public SysAttMainController(SysAttMainService baseService, AttResolver baseResolver,
                                ApplicationEventPublisher eventPublisher) {
        super(baseService, baseResolver, eventPublisher);
    }

    /**
     * 上传：POST /backend/sys/att/main/upload
     * multipart 参数：file、modelName、modelId、key(可空)
     */
    @PostMapping("/upload")
    public SysAttMainResponse upload(@RequestParam("file") MultipartFile file,
                                     @RequestParam("modelName") String modelName,
                                     @RequestParam("modelId") String modelId,
                                     @RequestParam(value = "key", required = false) String key) {
        authorize(new AuthorizationContext(ACTION_UPLOAD));
        return SysAttMainResponse.success(getBaseService().upload(file, modelName, modelId, key));
    }

    /**
     * 下载：GET /backend/sys/att/main/download?fieldId=${id}
     * 公共组件查看状态对所有人显示；下载次数 +1 并记录操作日志。
     */
    @GetMapping("/download")
    public ResponseEntity<Resource> download(@RequestParam("fieldId") String fieldId) {
        authorize(ACTION_DOWNLOAD, fieldId);
        return stream(fieldId, SysAttOperateType.DOWNLOAD, false);
    }

    /**
     * 预览：GET /backend/sys/att/main/view?fieldId=${id}
     * 公共组件查看状态对所有人显示；预览次数 +1 并记录操作日志，浏览器内联打开。
     */
    @GetMapping("/view")
    public ResponseEntity<Resource> view(@RequestParam("fieldId") String fieldId) {
        authorize(ACTION_VIEW, fieldId);
        return stream(fieldId, SysAttOperateType.VIEW, true);
    }

    /**
     * 删除（公共组件）：DELETE /backend/sys/att/main/delete?fieldId=${id}
     * 仅上传者本人或 SYS_ATT_MAIN；软删除并记录「删除」操作日志。
     */
    @DeleteMapping("/delete")
    public SysAttMainResponse delete(@RequestParam("fieldId") String fieldId) {
        authorize(ACTION_DELETE, fieldId);
        getBaseService().delete(fieldId, SysAttOperateType.DELETE);
        return SysAttMainResponse.success(null);
    }

    /**
     * 后台管理 - 附件列表：POST /backend/sys/att/main/list
     * 请求体为 { body: { fieldName, fieldModelName, fieldModelId, fieldKey, fieldEnable },
     *           pages: { pageNo, pageSize, needPage }, sortField, sortOrder }，
     * 总数经 metadata.total 返回；排序字段取 createTime / download / view。
     */
    @PostMapping("/list")
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
     */
    @PostMapping("/componentList")
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
     */
    @GetMapping("/load")
    public SysAttMainResponse load(@RequestParam("fieldId") String fieldId) {
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
     */
    @DeleteMapping("/adminDelete")
    public SysAttMainResponse adminDelete(@RequestParam("fieldId") String fieldId) {
        authorize(ACTION_ADMIN_DELETE, fieldId);
        getBaseService().delete(fieldId, SysAttOperateType.ADMIN_DELETE);
        return SysAttMainResponse.success(null);
    }

    /**
     * 后台管理 - 下载：GET /backend/sys/att/main/adminDownload?fieldId=${id}
     */
    @GetMapping("/adminDownload")
    public ResponseEntity<Resource> adminDownload(@RequestParam("fieldId") String fieldId) {
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
