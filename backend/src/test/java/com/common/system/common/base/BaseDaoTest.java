package com.common.system.common.base;

import com.common.system.common.base.dao.BaseDao;
import com.common.system.common.base.dao.IBaseMapper;
import com.common.system.common.base.model.BaseModel;
import com.common.system.common.base.model.Pages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BaseDaoTest {

    private IBaseMapper<TestModel> mapper;
    private TestDao dao;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mapper = mock(IBaseMapper.class);
        dao = new TestDao(mapper);
    }

    @Test
    void constructorRejectsMissingMapper() {
        assertThrows(NullPointerException.class, () -> new TestDao(null));
    }

    @Test
    void insertReturnsMapperAffectedRows() {
        TestModel model = new TestModel();
        when(mapper.insert(model)).thenReturn(1);

        assertEquals(1, dao.insert(model));

        verify(mapper).insert(model);
    }

    @Test
    void listDelegatesCriteriaToMapper() {
        TestModel criteria = new TestModel();
        Pages pages = new Pages();
        List<TestModel> expected = List.of(new TestModel());
        when(mapper.selectList(criteria, pages)).thenReturn(expected);

        assertSame(expected, dao.selectList(criteria, pages));
    }

    private static final class TestModel extends BaseModel {
    }

    private static final class TestDao extends BaseDao<TestModel, IBaseMapper<TestModel>> {
        private TestDao(IBaseMapper<TestModel> mapper) {
            super(mapper);
        }
    }
}
