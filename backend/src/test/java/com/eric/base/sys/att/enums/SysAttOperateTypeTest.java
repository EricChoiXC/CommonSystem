package com.eric.base.sys.att.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 附件操作类型枚举测试
 *
 * @author Eric
 * @date 2026/8/7
 * @description 校验枚举实际值/中文名与附件表结构文档一致，of() 解析逻辑正确。
 */
class SysAttOperateTypeTest {

    @Test
    void valuesMatchDesignDoc() {
        assertEquals("1", SysAttOperateType.UPLOAD.getValue());
        assertEquals("2", SysAttOperateType.UPDATE.getValue());
        assertEquals("3", SysAttOperateType.DELETE.getValue());
        assertEquals("4", SysAttOperateType.ADMIN_DELETE.getValue());
        assertEquals("5", SysAttOperateType.DOWNLOAD.getValue());
        assertEquals("6", SysAttOperateType.VIEW.getValue());
    }

    @Test
    void labelsMatchDesignDoc() {
        assertEquals("上传", SysAttOperateType.UPLOAD.getLabel());
        assertEquals("修改", SysAttOperateType.UPDATE.getLabel());
        assertEquals("删除", SysAttOperateType.DELETE.getLabel());
        assertEquals("后台删除", SysAttOperateType.ADMIN_DELETE.getLabel());
        assertEquals("下载", SysAttOperateType.DOWNLOAD.getLabel());
        assertEquals("浏览", SysAttOperateType.VIEW.getLabel());
    }

    @Test
    void ofResolvesKnownValueAndRejectsUnknown() {
        assertEquals(SysAttOperateType.ADMIN_DELETE, SysAttOperateType.of("4"));
        assertEquals(SysAttOperateType.VIEW, SysAttOperateType.of("6"));
        assertNull(SysAttOperateType.of("0"));
        assertNull(SysAttOperateType.of(null));
    }
}
