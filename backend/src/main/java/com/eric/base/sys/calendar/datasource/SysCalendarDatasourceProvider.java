package com.eric.base.sys.calendar.datasource;

import com.eric.base.common.base.model.Pages;
import com.eric.base.common.base.resolver.BaseResolver;
import com.eric.base.common.exception.AuthorizationException;
import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.calendar.properties.SysCalendarConstants;
import com.eric.base.sys.calendar.request.SysCalendarQuery;
import com.eric.base.sys.calendar.resolver.CalendarResolver;
import com.eric.base.sys.calendar.response.SysCalendarVo;
import com.eric.base.sys.calendar.service.SysCalendarMainService;
import com.eric.base.sys.datasource.spi.SysDatasourceDefaultDo;
import com.eric.base.sys.datasource.spi.SysDatasourceFieldDo;
import com.eric.base.sys.datasource.spi.SysDatasourceFieldScanner;
import com.eric.base.sys.datasource.spi.SysDatasourceProvider;
import com.eric.base.sys.datasource.spi.SysDatasourceQueryContext;
import com.eric.base.sys.datasource.spi.SysDatasourceQueryResult;
import com.eric.base.sys.org.model.SysOrgUser;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 日程数据源 Provider
 *
 * @author Eric
 * @date 2026/11/16
 * @description 向数据源模块注册日程「目标查询能力」与默认数据源（方案 §7）：
 *              <ul>
 *              <li>目标能力 {@code calendar.list}：对应日程查询接口
 *                  {@code POST /backend/sys/calendar/list}（ACTION sys.calendar.list），
 *                  条件/返回字段经 {@code @DatasourceField} 注解声明于
 *                  {@link SysCalendarQuery} / {@link SysCalendarVo}（A8，方案 §2.4），
 *                  条件字段 6 项、返回字段 9 项、可排序 startTime/createTime；</li>
 *              <li>默认数据源【日程列表】{@code calendar.list}：无条件、全字段；</li>
 *              <li>执行：把合并后条件转 {@link SysCalendarQuery}，委托
 *                  {@link SysCalendarMainService#list}——沿用「本人创建 OR 本人接收」
 *                  固定数据边界，fieldCreatorId 为边界内收紧条件（我创建的）；</li>
 *              <li>排序翻译：数据源侧排序 key 为返回字段 Java 名
 *                  （{@code fieldStartTime}/{@code fieldCreateTime}），日程 Mapper
 *                  排序片段只识别 {@code startTime}/{@code createTime}，经
 *                  {@link #SORT_FIELD_MAPPING} 白名单翻译，未知 key 回退 null
 *                  （Mapper 按默认排序执行）。</li>
 *              </ul>
 */
@Component
public class SysCalendarDatasourceProvider implements SysDatasourceProvider {

    /** 目标能力编码（同时是默认数据源【日程列表】编码） */
    public static final String TARGET_CALENDAR_LIST = "calendar.list";

    /** 数据源排序白名单 key（返回字段 Java 名）→ 日程 Mapper 排序词汇表 */
    private static final Map<String, String> SORT_FIELD_MAPPING = Map.of(
            "fieldStartTime", "startTime",
            "fieldCreateTime", "createTime");

    private final SysCalendarMainService calendarMainService;
    private final CalendarResolver calendarResolver;

    public SysCalendarDatasourceProvider(SysCalendarMainService calendarMainService,
                                         CalendarResolver calendarResolver) {
        this.calendarMainService = calendarMainService;
        this.calendarResolver = calendarResolver;
    }

    @Override
    public String getTargetCode() {
        return TARGET_CALENDAR_LIST;
    }

    @Override
    public String getTargetName() {
        return "日程查询";
    }

    @Override
    public String getTargetAction() {
        // 执行期委托日程模块 resolver 按「接口标识」鉴权（方案 A6）
        return SysCalendarConstants.ACTION_LIST;
    }

    @Override
    public String getTargetPath() {
        return "/backend/sys/calendar/list";
    }

    @Override
    public BaseResolver getTargetResolver() {
        return calendarResolver;
    }

    /**
     * 条件字段定义：{@code @DatasourceField} 声明于 {@link SysCalendarQuery}（A8），
     * 扫描归一为 SysDatasourceFieldDo，进程内缓存；fieldCreatorId 为 literalAllowed=false（A7）
     */
    @Override
    public List<SysDatasourceFieldDo> getConditionFields() {
        return SysDatasourceFieldScanner.conditionFields(SysCalendarQuery.class);
    }

    /**
     * 返回字段定义：{@code @DatasourceField} 声明于 {@link SysCalendarVo}（A8）；
     * 公共字段 fieldId 继承自 BaseModel 无法标注，由扫描器内建补齐（固定最前）
     */
    @Override
    public List<SysDatasourceFieldDo> getResultFields() {
        return SysDatasourceFieldScanner.resultFields(SysCalendarVo.class);
    }

    @Override
    public SysDatasourceQueryResult query(SysDatasourceQueryContext context) {
        SysCalendarQuery query = toCalendarQuery(context.getConditions());
        Pages pages = context.getPages();
        // 固定 userId 为当前执行人：沿用日程既有数据边界（本人创建 OR 本人接收）
        String userId = requireCurrentUserId();
        List<?> rows = calendarMainService.list(query, userId, pages,
                toCalendarSortField(context.getSortField()), context.getSortOrder());
        long total = needTotal(pages) ? calendarMainService.count(query, userId) : rows.size();
        return new SysDatasourceQueryResult(rows, total);
    }

    @Override
    public List<SysDatasourceDefaultDo> getDefaultDatasources() {
        // 默认数据源【日程列表】：无条件、全字段（fields 空列表 = 目标能力全字段）
        SysDatasourceDefaultDo list = new SysDatasourceDefaultDo();
        list.setCode(TARGET_CALENDAR_LIST);
        list.setName("日程列表");
        list.setConditions(List.of());
        list.setFields(List.of());
        return List.of(list);
    }

    /* ==================== 私有方法 ==================== */

    /**
     * 合并后条件 → SysCalendarQuery（仅取日程侧声明的条件 key）
     */
    private SysCalendarQuery toCalendarQuery(Map<String, Object> conditions) {
        SysCalendarQuery query = new SysCalendarQuery();
        if (null == conditions) {
            return query;
        }
        Object value;
        if (null != (value = conditions.get("fieldName"))) {
            query.setFieldName(String.valueOf(value));
        }
        if (null != (value = conditions.get("fieldType"))) {
            query.setFieldType(String.valueOf(value));
        }
        if (null != (value = conditions.get("fieldAccept"))) {
            query.setFieldAccept(String.valueOf(value));
        }
        if (null != (value = conditions.get("fieldStartTimeStart"))) {
            query.setFieldStartTimeStart((Date) value);
        }
        if (null != (value = conditions.get("fieldStartTimeEnd"))) {
            query.setFieldStartTimeEnd((Date) value);
        }
        if (null != (value = conditions.get("fieldCreatorId"))) {
            // 「我创建的」：仅来自 ${CURRENT_USER_ID} 解析结果（字段注解 literalAllowed=false）
            query.setFieldCreatorId(String.valueOf(value));
        }
        return query;
    }

    /**
     * 白名单 key 翻译为 Mapper 排序词汇；null 或未知回退 null（Mapper 按默认排序执行）
     */
    private String toCalendarSortField(String sortField) {
        return null == sortField ? null : SORT_FIELD_MAPPING.get(sortField);
    }

    private boolean needTotal(Pages pages) {
        // needPage 为基本型 boolean，Lombok 生成 isNeedPage()；null 视为不分页
        return null != pages && pages.isNeedPage();
    }

    private String requireCurrentUserId() {
        SysOrgUser user = UserUtil.getCurrentUser();
        if (null == user) {
            throw new AuthorizationException("UNAUTHORIZED", "登录已过期，请重新登录");
        }
        return user.getFieldId();
    }
}
