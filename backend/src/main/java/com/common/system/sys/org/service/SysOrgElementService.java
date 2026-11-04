package com.common.system.sys.org.service;

import com.common.system.common.base.model.Pages;
import com.common.system.common.utils.PasswordUtil;
import com.common.system.common.utils.StringUtil;
import com.common.system.common.utils.UserUtil;
import com.common.system.sys.org.dao.SysOrgDeptDao;
import com.common.system.sys.org.dao.SysOrgElementDao;
import com.common.system.sys.org.dao.SysOrgOrgDao;
import com.common.system.sys.org.dao.SysOrgPostDao;
import com.common.system.sys.org.dao.SysOrgUserDao;
import com.common.system.sys.org.enums.SysOrgAccountStatus;
import com.common.system.sys.org.enums.SysOrgJobStatus;
import com.common.system.sys.org.model.SysOrgDept;
import com.common.system.sys.org.model.SysOrgElement;
import com.common.system.sys.org.model.SysOrgOrg;
import com.common.system.sys.org.model.SysOrgPost;
import com.common.system.sys.org.model.SysOrgRole;
import com.common.system.sys.org.model.SysOrgUser;
import com.common.system.sys.org.request.SysOrgElementQuery;
import com.common.system.sys.org.response.SysOrgElementVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.common.system.common.properties.CommonProperties.ROLE_ADMIN;
import static com.common.system.sys.org.properties.SysOrgElementProperties.*;

/**
 * 组织架构元素业务服务
 *
 * @author Eric
 * @date 2026/8/10
 * @description 组织架构管理页的查询与维护业务：列表/详情查询、保存、编辑、启停用、删除。
 *              公共字段写 sys_org_element 主表，类型专有字段按 field_org_type 写对应扩展表；
 *              保存/编辑时自动计算 field_hierarchy / field_level，父级变更时递归修正子树。
 *              人员登录名按可见性裁剪（非管理员不可见）；创建人员时密码以 BCrypt 加密后入库，
 *              密码不随编辑接口更新（仅通过 sys.org.user.changePwd 修改），明文不落库。
 */
@Service
public class SysOrgElementService {

    private final SysOrgElementDao elementDao;
    private final SysOrgOrgDao orgDao;
    private final SysOrgDeptDao deptDao;
    private final SysOrgUserDao userDao;
    private final SysOrgPostDao postDao;

    public SysOrgElementService(SysOrgElementDao elementDao, SysOrgOrgDao orgDao,
                                SysOrgDeptDao deptDao, SysOrgUserDao userDao, SysOrgPostDao postDao) {
        this.elementDao = elementDao;
        this.orgDao = orgDao;
        this.deptDao = deptDao;
        this.userDao = userDao;
        this.postDao = postDao;
    }

    /**
     * 列表查询：归一化排序参数（白名单映射，杜绝 SQL 注入）后分页查询，
     * 非管理员对人员登录名做不可见裁剪。
     */
    public List<SysOrgElementVo> list(SysOrgElementQuery query, Pages pages,
                                      String sortField, String sortOrder) {
        List<SysOrgElementVo> list = elementDao.selectVoList(
                normalizeQuery(query),
                normalizePages(pages),
                normalizeSortField(sortField),
                normalizeSortOrder(sortOrder));
        if (!isElementManager()) {
            list.stream().filter(vo -> vo.getFieldOrgType() == ORG_TYPE_USER)
                    .forEach(vo -> vo.setFieldLoginName(null));
        }
        return list;
    }

    /**
     * 列表总数（与 {@link #list} 过滤条件一致，供分页 metadata.total 使用）
     */
    public long count(SysOrgElementQuery query) {
        return elementDao.countVoList(normalizeQuery(query));
    }

    /**
     * 详情：非管理员对人员登录名做不可见裁剪。
     */
    public SysOrgElementVo load(String fieldId) {
        SysOrgElementVo vo = elementDao.selectVoById(Objects.requireNonNull(fieldId, "fieldId must not be null").trim());
        if (null != vo && vo.getFieldOrgType() == ORG_TYPE_USER && !isElementManager()) {
            vo.setFieldLoginName(null);
        }
        return vo;
    }

    /**
     * 保存：校验公共字段后写入主表与对应扩展表，并计算层级。
     * 人员密码必填且以 BCrypt 加密后入库。
     */
    @Transactional
    public SysOrgElementVo save(SysOrgElementVo vo) {
        validateCommon(vo);
        int orgType = vo.getFieldOrgType();
        SysOrgUser current = UserUtil.getCurrentUser();
        Date now = new Date();
        vo.initFieldId();
        vo.setFieldCreatorId(null == current ? null : current.getFieldId());
        vo.setFieldCreateTime(now);
        vo.setFieldUpdaterId(null == current ? null : current.getFieldId());
        vo.setFieldUpdateTime(now);
        if (null == vo.getFieldEnable()) {
            vo.setFieldEnable(true);
        }
        rebuildHierarchy(vo);
        switch (orgType) {
            case ORG_TYPE_ORG -> {
                elementDao.insert(vo);
                orgDao.insert(toOrg(vo));
            }
            case ORG_TYPE_DEPT -> {
                elementDao.insert(vo);
                deptDao.insert(toDept(vo));
            }
            case ORG_TYPE_USER -> {
                if (StringUtil.isEmpty(vo.getFieldPassword())) {
                    throw new IllegalArgumentException("创建人员时密码不能为空");
                }
                userDao.insert(toUser(vo, true));
            }
            case ORG_TYPE_POST -> {
                elementDao.insert(vo);
                postDao.insert(toPost(vo));
            }
            default -> throw new IllegalArgumentException("无效的组织架构类型: " + orgType);
        }
        return load(vo.getFieldId());
    }

    /**
     * 编辑：更新主表与对应扩展表；父级变更时重算本节点层级并递归修正子树；
     * 人员密码不随本接口更新（仅通过 user/changePwd 修改）。
     */
    @Transactional
    public SysOrgElementVo update(SysOrgElementVo vo) {
        validateCommon(vo);
        int orgType = vo.getFieldOrgType();
        SysOrgElement exist = elementDao.selectById(vo.getFieldId());
        if (null == exist) {
            throw new IllegalArgumentException("组织架构元素不存在或已删除");
        }
        SysOrgUser current = UserUtil.getCurrentUser();
        Date now = new Date();
        vo.setFieldUpdaterId(null == current ? null : current.getFieldId());
        vo.setFieldUpdateTime(now);
        // 父级变更判定：fieldParentId 为 null 视为未携带（保留原父级），
        // 空字符串表示明确移到根级，非空 id 表示切换到新父级——避免直调 API 只改部分字段时误移根
        boolean parentChanged = false;
        if (vo.getFieldParentId() != null) {
            parentChanged = !Objects.equals(
                    normalizeParent(exist.getFieldParentId()), normalizeParent(vo.getFieldParentId()));
        }
        if (parentChanged) {
            validateParentChange(exist, vo);
            rebuildHierarchy(vo);
            elementDao.updateParentId(vo.getFieldId(), vo.getFieldParentId());
        } else {
            // 未变更父级时恢复请求未携带的层级字段：通用 update 对 field_level 无条件赋值，
            // 若用默认值 0 覆盖会导致本节点及后续挂载子节点的层级错乱
            if (StringUtil.isEmpty(vo.getFieldHierarchy()) || vo.getFieldLevel() < 1) {
                vo.setFieldHierarchy(exist.getFieldHierarchy());
                vo.setFieldLevel(exist.getFieldLevel());
            }
        }
        switch (orgType) {
            case ORG_TYPE_ORG -> {
                elementDao.update(vo);
                if (hasOrgFields(vo)) {
                    orgDao.update(toOrg(vo));
                }
            }
            case ORG_TYPE_DEPT -> {
                elementDao.update(vo);
                if (hasDeptFields(vo)) {
                    deptDao.update(toDept(vo));
                }
            }
            case ORG_TYPE_USER -> {
                // 登录名可见性：非管理员不可修改人员登录名，保留库中现值（编辑表单对非管理员不可见登录名）
                if (!isElementManager()) {
                    SysOrgElementVo currentVo = elementDao.selectVoById(vo.getFieldId());
                    vo.setFieldLoginName(null == currentVo ? null : currentVo.getFieldLoginName());
                }
                // 密码不随编辑接口更新（仅通过 user/changePwd 修改）：一律忽略请求携带的密码，
                // 防止通过更新接口意外重置他人密码
                vo.setFieldPassword(null);
                userDao.update(toUser(vo, false));
            }
            case ORG_TYPE_POST -> {
                elementDao.update(vo);
                if (hasPostFields(vo)) {
                    postDao.update(toPost(vo));
                }
            }
            default -> throw new IllegalArgumentException("无效的组织架构类型: " + orgType);
        }
        if (parentChanged) {
            rebuildChildren(vo);
        }
        return load(vo.getFieldId());
    }

    /**
     * 启停用：仅更新 field_enable 与修改人/修改时间。
     */
    @Transactional
    public void updateEnable(String fieldId, Boolean enable) {
        if (StringUtil.isEmpty(fieldId)) {
            throw new IllegalArgumentException("fieldId must not be blank");
        }
        if (null == enable) {
            throw new IllegalArgumentException("enable must not be null");
        }
        SysOrgUser current = UserUtil.getCurrentUser();
        int affected = elementDao.updateEnable(fieldId.trim(), enable,
                null == current ? null : current.getFieldId(), new Date());
        if (affected != 1) {
            throw new IllegalStateException("updateEnable expected one affected row but got " + affected);
        }
    }

    /**
     * 删除（置为无效）：校验节点存在且无直接下级，按类型删除扩展表与主表记录。
     */
    @Transactional
    public void delete(String fieldId) {
        if (StringUtil.isEmpty(fieldId)) {
            throw new IllegalArgumentException("fieldId must not be blank");
        }
        String id = fieldId.trim();
        SysOrgElementVo exist = elementDao.selectVoById(id);
        if (null == exist) {
            throw new IllegalArgumentException("组织架构元素不存在或已删除");
        }
        if (elementDao.countChildren(id) > 0) {
            throw new IllegalArgumentException("该节点存在下级组织架构，无法置为无效");
        }
        switch (exist.getFieldOrgType()) {
            case ORG_TYPE_ORG -> {
                orgDao.deleteById(id);
                elementDao.deleteById(id);
            }
            case ORG_TYPE_DEPT -> {
                deptDao.deleteById(id);
                elementDao.deleteById(id);
            }
            case ORG_TYPE_USER -> userDao.deleteById(id);
            case ORG_TYPE_POST -> {
                postDao.deleteById(id);
                elementDao.deleteById(id);
            }
            default -> elementDao.deleteById(id);
        }
    }

    /* ==================== 私有方法 ==================== */

    private static void validateCommon(SysOrgElementVo vo) {
        Objects.requireNonNull(vo, "body must not be null");
        if (StringUtil.isEmpty(vo.getFieldName())) {
            throw new IllegalArgumentException("名称不能为空");
        }
        if (StringUtil.isEmpty(vo.getFieldCode())) {
            throw new IllegalArgumentException("编码不能为空");
        }
        // 白名单校验：角色(5)/群组(6) 不参与组织架构树，禁止经 element 接口写入
        if (!ELEMENT_WRITABLE_ORG_TYPES.contains(vo.getFieldOrgType())) {
            throw new IllegalArgumentException("无效的组织架构类型: " + vo.getFieldOrgType());
        }
    }

    /**
     * 父级变更校验：新父级不能是自身或其任意后代，避免形成环导致递归死循环。
     */
    private void validateParentChange(SysOrgElement exist, SysOrgElementVo vo) {
        String newParentId = normalizeParent(vo.getFieldParentId());
        if (null == newParentId) {
            return; // 移到根级，无环风险
        }
        if (newParentId.equals(exist.getFieldId())) {
            throw new IllegalArgumentException("上级不能设置为自身");
        }
        if (StringUtil.isEmpty(exist.getFieldHierarchy())) {
            return; // 旧数据无层级信息时仅校验自身
        }
        SysOrgElement newParent = elementDao.selectById(newParentId);
        if (null == newParent) {
            throw new IllegalArgumentException("上级组织架构不存在: " + newParentId);
        }
        // 新父级的层级以自身层级为前缀 ⇒ 新父级在自身子树内，构成环
        if (StringUtil.isNotEmpty(newParent.getFieldHierarchy())
                && newParent.getFieldHierarchy().startsWith(exist.getFieldHierarchy())) {
            throw new IllegalArgumentException("上级不能设置为自身的下级节点");
        }
    }

    /**
     * 计算/重算节点的 field_hierarchy 与 field_level：
     * 根级为 "/{id}/" + level 1；子级在父级基础上追加 "/{id}/" 且 level + 1。
     */
    private void rebuildHierarchy(SysOrgElement element) {
        String parentId = normalizeParent(element.getFieldParentId());
        if (null == parentId) {
            element.setFieldParentId(null);
            element.setFieldHierarchy("/" + element.getFieldId() + "/");
            element.setFieldLevel(1);
            return;
        }
        SysOrgElement parent = elementDao.selectById(parentId);
        if (null == parent) {
            throw new IllegalArgumentException("上级组织架构不存在: " + parentId);
        }
        element.setFieldParentId(parentId);
        element.setFieldHierarchy(parent.getFieldHierarchy() + element.getFieldId() + "/");
        element.setFieldLevel(parent.getFieldLevel() + 1);
    }

    /**
     * 父级变更后递归修正子树层级（按当前 hierarchy / level 重新计算）。
     */
    private void rebuildChildren(SysOrgElement parent) {
        SysOrgElementQuery query = new SysOrgElementQuery();
        query.setParentId(parent.getFieldId());
        List<SysOrgElementVo> children = elementDao.selectVoList(query, null, SORT_CREATE_TIME, ORDER_DESC);
        SysOrgUser current = UserUtil.getCurrentUser();
        String updaterId = null == current ? null : current.getFieldId();
        Date now = new Date();
        for (SysOrgElementVo child : children) {
            elementDao.updateHierarchy(child.getFieldId(),
                    parent.getFieldHierarchy() + child.getFieldId() + "/",
                    parent.getFieldLevel() + 1, updaterId, now);
            child.setFieldHierarchy(parent.getFieldHierarchy() + child.getFieldId() + "/");
            child.setFieldLevel(parent.getFieldLevel() + 1);
            rebuildChildren(child);
        }
    }

    private static String normalizeParent(String parentId) {
        return StringUtil.isEmpty(parentId) ? null : parentId.trim();
    }

    private static SysOrgElementQuery normalizeQuery(SysOrgElementQuery query) {
        if (null == query) {
            return new SysOrgElementQuery();
        }
        query.setParentId(normalizeParent(query.getParentId()));
        query.setFieldName(StringUtil.isEmpty(query.getFieldName()) ? null : query.getFieldName().trim());
        query.setFieldCode(StringUtil.isEmpty(query.getFieldCode()) ? null : query.getFieldCode().trim());
        return query;
    }

    /**
     * 排序字段白名单：createTime / updateTime / code，非法值回退默认创建时间。
     */
    private static String normalizeSortField(String sortField) {
        if (SORT_UPDATE_TIME.equals(sortField)) {
            return SORT_UPDATE_TIME;
        }
        if (SORT_CODE.equals(sortField)) {
            return SORT_CODE;
        }
        return SORT_CREATE_TIME;
    }

    /**
     * 排序方向白名单：asc / desc，非法值回退默认降序。
     */
    private static String normalizeSortOrder(String sortOrder) {
        return ORDER_ASC.equalsIgnoreCase(sortOrder) ? ORDER_ASC : ORDER_DESC;
    }

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

    private static SysOrgOrg toOrg(SysOrgElementVo vo) {
        SysOrgOrg org = new SysOrgOrg();
        org.setFieldId(vo.getFieldId());
        org.setFieldLeaderId(vo.getFieldLeaderId());
        org.setFieldPhone(vo.getFieldPhone());
        org.setFieldEmail(vo.getFieldEmail());
        org.setFieldAddress(vo.getFieldAddress());
        return org;
    }

    private static SysOrgDept toDept(SysOrgElementVo vo) {
        SysOrgDept dept = new SysOrgDept();
        dept.setFieldId(vo.getFieldId());
        dept.setFieldLeaderId(vo.getFieldLeaderId());
        dept.setFieldPhone(vo.getFieldPhone());
        dept.setFieldAddress(vo.getFieldAddress());
        return dept;
    }

    /**
     * 转人员扩展对象：创建（withPassword=true）时密码必填并 BCrypt 加密；
     * 编辑时一律不携带密码——密码仅通过 user/changePwd 接口更新，不随编辑接口修改。
     */
    private static SysOrgUser toUser(SysOrgElementVo vo, boolean creating) {
        SysOrgUser user = new SysOrgUser();
        user.setFieldId(vo.getFieldId());
        user.setFieldCode(vo.getFieldCode());
        user.setFieldName(vo.getFieldName());
        user.setFieldParentId(vo.getFieldParentId());
        user.setFieldHierarchy(vo.getFieldHierarchy());
        user.setFieldLevel(vo.getFieldLevel());
        user.setFieldOrgType(vo.getFieldOrgType());
        user.setFieldIsVirtual(vo.isFieldIsVirtual());
        user.setFieldCreatorId(vo.getFieldCreatorId());
        user.setFieldCreateTime(vo.getFieldCreateTime());
        user.setFieldUpdaterId(vo.getFieldUpdaterId());
        user.setFieldUpdateTime(vo.getFieldUpdateTime());
        user.setFieldEnable(vo.getFieldEnable());
        user.setFieldPhone(vo.getFieldPhone());
        user.setFieldEmail(vo.getFieldEmail());
        user.setFieldAddress(vo.getFieldAddress());
        user.setFieldGender(null == vo.getFieldGender() ? 1 : vo.getFieldGender());
        user.setFieldBirthday(vo.getFieldBirthday());
        user.setFieldLoginName(vo.getFieldLoginName());
        user.setFieldJobStatus(null == vo.getFieldJobStatus() ? SysOrgJobStatus.WORKING.getValue() : vo.getFieldJobStatus());
        user.setFieldAccountStatus(null == vo.getFieldAccountStatus() ? SysOrgAccountStatus.NORMAL.getValue() : vo.getFieldAccountStatus());
        if (creating) {
            if (StringUtil.isEmpty(vo.getFieldPassword())) {
                throw new IllegalArgumentException("创建人员时密码不能为空");
            }
            user.setFieldPassword(PasswordUtil.encode(vo.getFieldPassword()));
        }
        return user;
    }

    private static SysOrgPost toPost(SysOrgElementVo vo) {
        SysOrgPost post = new SysOrgPost();
        post.setFieldId(vo.getFieldId());
        post.setFieldLeaderId(vo.getFieldLeaderId());
        return post;
    }

    /**
     * 扩展表更新保护：扩展表 Mapper 的 update 使用 <set> + <if> 动态拼接，
     * 全部字段为 null 时会产生空 SET 导致 SQL 语法错误；无扩展字段时跳过扩展表更新。
     */
    private static boolean hasOrgFields(SysOrgElementVo vo) {
        return null != vo.getFieldLeaderId() || null != vo.getFieldPhone()
                || null != vo.getFieldEmail() || null != vo.getFieldAddress();
    }

    private static boolean hasDeptFields(SysOrgElementVo vo) {
        return null != vo.getFieldLeaderId() || null != vo.getFieldPhone() || null != vo.getFieldAddress();
    }

    private static boolean hasPostFields(SysOrgElementVo vo) {
        return null != vo.getFieldLeaderId();
    }

    /**
     * 是否为组织架构管理员（含系统管理员/模块管理员），用于人员登录名等敏感信息的可见性判断。
     * 角色权限码取 sys_org_role.field_code。
     */
    private static boolean isElementManager() {
        SysOrgUser user = UserUtil.getCurrentUser();
        if (null == user || null == user.getRoles()) {
            return false;
        }
        return user.getRoles().stream()
                .map(SysOrgRole::getFieldCode)
                .anyMatch(Set.of(ROLE_ADMIN, ROLE_SYS_ORG_ADMIN, ROLE_SYS_ORG_ELEMENT_ADMIN)::contains);
    }
}
