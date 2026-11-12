package com.eric.base.sys.att.controller;

import com.eric.base.common.base.controller.BaseCrudController;
import com.eric.base.common.base.model.BaseResponse;
import com.eric.base.common.base.model.Pages;
import com.eric.base.common.resolver.AuthorizationContext;
import com.eric.base.sys.att.model.SysAttFile;
import com.eric.base.sys.att.request.SysAttFileRequest;
import com.eric.base.sys.att.resolver.AttResolver;
import com.eric.base.sys.att.service.SysAttFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static com.eric.base.sys.att.properties.SysAttConstants.ACTION_FILE_LIST;

/**
 * 附件文件表接口
 *
 * @author Eric
 * @date 2026/8/7
 * @description 后台管理-文件列表：仅对 sys_att_file 查询，
 *              核心查询信息为文件类型、文件大小、保存路径、是否删除等，要求 SYS_ATT_MAIN 权限。
 */
@RestController
@RequestMapping("${project.request.prefix}/sys/att/file")
@Tag(name = "附件-文件", description = "后台管理：物理文件清单查询（文件类型/大小/路径/是否删除）；需要 SYS_ATT_MAIN")
public class SysAttFileController extends BaseCrudController<SysAttFile, SysAttFileService, AttResolver> {

    public SysAttFileController(SysAttFileService baseService, AttResolver baseResolver,
                                ApplicationEventPublisher eventPublisher) {
        super(baseService, baseResolver, eventPublisher);
    }

    /**
     * 文件列表查询：POST /backend/sys/att/file/list
     * 请求体为 { body: { fieldName, fieldFileType, fieldFilePath, fieldIsDelete },
     *           pages: { pageNo, pageSize, needPage }, sortField, sortOrder }，
     * 总数经 metadata.total 返回；排序字段取 createTime / fileSize。
     *
     * @param request 文件列表请求（条件 + 分页 + 单条件排序）
     * @return 统一响应，data 为文件列表，metadata.total 为总数
     */
    @PostMapping("/list")
    @Operation(summary = "文件列表查询", description = "鉴权动作 sys.att.file.list（需要 SYS_ATT_MAIN）；"
            + "请求体 { body: { fieldName, fieldFileType, fieldFilePath, fieldIsDelete }, pages, "
            + "sortField, sortOrder }，排序字段取 createTime / fileSize")
    public BaseResponse<List<SysAttFile>> selectList(@RequestBody SysAttFileRequest request) {
        authorize(new AuthorizationContext(ACTION_FILE_LIST));
        SysAttFile criteria = null == request ? null : request.getBody();
        if (null == criteria) {
            criteria = new SysAttFile();
        }
        Pages pages = null == request ? null : request.getPages();
        List<SysAttFile> list = getBaseService().list(criteria, pages,
                null == request ? null : request.getSortField(),
                null == request ? null : request.getSortOrder());
        long total = getBaseService().count(criteria);
        return BaseResponse.success(list, Map.of("total", total));
    }
}
