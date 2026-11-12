package com.eric.base.common.properties;

import com.eric.base.common.utils.StringUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * 公共应用级配置
 *
 * @author Eric
 * @date 2026/10/16
 * @description 读取 application.properties 中 application.name（系统应用名称），
 *              供前端顶栏/LOGO 等公共展示位使用；配置缺失或为空时回退默认值 CommonSystem。
 *              <p>注意：Spring Boot 的 OriginTrackedPropertiesLoader 固定按 ISO-8859-1
 *              解析 .properties（Java Properties 规范），配置文件中以 UTF-8 保存的中文
 *              会被逐字节误读（中文应用名变成 Latin-1 乱码字节）。此处对读到的值做一次
 *              「按 ISO-8859-1 取回原始字节再按 UTF-8 解码」的还原，使配置文件中的
 *              中文保持可读可写；已正确解码的值（含码点 &gt; 0xFF，如环境变量注入或
 *              \\uXXXX 转义）与非法字节序列均原样保留。</p>
 */
@Component
public class CommonApplicationProperties {

    /** 系统应用名称默认值：application.name 未配置或为空时使用 */
    public static final String DEFAULT_APPLICATION_NAME = "CommonSystem";

    private final String applicationName;

    public CommonApplicationProperties(@Value("${application.name:}") String applicationName) {
        this.applicationName = resolveApplicationName(applicationName);
    }

    /**
     * 系统应用名称：application.name 为空时返回 {@link #DEFAULT_APPLICATION_NAME}。
     */
    public String getApplicationName() {
        return applicationName;
    }

    /**
     * 解析应用名称：空值回退默认值，非空值先还原 .properties 的 ISO-8859-1 误读再返回。
     */
    private static String resolveApplicationName(String configured) {
        if (StringUtil.isEmpty(configured)) {
            return DEFAULT_APPLICATION_NAME;
        }
        String value = restoreUtf8Misdecoded(configured.trim());
        return StringUtil.isEmpty(value) ? DEFAULT_APPLICATION_NAME : value;
    }

    /**
     * 还原被 ISO-8859-1 误读的 UTF-8 文本。
     * <p>仅当每个字符的码点都不超过 0xFF（即逐字节解码的特征）时才尝试还原：
     * 按 ISO-8859-1 取回原始字节后按 UTF-8 严格解码，解码失败（并非 UTF-8 序列，
     * 例如本就是 Latin-1 单字节字符）时返回原值，避免破坏正常文本。</p>
     */
    static String restoreUtf8Misdecoded(String value) {
        if (StringUtil.isEmpty(value)) {
            return value;
        }
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) > 0xFF) {
                // 含超出单字节范围的字符，说明已是正确 Unicode 文本，无需还原
                return value;
            }
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(value.getBytes(StandardCharsets.ISO_8859_1)))
                    .toString();
        } catch (CharacterCodingException e) {
            return value;
        }
    }
}
