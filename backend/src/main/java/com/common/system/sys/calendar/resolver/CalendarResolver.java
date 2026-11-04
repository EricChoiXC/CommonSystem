package com.common.system.sys.calendar.resolver;

import com.common.system.common.base.resolver.BaseResolver;
import com.common.system.common.resolver.AuthorizationContext;
import com.common.system.common.utils.StringUtil;
import com.common.system.common.utils.UserUtil;
import com.common.system.sys.calendar.dao.SysCalendarMainDao;
import com.common.system.sys.calendar.model.SysCalendarMain;
import com.common.system.sys.org.model.SysOrgUser;
import org.springframework.stereotype.Component;

import static com.common.system.sys.calendar.properties.SysCalendarConstants.*;

/**
 * 日程接口鉴权器
 *
 * @author Eric
 * @date 2026/8/21
 * @description 日程模块接口的专属 resolver，基于「接口标识 + 文档 ID」完成最终鉴权：
 *              <ul>
 *              <li>列表/详情/日历视图/接收操作：登录且启用即可，数据按本人过滤</li>
 *              <li>新增：登录且启用即可</li>
 *              <li>编辑/删除：仅创建者本人</li>
 *              </ul>
 */
@Component
public class CalendarResolver extends BaseResolver {

    private final SysCalendarMainDao calendarMainDao;

    public CalendarResolver(SysCalendarMainDao calendarMainDao) {
        this.calendarMainDao = calendarMainDao;
    }

    @Override
    public boolean resolve(String action) {
        return resolveLoggedIn();
    }

    @Override
    public boolean resolve(AuthorizationContext context) {
        if (null == context) {
            return false;
        }
        return switch (context.getAction()) {
            case ACTION_SAVE, ACTION_LIST, ACTION_MONTH_LIST, ACTION_TODAY_LIST,
                 ACTION_LOAD, ACTION_ACCEPT -> resolveLoggedIn();
            case ACTION_UPDATE, ACTION_DELETE -> resolveOwn(context);
            default -> resolveLoggedIn();
        };
    }

    /**
     * 编辑/删除必须由创建者本人操作（数据归属判定，非角色判定）。
     */
    private boolean resolveOwn(AuthorizationContext context) {
        SysOrgUser user = UserUtil.getCurrentUser();
        if (!isEnabled(user) || StringUtil.isEmpty(context.getDocumentId())) {
            return false;
        }
        SysCalendarMain main = calendarMainDao.selectById(context.getDocumentId());
        return null != main && user.getFieldId().equals(main.getFieldCreatorId());
    }

    private boolean resolveLoggedIn() {
        return isEnabled(UserUtil.getCurrentUser());
    }
}
