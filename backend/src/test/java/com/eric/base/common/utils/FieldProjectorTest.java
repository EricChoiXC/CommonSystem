package com.eric.base.common.utils;

import com.eric.base.sys.org.model.SysOrgRole;
import com.eric.base.sys.org.model.SysOrgUser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 字段裁剪工具测试
 *
 * @author Eric
 * @date 2026/8/4
 */
class FieldProjectorTest {

    /** 32 位 ID，避免 BaseModel.setFieldId 触发重新生成 */
    private static final String ID = "12345678901234567890123456789012";

    private static SysOrgUser sampleUser() {
        SysOrgUser user = new SysOrgUser();
        user.setFieldId(ID);
        user.setFieldName("张三");
        user.setFieldPhone("13800000000");
        user.setFieldEnable(true);
        user.setRoles(List.of(new SysOrgRole()));
        return user;
    }

    @Test
    void projectsOnlyVisibleFields() {
        Map<String, Object> result = FieldProjector.project(sampleUser(), Set.of("fieldId", "fieldName"));

        assertEquals(2, result.size());
        assertEquals(ID, result.get("fieldId"));
        assertEquals("张三", result.get("fieldName"));
        assertFalse(result.containsKey("fieldPhone"));
    }

    @Test
    void unknownFieldNameIsIgnored() {
        Map<String, Object> result = FieldProjector.project(sampleUser(), Set.of("fieldName", "notExistField"));

        assertEquals(1, result.size());
        assertTrue(result.containsKey("fieldName"));
    }

    @Test
    void systemGetterNamesAreRejected() {
        Map<String, Object> result = FieldProjector.project(sampleUser(), Set.of("class", "Class"));

        assertTrue(result.isEmpty());
    }

    @Test
    void nullValueFieldIsNotOutput() {
        SysOrgUser user = sampleUser(); // fieldUpdaterId 为 null
        Map<String, Object> result = FieldProjector.project(user, Set.of("fieldName", "fieldUpdaterId"));

        assertEquals(1, result.size());
        assertFalse(result.containsKey("fieldUpdaterId"));
    }

    @Test
    void nestedCollectionIsKeptWhole() {
        Map<String, Object> result = FieldProjector.project(sampleUser(), Set.of("roles"));

        assertTrue(result.containsKey("roles"));
        assertTrue(result.get("roles") instanceof List);
    }

    @Test
    void nullSourceOrEmptyFieldsReturnsEmptyMap() {
        assertTrue(FieldProjector.project(null, Set.of("fieldName")).isEmpty());
        assertTrue(FieldProjector.project(sampleUser(), Set.of()).isEmpty());
    }
}
