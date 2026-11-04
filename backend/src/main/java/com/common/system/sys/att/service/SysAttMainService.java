package com.common.system.sys.att.service;

import com.common.system.common.base.model.Pages;
import com.common.system.common.base.service.BaseService;
import com.common.system.common.exception.AuthorizationException;
import com.common.system.common.utils.StringUtil;
import com.common.system.common.utils.UserUtil;
import com.common.system.sys.att.dao.SysAttFileDao;
import com.common.system.sys.att.dao.SysAttMainDao;
import com.common.system.sys.att.dao.SysAttOperationDao;
import com.common.system.sys.att.enums.SysAttOperateType;
import com.common.system.sys.att.model.SysAttMain;
import com.common.system.sys.att.model.SysAttOperation;
import com.common.system.sys.att.properties.SysAttProperties;
import com.common.system.sys.org.model.SysOrgUser;
import com.common.system.sys.process.properties.SysProcessConstants;
import com.common.system.sys.process.properties.SysProcessTemplateProperties;
import com.common.system.sys.process.service.SysProcessExampleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 附件主表业务服务
 *
 * @author Eric
 * @date 2026/8/7
 * @description 承载附件核心业务：上传（sys_att_main + sys_att_file 双表同 ID 写入）、
 *              下载/预览（次数 +1 并记录操作日志）、删除（软删除并记录操作日志）。
 *              业务规则依据 artificial/docs/设计文档/sys/attachment/附件核心业务.md。
 */
@Service
public class SysAttMainService extends BaseService<SysAttMain, SysAttMainDao> {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

    /**
     * 禁止上传的危险/可执行/可脚本化扩展名（黑名单，纵深防护；正常业务类型不受限）
     */
    private static final Set<String> FORBIDDEN_EXTENSIONS = Set.of(
            "jsp", "jspx", "html", "htm", "svg", "js", "exe", "bat", "cmd",
            "sh", "php", "asp", "aspx", "jar", "class");

    private final SysAttFileDao sysAttFileDao;

    private final SysAttOperationDao sysAttOperationDao;

    private final SysAttProperties sysAttProperties;

    private final SysProcessExampleService processExampleService;

    public SysAttMainService(SysAttMainDao baseDao, SysAttFileDao sysAttFileDao,
                             SysAttOperationDao sysAttOperationDao, SysAttProperties sysAttProperties,
                             SysProcessExampleService processExampleService) {
        super(baseDao);
        this.sysAttFileDao = sysAttFileDao;
        this.sysAttOperationDao = sysAttOperationDao;
        this.sysAttProperties = sysAttProperties;
        this.processExampleService = processExampleService;
    }

    /**
     * 附件列表分页查询（后台管理）：支持单条件排序
     * （createTime / download / view，白名单映射）与分页参数兜底。
     */
    public List<SysAttMain> list(SysAttMain criteria, Pages pages, String sortField, String sortOrder) {
        return getBaseDao().selectListSorted(null == criteria ? new SysAttMain() : criteria,
                normalizePages(pages), normalizeSortField(sortField), normalizeSortOrder(sortOrder));
    }

    /**
     * 附件列表总数（与 {@link #list} 过滤条件一致，供分页 metadata.total 使用）
     */
    public long count(SysAttMain criteria) {
        return getBaseDao().selectCount(null == criteria ? new SysAttMain() : criteria);
    }

    /**
     * 分页参数兜底：null 视为不分页（needPage=false）；
     * pageNo / pageSize 小于 1 时归位到合法最小值，避免 SQL 出现负 OFFSET / 非法 LIMIT。
     */
    private static Pages normalizePages(Pages pages) {
        Pages safe = null == pages ? new Pages() : pages;
        if (safe.getPageNo() < 1) {
            safe.setPageNo(1);
        }
        if (safe.getPageSize() < 1) {
            safe.setPageSize(10);
        }
        return safe;
    }

    /**
     * 排序字段白名单：createTime / download / view，非法值回退默认创建时间。
     */
    private static String normalizeSortField(String sortField) {
        if ("download".equals(sortField) || "view".equals(sortField)) {
            return sortField;
        }
        return "createTime";
    }

    /**
     * 排序方向白名单：asc / desc，非法值回退默认降序。
     */
    private static String normalizeSortOrder(String sortOrder) {
        return "asc".equalsIgnoreCase(sortOrder) ? "asc" : "desc";
    }

    /**
     * 上传附件
     * <p>保存物理文件到存储目录（按月份分子目录），sys_att_main 与 sys_att_file
     * 同 field_id 双表写入，并记录「上传」操作日志；DB 写入失败时回滚物理文件。</p>
     *
     * @param file      上传文件（multipart）
     * @param modelName 模块名
     * @param modelId   文档 id
     * @param key       关键字（可空）
     * @return 上传后的附件主表对象（含文件信息）
     */
    @Transactional
    public SysAttMain upload(MultipartFile file, String modelName, String modelId, String key) {
        if (null == file || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        if (StringUtil.isEmpty(modelName)) {
            throw new IllegalArgumentException("模块名不能为空");
        }
        if (StringUtil.isEmpty(modelId)) {
            throw new IllegalArgumentException("文档id不能为空");
        }
        SysOrgUser current = UserUtil.getCurrentUser();
        if (null == current) {
            throw new AuthorizationException("UNAUTHORIZED", "登录已过期，请重新登录");
        }
        // 审批中单据附件受流程「操作标识」约束（默认不允许；非审批中/无流程约束放行）
        requireAttachmentOpAllowed(modelName, modelId);
        String originalName = file.getOriginalFilename();
        if (StringUtil.isEmpty(originalName)) {
            originalName = "unnamed";
        }
        String ext = extensionOf(originalName);
        String fieldId = new SysAttMain().initFieldId();
        String monthDir = MONTH_FORMAT.format(LocalDate.now());
        String storedName = StringUtil.isEmpty(ext) ? fieldId : fieldId + "." + ext;
        String relativePath = monthDir + "/" + storedName;

        File target = resolveStorageFile(relativePath);
        try {
            Files.createDirectories(target.getParentFile().toPath());
            file.transferTo(target);
        } catch (IOException e) {
            throw new IllegalStateException("文件保存失败: " + e.getMessage(), e);
        }

        SysAttMain att = new SysAttMain();
        att.setFieldId(fieldId);
        att.setFieldName(originalName);
        att.setFieldFileType(ext);
        att.setFieldModelName(modelName);
        att.setFieldModelId(modelId);
        att.setFieldKey(key);
        att.setFieldEnable(true);
        att.setFieldCreatorId(current.getFieldId());
        att.setFieldCreateTime(new Date());
        att.setFieldDownload(0);
        att.setFieldView(0);
        att.setFieldFilePath(relativePath);
        att.setFieldFileSize(file.getSize());
        att.setFieldIsDelete(false);
        try {
            getBaseDao().insert(att);
            sysAttOperationDao.insert(buildOperation(fieldId, SysAttOperateType.UPLOAD,
                    "上传附件: " + originalName, current.getFieldId()));
        } catch (RuntimeException e) {
            target.delete();
            throw e;
        }
        return att;
    }

    /**
     * 加载可流式读取的附件：校验附件有效、文件记录存在且未删除、保存路径非空。
     * 下载与预览接口共用。
     *
     * @param fieldId 附件 ID
     * @return 附件主表对象（含文件信息）
     */
    public SysAttMain loadStreamable(String fieldId) {
        SysAttMain att = getBaseDao().selectById(fieldId);
        if (null == att || Boolean.FALSE.equals(att.getFieldEnable())) {
            throw new IllegalArgumentException("附件不存在或已失效");
        }
        if (Boolean.TRUE.equals(att.getFieldIsDelete())) {
            throw new IllegalArgumentException("附件文件已删除");
        }
        if (StringUtil.isEmpty(att.getFieldFilePath())) {
            throw new IllegalArgumentException("附件文件路径为空");
        }
        return att;
    }

    /**
     * 记录下载/预览操作：次数 +1 并写入操作日志。
     * 仅 {@link SysAttOperateType#DOWNLOAD} 与 {@link SysAttOperateType#VIEW} 触发次数累加。
     */
    @Transactional
    public void recordOperate(String fieldId, SysAttOperateType type) {
        if (SysAttOperateType.DOWNLOAD == type) {
            getBaseDao().incrDownload(fieldId);
        } else if (SysAttOperateType.VIEW == type) {
            getBaseDao().incrView(fieldId);
        }
        SysOrgUser current = UserUtil.getCurrentUser();
        String userId = null == current ? null : current.getFieldId();
        String remark = SysAttOperateType.VIEW == type ? "预览附件" : "下载附件";
        sysAttOperationDao.insert(buildOperation(fieldId, type, remark, userId));
    }

    /**
     * 删除附件（软删除）：主表置 field_enable=0、文件表置 field_is_delete=1，并记录操作日志。
     * 公共组件删除与后台删除均走本方法，通过 operateType 区分日志（3=删除 / 4=后台删除）。
     * 已删除或不存在的附件幂等返回。
     */
    @Transactional
    public void delete(String fieldId, SysAttOperateType operateType) {
        SysAttMain att = getBaseDao().selectById(fieldId);
        if (null == att || Boolean.FALSE.equals(att.getFieldEnable())) {
            return;
        }
        // 公共组件删除受流程「操作标识」约束（后台管理删除不受限）
        if (SysAttOperateType.ADMIN_DELETE != operateType) {
            requireAttachmentOpAllowed(att.getFieldModelName(), att.getFieldModelId());
        }
        getBaseDao().updateEnable(fieldId, false);
        sysAttFileDao.updateIsDelete(fieldId, true);
        SysOrgUser current = UserUtil.getCurrentUser();
        String userId = null == current ? null : current.getFieldId();
        String remark = SysAttOperateType.ADMIN_DELETE == operateType
                ? "后台删除附件: " + att.getFieldName()
                : "删除附件: " + att.getFieldName();
        sysAttOperationDao.insert(buildOperation(fieldId, operateType, remark, userId));
    }

    /**
     * 审批中单据的附件修改（上传/删除）受流程「操作标识」约束（默认不允许）：
     * <ul>
     * <li>modelName 非流程分类 / 无流程实例 / 流程非审批中（DRAFT/RETURN 等）：无流程约束，放行
     *     （起草人编辑、合同签署等场景按各自业务规则处理）；</li>
     * <li>流程审批中：仅当当前用户为当前节点审批人且该节点开启 {@link SysProcessConstants#OP_ATTACHMENT}
     *     操作标识时才允许；否则拒绝（节点未配置/旧模板默认不允许）。</li>
     * </ul>
     * 依据 artificial/docs/设计文档/sys/process/流程核心业务.md「审批中审批人特定操作业务」。
     *
     * @param modelName 业务模块（process_template 二级配置项，如 sys_org / sys_calendar）
     * @param modelId   业务单据 id
     */
    private void requireAttachmentOpAllowed(String modelName, String modelId) {
        if (StringUtil.isEmpty(modelName) || StringUtil.isEmpty(modelId)) {
            return;
        }
        if (!SysProcessTemplateProperties.contains(modelName.trim())) {
            return;
        }
        Set<String> ops = processExampleService.resolveNodeOps(modelName, modelId);
        if (null == ops) {
            // 无流程约束（无流程实例或非审批中）
            return;
        }
        if (!ops.contains(SysProcessConstants.OP_ATTACHMENT)) {
            throw new AuthorizationException("FORBIDDEN",
                    "流程审批中，当前节点未开放「允许修改附件」操作，无法修改附件");
        }
    }

    /**
     * 将相对存储路径解析为物理文件对象
     * <p>拒绝绝对路径与包含 {@code ..} 的路径，防止越出存储根目录（纵深防护）。</p>
     *
     * @param relativePath sys_att_file.field_file_path（如 202608/xxx.pdf）
     */
    public File resolveStorageFile(String relativePath) {
        if (StringUtil.isEmpty(relativePath)
                || relativePath.startsWith("/")
                || relativePath.contains("..")) {
            throw new IllegalArgumentException("非法的附件保存路径: " + relativePath);
        }
        return Paths.get(sysAttProperties.getStoragePath(), relativePath).toFile();
    }

    /**
     * 提取小写扩展名：仅保留字母数字并限长 10，空则返回空串（无扩展名文件）；
     * 命中危险扩展名黑名单时拒绝上传。
     */
    private String extensionOf(String fileName) {
        int idx = fileName.lastIndexOf('.');
        if (idx < 0 || idx == fileName.length() - 1) {
            return "";
        }
        String ext = fileName.substring(idx + 1).toLowerCase(Locale.ROOT);
        String cleaned = ext.replaceAll("[^a-z0-9]", "");
        if (cleaned.length() > 10) {
            cleaned = cleaned.substring(0, 10);
        }
        if (FORBIDDEN_EXTENSIONS.contains(cleaned)) {
            throw new IllegalArgumentException("不允许上传该文件类型: ." + cleaned);
        }
        return cleaned;
    }

    private SysAttOperation buildOperation(String attId, SysAttOperateType type, String remark, String userId) {
        SysAttOperation operation = new SysAttOperation();
        operation.initFieldId();
        operation.setFieldAttId(attId);
        operation.setFieldUserId(userId);
        operation.setFieldOperateTime(new Date());
        operation.setFieldOperateType(type.getValue());
        operation.setFieldRemark(remark);
        return operation;
    }
}
