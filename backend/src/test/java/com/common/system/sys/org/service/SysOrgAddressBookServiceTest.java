package com.common.system.sys.org.service;

import com.common.system.common.base.model.Pages;
import com.common.system.sys.org.dao.SysOrgAddressBookDao;
import com.common.system.sys.org.model.SysOrgElement;
import com.common.system.sys.org.request.SysOrgAddressBookQuery;
import com.common.system.sys.org.request.SysOrgGroupQuery;
import com.common.system.sys.org.response.SysOrgGroupVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 地址本服务查询逻辑测试
 *
 * @author Eric
 * @date 2026/8/9
 * @description 覆盖入参兜底（null 查询、空 ids、空白关键字、null pages）与透传行为。
 *              ORG2-BE-004：群组页签两端点经 SysOrgGroupService 承担——
 *              groupList/countGroupList 验证 keyword→fieldName 同口径转换与委托，
 *              groupUsers 验证透传；群组可见性/求交逻辑在 SysOrgGroupServiceTest 覆盖。
 */
class SysOrgAddressBookServiceTest {

    private SysOrgAddressBookDao dao;
    private SysOrgGroupService groupService;
    private SysOrgAddressBookService service;

    @BeforeEach
    void setUp() {
        dao = mock(SysOrgAddressBookDao.class);
        groupService = mock(SysOrgGroupService.class);
        service = new SysOrgAddressBookService(dao, groupService);
    }

    @Test
    void queryTreePassesQueryAndReturnsDaoResult() {
        SysOrgAddressBookQuery query = new SysOrgAddressBookQuery();
        query.setOrgTypes(List.of(1, 2));
        query.setLimitRange(List.of("org-1"));
        query.setParentId("parent-1");
        List<SysOrgElement> expected = List.of(sampleElement("e-1"));
        when(dao.selectTree(List.of(1, 2), List.of("org-1"), "parent-1")).thenReturn(expected);

        List<SysOrgElement> result = service.queryTree(query);

        assertEquals(expected, result);
        verify(dao).selectTree(List.of(1, 2), List.of("org-1"), "parent-1");
    }

    @Test
    void queryTreeWithNullQueryReturnsEmptyAndSkipsDao() {
        List<SysOrgElement> result = service.queryTree(null);

        assertTrue(result.isEmpty());
        verify(dao, never()).selectTree(anyList(), anyList(), anyString());
    }

    @Test
    void queryListNormalizesBlankKeywordAndDefaultsPages() {
        SysOrgAddressBookQuery query = new SysOrgAddressBookQuery();
        query.setKeyword("   ");
        when(dao.selectList(isNull(), isNull(), isNull(), any(Pages.class))).thenReturn(List.of());

        service.queryList(query, null);

        verify(dao).selectList(isNull(), isNull(), isNull(), any(Pages.class));
    }

    @Test
    void queryListPassesTrimmedKeyword() {
        SysOrgAddressBookQuery query = new SysOrgAddressBookQuery();
        query.setKeyword("  研发  ");
        when(dao.selectList(eq("研发"), isNull(), isNull(), any(Pages.class))).thenReturn(List.of());

        service.queryList(query, new Pages());

        verify(dao).selectList(eq("研发"), isNull(), isNull(), any(Pages.class));
    }

    @Test
    void queryListClampsInvalidPageNumbers() {
        SysOrgAddressBookQuery query = new SysOrgAddressBookQuery();
        Pages pages = new Pages();
        pages.setPageNo(0);
        pages.setPageSize(-5);
        pages.setNeedPage(true);
        when(dao.selectList(isNull(), isNull(), isNull(), any(Pages.class))).thenReturn(List.of());

        service.queryList(query, pages);

        verify(dao).selectList(isNull(), isNull(), isNull(), argThat(p ->
                p.getPageNo() == 1 && p.getPageSize() == 10 && p.isNeedPage()));
    }

    @Test
    void queryListAndCountListPassRoleOrgTypesToDao() {
        // 角色页签（ORG1-BE-004）：orgTypes=[5] 必须原样透传，且 queryList 与 countList
        // 使用同一 query 对象，保证 metadata.total 与列表同口径
        SysOrgAddressBookQuery query = new SysOrgAddressBookQuery();
        query.setOrgTypes(List.of(5));
        Pages pages = new Pages();
        pages.setNeedPage(true);
        pages.setPageNo(1);
        pages.setPageSize(20);
        when(dao.selectList(isNull(), eq(List.of(5)), isNull(), any(Pages.class))).thenReturn(List.of());
        when(dao.countList(isNull(), eq(List.of(5)), isNull())).thenReturn(3L);

        List<SysOrgElement> result = service.queryList(query, pages);
        long total = service.countList(query);

        assertEquals(List.of(), result);
        assertEquals(3L, total);
        verify(dao).selectList(isNull(), eq(List.of(5)), isNull(), argThat(p ->
                p.isNeedPage() && p.getPageNo() == 1 && p.getPageSize() == 20));
        verify(dao).countList(isNull(), eq(List.of(5)), isNull());
    }

    @Test
    void countListReturnsZeroForNullQuery() {
        assertEquals(0, service.countList(null));
        verify(dao, never()).countList(anyString(), anyList(), anyList());
    }

    @Test
    void loadByIdsDeduplicatesAndTrims() {
        SysOrgElement e1 = sampleElement("e-1");
        when(dao.selectByIds(List.of("e-1", "e-2"))).thenReturn(List.of(e1));

        List<SysOrgElement> result = service.loadByIds(List.of(" e-1 ", "", "e-2", "e-1"));

        assertEquals(List.of(e1), result);
        verify(dao).selectByIds(List.of("e-1", "e-2"));
    }

    @Test
    void loadByIdsReturnsEmptyForNullOrEmptyIds() {
        assertTrue(service.loadByIds(null).isEmpty());
        assertTrue(service.loadByIds(List.of()).isEmpty());
        assertTrue(service.loadByIds(List.of("", "  ")).isEmpty());
        verify(dao, never()).selectByIds(anyList());
    }

    /* ==================== 群组页签：groupList / groupUsers（ORG2-BE-004） ==================== */

    @Test
    void groupListConvertsKeywordToFieldNameAndDelegates() {
        SysOrgAddressBookQuery query = new SysOrgAddressBookQuery();
        query.setKeyword("  项目  ");
        Pages pages = new Pages();
        List<SysOrgGroupVo> expected = List.of(new SysOrgGroupVo());
        when(groupService.groupList(any(SysOrgGroupQuery.class), any(Pages.class))).thenReturn(expected);

        List<SysOrgGroupVo> result = service.groupList(query, pages);

        assertEquals(expected, result);
        // keyword 转 fieldName（trim 兜底），pages 原样透传，由群组服务做可见性过滤分页
        verify(groupService).groupList(argThat(g -> "项目".equals(g.getFieldName())), eq(pages));
    }

    @Test
    void groupListTrimsBlankKeywordToNull() {
        SysOrgAddressBookQuery query = new SysOrgAddressBookQuery();
        query.setKeyword("   ");
        when(groupService.groupList(any(SysOrgGroupQuery.class), any(Pages.class))).thenReturn(List.of());

        service.groupList(query, new Pages());

        verify(groupService).groupList(argThat(g -> null == g.getFieldName()), any(Pages.class));
    }

    @Test
    void groupListReturnsEmptyForNullQueryWithoutDelegating() {
        assertTrue(service.groupList(null, new Pages()).isEmpty());
        verify(groupService, never()).groupList(any(), any());
        verify(groupService, never()).count(any());
    }

    @Test
    void countGroupListDelegatesWithSameKeywordConversion() {
        SysOrgAddressBookQuery query = new SysOrgAddressBookQuery();
        query.setKeyword("群组");
        when(groupService.count(any(SysOrgGroupQuery.class))).thenReturn(7L);

        assertEquals(7L, service.countGroupList(query));

        verify(groupService).count(argThat(g -> "群组".equals(g.getFieldName())));
    }

    @Test
    void countGroupListReturnsZeroForNullQuery() {
        assertEquals(0, service.countGroupList(null));
        verify(groupService, never()).count(any());
    }

    @Test
    void groupUsersDelegatesToGroupService() {
        List<SysOrgElement> members = List.of(sampleElement("u-1"));
        when(groupService.groupUsers("g-1", List.of(3))).thenReturn(members);

        assertEquals(members, service.groupUsers("g-1", List.of(3)));
    }

    private static SysOrgElement sampleElement(String id) {
        SysOrgElement element = new SysOrgElement();
        element.setFieldId(id);
        element.setFieldName("name-" + id);
        return element;
    }
}
