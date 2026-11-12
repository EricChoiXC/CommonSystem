package com.eric.base.sys.org.service;

import com.eric.base.common.base.model.Pages;
import com.eric.base.common.exception.AuthorizationException;
import com.eric.base.common.utils.RoleUtil;
import com.eric.base.common.utils.StringUtil;
import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.org.dao.SysOrgElementDao;
import com.eric.base.sys.org.dao.SysOrgGroupDao;
import com.eric.base.sys.org.dao.SysOrgGroupUserDao;
import com.eric.base.sys.org.dao.SysOrgGroupViewerDao;
import com.eric.base.sys.org.model.SysOrgElement;
import com.eric.base.sys.org.model.SysOrgGroup;
import com.eric.base.sys.org.model.SysOrgUser;
import com.eric.base.sys.org.request.SysOrgGroupQuery;
import com.eric.base.sys.org.request.SysOrgGroupRequest;
import com.eric.base.sys.org.response.SysOrgGroupVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.eric.base.common.properties.CommonProperties.ROLE_ADMIN;
import static com.eric.base.sys.org.properties.SysOrgElementProperties.ORDER_ASC;
import static com.eric.base.sys.org.properties.SysOrgElementProperties.ORG_TYPE_DEPT;
import static com.eric.base.sys.org.properties.SysOrgElementProperties.ORG_TYPE_GROUP;
import static com.eric.base.sys.org.properties.SysOrgElementProperties.ORG_TYPE_ORG;
import static com.eric.base.sys.org.properties.SysOrgElementProperties.ORG_TYPE_POST;
import static com.eric.base.sys.org.properties.SysOrgElementProperties.ORG_TYPE_USER;
import static com.eric.base.sys.org.properties.SysOrgElementProperties.SORT_UPDATE_TIME;
import static com.eric.base.sys.org.properties.SysOrgGroupProperties.GROUP_TYPE_COMMON;
import static com.eric.base.sys.org.properties.SysOrgGroupProperties.GROUP_TYPE_PERSONAL;
import static com.eric.base.sys.org.properties.SysOrgGroupProperties.ROLE_SYS_ORG_ADMIN;
import static com.eric.base.sys.org.properties.SysOrgGroupProperties.ROLE_SYS_ORG_GROUP_ADMIN;

/**
 * 群组业务服务
 *
 * @author Eric
 * @date 2026/11/5
 * @description 承载群组（field_org_type = 6）维护业务与地址本群组页签查询（ORG2-BE-004）：
 *              列表/详情/新建/编辑/删除 + 地址本 groupList/groupUsers。
 *              <p>群组「不设权 + 数据级过滤」，接口级鉴权由 GroupResolver（ORG2-BE-003）承担，
 *              本服务强校验：
 *              <ul>
 *              <li>可见性（02 文档 2.2）：列表/总数经 query.userId / query.admin 交给
 *                  VisibleClause 过滤（管理员短路，普通用户 = 自己的个人群组 OR
 *                  (公共群组 AND (全员可见 OR 命中可见范围)))；单条 load/update/delete 与
 *                  地址本 groupUsers 的可见性校验经 {@link SysOrgGroupDao#selectCountVisibleById}
 *                  复用同一 VisibleClause SQL 口径（见 {@link #isViewable}，ORG2-BE-004 统一），
 *                  不可见 FORBIDDEN「无权查看该群组」；</li>
 *              <li>维护权限（02 文档 2.3）：普通用户仅可创建个人群组、仅可维护自己的个人群组
 *                  且不可改类型；群组管理员（SYS_ORG_GROUP_ADMIN / SYS_ORG_ADMIN / SYS_ADMIN）
 *                  可维护全部公共群组与自己的个人群组、可改类型；无权 FORBIDDEN「无权维护该群组」；</li>
 *              <li>归属与派生（04 文档 1.1）：fieldOwnerId 新建取当前登录人、编辑恒保持原值；
 *                  fieldIsAllViewer 一律按「viewerIds 是否为空」派生（前端传值无效），
 *                  PERSONAL 不写可见范围明细、恒为全员可见；</li>
 *              <li>删除语义（02 文档 2.4）：物理删除，同事务按
 *                  sys_org_group_user → sys_org_group_viewer → sys_org_group → sys_org_element 顺序。</li>
 *              </ul>
 *              sys_org_element 主行（field_org_type = 6，不参与树）与群组行同事务双写，
 *              写法参照 {@code SysOrgRoleService#syncElementMirror}：缺失时插入、已存在时经
 *              {@code SysOrgElementDao#updateRoleMirror} 仅同步名称/编码/启用与审计字段
 *              （通用 update 对 field_level / field_org_type / field_is_virtual 无条件赋值，
 *              禁止用于镜像行更新）；群组无编码，镜像行 field_code 恒空。
 */
@Service
public class SysOrgGroupService {

    private static final Logger log = LoggerFactory.getLogger(SysOrgGroupService.class);

    /** 未登录错误码（与 AuthorizationHelper / 现有 Service 先例一致） */
    private static final String CODE_UNAUTHORIZED = "UNAUTHORIZED";

    /** 资源不存在错误码（04-API改动清单 7：未找到该群组） */
    private static final String CODE_E_NOT_FOUND = "E_NOT_FOUND";

    /** 权限不足错误码（04-API改动清单 7） */
    private static final String CODE_FORBIDDEN = "FORBIDDEN";

    /** 排序字段白名单：name（createTime / updateTime 复用 SysOrgElementProperties） */
    private static final String SORT_NAME = "name";

    /** 默认排序字段 */
    private static final String SORT_CREATE_TIME = "createTime";

    /** 默认排序方向 */
    private static final String ORDER_DESC = "desc";

    /** 可见范围允许的组织架构类型：机构 / 部门 / 岗位 / 人员（02 文档 2.2，已确认四类） */
    private static final Set<Integer> VIEWER_ORG_TYPES = Set.of(ORG_TYPE_ORG, ORG_TYPE_DEPT, ORG_TYPE_USER, ORG_TYPE_POST);

    /** 地址本 groupUsers 的默认 orgTypes：[3] 仅人员（04-API改动清单 2，成员本身均为人员） */
    private static final List<Integer> DEFAULT_GROUP_USER_ORG_TYPES = List.of(ORG_TYPE_USER);

    private final SysOrgGroupDao groupDao;

    private final SysOrgGroupUserDao groupUserDao;

    private final SysOrgGroupViewerDao groupViewerDao;

    private final SysOrgElementDao elementDao;

    public SysOrgGroupService(SysOrgGroupDao groupDao, SysOrgGroupUserDao groupUserDao,
                              SysOrgGroupViewerDao groupViewerDao, SysOrgElementDao elementDao) {
        this.groupDao = groupDao;
        this.groupUserDao = groupUserDao;
        this.groupViewerDao = groupViewerDao;
        this.elementDao = elementDao;
    }

    /* ==================== 查询 ==================== */

    /**
     * 群组列表分页查询：填充可见性过滤入参（query.userId = 当前登录人、query.admin = 角色判定，
     * 均不接受前端写入）后交 Dao 按 VisibleClause 过滤；排序字段/方向白名单归一化，
     * 分页参数兜底。总数经 {@link #count} 与本方法同条件查询，供 metadata.total 组装
     * （与 SysOrgElementService / SysOrgUserService 的 list + count 两方法先例一致）。
     */
    public List<SysOrgGroupVo> list(SysOrgGroupQuery query, Pages pages, String sortField, String sortOrder) {
        SysOrgGroupQuery safe = fillVisibility(normalizeQuery(query));
        return groupDao.selectVoList(safe, normalizePages(pages),
                normalizeSortField(sortField), normalizeSortOrder(sortOrder));
    }

    /**
     * 群组列表总数：与 {@link #list} 过滤条件（含可见性）一致，供分页 metadata.total 使用。
     */
    public long count(SysOrgGroupQuery query) {
        SysOrgGroupQuery safe = fillVisibility(normalizeQuery(query));
        return groupDao.countVoList(safe);
    }

    /**
     * 群组详情：基本字段 + 成员（userIds/userNames）+ 可见范围（viewerIds/viewerNames）。
     * <p>先区分「不存在」（E_NOT_FOUND，未找到该群组），再按 2.2 口径校验可见性，
     * 不可见 FORBIDDEN（无权查看该群组）。名称回填按入参顺序、不过滤启用状态
     * （已选中的停用节点也要回显，参照 SysOrgRoleService#resolveElementNames 先例）。</p>
     */
    public SysOrgGroupVo load(String fieldId) {
        if (StringUtil.isEmpty(fieldId)) {
            throw new IllegalArgumentException("fieldId must not be blank");
        }
        String id = fieldId.trim();
        SysOrgGroupVo vo = groupDao.selectVoById(id);
        if (null == vo) {
            throw new AuthorizationException(CODE_E_NOT_FOUND, "未找到该群组");
        }
        SysOrgUser current = requireCurrentUser();
        checkViewable(id, current.getFieldId());
        vo.setUserIds(groupUserDao.selectUserIdsByGroupId(id));
        vo.setUserNames(resolveElementNames(vo.getUserIds()));
        vo.setViewerIds(groupViewerDao.selectViewerIdsByGroupId(id));
        vo.setViewerNames(resolveElementNames(vo.getViewerIds()));
        return vo;
    }

    /* ==================== 新建 / 编辑 / 删除 ==================== */

    /**
     * 新建群组：普通用户强制 PERSONAL（显式指定 COMMON → BAD_REQUEST「普通用户仅可创建个人群组」，
     * 防伪造请求体）；管理员可两类。成员校验（存在且启用的 field_org_type = 3）与可见范围校验
     * （存在、field_org_type ∈ 机构/部门/岗位/人员、非虚拟组织）在写库前完成。
     * <p>同事务写入：sys_org_element 主行（field_org_type = 6，不参与树）+ sys_org_group 行
     * + 两张明细表（空列表跳过插入）；fieldOwnerId = 当前登录人（COMMON 记录创建人便于审计，
     * 02 文档 7.2 已确认）；fieldIsAllViewer 按「viewerIds 是否为空」派生
     * （PERSONAL 恒为 true，不写 viewer 明细）。</p>
     */
    @Transactional
    public SysOrgGroupVo save(SysOrgGroupRequest request) {
        Objects.requireNonNull(request, "body must not be null");
        if (StringUtil.isEmpty(request.getFieldName())) {
            throw new IllegalArgumentException("群组名称不能为空");
        }
        SysOrgUser current = requireCurrentUser();
        String uid = current.getFieldId();
        boolean admin = resolveAdmin(uid);
        String type = normalizeType(request.getFieldType());
        if (!admin && GROUP_TYPE_COMMON.equals(type)) {
            throw new IllegalArgumentException("普通用户仅可创建个人群组");
        }
        List<String> userIds = distinctIds(request.getUserIds());
        validateGroupUsers(userIds);
        boolean personal = GROUP_TYPE_PERSONAL.equals(type);
        List<String> viewerIds = personal ? List.of() : distinctIds(request.getViewerIds());
        validateViewers(viewerIds);
        Date now = new Date();
        SysOrgGroup group = new SysOrgGroup();
        // fieldId 由前端经 initID 预取；空或非 32 位时 setFieldId 自动生成
        group.setFieldId(request.getFieldId());
        group.setFieldName(request.getFieldName().trim());
        group.setFieldType(type);
        group.setFieldOwnerId(uid);
        group.setFieldIsAllViewer(viewerIds.isEmpty());
        group.setFieldEnable(null == request.getFieldEnable() ? Boolean.TRUE : request.getFieldEnable());
        group.setFieldCreatorId(uid);
        group.setFieldCreateTime(now);
        group.setFieldUpdaterId(uid);
        group.setFieldUpdateTime(now);
        requireSingleRow("insert", groupDao.insert(group));
        insertUsersIfPresent(group.getFieldId(), userIds);
        insertViewersIfPresent(group.getFieldId(), viewerIds);
        // 同事务维护主行（field_org_type = 6），失败时随事务回滚
        syncElementMirror(group);
        log.info("group saved: id={}, name={}, type={}, ownerId={}, isAllViewer={}",
                group.getFieldId(), group.getFieldName(), type, uid, group.getFieldIsAllViewer());
        return load(group.getFieldId());
    }

    /**
     * 编辑群组：fieldId 必传，不存在 → E_NOT_FOUND（未找到该群组）→ 可见性校验
     * （不可见 → FORBIDDEN，无权查看该群组）→ 维护权限（无权 → FORBIDDEN，无权维护该群组）。
     * <p>普通用户仅自己的个人群组且不可改类型（显式传值与库不一致 → BAD_REQUEST）；
     * 管理员可维护全部公共群组与自己的个人群组、可改类型；fieldOwnerId 恒保持原值
     * （不写入 update 参数，XML 对 null 跳过）；成员/可见范围明细全量替换（先删后插），
     * 改为 PERSONAL 时清空 viewer 明细；element 主行仅同步名称/启用/审计字段。</p>
     */
    @Transactional
    public SysOrgGroupVo update(SysOrgGroupRequest request) {
        Objects.requireNonNull(request, "body must not be null");
        if (StringUtil.isEmpty(request.getFieldId())) {
            throw new IllegalArgumentException("fieldId must not be blank");
        }
        String id = request.getFieldId().trim();
        SysOrgGroup exist = groupDao.selectById(id);
        if (null == exist) {
            throw new AuthorizationException(CODE_E_NOT_FOUND, "未找到该群组");
        }
        SysOrgUser current = requireCurrentUser();
        String uid = current.getFieldId();
        boolean admin = resolveAdmin(uid);
        checkViewable(id, uid);
        checkMaintainable(exist.getFieldType(), exist.getFieldOwnerId(), uid, admin);
        // 类型：未携带保持原值；显式传值归一化校验。普通用户与库不一致（改类型）→ BAD_REQUEST
        String targetType = null == request.getFieldType()
                ? exist.getFieldType() : normalizeType(request.getFieldType());
        if (!admin && !GROUP_TYPE_PERSONAL.equals(targetType)) {
            throw new IllegalArgumentException("普通用户不可修改群组类型");
        }
        if (StringUtil.isEmpty(request.getFieldName())) {
            throw new IllegalArgumentException("群组名称不能为空");
        }
        List<String> userIds = distinctIds(request.getUserIds());
        validateGroupUsers(userIds);
        boolean personal = GROUP_TYPE_PERSONAL.equals(targetType);
        List<String> viewerIds = personal ? List.of() : distinctIds(request.getViewerIds());
        validateViewers(viewerIds);
        Date now = new Date();
        SysOrgGroup group = new SysOrgGroup();
        group.setFieldId(id);
        group.setFieldName(request.getFieldName().trim());
        group.setFieldType(targetType);
        // fieldOwnerId 恒保持原值：不写入 update 参数（XML 对 null 跳过），管理员维护他人群组也不改归属
        group.setFieldIsAllViewer(viewerIds.isEmpty());
        group.setFieldEnable(null == request.getFieldEnable() ? exist.getFieldEnable() : request.getFieldEnable());
        // 主行审计回填：creator/createTime 保持库中现值，随镜像行同步复制
        group.setFieldCreatorId(exist.getFieldCreatorId());
        group.setFieldCreateTime(exist.getFieldCreateTime());
        group.setFieldUpdaterId(uid);
        group.setFieldUpdateTime(now);
        requireSingleRow("update", groupDao.update(group));
        // 明细全量替换（先删后插）；PERSONAL 不写可见范围明细（删除即清空）
        groupUserDao.deleteGroupUsersByGroupId(id);
        insertUsersIfPresent(id, userIds);
        groupViewerDao.deleteGroupViewersByGroupId(id);
        if (!personal) {
            insertViewersIfPresent(id, viewerIds);
        }
        // 主行仅同步名称/启用/审计字段（不触碰树字段与归属）
        syncElementMirror(group);
        log.info("group updated: id={}, name={}, type={}, isAllViewer={}",
                id, group.getFieldName(), targetType, group.getFieldIsAllViewer());
        return load(id);
    }

    /**
     * 删除群组：存在性（E_NOT_FOUND）→ 可见性校验 → 维护权限后，物理删除四表
     * （02 文档 2.4）：sys_org_group_user → sys_org_group_viewer → sys_org_group → sys_org_element，
     * 同事务完成，任一步失败整体回滚。群组不参与树，无子级校验。
     */
    @Transactional
    public void delete(String fieldId) {
        if (StringUtil.isEmpty(fieldId)) {
            throw new IllegalArgumentException("fieldId must not be blank");
        }
        String id = fieldId.trim();
        SysOrgGroup exist = groupDao.selectById(id);
        if (null == exist) {
            throw new AuthorizationException(CODE_E_NOT_FOUND, "未找到该群组");
        }
        SysOrgUser current = requireCurrentUser();
        String uid = current.getFieldId();
        boolean admin = resolveAdmin(uid);
        checkViewable(id, uid);
        checkMaintainable(exist.getFieldType(), exist.getFieldOwnerId(), uid, admin);
        // 四表顺序：成员明细 → 可见范围明细 → 群组扩展行 → 主行（02 文档 2.4）
        groupUserDao.deleteGroupUsersByGroupId(id);
        groupViewerDao.deleteGroupViewersByGroupId(id);
        requireSingleRow("delete", groupDao.deleteById(id));
        elementDao.deleteById(id);
        log.info("group deleted: id={}, name={}", id, exist.getFieldName());
    }

    /* ==================== 地址本群组查询（sys.org.addressbook.groupList / groupUsers，ORG2-BE-004） ==================== */

    /**
     * 地址本「群组」页签左侧列表（04-API改动清单 2）：请求体 { body: { keyword }, pages }，
     * Controller 将 keyword 转为 {@link SysOrgGroupQuery#setFieldName} 后复用
     * {@link #list(SysOrgGroupQuery, Pages, String, String)} 的可见性过滤分页
     * （02 文档 2.2 VisibleClause：管理员全部可见，普通用户仅可见自己的个人群组与
     * 授权可见的公共群组）。地址本页签无排序交互，固定按创建时间降序；
     * 返回 {@link SysOrgGroupVo}（fieldId/fieldName/fieldType/userCount 等已够用），
     * 总数由调用方经 {@link #count} 以同一 query 取得。
     */
    public List<SysOrgGroupVo> groupList(SysOrgGroupQuery query, Pages pages) {
        return list(query, pages, SORT_CREATE_TIME, ORDER_DESC);
    }

    /**
     * 地址本「群组」页签右侧成员人员列表（04-API改动清单 2）：请求体 { groupId, orgTypes? }。
     * 流程：群组不存在 → E_NOT_FOUND「未找到该群组」→ 可见性校验（经
     * {@link SysOrgGroupDao#selectCountVisibleById}，不可见 → FORBIDDEN「无权查看该群组」）
     * → orgTypes 归一化（null/空默认 [3] 仅人员）→ orgTypes 不含 3 直接返回空列表
     * （求交语义：成员均为 field_org_type = 3 的人员，orgTypes 含 3 才有交集）
     * → 查询成员人员明细（JOIN sys_org_element 过滤启用人员，
     * 返回与地址本列表同构的 {@link SysOrgElement}）。
     */
    public List<SysOrgElement> groupUsers(String groupId, List<Integer> orgTypes) {
        if (StringUtil.isEmpty(groupId)) {
            throw new IllegalArgumentException("groupId must not be blank");
        }
        String id = groupId.trim();
        if (null == groupDao.selectById(id)) {
            throw new AuthorizationException(CODE_E_NOT_FOUND, "未找到该群组");
        }
        SysOrgUser current = requireCurrentUser();
        checkViewable(id, current.getFieldId());
        List<Integer> typeFilter = normalizeGroupUserOrgTypes(orgTypes);
        if (!typeFilter.contains(ORG_TYPE_USER)) {
            // 成员均为人员（type = 3）：orgTypes 不含 3 即交集为空，直接空列表不查库
            return List.of();
        }
        return groupUserDao.selectUsersByGroupId(id);
    }

    /* ==================== 角色判定 ==================== */

    /**
     * 是否群组管理员（可见性与维护权限的放行口径，02 文档 2.1 / 2.3）：
     * SYS_ORG_GROUP_ADMIN（群组管理员）/ SYS_ORG_ADMIN（模块管理员，与 BaseResolver.hasAnyRole
     * 的模块管理员口径一致）/ SYS_ADMIN（系统管理员）三者之一。
     * <p>判定沿用 {@link RoleUtil#hasAnyRole}（匹配 sys_org_role.field_code，
     * 系统管理员自动包含所有权限）与 {@link UserUtil#getCurrentUser}（登录态携带角色列表），
     * 不自造 SQL；uid 与当前登录人不一致时一律视为非管理员。</p>
     */
    public boolean resolveAdmin(String uid) {
        if (StringUtil.isEmpty(uid)) {
            return false;
        }
        SysOrgUser user = UserUtil.getCurrentUser();
        if (null == user || !uid.equals(user.getFieldId())) {
            return false;
        }
        return RoleUtil.hasAnyRole(user, ROLE_SYS_ORG_GROUP_ADMIN, ROLE_SYS_ORG_ADMIN, ROLE_ADMIN);
    }

    /* ==================== 私有方法 ==================== */

    /**
     * 当前登录人获取与登录校验：未登录（含登录已过期）抛 UNAUTHORIZED，
     * 文案与 AuthorizationHelper / 现有 Service 先例一致。
     */
    private static SysOrgUser requireCurrentUser() {
        SysOrgUser current = UserUtil.getCurrentUser();
        if (null == current) {
            throw new AuthorizationException(CODE_UNAUTHORIZED, "登录已过期，请重新登录");
        }
        return current;
    }

    /**
     * 填充可见性过滤入参（02 文档 2.2 VisibleClause）：userId = 当前登录人、admin = 角色判定，
     * 均由服务端计算，前端传值无效（query 两字段 @JsonIgnore 阻断请求体绑定）。
     */
    private SysOrgGroupQuery fillVisibility(SysOrgGroupQuery query) {
        SysOrgUser current = requireCurrentUser();
        query.setUserId(current.getFieldId());
        query.setAdmin(resolveAdmin(current.getFieldId()));
        return query;
    }

    /**
     * 查询条件兜底：null 视为空条件；名称/类型去空白。
     */
    private static SysOrgGroupQuery normalizeQuery(SysOrgGroupQuery query) {
        SysOrgGroupQuery safe = null == query ? new SysOrgGroupQuery() : query;
        safe.setFieldName(StringUtil.trimToNull(safe.getFieldName()));
        safe.setFieldType(StringUtil.trimToNull(safe.getFieldType()));
        return safe;
    }

    /**
     * 地址本 groupUsers 的 orgTypes 归一化：null / 空默认 [3]（仅人员，04-API改动清单 2）；
     * 去重保序，元素值不再校验（非法值与成员类型 3 求交自然为空，行为从宽不报错）。
     */
    private static List<Integer> normalizeGroupUserOrgTypes(List<Integer> orgTypes) {
        if (null == orgTypes || orgTypes.isEmpty()) {
            return DEFAULT_GROUP_USER_ORG_TYPES;
        }
        return orgTypes.stream().distinct().toList();
    }

    /**
     * 单条可见性校验（02 文档 2.2 口径，load / update / delete 与地址本 groupUsers 前置）：
     * 经 {@link SysOrgGroupDao#selectCountVisibleById} 复用 VisibleClause 的 SQL 口径
     * （管理员短路 1=1，普通用户 = 自己的个人群组 OR (公共群组 AND (全员可见 OR 命中可见范围))），
     * 与列表/总数过滤完全同源（ORG2-BE-004 统一，消除 BE-002 的 Service 侧 Java 判定偏离）。
     * 存在性由调用方先行判定（E_NOT_FOUND「未找到该群组」）；不可见 → FORBIDDEN「无权查看该群组」。
     */
    private void checkViewable(String groupId, String uid) {
        if (!isViewable(groupId, uid)) {
            throw new AuthorizationException(CODE_FORBIDDEN, "无权查看该群组");
        }
    }

    /**
     * 可见性判定（与 {@link #checkViewable} 同口径，返回布尔便于复用）：
     * 可见性入参 query.userId / query.admin 由本方法填充（与 {@link #fillVisibility} 的
     * 列表口径同源：userId = 指定用户、admin = 按角色判定），COUNT > 0 视为可见。
     */
    private boolean isViewable(String groupId, String uid) {
        SysOrgGroupQuery query = new SysOrgGroupQuery();
        query.setUserId(uid);
        query.setAdmin(resolveAdmin(uid));
        return groupDao.selectCountVisibleById(groupId, query) > 0;
    }

    /**
     * 维护权限判定（02 文档 2.3）：普通用户仅自己的个人群组；
     * 管理员可全部公共群组 + 自己的个人群组（他人的个人群组不可维护）。
     * 无权 → FORBIDDEN「无权维护该群组」。
     */
    private void checkMaintainable(String fieldType, String fieldOwnerId, String uid, boolean admin) {
        if (admin) {
            if (GROUP_TYPE_COMMON.equals(fieldType) || uid.equals(fieldOwnerId)) {
                return;
            }
        } else if (GROUP_TYPE_PERSONAL.equals(fieldType) && uid.equals(fieldOwnerId)) {
            return;
        }
        throw new AuthorizationException(CODE_FORBIDDEN, "无权维护该群组");
    }

    /**
     * 群组类型归一化：空默认 PERSONAL（普通用户仅可个人群组，缺省从紧）；
     * 非 COMMON / PERSONAL 的非法值拒绝。
     */
    private static String normalizeType(String fieldType) {
        if (StringUtil.isEmpty(fieldType)) {
            return GROUP_TYPE_PERSONAL;
        }
        String type = fieldType.trim();
        if (!GROUP_TYPE_COMMON.equals(type) && !GROUP_TYPE_PERSONAL.equals(type)) {
            throw new IllegalArgumentException("无效的群组类型: " + fieldType);
        }
        return type;
    }

    /**
     * 成员校验（02 文档 7.3 / 06 文档 4.12）：每个成员必须为存在且启用的
     * field_org_type = 3 节点，否则 BAD_REQUEST「群组成员必须为启用的人员」。
     */
    private void validateGroupUsers(List<String> userIds) {
        for (String userId : userIds) {
            SysOrgElement user = elementDao.selectById(userId);
            if (null == user || ORG_TYPE_USER != user.getFieldOrgType()
                    || !Boolean.TRUE.equals(user.getFieldEnable())) {
                throw new IllegalArgumentException("群组成员必须为启用的人员");
            }
        }
    }

    /**
     * 可见范围校验（02 文档 2.2 已确认四类 / 06 文档 4.12）：每个可见节点必须存在、
     * field_org_type ∈ 机构(1)/部门(2)/岗位(4)/人员(3) 且非虚拟组织，
     * 否则 BAD_REQUEST「可见范围仅支持机构/部门/岗位/人员」。角色(5)/群组(6) 一并拒绝。
     */
    private void validateViewers(List<String> viewerIds) {
        for (String viewerId : viewerIds) {
            SysOrgElement viewer = elementDao.selectById(viewerId);
            if (null == viewer || !VIEWER_ORG_TYPES.contains(viewer.getFieldOrgType())
                    || viewer.isFieldIsVirtual()) {
                throw new IllegalArgumentException("可见范围仅支持机构/部门/岗位/人员");
            }
        }
    }

    /**
     * 明细插入（空列表跳过，与 insertRoleElements 先例一致：insert 语句要求非空入参）。
     */
    private void insertUsersIfPresent(String groupId, List<String> userIds) {
        if (!userIds.isEmpty()) {
            groupUserDao.insertGroupUsers(groupId, userIds);
        }
    }

    private void insertViewersIfPresent(String groupId, List<String> viewerIds) {
        if (!viewerIds.isEmpty()) {
            groupViewerDao.insertGroupViewers(groupId, viewerIds);
        }
    }

    /**
     * 同事务维护群组在 sys_org_element 的主行（field_org_type = 6，field_id 与群组一致）：
     * 缺失时按群组行插入（树字段固定：parentId/hierarchy 为 null、level = 1、isVirtual = false）；
     * 已存在时经 updateRoleMirror 仅同步名称/编码/启用与审计字段
     * （通用 update 对 field_level / field_org_type / field_is_virtual 无条件赋值，禁止用于镜像行更新）；
     * 群组无编码，code 写空串与镜像行 field_code 恒空一致（field_code 列 NOT NULL，禁止写 null）；更新返回 0 行视为主行缺失
     * （自愈孤儿场景），补插入保持幂等；异常不捕获吞掉，交由 @Transactional 回滚。
     */
    private void syncElementMirror(SysOrgGroup group) {
        // 审计兜底：群组行 updaterId/updateTime 为空时取当前用户与当前时间
        String updaterId = null == group.getFieldUpdaterId() ? UserUtil.getCurrentUserId() : group.getFieldUpdaterId();
        Date updateTime = null == group.getFieldUpdateTime() ? new Date() : group.getFieldUpdateTime();
        if (null == elementDao.selectById(group.getFieldId())) {
            elementDao.insert(buildGroupMirror(group, updaterId, updateTime));
            return;
        }
        if (elementDao.updateRoleMirror(group.getFieldId(), group.getFieldName(), "",
                group.getFieldEnable(), updaterId, updateTime) == 0) {
            elementDao.insert(buildGroupMirror(group, updaterId, updateTime));
        }
    }

    /**
     * 构造群组主行：编码恒空，树字段固定为根级平铺（field_org_type = 6，不参与树），
     * 启用状态与群组行一致。
     */
    private static SysOrgElement buildGroupMirror(SysOrgGroup group, String updaterId, Date updateTime) {
        SysOrgElement mirror = new SysOrgElement();
        mirror.setFieldId(group.getFieldId());
        mirror.setFieldName(group.getFieldName());
        mirror.setFieldCode("");
        mirror.setFieldOrgType(ORG_TYPE_GROUP);
        mirror.setFieldParentId(null);
        mirror.setFieldHierarchy(null);
        mirror.setFieldLevel(1);
        mirror.setFieldIsVirtual(false);
        mirror.setFieldEnable(group.getFieldEnable());
        mirror.setFieldCreatorId(group.getFieldCreatorId());
        mirror.setFieldCreateTime(group.getFieldCreateTime());
        mirror.setFieldUpdaterId(updaterId);
        mirror.setFieldUpdateTime(updateTime);
        return mirror;
    }

    /**
     * 名称回填：按 id 批量读取名称（不过滤启用状态），按入参顺序输出
     * （参照 SysOrgRoleService#resolveElementNames / 地址本 selectByIds 先例）。
     */
    private List<String> resolveElementNames(List<String> elementIds) {
        if (null == elementIds || elementIds.isEmpty()) {
            return List.of();
        }
        List<SysOrgElement> elements = elementDao.selectIdNameByIds(elementIds);
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
     * ID 归一：去空白、去空串、去重（明细入参兜底）。
     */
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
     * 排序字段白名单：createTime / updateTime / name，非法值回退默认创建时间。
     */
    private static String normalizeSortField(String sortField) {
        if (SORT_UPDATE_TIME.equals(sortField) || SORT_NAME.equals(sortField)) {
            return sortField;
        }
        return SORT_CREATE_TIME;
    }

    /**
     * 排序方向白名单：asc / desc，非法值回退默认降序。
     */
    private static String normalizeSortOrder(String sortOrder) {
        return ORDER_ASC.equalsIgnoreCase(sortOrder) ? ORDER_ASC : ORDER_DESC;
    }

    /**
     * 写入行数校验：影响行数不为 1 视为数据状态异常（先例 updateEnable / BaseService#requireSingleRow）。
     */
    private static void requireSingleRow(String operation, int affectedRows) {
        if (affectedRows != 1) {
            throw new IllegalStateException(operation + " expected one affected row but got " + affectedRows);
        }
    }
}
