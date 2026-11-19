package com.eric.base.sys.process.service.engine;

import com.eric.base.common.utils.StringUtil;
import com.eric.base.sys.process.dao.SysProcessAuditNoteDao;
import com.eric.base.sys.process.dao.SysProcessExampleDao;
import com.eric.base.sys.process.dao.SysProcessExampleNodeDao;
import com.eric.base.sys.process.dao.SysProcessExampleWorkitemDao;
import com.eric.base.sys.process.model.SysProcessAuditNote;
import com.eric.base.sys.process.model.SysProcessExample;
import com.eric.base.sys.process.model.SysProcessExampleNode;
import com.eric.base.sys.process.model.SysProcessExampleWorkitem;
import com.eric.base.sys.process.properties.SysProcessConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * 流程流转引擎写操作支撑（实例状态 / 节点状态 / 工作项 / 审批留痕）
 *
 * @author Eric
 * @date 2026/11/19
 * @description P17-BE-003 交付物：把流转过程中对实例、实例节点、工作项与审批留痕的
 *              <b>写操作</b>收口为一处，供 {@link ProcessFlowEngine} 与
 *              {@code SysProcessExampleService} 共同复用（避免二者循环依赖）。
 *              <p>只做单行/批量状态落库与留痕写入，不含任何流转决策；审批记录字段口径
 *              与存量实现保持一致（{@code sys_process_audit_note} 只增不改）。</p>
 */
@Component
public class ProcessFlowSupport {

    private static final Logger log = LoggerFactory.getLogger(ProcessFlowSupport.class);

    private final SysProcessExampleDao exampleDao;
    private final SysProcessExampleNodeDao nodeDao;
    private final SysProcessExampleWorkitemDao workitemDao;
    private final SysProcessAuditNoteDao auditNoteDao;
    private final ObjectMapper objectMapper;

    public ProcessFlowSupport(SysProcessExampleDao exampleDao,
                              SysProcessExampleNodeDao nodeDao,
                              SysProcessExampleWorkitemDao workitemDao,
                              SysProcessAuditNoteDao auditNoteDao,
                              ObjectMapper objectMapper) {
        this.exampleDao = exampleDao;
        this.nodeDao = nodeDao;
        this.workitemDao = workitemDao;
        this.auditNoteDao = auditNoteDao;
        this.objectMapper = objectMapper;
    }

    /* ==================== 审批留痕 ==================== */

    /** 生成审批记录（workitemId / content 可空） */
    public void addAudit(String exampleId, String workitemId, String handlerId,
                         String handleType, String content) {
        SysProcessAuditNote note = new SysProcessAuditNote();
        note.initFieldId();
        note.setFieldExampleId(exampleId);
        note.setFieldWorkitemId(workitemId);
        note.setFieldHandlerId(handlerId);
        note.setFieldContent(content);
        note.setFieldCreateTime(new Date());
        note.setFieldHandleType(handleType);
        auditNoteDao.insert(note);
    }

    /* ==================== 实例状态 ==================== */

    /** 实例置审批中 */
    public void markProcessing(SysProcessExample example) {
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);
        exampleDao.update(example);
    }

    /** 实例置完成（记录完成时间） */
    public void markFinished(SysProcessExample example) {
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_FINISH);
        example.setFieldFinishTime(new Date());
        exampleDao.update(example);
    }

    /** 实例置终态（作废等，记录完成时间） */
    public void markEnded(SysProcessExample example, String status) {
        example.setFieldStatus(status);
        example.setFieldFinishTime(new Date());
        exampleDao.update(example);
    }

    /* ==================== 节点状态 ==================== */

    /** 节点置终态并落库 */
    public void updateNodeStatus(SysProcessExampleNode node, String status) {
        node.setFieldStatus(status);
        nodeDao.update(node);
    }

    /** 节点运行态落库（JSON 固定结构序列化；写序列化失败时抛流程异常） */
    public void saveNodeRuntime(SysProcessExampleNode node,
                                com.eric.base.sys.process.model.SysProcessExampleNodeRuntimeDo runtime) {
        try {
            node.setFieldRuntime(objectMapper.writeValueAsString(runtime));
        } catch (Exception e) {
            log.error("序列化节点运行态失败: node={}", node.getFieldId(), e);
            throw ProcessFlowException.system("节点「" + node.getFieldNodeName() + "」运行态序列化失败", e);
        }
        nodeDao.update(node);
    }

    /** 将当前处理中的节点（PROCESSING）置为等待（收回/跳转离开后不再停留） */
    public void markCurrentNodeWaiting(String exampleId) {
        for (SysProcessExampleNode node : nodeDao.selectByExampleAndStatus(exampleId,
                SysProcessConstants.NODE_STATUS_PROCESSING)) {
            if (SysProcessConstants.NODE_TYPE_DRAFT.equals(node.getFieldNodeType())
                    || SysProcessConstants.NODE_TYPE_END.equals(node.getFieldNodeType())) {
                continue;
            }
            node.setFieldStatus(SysProcessConstants.NODE_STATUS_WAITING);
            nodeDao.update(node);
        }
    }

    /* ==================== 工作项 ==================== */

    /** 新建工作项并落库（返回落库后的对象） */
    public SysProcessExampleWorkitem insertWorkitem(SysProcessExample example, SysProcessExampleNode node,
                                                    String handlerId, String status, String configJson) {
        SysProcessExampleWorkitem workitem = new SysProcessExampleWorkitem();
        workitem.initFieldId();
        workitem.setFieldExampleId(example.getFieldId());
        workitem.setFieldNodeId(node.getFieldId());
        workitem.setFieldHandlerId(handlerId);
        workitem.setFieldExpectHandlerId(handlerId);
        workitem.setFieldCreateTime(new Date());
        workitem.setFieldStatus(status);
        workitem.setFieldConfig(configJson);
        if (SysProcessConstants.WORKITEM_STATUS_FINISH.equals(status)) {
            workitem.setFieldFinishTime(new Date());
        }
        workitemDao.insert(workitem);
        return workitem;
    }

    /** 完成单个工作项（置 FINISH 并记录完成时间） */
    public void finishWorkitem(SysProcessExampleWorkitem workitem) {
        workitem.setFieldStatus(SysProcessConstants.WORKITEM_STATUS_FINISH);
        workitem.setFieldFinishTime(new Date());
        workitemDao.update(workitem);
    }

    /** 将该节点其他（未完成）工作项置为完成 */
    public void finishRemainingWorkitems(String nodeId, String exceptWorkitemId) {
        Date now = new Date();
        for (SysProcessExampleWorkitem workitem : workitemDao.selectListByNodeId(nodeId)) {
            if (!SysProcessConstants.WORKITEM_STATUS_FINISH.equals(workitem.getFieldStatus())
                    && (null == exceptWorkitemId || !exceptWorkitemId.equals(workitem.getFieldId()))) {
                workitem.setFieldStatus(SysProcessConstants.WORKITEM_STATUS_FINISH);
                workitem.setFieldFinishTime(now);
                workitemDao.update(workitem);
            }
        }
    }

    /** 废弃处理中（PROCESSING / PAUSE）的工作项（收回/作废/跳转时） */
    public void abandonActiveWorkitems(String exampleId) {
        Date now = new Date();
        for (SysProcessExampleWorkitem workitem : workitemDao.selectActiveByExample(exampleId)) {
            workitem.setFieldStatus(SysProcessConstants.WORKITEM_STATUS_ABANDON);
            workitem.setFieldFinishTime(now);
            workitemDao.update(workitem);
        }
    }

    /** 完成处理中（PROCESSING / PAUSE）的工作项（管理员办结时） */
    public void finishActiveWorkitems(String exampleId) {
        Date now = new Date();
        for (SysProcessExampleWorkitem workitem : workitemDao.selectActiveByExample(exampleId)) {
            workitem.setFieldStatus(SysProcessConstants.WORKITEM_STATUS_FINISH);
            workitem.setFieldFinishTime(now);
            workitemDao.update(workitem);
        }
    }

    /** 暂停/恢复：按工作项原状态在新旧状态间批量切换 */
    public void toggleActiveWorkitems(String exampleId, String fromStatus, String toStatus) {
        for (SysProcessExampleWorkitem workitem : workitemDao.selectActiveByExample(exampleId)) {
            if (fromStatus.equals(workitem.getFieldStatus())) {
                workitem.setFieldStatus(toStatus);
                workitemDao.update(workitem);
            }
        }
    }

    /* ==================== JSON 小件 ==================== */

    /** 对象序列化为 JSON 字符串（失败抛流程异常，禁止手工拼串） */
    public String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw ProcessFlowException.system("流程配置序列化失败", e);
        }
    }

    /** 节点配置 JSON 解析为配置对象（空或解析失败返回 null） */
    public com.eric.base.sys.process.model.SysProcessTemplateNodeConfigDo parseNodeConfig(String json) {
        if (StringUtil.isEmpty(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json,
                    com.eric.base.sys.process.model.SysProcessTemplateNodeConfigDo.class);
        } catch (Exception e) {
            log.warn("parse node config failed, fallback to null: {}", e.getMessage());
            return null;
        }
    }

    /** 工作项配置 JSON 解析为配置对象（空或解析失败返回 null） */
    public com.eric.base.sys.process.model.SysProcessExampleWorkitemConfigDo parseWorkitemConfig(String json) {
        if (StringUtil.isEmpty(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json,
                    com.eric.base.sys.process.model.SysProcessExampleWorkitemConfigDo.class);
        } catch (Exception e) {
            log.warn("parse workitem config failed, fallback to null: {}", e.getMessage());
            return null;
        }
    }

    /** 节点运行态 JSON 解析（空或解析失败返回 null） */
    public com.eric.base.sys.process.model.SysProcessExampleNodeRuntimeDo parseNodeRuntime(String json) {
        if (StringUtil.isEmpty(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json,
                    com.eric.base.sys.process.model.SysProcessExampleNodeRuntimeDo.class);
        } catch (Exception e) {
            log.warn("parse node runtime failed, fallback to null: {}", e.getMessage());
            return null;
        }
    }

    /** 查询节点全部工作项（按创建时间升序） */
    public List<SysProcessExampleWorkitem> workitemsOfNode(String nodeId) {
        return workitemDao.selectListByNodeId(nodeId);
    }
}
