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
 * 流程通知发送收口（P17-BE-012 起，BE-014 通知收口的载体）
 *
 * @author Eric
 * @date 2026/11/20
 * @description P17-BE-012 交付物（依据 docs/v1_7-流程管理/功能实现方案-改造分析与方案设计.md
 *              §5.2 抄送节点、docs/设计文档/sys/process/流程核心业务.md「通知」章节）：
 *              把「READ 待阅发送 + 标题模板渲染」从 {@code SysProcessExampleService}（催办，
 *              P17-BE-011 产出）收口为共享组件，供流程引擎抄送节点（{@code ProcessFlowEngine#enterCc}）、
 *              抄送动作（handler_cc）与催办动作共用；标题占位符渲染逻辑同步迁移到本组件。
 *              <p>后续 P17-BE-014 通知收口（审批待办 TODO + 既有 4 个标题模板消费）在本组件扩展，
 *              引擎与 Service 的通知发送统一经此处出口。</p>
 *
 *              <h3>发送口径</h3>
 *              <ul>
 *              <li>READ 待阅：{@link SysNotifyType#READ}，来源模块
 *                  {@link SysProcessConstants#MODEL_KEY_SYS_PROCESS}，modelId=实例 id，
 *                  链接=实例详情页（现状路由 {@code /processExample/detail/{id}}；Wave 3 审批中心
 *                  调整路由时同步更新）；</li>
 *              <li>标题：按公共配置模板渲染（{@link #renderNotifySubject}），配置缺失或为空回退
 *                  默认模板（与 application.yml SETTING 默认值一致）；</li>
 *              <li>收件人：由调用方给定（抄送为显式指定/解析结果，不排除发起人；催办由 Service
 *                  预先排除发起人），收件人为空时不发送（催办「仅留痕不发通知」口径由调用方承接）；</li>
 *              <li>事务：通知经 {@link SysNotifyTodoService#send} 落库即「待阅可达」（SSE 推送由
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

    /** READ 待阅统一出口：标题按模板渲染，收件人为空时静默不发（「仅留痕不发通知」口径由调用方承接）。 */
    private void sendRead(String template, SysProcessExample example, String content, List<String> userIds) {
        if (null == userIds || userIds.isEmpty()) {
            return;
        }
        SysNotifyTodo todo = new SysNotifyTodo();
        todo.setFieldName(renderNotifySubject(template, example));
        todo.setFieldNotifyType(SysNotifyType.READ.getValue());
        todo.setFieldModelName(SysProcessConstants.MODEL_KEY_SYS_PROCESS);
        todo.setFieldModelId(example.getFieldId());
        todo.setFieldUrl(SysProcessConstants.EXAMPLE_DETAIL_URL_PREFIX + example.getFieldId());
        todo.setFieldContent(content);
        notifyTodoService.send(todo, userIds);
    }

    /* ==================== 标题模板 ==================== */

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
        if (StringUtil.isEmpty(template)) {
            return "";
        }
        String creatorName = resolveUserName(example.getFieldCreatorId());
        return template.replace(SysProcessConstants.NOTIFY_PLACEHOLDER_CREATOR_NAME,
                        StringUtil.isEmpty(creatorName) ? "" : creatorName)
                .replace(SysProcessConstants.NOTIFY_PLACEHOLDER_EXAMPLE_NAME, resolveExampleName(example));
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
