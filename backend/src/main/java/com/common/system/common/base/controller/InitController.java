package com.common.system.common.base.controller;

import com.common.system.common.base.model.BaseResponse;
import com.common.system.common.properties.CommonApplicationProperties;
import com.common.system.common.utils.IDUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通用初始化接口
 *
 * @author Eric
 * @date 2026/8/5
 * @description 提供前端页面初始化所需的基础数据：
 *              <ul>
 *              <li>{@code GET /initID}：新建页/新建弹窗预取对象 ID，返回一个 32 位主键 ID
 *                  （毫秒级时间戳 + UUID v4 补位），前端将其赋值到表单对象的 id 字段后随 save 请求提交；
 *                  遵循 docs/开发规范.md「前端新建页/弹窗 fieldId 初始化规范」。</li>
 *              <li>{@code GET /init}：返回系统应用名称（application.properties 的 application.name，
 *                  为空时回退默认应用名），供前端 LOGO、浏览器标题等公共展示位使用。</li>
 *              </ul>
 */
@RestController
@RequestMapping("/backend/common")
public class InitController {

    private final CommonApplicationProperties commonApplicationProperties;

    public InitController(CommonApplicationProperties commonApplicationProperties) {
        this.commonApplicationProperties = commonApplicationProperties;
    }

    @GetMapping("/initID")
    public BaseResponse<String> init() {
        return BaseResponse.success(IDUtil.initID(), null);
    }

    /**
     * 系统初始化信息：当前返回应用名称，供前端公共展示位读取。
     * GET /backend/common/init
     */
    @GetMapping("/init")
    public BaseResponse<Map<String, Object>> initInfo() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("applicationName", commonApplicationProperties.getApplicationName());
        return BaseResponse.success(data, null);
    }
}
