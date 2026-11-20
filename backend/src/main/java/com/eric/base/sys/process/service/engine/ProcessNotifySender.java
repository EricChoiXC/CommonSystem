package com.eric.base.sys.process.service.engine;

import com.eric.base.common.setting.service.CommonSettingService;
import com.eric.base.common.utils.StringUtil;
import com.eric.base.sys.notify.enums.SysNotifyType;
import com.eric.base.sys.notify.model.SysNotifyTodo;
import com.eric.base.sys.notify.service.SysNotifyTodoService;
import com.eric.base.sys.org.mapper.SysOrgUserMapper;
import com.eric.base.sys.org.model.SysOrgUser;
import com.eric.base.sys.process.dao.SysProcessTemplateDao;
import com.eric.base.sys.process.model.SysProcessExample;
import com.eric.base.sys.process.model.SysProcessTemplate;
import com.eric.base.sys.process.properties.SysProcessConstants;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 流程通知发送收口（P17-BE-012 起，P17-BE-014 通知收口载体）
 *
 * @author Eric
 * @date 2026/11/20
 * @description P17-BE-012 交付物：把「READ 待阅发送 + 标题模板渲染」从 {@code SysProcessExampleService}
 *              （催办，P17-BE-011 产出）收口为共享组件，供流程引擎抄送节点
 *              （{@code ProcessFlowEngine#enterCc}）、抄送动作（handler_cc）与催办动作共用；
 *              标题占位符渲染逻辑同步迁移到本组件。
 *              <p>P17-BE-014 通知收口在本组件扩展 TODO 待办发送与既有 4 个标题模板消费：
 *              审批待办（{@link #sendApproveTodo}，notify_handle_subject）、被驳回待办
 *              （{@link #sendRefuseTodo}，notify_refuse_subject）、办结起草人待阅
 *              （{@link #sendFinishDrafterRead}，notify_finish_drafter_subject）与办结审批人待阅
 *              （{@link #sendFinishHandlerRead}，notify_finish_handler_subject）——引擎与 Service
 *              的流程通知发送统一经此处出口，业务模块旧的自发通知不强制下线（重复发送风险 R05
 *              与兼容策略见 docs/设计文档/sys/process/流程核心业务.md「通知」章节、docs/迁移说明.md）。</p>
 *
 *              <h3>发送口径</h3>
 *              <ul>
 *              <li>类型：审批待办/被驳回待办为 {@link SysNotifyType#TODO}（待办），抄送/催办/办结
 *                  为 {@link SysNotifyType#READ}（待阅）；来源模块
 *                  {@link SysProcessConstants#MODEL_KEY_SYS_PROCESS}，modelId=实例 id，
 *                  链接=实例详情页（现状路由 {@code /processExample/detail/{id}}；Wave 3 审批中心
 *                  调整路由时同步更新）；</li>
 *              <li>标题：按公共配置模板渲染（{@link #renderNotifySubject}），配置缺失或为空回退
 *                  默认模板（与 application.yml SETTING 默认值一致）；</li>
 *              <li>收件人：由调用方给定（审批待办=本次生成 PROCESSING 工作项处理人、办结审批人待阅=
 *                  实际审批过的用户、抄送为显式指定/解析结果不排除发起人、催办由 Service 预先排除
 *                  发起人），收件人为空时不发送（「仅留痕不发通知」口径由调用方承接）；</li>
 *              <li>事务：通知经 {@link SysNotifyTodoService#send} 落库即「待办/待阅可达」（SSE 推送由
 *                  NotifySendEvent 事务提交后异步处理）；发送失败不在此捕获——与调用方操作同事务
 *                  整体失败（对齐「任务在流转事务内同步执行」的既有风格）。</li>
 *              </ul>
 */
@Component
public class ProcessNotifySender {

    private final SysNotifyTodoService notifyTodoService;
    private final SysOrgUserMapper userMapper;
    private final SysProcessTemplateDao templateDao;
    private final CommonSettingService settingService;

    public ProcessNotifySender(SysNotifyTodoService notifyTodoService,
                               SysOrgUserMapper userMapper,
                               SysProcessTemplateDao templateDao,
                               CommonSettingService settingService) {
        this.notifyTodoService = notifyTodoService;
        this.userMapper = userMapper;
        this.templateDao = templateDao;
        this.settingService = settingService;
    }

    /* ==================== TODO 待办发送（P17-BE-014） ==================== */

    /**
     * 发送审批待办（TODO，P17-BE-014 通知收口）：审批节点生成「实际需审批（PROCESSING）」工作项后
     * 由引擎触发（{@code ProcessFlowEngine} 的 {@code createApproveWorkitems} / {@code enterSerialApprove} /
     * {@code createNextSerialWorkitem} 三处生成点统一收口），收件人 = 本次生成的 PROCESSING 工作项
     * 处理人（自动跳过者工作项为 FINISH，不在收件人；一次节点进入合并为一次发送）。
     * <p>标题消费公共配置 {@code notify.notify_handle_subject}（缺省回退默认模板）；内容为固定文案
     * 「您有流程审批待办，请及时处理」——等价口径：改造建议的「流程审批：{当前节点名}」需在签名中
     * 携带节点名（冻结签名为 example + userIds），故采用等价固定文案，当前节点信息可经待办链接
     * （实例详情页）查看。收件人为空时不发送。</p>
     *
     * @param example 流程实例（非空，modelId / 标题占位符取值来源）
     * @param userIds 收件人用户 id 列表（本次生成 PROCESSING 工作项的处理人，去重保序；空表不发）
     */
    public void sendApproveTodo(SysProcessExample example, List<String> userIds) {
        send(SysNotifyType.TODO.getValue(), renderNotifySubject(approveSubjectTemplate(), example),
                example, APPROVE_TODO_CONTENT, userIds);
    }

    /**
     * 发送被驳回待办（TODO，P17-BE-014 通知收口）：审批人驳回后由 Service
     * （{@code SysProcessExampleService#reject}）触发，收件人 = 流程起草人（驳回人=起草人时不发送）。
     * 标题消费公共配置 {@code notify.notify_refuse_subject}（缺省回退默认模板，
     * 占位符 {@link SysProcessConstants#NOTIFY_PLACEHOLDER_HANDLER_NAME} = 驳回人姓名）；
     * 内容 = 驳回意见（审批意见原样传入，可空）。收件人为空时不发送。
     * <p>本方法未携带驳回人姓名（{@code ${fieldHandlerName}} 渲染为空串）；已知驳回人时
     * 请使用 {@link #sendRefuseTodo(SysProcessExample, String, String, List)} 重载。</p>
     *
     * @param example 流程实例（非空）
     * @param content 驳回意见（可空）
     * @param userIds 收件人用户 id 列表（空表不发）
     */
    public void sendRefuseTodo(SysProcessExample example, String content, List<String> userIds) {
        sendRefuseTodo(example, null, content, userIds);
    }

    /**
     * 发送被驳回待办（TODO，携带驳回人姓名重载）：语义同
     * {@link #sendRefuseTodo(SysProcessExample, String, List)}，标题占位符
     * {@link SysProcessConstants#NOTIFY_PLACEHOLDER_HANDLER_NAME} = handlerName
     * （驳回人姓名，空值渲染为空串）。Service 驳回动作以当前用户姓名调用本重载。
     *
     * @param example    流程实例（非空）
     * @param handlerName 驳回人姓名（可空，空值占位符渲染为空串）
     * @param content    驳回意见（可空）
     * @param userIds    收件人用户 id 列表（空表不发）
     */
    public void sendRefuseTodo(SysProcessExample example, String handlerName, String content,
                               List<String> userIds) {
        send(SysNotifyType.TODO.getValue(),
                renderNotifySubject(refuseSubjectTemplate(), example, handlerName),
                example, content, userIds);
    }

    /* ==================== READ 待阅发送 ==================== */

    /**
     * 发送抄送待阅（READ）：收件人 = 抄送对象（抄送节点解析结果 / 抄送动作显式指定的 ccToIds，
     * 不排除发起人），标题消费公共配置 {@code notify.notify_cc_subject}，内容 = 调用方给定的抄送说明
     * （抄送节点=「流程抄送：{节点名}」，抄送动作=审批意见）。收件人为空时不发送。
     *
     * @param example 流程实例（非空，modelId / 标题占位符取值来源）
     * @param content 抄送说明（写入待阅内容，可空）
     * @param userIds 收件人用户 id 列表（去重保序由调用方保证；空表不发）
     */
    public void sendCcRead(SysProcessExample example, String content, List<String> userIds) {
        sendRead(ccSubjectTemplate(), example, content, userIds);
    }

    /**
     * 发送催办待阅（READ）：标题消费公共配置 {@code notify.notify_expedite_subject}，内容 = 催办意见；
     * 收件人（当前节点全部 PROCESSING 工作项处理人，排除发起人）由调用方
     * （{@code SysProcessExampleService} 催办动作）解析后传入，收件人为空时不发送。
     *
     * @param example 流程实例（非空）
     * @param content 催办意见（可空）
     * @param userIds 收件人用户 id 列表（空表不发）
     */
    public void sendExpediteRead(SysProcessExample example, String content, List<String> userIds) {
        sendRead(expediteSubjectTemplate(), example, content, userIds);
    }

    /**
     * 发送办结起草人待阅（READ，P17-BE-014 通知收口）：流程办结（FINISH 终态）时发送——
     * 引擎图遍历办结（{@code ProcessFlowEngine#enterEnd}）与管理员终审通过
     * （{@code SysProcessExampleService} ADMIN_FINISH）两个办结路径共用；收件人 = 流程起草人
     * （办结为系统事件，不排除任何人）。标题消费公共配置 {@code notify.notify_finish_drafter_subject}
     * （缺省回退默认模板），内容 = 固定文案「流程已办结」（办结无审批意见可携带）。
     * 起草人待阅不受公共配置 {@code finish_notify_handler} 控制，始终发送。收件人为空时不发送。
     *
     * @param example 流程实例（非空）
     * @param userIds 收件人用户 id 列表（空表不发）
     */
    public void sendFinishDrafterRead(SysProcessExample example, List<String> userIds) {
        sendRead(finishDrafterSubjectTemplate(), example, FINISH_READ_CONTENT, userIds);
    }

    /**
     * 发送办结审批人待阅（READ，P17-BE-014 通知收口）：流程办结（FINISH 终态）时发送，收件人 =
     * 本实例「实际审批过」的用户（HANDLER_PASS 审批记录关联工作项的处理人去重保序；
     * 自动跳过者无通过留痕，不在收件人）。标题消费公共配置 {@code notify.notify_finish_handler_subject}
     * （缺省回退默认模板），内容 = 固定文案「流程已办结」。受公共配置
     * {@code finish_notify_handler}（缺省 true）控制，false 时调用方不触发本发送。收件人为空时不发送。
     *
     * @param example 流程实例（非空）
     * @param userIds 收件人用户 id 列表（空表不发）
     */
    public void sendFinishHandlerRead(SysProcessExample example, List<String> userIds) {
        sendRead(finishHandlerSubjectTemplate(), example, FINISH_READ_CONTENT, userIds);
    }

    /** READ 待阅统一出口：标题按模板渲染，收件人为空时静默不发（「仅留痕不发通知」口径由调用方承接）。 */
    private void sendRead(String template, SysProcessExample example, String content, List<String> userIds) {
        send(SysNotifyType.READ.getValue(), renderNotifySubject(template, example), example, content, userIds);
    }

    /**
     * 通知发送统一出口（TODO/READ 共用，P17-BE-014 收口）：来源模块=SYS-PROCESS、modelId=实例 id、
     * 链接=实例详情页；标题由调用方渲染完成传入；收件人为空时静默不发。
     */
    private void send(String notifyType, String subject, SysProcessExample example, String content,
                      List<String> userIds) {
        if (null == userIds || userIds.isEmpty()) {
            return;
        }
        SysNotifyTodo todo = new SysNotifyTodo();
        todo.setFieldName(subject);
        todo.setFieldNotifyType(notifyType);
        todo.setFieldModelName(SysProcessConstants.MODEL_KEY_SYS_PROCESS);
        todo.setFieldModelId(example.getFieldId());
        todo.setFieldUrl(SysProcessConstants.EXAMPLE_DETAIL_URL_PREFIX + example.getFieldId());
        todo.setFieldContent(content);
        notifyTodoService.send(todo, userIds);
    }

    /* ==================== 标题模板 ==================== */

    /** 审批待办固定内容（等价口径，见 {@link #sendApproveTodo} 说明） */
    private static final String APPROVE_TODO_CONTENT = "您有流程审批待办，请及时处理";

    /** 办结待阅固定内容（办结无审批意见可携带） */
    private static final String FINISH_READ_CONTENT = "流程已办结";

    /**
     * 读取审批待办标题模板：公共配置 {@code notify_handle_subject}（notify 类型，既有配置，
     * 可由「流程管理-公共配置」页维护），缺失或为空回退默认模板
     * {@link SysProcessConstants#DEFAULT_NOTIFY_HANDLE_SUBJECT}（与 application.yml SETTING
     * 默认值一致）。
     */
    private String approveSubjectTemplate() {
        String template = settingService.getValue(SysProcessConstants.SETTING_TYPE_NOTIFY,
                SysProcessConstants.SETTING_NOTIFY_HANDLE_SUBJECT);
        return StringUtil.isEmpty(template)
                ? SysProcessConstants.DEFAULT_NOTIFY_HANDLE_SUBJECT : template;
    }

    /**
     * 读取被驳回待办标题模板：公共配置 {@code notify_refuse_subject}（notify 类型，既有配置），
     * 缺失或为空回退默认模板 {@link SysProcessConstants#DEFAULT_NOTIFY_REFUSE_SUBJECT}
     * （与 application.yml SETTING 默认值一致）。
     */
    private String refuseSubjectTemplate() {
        String template = settingService.getValue(SysProcessConstants.SETTING_TYPE_NOTIFY,
                SysProcessConstants.SETTING_NOTIFY_REFUSE_SUBJECT);
        return StringUtil.isEmpty(template)
                ? SysProcessConstants.DEFAULT_NOTIFY_REFUSE_SUBJECT : template;
    }

    /**
     * 读取办结起草人待阅标题模板：公共配置 {@code notify_finish_drafter_subject}（notify 类型，
     * 既有配置），缺失或为空回退默认模板
     * {@link SysProcessConstants#DEFAULT_NOTIFY_FINISH_DRAFTER_SUBJECT}（与 application.yml
     * SETTING 默认值一致）。
     */
    private String finishDrafterSubjectTemplate() {
        String template = settingService.getValue(SysProcessConstants.SETTING_TYPE_NOTIFY,
                SysProcessConstants.SETTING_NOTIFY_FINISH_DRAFTER_SUBJECT);
        return StringUtil.isEmpty(template)
                ? SysProcessConstants.DEFAULT_NOTIFY_FINISH_DRAFTER_SUBJECT : template;
    }

    /**
     * 读取办结审批人待阅标题模板：公共配置 {@code notify_finish_handler_subject}（notify 类型，
     * 既有配置），缺失或为空回退默认模板
     * {@link SysProcessConstants#DEFAULT_NOTIFY_FINISH_HANDLER_SUBJECT}（与 application.yml
     * SETTING 默认值一致）。
     */
    private String finishHandlerSubjectTemplate() {
        String template = settingService.getValue(SysProcessConstants.SETTING_TYPE_NOTIFY,
                SysProcessConstants.SETTING_NOTIFY_FINISH_HANDLER_SUBJECT);
        return StringUtil.isEmpty(template)
                ? SysProcessConstants.DEFAULT_NOTIFY_FINISH_HANDLER_SUBJECT : template;
    }

    /**
     * 读取抄送待阅标题模板：公共配置 {@code notify_cc_subject}（notify 类型，业务配置，
     * 可由「流程管理-公共配置」页维护），缺失或为空回退默认模板
     * {@link SysProcessConstants#DEFAULT_NOTIFY_CC_SUBJECT}（与 application.yml SETTING 默认值一致）。
     */
    private String ccSubjectTemplate() {
        String template = settingService.getValue(SysProcessConstants.SETTING_TYPE_NOTIFY,
                SysProcessConstants.SETTING_NOTIFY_CC_SUBJECT);
        return StringUtil.isEmpty(template)
                ? SysProcessConstants.DEFAULT_NOTIFY_CC_SUBJECT : template;
    }

    /**
     * 读取催办待阅标题模板：公共配置 {@code notify_expedite_subject}（notify 类型，
     * 业务配置，可由「流程管理-公共配置」页维护），缺失或为空回退默认模板
     * {@link SysProcessConstants#DEFAULT_NOTIFY_EXPEDITE_SUBJECT}（与 application.yml
     * SETTING 默认值一致）。
     */
    private String expediteSubjectTemplate() {
        String template = settingService.getValue(SysProcessConstants.SETTING_TYPE_NOTIFY,
                SysProcessConstants.SETTING_NOTIFY_EXPEDITE_SUBJECT);
        return StringUtil.isEmpty(template)
                ? SysProcessConstants.DEFAULT_NOTIFY_EXPEDITE_SUBJECT : template;
    }

    /**
     * 通知标题模板渲染（P17-BE-011 产出、P17-BE-012 迁移收口，P17-BE-014 通知收口复用）：
     * 简单字符串替换占位符，不引入模板引擎。占位符口径与既有 {@code notify_handle_subject}
     * 一致：
     * <ul>
     * <li>{@link SysProcessConstants#NOTIFY_PLACEHOLDER_CREATOR_NAME}：流程起草人姓名，
     *     经用户表按 {@code example.fieldCreatorId} 查询 {@code field_name}，查不到回退空串；</li>
     * <li>{@link SysProcessConstants#NOTIFY_PLACEHOLDER_EXAMPLE_NAME}：流程名称（模板名），
     *     经模板表按 {@code example.fieldTemplateId} 查询 {@code field_name}，加载失败回退
     *     流程编号（见 {@link #resolveExampleName}）。</li>
     * </ul>
     * 模板为空时返回空串。
     *
     * @param template 标题模板（可空）
     * @param example  流程实例（占位符取值来源）
     * @return 渲染后的标题
     */
    String renderNotifySubject(String template, SysProcessExample example) {
        return renderNotifySubject(template, example, null);
    }

    /**
     * 通知标题模板渲染（携带驳回人姓名重载，P17-BE-014）：占位符口径在既有两个占位符
     * （{@code ${fieldCreatorName}} / {@code ${fieldName}}，行为不变）基础上新增可选
     * {@link SysProcessConstants#NOTIFY_PLACEHOLDER_HANDLER_NAME}——被驳回待办标题的驳回人姓名，
     * 由调用方传入；未传入（null）或为空时渲染为空串。
     *
     * @param template    标题模板（可空）
     * @param example     流程实例（占位符取值来源）
     * @param handlerName 驳回人姓名（可空，占位符渲染为空串）
     * @return 渲染后的标题
     */
    String renderNotifySubject(String template, SysProcessExample example, String handlerName) {
        if (StringUtil.isEmpty(template)) {
            return "";
        }
        String creatorName = resolveUserName(example.getFieldCreatorId());
        return template.replace(SysProcessConstants.NOTIFY_PLACEHOLDER_CREATOR_NAME,
                        StringUtil.isEmpty(creatorName) ? "" : creatorName)
                .replace(SysProcessConstants.NOTIFY_PLACEHOLDER_EXAMPLE_NAME, resolveExampleName(example))
                .replace(SysProcessConstants.NOTIFY_PLACEHOLDER_HANDLER_NAME,
                        StringUtil.isEmpty(handlerName) ? "" : handlerName);
    }

    /**
     * 流程名称（标题占位符 {@code ${fieldName}} 取值）：模板名（按实例模板 id 查模板
     * {@code field_name}）；模板缺失或名称为空回退流程编号，再回退空串。
     */
    private String resolveExampleName(SysProcessExample example) {
        SysProcessTemplate template = templateDao.selectById(example.getFieldTemplateId());
        if (null != template && StringUtil.isNotEmpty(template.getFieldName())) {
            return template.getFieldName();
        }
        return StringUtil.isEmpty(example.getFieldCode()) ? "" : example.getFieldCode();
    }

    /** 用户姓名（标题占位符 {@code ${fieldCreatorName}} 取值；查不到返回 null，渲染回退空串）。 */
    private String resolveUserName(String userId) {
        if (StringUtil.isEmpty(userId)) {
            return null;
        }
        SysOrgUser user = userMapper.selectById(userId);
        return null == user ? null : user.getFieldName();
    }
}
