package com.common.system.sys.org.service;

import com.common.system.sys.org.dao.SysOrgDeptDao;
import com.common.system.sys.org.dao.SysOrgElementDao;
import com.common.system.sys.org.dao.SysOrgOrgDao;
import com.common.system.sys.org.dao.SysOrgPostDao;
import com.common.system.sys.org.dao.SysOrgUserDao;
import com.common.system.sys.org.properties.SysOrgElementProperties;
import com.common.system.sys.org.response.SysOrgElementVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 组织架构元素服务类型校验测试
 *
 * @author Eric
 * @date 2026/11/4
 * @description 覆盖 v1.1 类型白名单：角色（field_org_type = 5）与群组（6）不参与组织架构树，
 *              经 element 接口（save / update）写入时必须被拒绝，且不产生任何落库动作。
 */
class SysOrgElementServiceTest {

    private SysOrgElementDao elementDao;
    private SysOrgOrgDao orgDao;
    private SysOrgDeptDao deptDao;
    private SysOrgUserDao userDao;
    private SysOrgPostDao postDao;
    private SysOrgElementService service;

    @BeforeEach
    void setUp() {
        elementDao = mock(SysOrgElementDao.class);
        orgDao = mock(SysOrgOrgDao.class);
        deptDao = mock(SysOrgDeptDao.class);
        userDao = mock(SysOrgUserDao.class);
        postDao = mock(SysOrgPostDao.class);
        service = new SysOrgElementService(elementDao, orgDao, deptDao, userDao, postDao);
    }

    @Test
    void saveRejectsRoleType() {
        SysOrgElementVo vo = element(SysOrgElementProperties.ORG_TYPE_ROLE);

        assertThrows(IllegalArgumentException.class, () -> service.save(vo));
        verify(elementDao, never()).insert(any());
    }

    @Test
    void saveRejectsGroupType() {
        SysOrgElementVo vo = element(SysOrgElementProperties.ORG_TYPE_GROUP);

        assertThrows(IllegalArgumentException.class, () -> service.save(vo));
        verify(elementDao, never()).insert(any());
    }

    @Test
    void saveRejectsUnsupportedType() {
        SysOrgElementVo vo = element(7);

        assertThrows(IllegalArgumentException.class, () -> service.save(vo));
        verify(elementDao, never()).insert(any());
    }

    @Test
    void updateRejectsRoleTypeBeforeDaoAccess() {
        SysOrgElementVo vo = element(SysOrgElementProperties.ORG_TYPE_ROLE);
        vo.setFieldId("0".repeat(31) + "1");

        assertThrows(IllegalArgumentException.class, () -> service.update(vo));
        verify(elementDao, never()).update(any());
    }

    private static SysOrgElementVo element(int orgType) {
        SysOrgElementVo vo = new SysOrgElementVo();
        vo.setFieldName("测试节点");
        vo.setFieldCode("TEST_NODE");
        vo.setFieldOrgType(orgType);
        return vo;
    }
}
