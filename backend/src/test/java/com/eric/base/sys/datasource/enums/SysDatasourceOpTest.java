package com.eric.base.sys.datasource.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 数据源条件操作符枚举测试
 *
 * @author Eric
 * @date 2026/11/13
 * @description 校验操作符全集与方案 §5.3 条件契约一致，of() 解析不区分大小写、非法值返回 null
 *              （由调用方报 CS_DATASOURCE_CONDITION_INVALID）。
 */
class SysDatasourceOpTest {

    @Test
    void valuesMatchDesignDoc() {
        assertEquals(8, SysDatasourceOp.values().length);
        assertEquals("EQ", SysDatasourceOp.EQ.name());
        assertEquals("NE", SysDatasourceOp.NE.name());
        assertEquals("LIKE", SysDatasourceOp.LIKE.name());
        assertEquals("GT", SysDatasourceOp.GT.name());
        assertEquals("GE", SysDatasourceOp.GE.name());
        assertEquals("LT", SysDatasourceOp.LT.name());
        assertEquals("LE", SysDatasourceOp.LE.name());
        assertEquals("IN", SysDatasourceOp.IN.name());
    }

    @Test
    void ofResolvesKnownValue() {
        assertEquals(SysDatasourceOp.EQ, SysDatasourceOp.of("EQ"));
        assertEquals(SysDatasourceOp.NE, SysDatasourceOp.of("NE"));
        assertEquals(SysDatasourceOp.LIKE, SysDatasourceOp.of("LIKE"));
        assertEquals(SysDatasourceOp.IN, SysDatasourceOp.of("IN"));
        assertEquals(SysDatasourceOp.LE, SysDatasourceOp.of("LE"));
    }

    @Test
    void ofIsCaseInsensitive() {
        assertEquals(SysDatasourceOp.EQ, SysDatasourceOp.of("eq"));
        assertEquals(SysDatasourceOp.LIKE, SysDatasourceOp.of("Like"));
        assertEquals(SysDatasourceOp.IN, SysDatasourceOp.of("in"));
        assertEquals(SysDatasourceOp.GT, SysDatasourceOp.of("gT"));
    }

    @Test
    void ofReturnsNullOnInvalidValue() {
        assertNull(SysDatasourceOp.of(null));
        assertNull(SysDatasourceOp.of(""));
        assertNull(SysDatasourceOp.of("MATCH"));
        assertNull(SysDatasourceOp.of(" = "));
        assertNull(SysDatasourceOp.of("EQ "));
    }
}
