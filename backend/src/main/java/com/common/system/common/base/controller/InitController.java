package com.common.system.common.base.controller;

import com.common.system.common.base.model.BaseResponse;
import com.common.system.common.properties.CommonApplicationProperties;
import com.common.system.common.utils.IDUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 *              <p>两个接口均不要求登录，因此不经过任何 Resolver 鉴权。</p>
 */
@RestController
@RequestMapping("/backend/common")
@Tag(name = "通用初始化", description = "前端公共初始化数据：预取主键 ID、应用名称；无需登录")
public class InitController {

    private final CommonApplicationProperties commonApplicationProperties;

    public InitController(CommonApplicationProperties commonApplicationProperties) {
        this.commonApplicationProperties = commonApplicationProperties;
    }

    /**
     * 预取主键 ID：{@code GET /backend/common/initID}
     * <p>返回 32 位 ID（毫秒时间戳 + UUID v4 补位），供新建页/新建弹窗在提交前预置 fieldId。</p>
     * <p>注：方法名 {@code init} 与路径 {@code /initID} 不一致属历史命名，为保持兼容不改方法名，
     * 调用方与文档一律以路径 {@code /initID} 为准。</p>
     *
     * @return 统一响应，data 为 32 位主键字符串
     */
    @GetMapping("/initID")
    @Operation(summary = "预取主键 ID", description = "无需鉴权（无 ACTION 常量，登录前即可调用）；"
            + "返回 32 位主键（毫秒时间戳 + UUID v4 补位），供新建页/新建弹窗在提交前预置 fieldId")
    @SecurityRequirements
    public BaseResponse<String> init() {
        return BaseResponse.success(IDUtil.initID(), null);
    }

    /**
     * 系统初始化信息：当前返回应用名称，供前端公共展示位读取。
     * GET /backend/common/init
     *
     * @return 统一响应，data.applicationName 为配置的应用名称（为空时回退默认值）
     */
    @GetMapping("/init")
    @Operation(summary = "系统初始化信息", description = "无需鉴权（无 ACTION 常量，登录前即可调用）；"
            + "返回 data.applicationName（application.name，为空时回退默认应用名），供前端 LOGO、浏览器标题使用")
    @SecurityRequirements
    public BaseResponse<Map<String, Object>> initInfo() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("applicationName", commonApplicationProperties.getApplicationName());
        return BaseResponse.success(data, null);
    }
}
