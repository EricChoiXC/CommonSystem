package com.common.system.sys.org.service;

import com.common.system.common.base.model.Pages;
import com.common.system.sys.org.dao.SysOrgAddressBookDao;
import com.common.system.sys.org.model.SysOrgElement;
import com.common.system.sys.org.request.SysOrgAddressBookQuery;
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
 */
class SysOrgAddressBookServiceTest {

    private SysOrgAddressBookDao dao;
    private SysOrgAddressBookService service;

    @BeforeEach
    void setUp() {
        dao = mock(SysOrgAddressBookDao.class);
        service = new SysOrgAddressBookService(dao);
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

    private static SysOrgElement sampleElement(String id) {
        SysOrgElement element = new SysOrgElement();
        element.setFieldId(id);
        element.setFieldName("name-" + id);
        return element;
    }
}
