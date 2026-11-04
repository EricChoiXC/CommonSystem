package com.common.system.common.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.Date;

/**
 * Jackson 全局日期反序列化配置
 *
 * <p>前端各模块提交时间字段的格式不统一：Element Plus 日期控件配置 value-format 时
 * 提交 "yyyy-MM-dd HH:mm:ss"（日历、项目、合同、生产、订单等新建/编辑表单）或
 * "yyyy-MM-dd"（生日、通知筛选）；未配置 value-format 时提交 Date 对象序列化后的
 * ISO-8601 字符串（资产模块）；编辑回填后原样回传时为毫秒时间戳。Jackson 默认的
 * StdDateFormat 仅支持 ISO-8601 与 date-only，遇到 "yyyy-MM-dd HH:mm:ss" 会抛出
 * InvalidFormatException（如 POST /backend/sys/calendar/save）。</p>
 *
 * <p>本配置为 HTTP 消息转换使用的 ObjectMapper 注册统一的 java.util.Date 反序列化器，
 * 按以下顺序解析：</p>
 * <ol>
 *   <li>JSON 数字 → 毫秒时间戳（编辑回填回传场景，保持 Jackson 默认行为）；</li>
 *   <li>"yyyy-MM-dd HH:mm:ss" / "yyyy-MM-dd HH:mm" / "yyyy-MM-dd" 等
 *       无时区字符串 → 按应用默认时区（Asia/Shanghai，与数据库连接
 *       serverTimezone=Asia/Shanghai 一致）解析；</li>
 *   <li>ISO-8601 带时区偏移字符串（"2026-09-17T08:00:00.000+08:00" / "...Z"）→
 *       按偏移解析；</li>
 *   <li>ISO-8601 无偏移字符串（"2026-09-17T16:00:00"）→ 按应用默认时区解析。</li>
 * </ol>
 *
 * <p>仅影响反序列化；序列化保持 Spring Boot 默认（毫秒时间戳），前端展示与编辑回填
 * 行为不变。模块内自建的 ObjectMapper 不经过此配置，不受影响。</p>
 *
 * @author Eric
 * @date 2026/9/17
 */
@Configuration
public class JacksonConfig {

    /** 无时区字符串统一按应用默认时区解析（当前部署为 Asia/Shanghai） */
    private static final ZoneId DEFAULT_ZONE = ZoneId.systemDefault();

    /** 常见无时区格式，覆盖前端 Element Plus 各 value-format 组合 */
    private static final DateTimeFormatter[] WALL_CLOCK_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
    };

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer flexibleDateDeserializerCustomizer() {
        return builder -> builder.deserializerByType(Date.class, new FlexibleDateDeserializer());
    }

    /**
     * 宽松的 java.util.Date 反序列化器：兼容毫秒时间戳、常见无时区格式与 ISO-8601。
     */
    public static class FlexibleDateDeserializer extends JsonDeserializer<Date> {

        @Override
        public Date deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (parser.currentToken() == JsonToken.VALUE_NUMBER_INT) {
                return new Date(parser.getLongValue());
            }
            String text = parser.getValueAsString();
            if (text == null || text.isBlank()) {
                return null;
            }
            text = text.trim();
            for (DateTimeFormatter formatter : WALL_CLOCK_FORMATS) {
                try {
                    return parseWallClock(formatter, text);
                } catch (DateTimeParseException ignored) {
                    // 尝试下一种格式
                }
            }
            try {
                return Date.from(OffsetDateTime.parse(text).toInstant());
            } catch (DateTimeParseException ignored) {
                // 尝试无偏移的 ISO 格式
            }
            try {
                return Date.from(LocalDateTime.parse(text).atZone(DEFAULT_ZONE).toInstant());
            } catch (DateTimeParseException e) {
                throw context.weirdStringException(text, Date.class,
                        "expected epoch millis, yyyy-MM-dd HH:mm:ss, yyyy-MM-dd or ISO-8601");
            }
        }

        /**
         * 按无时区格式解析：完整日期时间按默认时区，date-only 取当天零点。
         */
        private Date parseWallClock(DateTimeFormatter formatter, String text) {
            TemporalAccessor parsed = formatter.parseBest(text, LocalDateTime::from, LocalDate::from);
            if (parsed instanceof LocalDateTime dateTime) {
                return Date.from(dateTime.atZone(DEFAULT_ZONE).toInstant());
            }
            if (parsed instanceof LocalDate date) {
                return Date.from(date.atStartOfDay(DEFAULT_ZONE).toInstant());
            }
            throw new DateTimeParseException("unparseable", text, 0);
        }
    }
}
