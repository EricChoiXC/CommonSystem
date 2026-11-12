package com.eric.base.sys.org.properties;

import com.eric.base.common.utils.YmlResourceUtil;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 组织架构 v1.1 常量与配置文件测试
 *
 * @author Eric
 * @date 2026/11/4
 * @description 覆盖角色（5）/群组（6）纳入组织架构后的常量口径与配置同步：
 *              类型常量与白名单/排除集合；dict.yml 的 org_element_type 新增码值与 org_group_type 码值；
 *              roles.yml 新增 SYS_ORG_GROUP_ADMIN 权限码。
 */
class SysOrgPropertiesConfigTest {

    private static final String ORG_DICT_YML = "properties/sys/org/dict.yml";
    private static final String ORG_ROLES_YML = "properties/sys/org/roles.yml";

    @Test
    void orgTypeConstantsCoverRoleAndGroup() {
        assertEquals(1, SysOrgElementProperties.ORG_TYPE_ORG);
        assertEquals(2, SysOrgElementProperties.ORG_TYPE_DEPT);
        assertEquals(3, SysOrgElementProperties.ORG_TYPE_USER);
        assertEquals(4, SysOrgElementProperties.ORG_TYPE_POST);
        assertEquals(5, SysOrgElementProperties.ORG_TYPE_ROLE);
        assertEquals(6, SysOrgElementProperties.ORG_TYPE_GROUP);
    }

    @Test
    void elementWritableTypesExcludeRoleAndGroup() {
        assertEquals(Set.of(1, 2, 3, 4), SysOrgElementProperties.ELEMENT_WRITABLE_ORG_TYPES);
        assertFalse(SysOrgElementProperties.ELEMENT_WRITABLE_ORG_TYPES.contains(SysOrgElementProperties.ORG_TYPE_ROLE));
        assertFalse(SysOrgElementProperties.ELEMENT_WRITABLE_ORG_TYPES.contains(SysOrgElementProperties.ORG_TYPE_GROUP));
        assertEquals(Set.of(5, 6), SysOrgElementProperties.EXCLUDED_ORG_TYPES);
    }

    @Test
    void dictYmlDeclaresRoleAndGroupElementTypes() throws IOException {
        Map<String, Object> dict = dictSection();

        Map<?, ?> elementTypes = (Map<?, ?>) dict.get("org_element_type");
        assertEquals("机构", elementTypes.get(1));
        assertEquals("部门", elementTypes.get(2));
        assertEquals("人员", elementTypes.get(3));
        assertEquals("岗位", elementTypes.get(4));
        assertEquals("角色", elementTypes.get(5));
        assertEquals("群组", elementTypes.get(6));
        // desc 作为类型描述随启动任务同步，需与新码值口径一致
        assertEquals("sys_org_element.field_org_type（1 机构 / 2 部门 / 3 人员 / 4 岗位 / 5 角色 / 6 群组）",
                elementTypes.get("desc"));
    }

    @Test
    void dictYmlDeclaresGroupTypeCodes() throws IOException {
        Map<?, ?> groupTypes = (Map<?, ?>) dictSection().get("org_group_type");
        assertEquals("群组类型", groupTypes.get("name"));
        assertEquals("sys_org_group.field_type", groupTypes.get("desc"));
        assertEquals("公共", groupTypes.get("COMMON"));
        assertEquals("个人", groupTypes.get("PERSONAL"));
    }

    @Test
    void rolesYmlDeclaresGroupAdminRole() throws IOException {
        Map<String, Object> roles = YmlResourceUtil.loadYml(new ClassPathResource(ORG_ROLES_YML));

        @SuppressWarnings("unchecked")
        List<String> roleList = (List<String>) roles.get("ROLE_LIST");
        assertTrue(roleList.contains(SysOrgGroupProperties.ROLE_SYS_ORG_GROUP_ADMIN),
                "ROLE_LIST 必须包含 SYS_ORG_GROUP_ADMIN");

        Map<?, ?> roleMap = (Map<?, ?>) roles.get("ROLE_MAP");
        Map<?, ?> groupAdmin = (Map<?, ?>) roleMap.get(SysOrgGroupProperties.ROLE_SYS_ORG_GROUP_ADMIN);
        assertEquals("群组管理员", groupAdmin.get("NAME"));
        assertEquals("拥有群组管理权限，可维护全部公共群组", groupAdmin.get("DESC"));
        // ROLE_LIST 与 ROLE_MAP 必须同批维护，避免启动同步遗漏权限码
        assertEquals(roleList.size(), roleMap.size());
    }

    @Test
    void groupActionsUseSysOrgGroupPrefix() {
        assertEquals("sys.org.group.list", SysOrgGroupProperties.ACTION_LIST);
        assertEquals("sys.org.group.load", SysOrgGroupProperties.ACTION_LOAD);
        assertEquals("sys.org.group.save", SysOrgGroupProperties.ACTION_SAVE);
        assertEquals("sys.org.group.update", SysOrgGroupProperties.ACTION_UPDATE);
        assertEquals("sys.org.group.delete", SysOrgGroupProperties.ACTION_DELETE);
        assertEquals("SYS_ORG_GROUP_ADMIN", SysOrgGroupProperties.ROLE_SYS_ORG_GROUP_ADMIN);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> dictSection() throws IOException {
        Map<String, Object> data = YmlResourceUtil.loadYml(new ClassPathResource(ORG_DICT_YML));
        return (Map<String, Object>) data.get("dict");
    }
}
