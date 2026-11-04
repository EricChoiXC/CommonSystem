package com.common.system.sys.org.service;

import com.common.system.common.base.model.Pages;
import com.common.system.common.base.service.BaseService;
import com.common.system.common.setting.service.CommonSettingService;
import com.common.system.common.utils.PasswordUtil;
import com.common.system.common.utils.StringUtil;
import com.common.system.sys.org.dao.SysOrgAuthDao;
import com.common.system.sys.org.dao.SysOrgElementDao;
import com.common.system.sys.org.dao.SysOrgPostDao;
import com.common.system.sys.org.dao.SysOrgUserPwdHistoryDao;
import com.common.system.sys.org.dao.SysOrgRoleDao;
import com.common.system.sys.org.dao.SysOrgUserDao;
import com.common.system.sys.org.enums.SysOrgAccountStatus;
import com.common.system.sys.org.model.SysOrgUser;
import com.common.system.sys.org.model.SysOrgUserPwdHistory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import static com.common.system.common.setting.properties.CommonSettingProperties.*;

/**
 * 用户业务服务
 *
 * @author Eric
 * @date 2026/8/3
 * @description 用户(sys_org_user)业务逻辑，事务与参数校验由 BaseService 统一承担。
 *              field_password 在新增用户时经 {@link PasswordUtil} 加密后入库，
 *              修改密码仅通过 {@link #changePassword}（changePwd 接口）进行，明文不落库。
 *              新增/编辑时校验登录名格式、邮箱/电话唯一性。
 */
@Service
public class SysOrgUserService extends BaseService<SysOrgUser, SysOrgUserDao> {

    /** 密码最小长度（与前端修改密码弹窗校验一致） */
    private static final int PASSWORD_MIN_LENGTH = 6;

    /** 密码最大长度（与前端修改密码弹窗校验一致） */
    private static final int PASSWORD_MAX_LENGTH = 32;

    /** 邮箱正则（简单校验） */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    /** 11位数字正则（纯手机号格式） */
    private static final Pattern PHONE_11_PATTERN = Pattern.compile("^\\d{11}$");

    private final SysOrgRoleDao sysOrgRoleDao;

    private final SysOrgAuthDao sysOrgAuthDao;

    private final SysOrgPostDao sysOrgPostDao;

    private final SysOrgElementDao sysOrgElementDao;

    private final SysOrgUserPwdHistoryDao sysOrgPwdHistoryDao;

    private final CommonSettingService commonSettingService;

    public SysOrgUserService(SysOrgUserDao baseDao, SysOrgRoleDao sysOrgRoleDao, SysOrgAuthDao sysOrgAuthDao,
                             SysOrgPostDao sysOrgPostDao, SysOrgElementDao sysOrgElementDao,
                             SysOrgUserPwdHistoryDao sysOrgPwdHistoryDao,
                             CommonSettingService commonSettingService) {
        super(baseDao);
        this.sysOrgRoleDao = sysOrgRoleDao;
        this.sysOrgAuthDao = sysOrgAuthDao;
        this.sysOrgPostDao = sysOrgPostDao;
        this.sysOrgElementDao = sysOrgElementDao;
        this.sysOrgPwdHistoryDao = sysOrgPwdHistoryDao;
        this.commonSettingService = commonSettingService;
    }

    public SysOrgUserDao getBaseDao() {
        return super.getBaseDao();
    }

    /**
     * 新增用户：校验登录名格式、邮箱/电话唯一性，密码 BCrypt 加密后入库
     */
    @Override
    public SysOrgUser insert(SysOrgUser model) {
        if (null != model) {
            validateLoginName(model.getFieldLoginName());
            validateEmailPhoneUnique(model.getFieldId(), model.getFieldEmail(), model.getFieldPhone());
            if (StringUtil.isNotEmpty(model.getFieldPassword())) {
                model.setFieldPassword(PasswordUtil.encode(model.getFieldPassword()));
            }
        }
        return super.insert(model);
    }

    /**
     * 编辑用户：校验登录名格式、邮箱/电话唯一性，忽略请求携带的密码
     */
    @Override
    public SysOrgUser update(SysOrgUser model) {
        if (null != model) {
            validateLoginName(model.getFieldLoginName());
            validateEmailPhoneUnique(model.getFieldId(), model.getFieldEmail(), model.getFieldPhone());
            model.setFieldPassword(null);
        }
        return super.update(model);
    }

    /**
     * 校验登录名格式：不可为邮箱格式或11位数字
     */
    private void validateLoginName(String loginName) {
        if (StringUtil.isEmpty(loginName)) {
            return;
        }
        if (EMAIL_PATTERN.matcher(loginName).matches()) {
            throw new IllegalArgumentException("登录名不能为邮箱格式");
        }
        if (PHONE_11_PATTERN.matcher(loginName).matches()) {
            throw new IllegalArgumentException("登录名不能为纯手机号（11位数字）");
        }
    }

    /**
     * 校验联系邮箱和联系电话不可和现有数据相同
     * @param fieldId 当前用户ID（编辑时排除自身）
     * @param email 邮箱
     * @param phone 电话
     */
    private void validateEmailPhoneUnique(String fieldId, String email, String phone) {
        if (StringUtil.isNotEmpty(email)) {
            SysOrgUser existByEmail = getBaseDao().selectByEmail(email);
            if (null != existByEmail && !existByEmail.getFieldId().equals(fieldId)) {
                throw new IllegalArgumentException("联系邮箱已被其他用户使用");
            }
        }
        if (StringUtil.isNotEmpty(phone)) {
            SysOrgUser existByPhone = getBaseDao().selectByPhone(phone);
            if (null != existByPhone && !existByPhone.getFieldId().equals(fieldId)) {
                throw new IllegalArgumentException("联系电话已被其他用户使用");
            }
        }
    }

    /**
     * 仅修改密码：加密后只更新 field_password 与 field_last_chg_pwd_time，
     * 并将新密码哈希写入密码历史表，清理超出保留条数的历史记录。
     * 不可重复设定密码次数校验：检查新密码是否与前n次密码相同。
     *
     * @param fieldId     用户 ID
     * @param rawPassword 明文新密码
     */
    @Transactional
    public void changePassword(String fieldId, String rawPassword) {
        if (StringUtil.isEmpty(fieldId)) {
            throw new IllegalArgumentException("fieldId must not be blank");
        }
        if (StringUtil.isEmpty(rawPassword)) {
            throw new IllegalArgumentException("rawPassword must not be blank");
        }
        String trimmed = rawPassword.trim();
        if (trimmed.length() < PASSWORD_MIN_LENGTH || trimmed.length() > PASSWORD_MAX_LENGTH) {
            throw new IllegalArgumentException("密码长度需为 " + PASSWORD_MIN_LENGTH + "-" + PASSWORD_MAX_LENGTH + " 位");
        }

        // 不可重复设定密码次数校验
        int repeatLimit = getPwdRepeatLimit();
        if (repeatLimit > 0) {
            List<SysOrgUserPwdHistory> historyList = sysOrgPwdHistoryDao.selectByUserId(fieldId);
            for (SysOrgUserPwdHistory history : historyList) {
                if (PasswordUtil.matches(trimmed, history.getFieldPwd())) {
                    throw new IllegalArgumentException("新密码不能与最近" + repeatLimit + "次密码相同");
                }
            }
        }

        String encoded = PasswordUtil.encode(trimmed);
        SysOrgUser model = new SysOrgUser();
        model.setFieldId(fieldId.trim());
        model.setFieldPassword(encoded);
        model.setFieldLastChgPwdTime(new Date());
        int affected = getBaseDao().updatePassword(model);
        if (affected != 1) {
            throw new IllegalStateException("updatePassword expected one affected row but got " + affected);
        }

        // 写入密码历史：查询当前最大版本号，新记录版本号+1
        int maxVersion = 0;
        List<SysOrgUserPwdHistory> existing = sysOrgPwdHistoryDao.selectByUserId(fieldId.trim());
        if (!existing.isEmpty()) {
            maxVersion = existing.get(0).getFieldVersion();
        }
        SysOrgUserPwdHistory history = new SysOrgUserPwdHistory();
        history.initFieldId();
        history.setFieldUserId(fieldId.trim());
        history.setFieldPwd(encoded);
        history.setFieldVersion(maxVersion + 1);
        sysOrgPwdHistoryDao.insert(history);

        // 清理超出保留条数的历史记录
        if (repeatLimit > 0) {
            sysOrgPwdHistoryDao.deleteExcess(fieldId.trim(), repeatLimit);
        }
    }

    /**
     * 用户登录：先按登录名查询，若未开启邮箱/电话登录则只按登录名校验；
     * 若开启了邮箱登录，尝试按邮箱匹配；若开启了电话登录，尝试按电话匹配。
     * <p>登录前无身份，因此本方法不经过 resolver 鉴权，由 Controller 直接调用；
     * 校验失败统一抛出 {@link IllegalArgumentException}，由全局异常处理器转为 BAD_REQUEST。
     * 登录成功时更新 field_last_login_time，返回的用户对象不含密码（密码字段置空，
     * 且模型上标注 WRITE_ONLY，双重保证不泄露）。</p>
     *
     * @param loginName 登录名（也可能是邮箱或电话）
     * @param rawPassword 明文密码
     * @return 登录成功的用户（不含密码）
     */
    @Transactional
    public SysOrgUser login(String loginName, String rawPassword) {
        if (StringUtil.isEmpty(loginName) || StringUtil.isEmpty(rawPassword)) {
            throw new IllegalArgumentException("账号或密码不能为空");
        }
        String trimmedLogin = loginName.trim();
        SysOrgUser user = getBaseDao().selectByLoginName(trimmedLogin);

        // 按登录名未找到，尝试邮箱登录
        if (null == user && isEmailLoginEnabled() && EMAIL_PATTERN.matcher(trimmedLogin).matches()) {
            user = getBaseDao().selectByEmail(trimmedLogin);
        }
        // 按邮箱未找到，尝试电话登录
        if (null == user && isPhoneLoginEnabled() && PHONE_11_PATTERN.matcher(trimmedLogin).matches()) {
            user = getBaseDao().selectByPhone(trimmedLogin);
        }

        if (null == user || StringUtil.isEmpty(user.getFieldPassword())) {
            throw new IllegalArgumentException("账号或密码错误");
        }
        if (!PasswordUtil.matches(rawPassword, user.getFieldPassword())) {
            throw new IllegalArgumentException("账号或密码错误");
        }
        if (Boolean.FALSE.equals(user.getFieldEnable())) {
            throw new IllegalArgumentException("账号已被禁用");
        }
        if (SysOrgAccountStatus.NORMAL.getValue() != user.getFieldAccountStatus()) {
            throw new IllegalArgumentException("账号状态异常，请联系管理员");
        }
        // 登录成功：更新最后登录时间
        user.setFieldLastLoginTime(new Date());
        getBaseDao().update(user);
        // 组装角色、权限、岗位、虚拟组织（角色/岗位/虚拟组织仅返回 field_id、field_name，权限返回 field_code 列表）
        user.setRoles(sysOrgRoleDao.selectIdNameListByUserId(user.getFieldId()));
        user.setAuths(sysOrgAuthDao.selectCodesByUserId(user.getFieldId()));
        user.setPosts(sysOrgPostDao.selectIdNameListByUserId(user.getFieldId()));
        user.setVirtuals(sysOrgElementDao.selectVirtualIdNameListByUserId(user.getFieldId()));
        // 返回前清空密码，避免敏感信息进入响应体
        user.setFieldPassword(null);
        return user;
    }

    /**
     * 检查用户是否需要强制修改密码
     * @param fieldId 用户ID
     * @return true=需要强制修改密码
     */
    public boolean isPasswordChangeRequired(String fieldId) {
        int cycle = getPwdChangeCycle();
        if (cycle <= 0) {
            return false;
        }
        SysOrgUser user = getBaseDao().selectById(fieldId);
        if (null == user || null == user.getFieldLastChgPwdTime()) {
            return true;
        }
        long diffMs = System.currentTimeMillis() - user.getFieldLastChgPwdTime().getTime();
        long diffDays = diffMs / (1000 * 60 * 60 * 24);
        return diffDays >= cycle;
    }

    /**
     * 获取定期更换密码周期
     */
    private int getPwdChangeCycle() {
        Integer val = commonSettingService.getIntValue(TYPE_SYSTEM, KEY_PWD_CHANGE_CYCLE);
        return null == val ? 0 : val;
    }

    /**
     * 获取不可重复设定密码次数
     */
    private int getPwdRepeatLimit() {
        Integer val = commonSettingService.getIntValue(TYPE_SYSTEM, KEY_PWD_REPEAT_LIMIT);
        return null == val ? 0 : val;
    }

    /**
     * 是否开启邮箱登录
     */
    private boolean isEmailLoginEnabled() {
        Boolean val = commonSettingService.getBooleanValue(TYPE_SYSTEM, KEY_LOGIN_EMAIL_ENABLE);
        return Boolean.TRUE.equals(val);
    }

    /**
     * 是否开启电话登录
     */
    private boolean isPhoneLoginEnabled() {
        Boolean val = commonSettingService.getBooleanValue(TYPE_SYSTEM, KEY_LOGIN_PHONE_ENABLE);
        return Boolean.TRUE.equals(val);
    }

    /**
     * load 专用：查询完整数据后按可见字段集裁剪
     * @param id         文档 ID
     * @param visibility 字段可见性判定结果；为 null 时表示未配置规则，按全量字段返回
     * @return 仅含可见字段的 Map
     */
    public SysOrgUser selectByIdWithFields(String id, String visibility) {
        return getBaseDao().selectByIdWithFields(id, visibility);
    }

    public List<SysOrgUser> selectListWithFields(SysOrgUser criteria, Pages pages, String visibleFields) {
        return getBaseDao().selectListWithFields(criteria, pages, visibleFields);
    }
}