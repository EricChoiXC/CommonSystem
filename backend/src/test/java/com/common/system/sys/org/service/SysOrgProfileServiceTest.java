package com.common.system.sys.org.service;

import com.common.system.common.base.model.Pages;
import com.common.system.common.utils.PinyinUtil;
import com.common.system.common.utils.UserUtil;
import com.common.system.sys.att.dao.SysAttMainDao;
import com.common.system.sys.att.model.SysAttMain;
import com.common.system.sys.org.dao.SysOrgUserDao;
import com.common.system.sys.org.model.SysOrgUser;
import com.common.system.sys.org.model.SysOrgUserOpenidDo;
import com.common.system.sys.org.request.SysOrgProfileSaveRequest;
import com.common.system.sys.org.response.SysOrgProfileResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 个人资料页业务服务测试
 *
 * @author Eric
 * @date 2026/11/11
 * @description 覆盖 PF2-BE-002 验收点（方案 4.2 / 4.3）：load 组装两表 JOIN 行字段、
 *              头像附件ID取 field_create_time 降序最新一条（Pages 单页单条）、openid 合法/非法
 *              JSON 反序列化（非法降级全未绑定）、渠道开关占位 false、用户行不存在抛
 *              「当前用户不存在」；save 覆盖一致性断言（null 请求体/fieldId 不一致）、
 *              校验分支文案（用户名空/手机号/邮箱/地址超长/性别/生日晚于当天）、正常保存仅
 *              覆写请求字段（field_job_status/field_account_status 等无条件列保持库值、
 *              fieldLoginName 保持库值、fieldPassword 恒 null）、用户名变更经
 *              SysOrgUserDao.update 双表写链路重算拼音（PinyinUtil 真实返回值断言）、
 *              用户名未变（trim 后相等）不重算不覆写。
 *              注意：不起 Spring 容器，Dao 用 Mockito mock；UserUtil 静态方法经
 *              MockedStatic 模拟（参照 SysOrgGroupServiceTest 先例）；BaseModel.setFieldId
 *              仅接受 32 位 ID，测试模型 ID 一律使用 32 位常量。
 */
class SysOrgProfileServiceTest {

    /** 32 位测试 ID（避免 BaseModel.setFieldId 触发重新生成） */
    private static final String USER_ID = "1".repeat(32);
    private static final String OTHER_ID = "2".repeat(32);
    private static final String ATT_LATEST_ID = "a".repeat(32);
    private static final String ATT_OLDER_ID = "b".repeat(32);

    /** 合法 openid JSON（方案 4.1 外层 wechat/qq 结构） */
    private static final String OPENID_JSON =
            "{\"wechat\":{\"openid\":\"o-123\",\"nickname\":\"张三\",\"bindTime\":\"2026-11-11 10:00:00\"}}";

    private SysOrgUserDao userDao;

    private SysAttMainDao attDao;

    private SysOrgProfileService service;

    private MockedStatic<UserUtil> userUtil;

    @BeforeEach
    void setUp() {
        userDao = mock(SysOrgUserDao.class);
        attDao = mock(SysAttMainDao.class);
        service = new SysOrgProfileService(userDao, attDao, new ObjectMapper());
        // 默认当前登录人为 USER_ID 本人（接口不收 userId，操作对象由会话定位）
        userUtil = mockStatic(UserUtil.class);
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user());
        userUtil.when(UserUtil::getCurrentUserId).thenReturn(USER_ID);
        when(userDao.selectById(USER_ID)).thenReturn(user());
    }

    @AfterEach
    void tearDown() {
        userUtil.close();
    }

    /* ==================== load：资料组装 ==================== */

    @Test
    void loadAssemblesProfileFromJoinedRow() {
        SysOrgProfileResponse response = service.load();

        assertEquals("张三", response.getFieldName());
        assertEquals("zhangsan", response.getFieldLoginName());
        assertEquals("13800138000", response.getFieldPhone());
        assertEquals("old@example.com", response.getFieldEmail());
        assertEquals("旧地址", response.getFieldAddress());
        assertEquals(Integer.valueOf(1), response.getFieldGender());
        assertEquals(Integer.valueOf(1), response.getFieldJobStatus());
        assertEquals(Integer.valueOf(1), response.getFieldAccountStatus());
        assertNotNull(response.getFieldBirthday());
    }

    @Test
    void loadReturnsLatestAvatarAttId() {
        when(attDao.selectListSorted(any(SysAttMain.class), anyPages(), isNull(), isNull()))
                .thenReturn(List.of(att(ATT_LATEST_ID), att(ATT_OLDER_ID)));

        SysOrgProfileResponse response = service.load();

        // 两条按 field_create_time 降序的记录，取首条（最新）
        assertEquals(ATT_LATEST_ID, response.getAvatarAttId());
    }

    @Test
    void loadQueriesAvatarWithSingleRowPage() {
        service.load();

        ArgumentCaptor<SysAttMain> modelCaptor = ArgumentCaptor.forClass(SysAttMain.class);
        ArgumentCaptor<Pages> pagesCaptor = ArgumentCaptor.forClass(Pages.class);
        verify(attDao).selectListSorted(modelCaptor.capture(), pagesCaptor.capture(),
                isNull(), isNull());
        // 附件过滤条件：model_name='sys_org_user' + model_id=当前用户 + key='profile'（方案 4.3）
        assertEquals("sys_org_user", modelCaptor.getValue().getFieldModelName());
        assertEquals(USER_ID, modelCaptor.getValue().getFieldModelId());
        assertEquals("profile", modelCaptor.getValue().getFieldKey());
        // 单页单条：needPage=true、pageNo=1、pageSize=1
        Pages pages = pagesCaptor.getValue();
        assertTrue(pages.isNeedPage(), "needPage 应为 true");
        assertEquals(1, pages.getPageNo());
        assertEquals(1, pages.getPageSize());
    }

    @Test
    void loadReturnsNullAvatarWhenNoAtt() {
        when(attDao.selectListSorted(any(SysAttMain.class), anyPages(), isNull(), isNull()))
                .thenReturn(List.of());

        assertNull(service.load().getAvatarAttId());
    }

    /* ==================== load：openid 反序列化与渠道开关 ==================== */

    @Test
    void loadParsesValidOpenidJson() {
        userUtil.when(UserUtil::getCurrentUserId).thenReturn(USER_ID);
        SysOrgUser user = user();
        user.setFieldOpenid(OPENID_JSON);
        when(userDao.selectById(USER_ID)).thenReturn(user);

        SysOrgProfileResponse response = service.load();

        assertNotNull(response.getOpenid());
        assertEquals("o-123", response.getOpenid().getWechat().getOpenid());
        assertEquals("张三", response.getOpenid().getWechat().getNickname());
        assertNull(response.getOpenid().getQq());
    }

    @Test
    void loadFallsBackToUnboundWhenOpenidJsonInvalid() {
        SysOrgUser user = user();
        user.setFieldOpenid("{not-valid-json");
        when(userDao.selectById(USER_ID)).thenReturn(user);

        // 反序列化失败按全未绑定处理，不让脏数据阻断资料加载
        SysOrgUserOpenidDo openid = service.load().getOpenid();
        assertNotNull(openid);
        assertNull(openid.getWechat());
        assertNull(openid.getQq());
    }

    @Test
    void loadFallsBackToUnboundWhenOpenidBlank() {
        SysOrgUser user = user();
        user.setFieldOpenid(null);
        when(userDao.selectById(USER_ID)).thenReturn(user);

        SysOrgUserOpenidDo openid = service.load().getOpenid();
        assertNotNull(openid);
        assertNull(openid.getWechat());
        assertNull(openid.getQq());
    }

    @Test
    void loadChannelFlagsPlaceholderFalse() {
        // PF3-BE-002 接通前渠道开关以常量 false 占位
        SysOrgProfileResponse response = service.load();
        assertFalse(response.getOpenidBindEnabled());
        assertFalse(response.getWechatEnabled());
        assertFalse(response.getQqEnabled());
    }

    @Test
    void loadThrowsWhenUserMissing() {
        when(userDao.selectById(USER_ID)).thenReturn(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.load());
        assertEquals("当前用户不存在", ex.getMessage());
    }

    /* ==================== save：一致性断言与校验分支 ==================== */

    @Test
    void saveRejectsNullRequest() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.save(null));
        assertEquals("提交人员与会话用户不一致", ex.getMessage());
    }

    @Test
    void saveRejectsMismatchedFieldId() {
        SysOrgProfileSaveRequest request = request();
        request.setFieldId(OTHER_ID);

        // 防并发换号后脏提交：提交人员与会话用户不一致即拒绝
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.save(request));
        assertEquals("提交人员与会话用户不一致", ex.getMessage());
        verify(userDao, never()).update(any(SysOrgUser.class));
    }

    @Test
    void saveRejectsBlankName() {
        SysOrgProfileSaveRequest request = request();
        request.setFieldName("  ");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.save(request));
        assertEquals("用户名不能为空", ex.getMessage());
    }

    @Test
    void saveRejectsInvalidPhone() {
        SysOrgProfileSaveRequest request = request();
        request.setFieldPhone("12345678901");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.save(request));
        assertEquals("手机号格式不正确", ex.getMessage());
    }

    @Test
    void saveRejectsInvalidEmail() {
        SysOrgProfileSaveRequest request = request();
        request.setFieldEmail("not-an-email");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.save(request));
        assertEquals("邮箱格式不正确", ex.getMessage());
    }

    @Test
    void saveRejectsOverlongAddress() {
        SysOrgProfileSaveRequest request = request();
        request.setFieldAddress("a".repeat(501));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.save(request));
        assertEquals("联系地址长度不能超过500", ex.getMessage());
    }

    @Test
    void saveRejectsInvalidGender() {
        SysOrgProfileSaveRequest request = request();
        request.setFieldGender(3);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.save(request));
        assertEquals("性别取值无效", ex.getMessage());
    }

    @Test
    void saveRejectsFutureBirthday() {
        SysOrgProfileSaveRequest request = request();
        request.setFieldBirthday(daysFromToday(2));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.save(request));
        assertEquals("生日不能晚于当天", ex.getMessage());
    }

    /* ==================== save：正常保存与字段保护 ==================== */

    @Test
    void saveOverwritesOnlyProvidedFields() {
        SysOrgProfileSaveRequest request = request();
        request.setFieldName("张三");
        request.setFieldPhone("13900139000");
        request.setFieldEmail("new@example.com");
        request.setFieldAddress("北京市朝阳区");
        request.setFieldGender(2);
        request.setFieldBirthday(daysFromToday(0));

        service.save(request);

        ArgumentCaptor<SysOrgUser> captor = ArgumentCaptor.forClass(SysOrgUser.class);
        verify(userDao).update(captor.capture());
        SysOrgUser updated = captor.getValue();
        // 请求携带（非 null）的字段被覆写
        assertEquals("13900139000", updated.getFieldPhone());
        assertEquals("new@example.com", updated.getFieldEmail());
        assertEquals("北京市朝阳区", updated.getFieldAddress());
        assertEquals(2, updated.getFieldGender());
        assertEquals(daysFromToday(0), updated.getFieldBirthday());
        assertEquals(USER_ID, updated.getFieldUpdaterId());
        assertNotNull(updated.getFieldUpdateTime());
        // 无条件更新列保持库中现行值（以现行整行为基覆写，不被默认值覆盖）
        assertEquals(1, updated.getFieldJobStatus());
        assertEquals(1, updated.getFieldAccountStatus());
        // 登录名只读保持库值；密码绝不触碰
        assertEquals("zhangsan", updated.getFieldLoginName());
        assertNull(updated.getFieldPassword());
    }

    @Test
    void saveKeepsUntouchedFieldsWhenRequestPartial() {
        // 仅携带手机号：其余字段（含无条件列与生日）保持库值
        SysOrgProfileSaveRequest request = request();
        request.setFieldName("张三");
        request.setFieldPhone("13900139000");
        request.setFieldEmail(null);
        request.setFieldAddress(null);
        request.setFieldGender(null);
        request.setFieldBirthday(null);

        service.save(request);

        ArgumentCaptor<SysOrgUser> captor = ArgumentCaptor.forClass(SysOrgUser.class);
        verify(userDao).update(captor.capture());
        SysOrgUser updated = captor.getValue();
        assertEquals("13900139000", updated.getFieldPhone());
        assertEquals("old@example.com", updated.getFieldEmail());
        assertEquals("旧地址", updated.getFieldAddress());
        assertEquals(1, updated.getFieldGender());
        assertNotNull(updated.getFieldBirthday());
    }

    /* ==================== save：用户名同步与拼音重算 ==================== */

    @Test
    void saveSyncsElementNameAndPinyinOnChange() {
        SysOrgProfileSaveRequest request = request();
        request.setFieldName("李四");

        service.save(request);

        // 用户名变更：名称与重算拼音（PinyinUtil 真实返回值）随双表写链路传入 update
        ArgumentCaptor<SysOrgUser> captor = ArgumentCaptor.forClass(SysOrgUser.class);
        verify(userDao).update(captor.capture());
        SysOrgUser updated = captor.getValue();
        assertEquals("李四", updated.getFieldName());
        assertEquals(PinyinUtil.toPinyin("李四"), updated.getFieldPinyin());
        assertEquals("lisi", updated.getFieldPinyin());
        assertEquals(PinyinUtil.toPinyinShort("李四"), updated.getFieldPinyinShort());
        assertEquals("ls", updated.getFieldPinyinShort());
    }

    @Test
    void saveSkipsPinyinWhenNameUnchangedAfterTrim() {
        SysOrgUser user = user();
        user.setFieldName(" 张三 ");
        when(userDao.selectById(USER_ID)).thenReturn(user);
        SysOrgProfileSaveRequest request = request();
        request.setFieldName("张三");

        service.save(request);

        // trim 后同值幂等跳过：不覆写名称、不重算拼音（拼音列为 null，XML <if> 不更新该列）
        ArgumentCaptor<SysOrgUser> captor = ArgumentCaptor.forClass(SysOrgUser.class);
        verify(userDao).update(captor.capture());
        SysOrgUser updated = captor.getValue();
        assertEquals(" 张三 ", updated.getFieldName());
        assertNull(updated.getFieldPinyin());
        assertNull(updated.getFieldPinyinShort());
    }

    @Test
    void saveKeepsPinyinUntouchedWhenNameSameLiteral() {
        SysOrgProfileSaveRequest request = request();
        request.setFieldName("张三");

        service.save(request);

        ArgumentCaptor<SysOrgUser> captor = ArgumentCaptor.forClass(SysOrgUser.class);
        verify(userDao).update(captor.capture());
        assertNull(captor.getValue().getFieldPinyin());
        assertNull(captor.getValue().getFieldPinyinShort());
    }

    /* ==================== 测试夹具 ==================== */

    private static SysOrgUser user() {
        SysOrgUser user = new SysOrgUser();
        user.setFieldId(USER_ID);
        user.setFieldName("张三");
        user.setFieldCode("U001");
        user.setFieldPhone("13800138000");
        user.setFieldEmail("old@example.com");
        user.setFieldAddress("旧地址");
        user.setFieldGender(1);
        user.setFieldBirthday(daysFromToday(-10000));
        user.setFieldLoginName("zhangsan");
        user.setFieldJobStatus(1);
        user.setFieldAccountStatus(1);
        user.setFieldOpenid(OPENID_JSON);
        return user;
    }

    private static SysOrgProfileSaveRequest request() {
        SysOrgProfileSaveRequest request = new SysOrgProfileSaveRequest();
        request.setFieldId(USER_ID);
        request.setFieldName("张三");
        request.setFieldPhone("13800138000");
        return request;
    }

    private static SysAttMain att(String fieldId) {
        SysAttMain att = new SysAttMain();
        att.setFieldId(fieldId);
        att.setFieldModelName("sys_org_user");
        att.setFieldModelId(USER_ID);
        att.setFieldKey("profile");
        return att;
    }

    private static Pages anyPages() {
        return any(Pages.class);
    }

    /**
     * 以今天为基准偏移 n 天的零点时刻：n=0 即今天 00:00（不晚于当天，应通过校验）
     */
    private static Date daysFromToday(int days) {
        return Date.from(LocalDate.now().plusDays(days)
                .atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
