package com.common.system.sys.org.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用户请求对象反序列化测试
 *
 * @author Eric
 * @date 2026/8/4
 * @description 验证 Spring MVC 反序列化 SysOrgUserRequest 时，body 能正确解析为具体类型 SysOrgUser。
 */
class SysOrgUserRequestTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void bodyDeserializesToConcreteSysOrgUser() throws Exception {
        // fieldId 必须是 32 位，否则会被 BaseModel.setFieldId 中的 IDUtil.initID 重新生成
        String fieldId = "12345678901234567890123456789012";
        String json = "{"
                + "\"body\":{\"fieldId\":\"" + fieldId + "\",\"fieldName\":\"zhangsan\"},"
                + "\"pages\":{\"pageNo\":1,\"pageSize\":10,\"needPage\":true}"
                + "}";

        SysOrgUserRequest request = mapper.readValue(json, SysOrgUserRequest.class);

        assertNotNull(request.getBody());
        assertEquals(fieldId, request.getBody().getFieldId());
        assertEquals("zhangsan", request.getBody().getFieldName());
        assertTrue(request.getPages().isNeedPage());
    }

    @Test
    void paramsDeserializesToAuthorizationContext() throws Exception {
        String json = "{\"params\":{\"action\":\"sys.org.user.save\",\"documentId\":\"123\"}}";

        SysOrgUserRequest request = mapper.readValue(json, SysOrgUserRequest.class);

        assertNotNull(request.getParams());
        assertEquals("sys.org.user.save", request.getParams().get("action"));
        assertEquals("123", request.getParams().get("documentId"));
    }
}
