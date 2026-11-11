package com.common.system.sys.org.service;

import com.common.system.common.base.model.Pages;
import com.common.system.common.utils.PinyinUtil;
import com.common.system.common.utils.StringUtil;
import com.common.system.common.utils.UserUtil;
import com.common.system.sys.att.dao.SysAttMainDao;
import com.common.system.sys.att.model.SysAttMain;
import com.common.system.sys.org.dao.SysOrgUserDao;
import com.common.system.sys.org.model.SysOrgUser;
import com.common.system.sys.org.model.SysOrgUserOpenidDo;
import com.common.system.sys.org.properties.SysOrgProfileProperties;
import com.common.system.sys.org.request.SysOrgProfileSaveRequest;
import com.common.system.sys.org.response.SysOrgProfileResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 个人资料页业务服务
 *
 * @author Eric
 * @date 2026/11/11
 * @description 个人资料页（本人自助）业务逻辑（PF2-BE-002，方案 4.2 / 4.3）：
 *              操作对象一律由会话定位（{@link UserUtil#getCurrentUserId()}），接口不收 userId 参数，
 *              从根上排除「A 修改 B」的越权路径。load 组装基础资料（sys_org_user JOIN sys_org_element）、
 *              头像附件ID（sys_att_main 按 model_name + model_id + key 取 field_create_time 降序最新一条）、
 *              openid 反序列化（field_openid JSON 串 → SysOrgUserOpenidDo，失败按全未绑定处理）与
 *              渠道开关占位（PF3-BE-002 接通前常量 false）。save 校验可编辑字段格式后更新本人资料行，
 *              登录名与密码绝不触碰；用户名变更时经 SysOrgUserDao.update 的双表写链路同步
 *              sys_org_element.field_name，并按 SysOrgElementService#save/update 幂等口径重算拼音两列
 *              （PinyinUtil，后端统一重算、前端不传、不可直改）。数据一律经 Dao 访问，不直连 Mapper。
 */
@Service
public class SysOrgProfileService {

    private static final Logger log = LoggerFactory.getLogger(SysOrgProfileService.class);

    /**
     * 手机号格式（方案 4.2）：非空时校验
     */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    /**
     * 邮箱格式：与 SysOrgUserService.EMAIL_PATTERN 同款
     */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    /**
     * 联系地址长度上限（与表列一致，方案 4.2）
     */
    private static final int ADDRESS_MAX_LENGTH = 500;

    private final SysOrgUserDao userDao;

    private final SysAttMainDao attDao;

    private final ObjectMapper objectMapper;

    public SysOrgProfileService(SysOrgUserDao userDao, SysAttMainDao attDao,
                                ObjectMapper objectMapper) {
        this.userDao = userDao;
        this.attDao = attDao;
        this.objectMapper = objectMapper;
    }

    /**
     * 加载当前登录用户资料（方案 4.2，GET /backend/sys/org/profile/load）：
     * 基础资料取 sys_org_user JOIN sys_org_element（selectById 的 BaseColumns 已含两表字段），
     * 头像附件ID取该用户「头像」类附件的最新一条（field_create_time 降序取一），未上传为 null；
     * openid 由 field_openid JSON 串反序列化，反序列化失败按全未绑定处理；
     * 渠道开关 PF3-BE-002 接通前以常量 false 占位。
     *
     * @return 个人资料响应（fieldName/fieldLoginName 等取自两表 JOIN 行）
     */
    public SysOrgProfileResponse load() {
        SysOrgUser user = requireCurrentUser();
        SysOrgProfileResponse response = new SysOrgProfileResponse();
        response.setFieldName(user.getFieldName());
        response.setFieldLoginName(user.getFieldLoginName());
        response.setFieldPhone(user.getFieldPhone());
        response.setFieldEmail(user.getFieldEmail());
        response.setFieldAddress(user.getFieldAddress());
        response.setFieldGender(user.getFieldGender());
        response.setFieldBirthday(user.getFieldBirthday());
        response.setFieldJobStatus(user.getFieldJobStatus());
        response.setFieldAccountStatus(user.getFieldAccountStatus());
        response.setAvatarAttId(latestAvatarAttId(user.getFieldId()));
        response.setOpenid(deserializeOpenid(user.getFieldOpenid()));
        response.setOpenidBindEnabled(isBindEnabled());
        response.setWechatEnabled(isWechatEnabled());
        response.setQqEnabled(isQqEnabled());
        return response;
    }

    /**
     * 保存本人资料（方案 4.2，POST /backend/sys/org/profile/save）：
     * 先做一致性断言（fieldId 必须与当前登录用户一致，防并发换号后脏提交），
     * 再做字段格式校验（用户名非空、手机号/邮箱格式、地址长度、性别取值、生日不晚于当天），
     * 校验通过后读取本人现行资料行，仅覆写请求携带（非 null）的字段后整行更新——
     * XML update 对 field_gender / field_job_status / field_account_status 是无条件更新列，
     * 以现行值回填保证未请求字段不被默认值覆盖。用户名变更时经双表写链路同步 element
     * 名称并重算拼音两列；登录名只读不接收、密码绝不触碰。
     *
     * @param request 保存请求（fieldId 为一致性断言入参，登录名不在请求体内）
     */
    @Transactional
    public void save(SysOrgProfileSaveRequest request) {
        String currentUserId = UserUtil.getCurrentUserId();
        if (null == request || !Objects.equals(request.getFieldId(), currentUserId)) {
            throw new IllegalArgumentException("提交人员与会话用户不一致");
        }
        validate(request);
        SysOrgUser current = requireCurrentUser();

        // 以现行整行为基覆写请求字段再整行 update：无条件更新列（field_gender /
        // field_job_status / field_account_status）保持库中现值，避免被模型默认值覆盖
        SysOrgUser updated = current;
        updated.setFieldUpdaterId(null == currentUserId ? null : currentUserId);
        updated.setFieldUpdateTime(new Date());
        if (null != request.getFieldPhone()) {
            updated.setFieldPhone(request.getFieldPhone());
        }
        if (null != request.getFieldEmail()) {
            updated.setFieldEmail(request.getFieldEmail());
        }
        if (null != request.getFieldAddress()) {
            updated.setFieldAddress(request.getFieldAddress());
        }
        if (null != request.getFieldGender()) {
            updated.setFieldGender(request.getFieldGender());
        }
        if (null != request.getFieldBirthday()) {
            updated.setFieldBirthday(request.getFieldBirthday());
        }
        // fieldLoginName / fieldPassword 绝不触碰：登录名只读（方案 4.2，由管理员在
        // 组织架构人员维护中修改），密码仅经 user/changePwd 接口更新；updated 为
        // selectById 现行行，登录名保持库中现值，且 field_password 本就不在
        // XML update 列集合中，不会被意外重置。
        //
        // 用户名变更同步（关键验收点）：请求用户名与 element 现行 field_name 不同
        // （trim 后比较，空串与 null 视为相等，同值幂等跳过）时，把新名与重算拼音
        // 覆写到 updated 上——SysOrgUserDao.update 是双表写（先经
        // SysOrgElementMapper 更新主表，再经 SysOrgUserMapper 更新扩展表，见
        // SysOrgUserDao#update），element 主表的 field_name / field_pinyin /
        // field_pinyin_short 与审计字段经此一次落地，不会先写后被双写旧名回滚；
        // 拼音口径与 SysOrgElementService#save/update 一致（PinyinUtil 后端统一重算，
        // 前端不传、不可直改）。updatePinyin 只更新拼音两列、不含名称，故不经它。
        if (!Objects.equals(trimToNull(request.getFieldName()), trimToNull(current.getFieldName()))) {
            updated.setFieldName(request.getFieldName());
            updated.setFieldPinyin(PinyinUtil.toPinyin(request.getFieldName()));
            updated.setFieldPinyinShort(PinyinUtil.toPinyinShort(request.getFieldName()));
        }

        userDao.update(updated);
    }

    /**
     * 保存请求字段校验（顺序即方案 4.2 校验顺序），失败抛 IllegalArgumentException
     * 交 GlobalExceptionHandler 统一转 BaseResponse
     */
    private void validate(SysOrgProfileSaveRequest request) {
        if (StringUtil.isEmpty(request.getFieldName())) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (!StringUtil.isEmpty(request.getFieldPhone())
                && !PHONE_PATTERN.matcher(request.getFieldPhone()).matches()) {
            throw new IllegalArgumentException("手机号格式不正确");
        }
        if (!StringUtil.isEmpty(request.getFieldEmail())
                && !EMAIL_PATTERN.matcher(request.getFieldEmail()).matches()) {
            throw new IllegalArgumentException("邮箱格式不正确");
        }
        if (!StringUtil.isEmpty(request.getFieldAddress())
                && request.getFieldAddress().length() > ADDRESS_MAX_LENGTH) {
            throw new IllegalArgumentException("联系地址长度不能超过500");
        }
        if (null != request.getFieldGender()
                && (request.getFieldGender() < 0 || request.getFieldGender() > 2)) {
            throw new IllegalArgumentException("性别取值无效");
        }
        if (null != request.getFieldBirthday()
                && request.getFieldBirthday().after(todayEnd())) {
            throw new IllegalArgumentException("生日不能晚于当天");
        }
    }

    /**
     * 读取当前登录用户资料行，不存在（未登录或会话用户已失效）时抛
     * IllegalArgumentException("当前用户不存在")
     */
    private SysOrgUser requireCurrentUser() {
        String userId = UserUtil.getCurrentUserId();
        SysOrgUser user = userDao.selectById(userId);
        if (null == user) {
            throw new IllegalArgumentException("当前用户不存在");
        }
        return user;
    }

    /**
     * 头像附件ID（方案 4.3）：sys_att_main 按 field_model_name='sys_org_user' +
     * field_model_id=当前用户 + field_key='profile' 过滤，field_create_time 降序取
     * 最新一条的 fieldId；未上传返回 null。附件过滤条件经 SysAttMainDao.selectListSorted，
     * Pages 设 needPage=true/pageNo=1/pageSize=1 只取一条（本类不改附件模块任何文件）
     */
    private String latestAvatarAttId(String userId) {
        SysAttMain criteria = new SysAttMain();
        criteria.setFieldModelName(SysOrgProfileProperties.ATT_MODEL_NAME);
        criteria.setFieldModelId(userId);
        criteria.setFieldKey(SysOrgProfileProperties.ATT_KEY_PROFILE);
        Pages pages = new Pages();
        pages.setNeedPage(true);
        pages.setPageNo(1);
        pages.setPageSize(1);
        // 排序字段/方向为 Mapper 白名单值：缺省即 field_create_time 降序
        List<SysAttMain> list = attDao.selectListSorted(criteria, pages, null, null);
        return list.isEmpty() ? null : list.get(0).getFieldId();
    }

    /**
     * openid 反序列化：field_openid 为空按全未绑定处理；JSON 非法时降级为
     * 全未绑定（空对象）并 warn，不让脏数据阻断资料加载
     */
    private SysOrgUserOpenidDo deserializeOpenid(String openidJson) {
        if (StringUtil.isEmpty(openidJson)) {
            return new SysOrgUserOpenidDo();
        }
        try {
            return objectMapper.readValue(openidJson, SysOrgUserOpenidDo.class);
        } catch (Exception e) {
            log.warn("解析 field_openid 失败，按全未绑定处理，fieldId={}, json={}",
                    UserUtil.getCurrentUserId(), openidJson, e);
            return new SysOrgUserOpenidDo();
        }
    }

    /**
     * 第三方账号绑定总开关（system.openid_bind_enable）：PF3-BE-002 接通前常量占位 false
     */
    protected boolean isBindEnabled() {
        return false;
    }

    /**
     * 微信渠道开放 = 总开关开启且微信凭证齐全：PF3-BE-002 接通前常量占位 false
     */
    protected boolean isWechatEnabled() {
        return false;
    }

    /**
     * QQ 渠道开放 = 总开关开启且 QQ 凭证齐全：PF3-BE-002 接通前常量占位 false
     */
    protected boolean isQqEnabled() {
        return false;
    }

    /**
     * 当天 23:59:59.999 的 Date：生日不晚于当天按「早于该时刻」判定
     */
    private static Date todayEnd() {
        return Date.from(LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    /**
     * trim 归一：null 与空串视为相等（用户名变更判定的口径）
     */
    private static String trimToNull(String value) {
        if (null == value) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
