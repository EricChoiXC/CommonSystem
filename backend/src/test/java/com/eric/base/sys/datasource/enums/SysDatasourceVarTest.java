package com.eric.base.sys.datasource.enums;

import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.org.model.SysOrgUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

/**
 * 数据源固定条件运行时变量测试
 *
 * @author Eric
 * @date 2026/11/13
 * @description 校验占位符形态、matches 判定与 resolve 求值：CURRENT_USER_ID 随执行上下文
 *              当前登录人求值（未登录返回 null，由调用方走 UNAUTHORIZED），日期/时间格式
 *              与样例口径一致（yyyy-MM-dd / yyyy-MM-dd HH:mm:ss）。
 *              注意：不起 Spring 容器，UserUtil 静态方法经 MockedStatic 模拟
 *              （参照 SysOrgProfileServiceTest 先例）；测试模型 ID 使用 32 位常量。
 */
class SysDatasourceVarTest {

    /** 32 位测试 ID（避免 BaseModel.setFieldId 触发重新生成） */
    private static final String USER_ID = "1".repeat(32);

    private MockedStatic<UserUtil> userUtil;

    @BeforeEach
    void setUp() {
        userUtil = mockStatic(UserUtil.class);
    }

    @AfterEach
    void tearDown() {
        userUtil.close();
    }

    /* ==================== placeholder / matches ==================== */

    @Test
    void placeholdersMatchDesignDoc() {
        assertEquals("${CURRENT_USER_ID}", SysDatasourceVar.CURRENT_USER_ID.placeholder());
        assertEquals("${CURRENT_DATE}", SysDatasourceVar.CURRENT_DATE.placeholder());
        assertEquals("${CURRENT_DATETIME}", SysDatasourceVar.CURRENT_DATETIME.placeholder());
    }

    @Test
    void matchesAcceptsExactAndTrimmedPlaceholder() {
        assertTrue(SysDatasourceVar.CURRENT_USER_ID.matches("${CURRENT_USER_ID}"));
        assertTrue(SysDatasourceVar.CURRENT_USER_ID.matches("  ${CURRENT_USER_ID}  "));
        assertTrue(SysDatasourceVar.CURRENT_DATE.matches("${CURRENT_DATE}"));
        assertTrue(SysDatasourceVar.CURRENT_DATETIME.matches("${CURRENT_DATETIME}"));
    }

    @Test
    void matchesRejectsNonPlaceholder() {
        assertFalse(SysDatasourceVar.CURRENT_USER_ID.matches(null));
        assertFalse(SysDatasourceVar.CURRENT_USER_ID.matches(""));
        assertFalse(SysDatasourceVar.CURRENT_USER_ID.matches("admin"));
        // 交叉变量不命中
        assertFalse(SysDatasourceVar.CURRENT_USER_ID.matches("${CURRENT_DATE}"));
        // 占位符为大小写敏感的字面比对
        assertFalse(SysDatasourceVar.CURRENT_USER_ID.matches("${current_user_id}"));
    }

    /* ==================== resolve：随执行上下文求值 ==================== */

    @Test
    void resolveCurrentUserIdUsesLoggedInUser() {
        SysOrgUser user = new SysOrgUser();
        user.setFieldId(USER_ID);
        userUtil.when(UserUtil::getCurrentUser).thenReturn(user);

        assertEquals(USER_ID, SysDatasourceVar.CURRENT_USER_ID.resolve());
    }

    @Test
    void resolveCurrentUserIdReturnsNullWhenNotLoggedIn() {
        userUtil.when(UserUtil::getCurrentUser).thenReturn(null);

        assertNull(SysDatasourceVar.CURRENT_USER_ID.resolve());
    }

    @Test
    void resolveCurrentDateMatchesIsoFormat() {
        String value = SysDatasourceVar.CURRENT_DATE.resolve();

        assertEquals(LocalDate.now(), LocalDate.parse(value));
        assertEquals(10, value.length(), "CURRENT_DATE 应为 yyyy-MM-dd 十位定长格式");
    }

    @Test
    void resolveCurrentDatetimeMatchesPattern() {
        String value = SysDatasourceVar.CURRENT_DATETIME.resolve();

        LocalDateTime parsed = LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        assertEquals(LocalDate.now(), parsed.toLocalDate());
        assertEquals(19, value.length(), "CURRENT_DATETIME 应为 yyyy-MM-dd HH:mm:ss 十九位定长格式");
    }
}
