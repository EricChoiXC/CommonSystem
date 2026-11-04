package com.common.system.sys.calendar.service;

import com.common.system.common.base.model.Pages;
import com.common.system.common.base.service.BaseService;
import com.common.system.common.exception.AuthorizationException;
import com.common.system.common.utils.StringUtil;
import com.common.system.common.utils.UserUtil;
import com.common.system.sys.calendar.dao.SysCalendarMainDao;
import com.common.system.sys.calendar.dao.SysCalendarPersonsDao;
import com.common.system.sys.calendar.enums.SysCalendarType;
import com.common.system.sys.calendar.model.SysCalendarMain;
import com.common.system.sys.calendar.model.SysCalendarPersons;
import com.common.system.sys.calendar.request.SysCalendarMainRequest;
import com.common.system.sys.calendar.request.SysCalendarQuery;
import com.common.system.sys.calendar.response.SysCalendarPersonVo;
import com.common.system.sys.calendar.response.SysCalendarVo;
import com.common.system.sys.notify.enums.SysNotifyType;
import com.common.system.sys.notify.model.SysNotifyTodo;
import com.common.system.sys.notify.service.SysNotifyTodoService;
import com.common.system.sys.org.model.SysOrgUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 日程业务服务
 *
 * @author Eric
 * @date 2026/8/21
 * @description 承载日程核心业务：创建/编辑/删除日程、查询列表与日历视图、
 *              接收/拒绝操作。团队日程创建/修改后自动发送待阅通知。
 */
@Service
public class SysCalendarMainService extends BaseService<SysCalendarMain, SysCalendarMainDao> {

    private final SysCalendarPersonsDao personsDao;
    private final SysNotifyTodoService notifyTodoService;

    public SysCalendarMainService(SysCalendarMainDao baseDao,
                                  SysCalendarPersonsDao personsDao,
                                  SysNotifyTodoService notifyTodoService) {
        super(baseDao);
        this.personsDao = personsDao;
        this.notifyTodoService = notifyTodoService;
    }

    /* ==================== 新增日程 ==================== */

    /**
     * 新增日程
     * <p>个人日程：接收人即为自己且接收状态固定为是；<br>
     * 团队日程：为所有接收人发送待阅通知。</p>
     */
    @Transactional
    public SysCalendarMain create(SysCalendarMainRequest request) {
        SysCalendarMain main = buildMainFromRequest(request);
        SysOrgUser current = UserUtil.getCurrentUser();
        // 使用前端预取的 ID（fieldId 由前端 getInitID 获取）
        if (null != request.getFieldId()) {
            main.setFieldId(request.getFieldId());
        }
        main.initFieldId();
        main.setFieldCreatorId(current.getFieldId());
        main.setFieldCreateTime(new Date());
        getBaseDao().insert(main);

        // 处理接收人
        if (SysCalendarType.PERSONAL.getValue().equals(main.getFieldType())) {
            // 个人日程：接收人即为自己，接收状态为是
            SysCalendarPersons self = new SysCalendarPersons();
            self.initFieldId();
            self.setFieldCalendarId(main.getFieldId());
            self.setFieldPersonId(current.getFieldId());
            self.setFieldAccept(true);
            personsDao.insert(self);
        } else if (SysCalendarType.TEAM.getValue().equals(main.getFieldType())) {
            // 团队日程
            List<String> personIds = request.getPersonIds();
            if (null == personIds || personIds.isEmpty()) {
                throw new IllegalArgumentException("团队日程必须指定接收人");
            }
            List<SysCalendarPersons> list = new ArrayList<>();
            for (String personId : personIds) {
                if (StringUtil.isEmpty(personId)) continue;
                SysCalendarPersons p = new SysCalendarPersons();
                p.initFieldId();
                p.setFieldCalendarId(main.getFieldId());
                p.setFieldPersonId(personId);
                p.setFieldAccept(null); // 未确认
                list.add(p);
            }
            if (!list.isEmpty()) {
                personsDao.batchInsert(list);
            }

            // 为所有接收人发送待阅通知
            sendNotifyTodo(main, current, list);
        }

        return main;
    }

    /* ==================== 编辑日程 ==================== */

    /**
     * 编辑日程
     * <p>团队日程编辑后重新发送待阅通知给所有接收人。</p>
     */
    @Transactional
    public SysCalendarMain update(SysCalendarMainRequest request) {
        String id = request.getFieldId();
        if (StringUtil.isEmpty(id)) {
            throw new IllegalArgumentException("id 不能为空");
        }
        SysCalendarMain exist = getBaseDao().selectById(id);
        if (null == exist) {
            throw new IllegalArgumentException("日程不存在");
        }

        SysCalendarMain main = buildMainFromRequest(request);
        main.setFieldId(id);
        main.setFieldUpdateTime(new Date());
        getBaseDao().update(main);

        // 团队日程：重新处理接收人
        if (SysCalendarType.TEAM.getValue().equals(main.getFieldType())) {
            // 删除旧接收人
            personsDao.deleteByCalendarId(id);

            List<String> personIds = request.getPersonIds();
            if (null != personIds && !personIds.isEmpty()) {
                List<SysCalendarPersons> list = new ArrayList<>();
                for (String personId : personIds) {
                    if (StringUtil.isEmpty(personId)) continue;
                    SysCalendarPersons p = new SysCalendarPersons();
                    p.initFieldId();
                    p.setFieldCalendarId(id);
                    p.setFieldPersonId(personId);
                    p.setFieldAccept(null);
                    list.add(p);
                }
                if (!list.isEmpty()) {
                    personsDao.batchInsert(list);
                }

                // 重新发送待阅通知
                SysOrgUser current = UserUtil.getCurrentUser();
                sendNotifyTodo(main, current, list);
            }
        }

        return main;
    }

    /* ==================== 删除日程 ==================== */

    /**
     * 删除日程
     * <p>仅限创建者删除；团队日程删除后，接收人的待阅统一转入已阅。</p>
     */
    @Transactional
    public void delete(String fieldId) {
        SysOrgUser current = UserUtil.getCurrentUser();
        if (null == current) {
            throw new AuthorizationException("UNAUTHORIZED", "登录已过期，请重新登录");
        }
        SysCalendarMain main = getBaseDao().selectById(fieldId);
        if (null == main) {
            throw new IllegalArgumentException("日程不存在");
        }
        if (!current.getFieldId().equals(main.getFieldCreatorId())) {
            throw new IllegalArgumentException("只能删除自己创建的日程");
        }

        // 团队日程：接收人的待阅统一转入已阅
        if (SysCalendarType.TEAM.getValue().equals(main.getFieldType())) {
            List<SysCalendarPersonVo> persons = personsDao.selectByCalendarId(fieldId);
            for (SysCalendarPersonVo person : persons) {
                notifyTodoService.autoDoneByModel("SYS-CALENDAR", fieldId, person.getFieldPersonId());
            }
        }

        // 删除接收人记录
        personsDao.deleteByCalendarId(fieldId);
        // 删除主表
        getBaseDao().deleteById(fieldId);
    }

    /* ==================== 查询 ==================== */

    /**
     * 查询日程列表（含分页）
     * <p>固定查询条件：自己创建的日程或自己作为接收人的日程。</p>
     */
    public List<SysCalendarVo> list(SysCalendarQuery query, String userId,
                                    Pages pages, String sortField, String sortOrder) {
        SysCalendarQuery safe = null == query ? new SysCalendarQuery() : query;
        return getBaseDao().selectCalendarList(safe, userId, pages, sortField, sortOrder);
    }

    /**
     * 查询日程列表总数
     */
    public long count(SysCalendarQuery query, String userId) {
        SysCalendarQuery safe = null == query ? new SysCalendarQuery() : query;
        return getBaseDao().selectCalendarCount(safe, userId);
    }

    /**
     * 查询当月日程（日历视图）
     */
    public List<SysCalendarVo> monthList(SysCalendarQuery query, String userId) {
        SysCalendarQuery safe = null == query ? new SysCalendarQuery() : query;
        return getBaseDao().selectMonthList(safe, userId);
    }

    /**
     * 查询日程详情（含接收人列表）
     */
    public SysCalendarVo loadDetail(String fieldId) {
        SysCalendarVo vo = getBaseDao().selectCalendarDetail(fieldId);
        if (null == vo) {
            throw new IllegalArgumentException("日程不存在");
        }
        // 查询接收人列表
        List<SysCalendarPersonVo> persons = personsDao.selectByCalendarId(fieldId);
        vo.setPersons(persons);
        return vo;
    }

    /* ==================== 接收/拒绝 ==================== */

    /**
     * 接收/拒绝日程
     * <p>仅限团队日程的接收人本人操作。</p>
     */
    @Transactional
    public void accept(String fieldId, Boolean accept) {
        SysOrgUser current = UserUtil.getCurrentUser();
        if (null == current) {
            throw new AuthorizationException("UNAUTHORIZED", "登录已过期，请重新登录");
        }
        SysCalendarPersons target = personsDao.selectByCalendarAndPerson(fieldId, current.getFieldId());
        if (null == target) {
            throw new IllegalArgumentException("您不是该日程的接收人");
        }
        personsDao.updateAccept(target.getFieldId(), current.getFieldId(), accept);
    }

    /* ==================== 私有方法 ==================== */

    private SysCalendarMain buildMainFromRequest(SysCalendarMainRequest request) {
        if (null == request) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (StringUtil.isEmpty(request.getFieldName())) {
            throw new IllegalArgumentException("标题不能为空");
        }
        if (null == request.getFieldStartTime()) {
            throw new IllegalArgumentException("开始时间不能为空");
        }
        if (null == request.getFieldEndTime()) {
            throw new IllegalArgumentException("结束时间不能为空");
        }
        if (request.getFieldEndTime().before(request.getFieldStartTime())) {
            throw new IllegalArgumentException("结束时间不能早于开始时间");
        }
        SysCalendarType type = SysCalendarType.of(request.getFieldType());
        if (null == type) {
            throw new IllegalArgumentException("日程类型不合法");
        }

        SysCalendarMain main = new SysCalendarMain();
        main.setFieldName(request.getFieldName());
        main.setFieldStartTime(request.getFieldStartTime());
        main.setFieldEndTime(request.getFieldEndTime());
        main.setFieldType(request.getFieldType());
        main.setFieldContent(request.getFieldContent());
        return main;
    }

    /**
     * 发送待阅通知
     */
    private void sendNotifyTodo(SysCalendarMain main, SysOrgUser current, List<SysCalendarPersons> persons) {
        List<String> userIds = new ArrayList<>();
        for (SysCalendarPersons p : persons) {
            // 不给自己发通知
            if (!current.getFieldId().equals(p.getFieldPersonId())) {
                userIds.add(p.getFieldPersonId());
            }
        }
        if (userIds.isEmpty()) {
            return;
        }

        SysNotifyTodo todo = new SysNotifyTodo();
        todo.setFieldName(current.getFieldName() + "向您发起了一个新日程，请确认。");
        todo.setFieldNotifyType(SysNotifyType.READ.getValue());
        todo.setFieldModelName("SYS-CALENDAR");
        todo.setFieldModelId(main.getFieldId());
        // 链接到日程详情页
        todo.setFieldUrl("/calendar/detail/" + main.getFieldId());
        // 直接调用通知服务
        notifyTodoService.send(todo, userIds);
    }
}