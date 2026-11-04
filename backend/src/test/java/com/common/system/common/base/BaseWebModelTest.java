package com.common.system.common.base;

import com.common.system.common.base.controller.BaseController;
import com.common.system.common.base.model.BaseModel;
import com.common.system.common.base.model.BaseResponse;
import com.common.system.common.base.service.IBaseService;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;

class BaseWebModelTest {

    @Test
    void modelDoesNotGenerateAnIdDuringConstruction() {
        assertNull(new TestModel().getFieldId());
    }

    @Test
    @SuppressWarnings("unchecked")
    void controllerKeepsOnlyItsRequiredServiceDependency() {
        IBaseService<TestModel> service = mock(IBaseService.class);
        TestController controller = new TestController(service);

        assertSame(service, controller.service());
        assertThrows(NullPointerException.class, () -> new TestController(null));
    }

    @Test
    void responseUsesOneTypedDataPayload() {
        List<TestModel> models = List.of(new TestModel());
        BaseResponse<List<TestModel>> response = BaseResponse.success(models, new HashMap<>());

        assertEquals("SUCCESS", response.code());
        assertSame(models, response.data());
    }

    @Test
    void responseSerializesCodeAndDataWithJackson() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        BaseResponse<String> response = BaseResponse.success("hello", null);

        String json = mapper.writeValueAsString(response);

        assertTrue(json.contains("\"code\":\"SUCCESS\""));
        assertTrue(json.contains("\"data\":\"hello\""));
    }

    private static final class TestModel extends BaseModel {
    }

    private static final class TestController
            extends BaseController<TestModel, IBaseService<TestModel>> {

        private TestController(IBaseService<TestModel> service) {
            super(service);
        }

        private IBaseService<TestModel> service() {
            return getBaseService();
        }
    }
}
