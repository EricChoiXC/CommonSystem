package com.common.system.common.properties;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 公共应用级配置测试
 *
 * @author Eric
 * @date 2026/10/16
 * @description 固化 application.name 的读取契约：
 *              <ul>
 *              <li>配置有值时去除首尾空格返回；配置缺失或为空白时回退 {@code CommonSystem}；</li>
 *              <li>Spring Boot 按 ISO-8859-1 解析 .properties，配置文件中的 UTF-8 中文会被逐字节误读，
 *                  读取时须还原为原始中文；</li>
 *              <li>已正确解码的文本（含超单字节码点或转义后的中文）与非法 UTF-8 字节序列保持原样。</li>
 *              </ul>
 */
class CommonApplicationPropertiesTest {

    private static final String APPLICATION_NAME = "公共系统";

    /** 模拟 Spring 读取 UTF-8 保存的 .properties 时的 ISO-8859-1 误读结果 */
    private static String misdecoded(String value) {
        return new String(value.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
    }

    @Test
    void returnsConfiguredApplicationName() {
        assertEquals(APPLICATION_NAME, new CommonApplicationProperties(APPLICATION_NAME).getApplicationName());
    }

    @Test
    void trimsConfiguredApplicationName() {
        assertEquals(APPLICATION_NAME, new CommonApplicationProperties("  " + APPLICATION_NAME + "  ").getApplicationName());
    }

    @Test
    void restoresUtf8MisdecodedApplicationName() {
        // 实际运行路径：application.properties 中 application.name 为 UTF-8 中文，Spring 读到的是误读文本
        String misdecodedName = misdecoded(APPLICATION_NAME);
        // 「公」= E5 85 AC、「共」= E5 85 B1、「系」= E7 B3 BB、「统」= E7 BB 9F，逐字节按 ISO-8859-1 解码即下面这一串
        assertEquals("\u00e5\u0085\u00ac\u00e5\u0085\u00b1\u00e7\u00b3\u00bb\u00e7\u00bb\u009f", misdecodedName);
        assertEquals(APPLICATION_NAME, new CommonApplicationProperties(misdecodedName).getApplicationName());
    }

    @Test
    void keepsAsciiApplicationNameUnchanged() {
        assertEquals("CommonSystem", new CommonApplicationProperties("CommonSystem").getApplicationName());
    }

    @Test
    void keepsEscapedChineseApplicationNameUnchanged() {
        // 转义写法（反斜杠 u 四位十六进制）由 Properties 解析器还原为正确字符，不再走误读还原分支
        String escaped = new String(new char[] {'\u516c', '\u5171', '\u7cfb', '\u7edf'});
        assertEquals(APPLICATION_NAME, new CommonApplicationProperties(escaped).getApplicationName());
    }

    @Test
    void keepsNonUtf8SingleByteTextUnchanged() {
        // 0xE9 单字节（Latin-1「é」）不是合法 UTF-8 序列，应原样保留
        assertEquals("café", new CommonApplicationProperties("café").getApplicationName());
    }

    @Test
    void fallsBackWhenApplicationNameAbsent() {
        // Spring 占位符默认值为空串，等价于 application.name 未配置
        assertEquals(CommonApplicationProperties.DEFAULT_APPLICATION_NAME,
                new CommonApplicationProperties("").getApplicationName());
        assertEquals(CommonApplicationProperties.DEFAULT_APPLICATION_NAME,
                new CommonApplicationProperties(null).getApplicationName());
    }

    @Test
    void fallsBackWhenApplicationNameBlank() {
        assertEquals(CommonApplicationProperties.DEFAULT_APPLICATION_NAME,
                new CommonApplicationProperties("   ").getApplicationName());
    }
}
