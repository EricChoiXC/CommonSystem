package com.eric.base.common.base.controller;

import com.eric.base.common.base.model.BaseResponse;
import com.eric.base.common.properties.CommonApplicationProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 通用初始化接口测试
 *
 * @author Eric
 * @date 2026/10/16
 * @description 固化 GET /backend/common/init 的返回契约：
 *              data.applicationName 取 application.name，为空时回退默认应用名，
 *              保证前端 LOGO 读取的字段名与应用名回退规则不被改动。
 */
class InitControllerTest {

    @Test
    void initReturnsConfiguredApplicationName() {
        InitController controller = new InitController(new CommonApplicationProperties("公共系统"));

        BaseResponse<Map<String, Object>> response = controller.initInfo();

        assertEquals("SUCCESS", response.getCode());
        assertEquals("公共系统", response.getData().get("applicationName"));
    }

    @Test
    void initFallsBackToDefaultWhenApplicationNameAbsent() {
        InitController controller = new InitController(new CommonApplicationProperties(""));

        assertEquals(CommonApplicationProperties.DEFAULT_APPLICATION_NAME,
                controller.initInfo().getData().get("applicationName"));
    }
}
