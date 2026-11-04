package com.common.system.sys.org.service;

import com.common.system.common.base.model.Pages;
import com.common.system.common.base.service.BaseService;
import com.common.system.common.properties.CommonProperties;
import com.common.system.common.utils.StringUtil;
import com.common.system.common.utils.UserUtil;
import com.common.system.sys.org.dao.SysOrgElementDao;
import com.common.system.sys.org.dao.SysOrgRoleDao;
import com.common.system.sys.org.model.SysOrgAuth;
import com.common.system.sys.org.model.SysOrgElement;
import com.common.system.sys.org.model.SysOrgRole;
import com.common.system.sys.org.model.SysOrgUser;
import com.common.system.sys.org.properties.SysOrgElementProperties;
import com.common.system.sys.org.request.SysOrgRoleQuery;
import com.common.system.sys.org.response.SysOrgRoleVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * 角色业务服务
 *
 * @author Eric
 * @date 2026/8/7
 * @description 承载「系统管理员角色初始化」与「角色管理」业务：
 *              列表/详情查询（含关联组织架构与权限回显）、新建/编辑（全量替换
 *              sys_org_role_element / sys_org_role_auth 关联表）。
 */
@Service
public class SysOrgRoleService extends BaseService<SysOrgRole, SysOrgRoleDao> {

    private static final Logger log = LoggerFactory.getLogger(SysOrgRoleService.class);

    /** 系统管理员角色 ID：'0' 补足 32 位 */
    public static final String SYSTEM_ADMIN_ROLE_ID = "0".repeat(32);

    /** SYS_ADMIN 权限的归属模块（与 common/application.yml 的 MODEL_KEY 一致） */
    private static final String AUTH_MODEL_SYS_ADMIN = "COMMON";

    private final SysOrgAuthService sysOrgAuthService;

    private final SysOrgElementDao sysOrgElementDao;

    public SysOrgRoleService(SysOrgRoleDao baseDao, SysOrgAuthService sysOrgAuthService,
                             SysOrgElementDao sysOrgElementDao) {
        super(baseDao);
        this.sysOrgAuthService = sysOrgAuthService;
        this.sysOrgElementDao = sysOrgElementDao;
    }

    /* ==================== 角色管理 ==================== */

    /**
     * 角色列表分页查询：仅返回基本字段（角色名/编码/创建时间/更新时间/启用状态），
     * 支持单条件排序（createTime / updateTime / code，白名单映射）与分页参数兜底；
     * 关联组织架构与权限在 {@link #load} 时随详情返回，避免列表 N+1 查询。
     */
    public List<SysOrgRoleVo> list(SysOrgRoleQuery query, Pages pages,
                                   String sortField, String sortOrder) {
        SysOrgRole criteria = toCriteria(query);
        return getBaseDao().selectListSorted(criteria, normalizePages(pages),
                        normalizeSortField(sortField), normalizeSortOrder(sortOrder)).stream()
                .map(SysOrgRoleService::toBasicVo)
                .toList();
    }

    /**
     * 角色列表总数（与 {@link #list} 过滤条件一致，供分页 metadata.total 使用）
     */
    public long count(SysOrgRoleQuery query) {
        return getBaseDao().selectCount(toCriteria(query));
    }

    /**
     * 角色详情：基本字段 + 关联组织架构（elementIds/elementNames）+ 已选权限（authIds），
     * 供编辑页回显。
     */
    public SysOrgRoleVo load(String fieldId) {
        if (StringUtil.isEmpty(fieldId)) {
            throw new IllegalArgumentException("fieldId must not be blank");
        }
        SysOrgRole role = getBaseDao().selectById(fieldId.trim());
        if (null == role) {
            return null;
        }
        SysOrgRoleVo vo = toBasicVo(role);
        List<String> elementIds = getBaseDao().selectElementIdsByRoleId(role.getFieldId());
        vo.setElementIds(elementIds);
        vo.setElementNames(resolveElementNames(elementIds));
        vo.setAuthIds(getBaseDao().selectAuthIdsByRoleId(role.getFieldId()));
        return vo;
    }

    /**
     * 新建角色：校验基本字段后写入 sys_org_role，并全量写入关联表
     * （sys_org_role_element / sys_org_role_auth）。
     */
    @Transactional
    public SysOrgRoleVo saveRole(SysOrgRoleVo vo) {
        validate(vo);
        SysOrgRole role = new SysOrgRole();
        role.setFieldId(vo.getFieldId());
        role.setFieldName(vo.getFieldName().trim());
        role.setFieldCode(trimToNull(vo.getFieldCode()));
        role.setFieldEnable(null == vo.getFieldEnable() ? Boolean.TRUE : vo.getFieldEnable());
        SysOrgUser current = UserUtil.getCurrentUser();
        Date now = new Date();
        role.setFieldCreatorId(null == current ? null : current.getFieldId());
        role.setFieldCreateTime(now);
        role.setFieldUpdaterId(null == current ? null : current.getFieldId());
        role.setFieldUpdateTime(now);
        insert(role);
        replaceElements(role.getFieldId(), vo.getElementIds());
        replaceAuths(role.getFieldId(), vo.getAuthIds());
        // 同事务维护组织架构镜像行（field_org_type = 5），失败时随事务回滚
        syncElementMirror(role);
        log.info("role saved: id={}, name={}, code={}", role.getFieldId(), role.getFieldName(), role.getFieldCode());
        return load(role.getFieldId());
    }

    /**
     * 编辑角色：更新 sys_org_role 基本字段，并全量替换关联表
     * （sys_org_role_element / sys_org_role_auth 先删后插）。
     */
    @Transactional
    public SysOrgRoleVo updateRole(SysOrgRoleVo vo) {
        if (null == vo || StringUtil.isEmpty(vo.getFieldId())) {
            throw new IllegalArgumentException("fieldId must not be blank");
        }
        validate(vo);
        SysOrgRole exist = getBaseDao().selectById(vo.getFieldId().trim());
        if (null == exist) {
            throw new IllegalArgumentException("角色不存在或已删除");
        }
        exist.setFieldName(vo.getFieldName().trim());
        exist.setFieldCode(trimToNull(vo.getFieldCode()));
        if (null != vo.getFieldEnable()) {
            exist.setFieldEnable(vo.getFieldEnable());
        }
        SysOrgUser current = UserUtil.getCurrentUser();
        exist.setFieldUpdaterId(null == current ? null : current.getFieldId());
        exist.setFieldUpdateTime(new Date());
        update(exist);
        replaceElements(exist.getFieldId(), vo.getElementIds());
        replaceAuths(exist.getFieldId(), vo.getAuthIds());
        // 同事务维护组织架构镜像行（field_org_type = 5），名称/编码（含清空）/启用/审计随 exist 同步
        syncElementMirror(exist);
        log.info("role updated: id={}, name={}, code={}", exist.getFieldId(), exist.getFieldName(), exist.getFieldCode());
        return load(exist.getFieldId());
    }

    /* ==================== 私有方法 ==================== */

    private static SysOrgRole toCriteria(SysOrgRoleQuery query) {
        SysOrgRole criteria = new SysOrgRole();
        if (null == query) {
            return criteria;
        }
        criteria.setFieldName(StringUtil.isEmpty(query.getFieldName()) ? null : query.getFieldName().trim());
        criteria.setFieldCode(StringUtil.isEmpty(query.getFieldCode()) ? null : query.getFieldCode().trim());
        criteria.setFieldEnable(query.getEnable());
        return criteria;
    }

    private static void validate(SysOrgRoleVo vo) {
        Objects.requireNonNull(vo, "body must not be null");
        if (StringUtil.isEmpty(vo.getFieldName())) {
            throw new IllegalArgumentException("角色名称不能为空");
        }
    }

    private static String trimToNull(String value) {
        return StringUtil.isEmpty(value) ? null : value.trim();
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
     * 排序字段白名单：createTime / updateTime / code，非法值回退默认创建时间。
     */
    private static String normalizeSortField(String sortField) {
        if ("updateTime".equals(sortField) || "code".equals(sortField)) {
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

    private static SysOrgRoleVo toBasicVo(SysOrgRole role) {
        SysOrgRoleVo vo = new SysOrgRoleVo();
        vo.setFieldId(role.getFieldId());
        vo.setFieldName(role.getFieldName());
        vo.setFieldCode(role.getFieldCode());
        vo.setFieldEnable(role.getFieldEnable());
        vo.setFieldCreateTime(role.getFieldCreateTime());
        vo.setFieldUpdateTime(role.getFieldUpdateTime());
        return vo;
    }

    /**
     * 关联组织架构名称回填：按 id 批量读取名称（不过滤启用状态，已选中的停用节点也要回显）。
     */
    private List<String> resolveElementNames(List<String> elementIds) {
        if (null == elementIds || elementIds.isEmpty()) {
            return List.of();
        }
        List<SysOrgElement> elements = sysOrgElementDao.selectIdNameByIds(elementIds);
        List<String> names = new ArrayList<>(elementIds.size());
        for (String elementId : elementIds) {
            String name = elements.stream()
                    .filter(e -> Objects.equals(e.getFieldId(), elementId))
                    .map(SysOrgElement::getFieldName)
                    .findFirst()
                    .orElse(null);
            names.add(name);
        }
        return names;
    }

    /**
     * 全量替换角色-组织元素关联（sys_org_role_element）。
     */
    private void replaceElements(String roleId, List<String> elementIds) {
        getBaseDao().deleteRoleElements(roleId);
        List<String> distinct = distinctIds(elementIds);
        if (!distinct.isEmpty()) {
            getBaseDao().insertRoleElements(roleId, distinct);
        }
    }

    /**
     * 全量替换角色-权限关联（sys_org_role_auth）。
     */
    private void replaceAuths(String roleId, List<String> authIds) {
        getBaseDao().deleteRoleAuths(roleId);
        List<String> distinct = distinctIds(authIds);
        if (!distinct.isEmpty()) {
            getBaseDao().insertRoleAuths(roleId, distinct);
        }
    }

    private static List<String> distinctIds(List<String> ids) {
        if (null == ids || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .filter(StringUtil::isNotEmpty)
                .map(String::trim)
                .distinct()
                .toList();
    }

    /**
     * 同事务维护角色在 sys_org_element 的镜像行（field_org_type = 5，field_id 与角色一致）：
     * 缺失时按角色全字段插入（树字段固定：parentId/hierarchy 为 null、level = 1、isVirtual = false）；
     * 已存在时经专用 updateRoleMirror 仅同步名称/编码/启用与审计字段
     * （通用 update 对 field_level / field_org_type / field_is_virtual 无条件赋值，禁止用于镜像行更新）；
     * 更新返回 0 行视为镜像行缺失（自愈孤儿场景），补插入保持幂等；
     * 异常不捕获吞掉，交由 @Transactional 回滚角色与镜像行。
     */
    private void syncElementMirror(SysOrgRole role) {
        // 审计兜底：角色 updaterId/updateTime 为空（如启动任务新建）时取当前用户与当前时间
        String updaterId = null == role.getFieldUpdaterId() ? UserUtil.getCurrentUserId() : role.getFieldUpdaterId();
        Date updateTime = null == role.getFieldUpdateTime() ? new Date() : role.getFieldUpdateTime();
        if (null == sysOrgElementDao.selectById(role.getFieldId())) {
            sysOrgElementDao.insert(buildRoleMirror(role, updaterId, updateTime));
            return;
        }
        if (sysOrgElementDao.updateRoleMirror(role.getFieldId(), role.getFieldName(), role.getFieldCode(),
                role.getFieldEnable(), updaterId, updateTime) == 0) {
            sysOrgElementDao.insert(buildRoleMirror(role, updaterId, updateTime));
        }
    }

    /**
     * 构造角色镜像行：公共字段取自角色（编码可为 null，镜像行同步为 null），
     * 树字段固定为根级平铺（field_org_type = 5，不参与树），启用状态与角色一致。
     */
    private static SysOrgElement buildRoleMirror(SysOrgRole role, String updaterId, Date updateTime) {
        SysOrgElement mirror = new SysOrgElement();
        mirror.setFieldId(role.getFieldId());
        mirror.setFieldName(role.getFieldName());
        mirror.setFieldCode(role.getFieldCode());
        mirror.setFieldOrgType(SysOrgElementProperties.ORG_TYPE_ROLE);
        mirror.setFieldParentId(null);
        mirror.setFieldHierarchy(null);
        mirror.setFieldLevel(1);
        mirror.setFieldIsVirtual(false);
        mirror.setFieldEnable(role.getFieldEnable());
        mirror.setFieldCreatorId(role.getFieldCreatorId());
        mirror.setFieldCreateTime(role.getFieldCreateTime());
        mirror.setFieldUpdaterId(updaterId);
        mirror.setFieldUpdateTime(updateTime);
        return mirror;
    }

    /* ==================== 启动任务（系统管理员角色初始化） ==================== */

    /**
     * 第三个启动任务：确保系统管理员角色存在（field_id = '0' 补足 32 位，field_name = 系统管理员），
     * 并默认挂载 SYS_ADMIN 权限与 admin 用户。
     *
     * @param adminUserId 内置管理员用户 ID（sys_org_role_element 关联的组织元素）
     */
    @Transactional
    public void ensureSystemAdminRole(String adminUserId) {
        SysOrgRole role = getBaseDao().selectById(SYSTEM_ADMIN_ROLE_ID);
        if (role == null) {
            role = new SysOrgRole();
            role.setFieldId(SYSTEM_ADMIN_ROLE_ID);
            role.setFieldName("系统管理员");
            role.setFieldCode(CommonProperties.ROLE_ADMIN);
            role.setFieldEnable(true);
            role.setFieldCreateTime(new Date());
            // 启动任务无登录上下文，updaterId 置空由 syncElementMirror 兜底；updateTime 与 createTime 保持一致
            role.setFieldUpdateTime(role.getFieldCreateTime());
            insert(role);
            log.info("system admin role created: id={}, code={}, name={}",
                    SYSTEM_ADMIN_ROLE_ID, CommonProperties.ROLE_ADMIN, role.getFieldName());
        }
        // 同事务维护组织架构镜像行（field_org_type = 5）：新建与已存在分支都调用，天然幂等
        syncElementMirror(role);

        // 默认添加 SYS_ADMIN 权限（幂等），并挂载到系统管理员角色
        SysOrgAuth auth = sysOrgAuthService.ensureAuth(CommonProperties.ROLE_ADMIN, "系统管理员", AUTH_MODEL_SYS_ADMIN);
        if (getBaseDao().countRoleAuth(auth.getFieldId(), role.getFieldId()) == 0) {
            getBaseDao().insertRoleAuth(auth.getFieldId(), role.getFieldId());
            log.info("role-auth linked: roleId={}, authId={}, authCode={}",
                    role.getFieldId(), auth.getFieldId(), CommonProperties.ROLE_ADMIN);
        }

        // 默认将 admin 用户挂载到系统管理员角色
        if (getBaseDao().countRoleElement(adminUserId, role.getFieldId()) == 0) {
            getBaseDao().insertRoleElement(adminUserId, role.getFieldId());
            log.info("role-element linked: roleId={}, elementId={}", role.getFieldId(), adminUserId);
        }
    }
}
