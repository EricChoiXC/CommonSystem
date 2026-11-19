package com.eric.base.sys.process.service.engine;

import com.eric.base.common.setting.service.CommonSettingService;
import com.eric.base.sys.formula.service.engine.FormulaEngine;
import com.eric.base.sys.process.dao.SysProcessAuditNoteDao;
import com.eric.base.sys.process.dao.SysProcessExampleDao;
import com.eric.base.sys.process.dao.SysProcessExampleLineDao;
import com.eric.base.sys.process.dao.SysProcessExampleNodeDao;
import com.eric.base.sys.process.dao.SysProcessExampleWorkitemDao;
import com.eric.base.sys.process.dao.SysProcessTemplateLineDao;
import com.eric.base.sys.process.model.SysProcessAuditNote;
import com.eric.base.sys.process.model.SysProcessExample;
import com.eric.base.sys.process.model.SysProcessExampleLine;
import com.eric.base.sys.process.model.SysProcessExampleNode;
import com.eric.base.sys.process.model.SysProcessExampleNodeRuntimeDo;
import com.eric.base.sys.process.model.SysProcessExampleWorkitem;
import com.eric.base.sys.process.model.SysProcessExampleWorkitemConfigDo;
import com.eric.base.sys.process.model.SysProcessTemplateLine;
import com.eric.base.sys.process.model.SysProcessTemplateNodeConfigDo;
import com.eric.base.sys.process.properties.SysProcessConstants;
import com.eric.base.sys.process.service.SysProcessDocumentFieldService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 流程流转引擎测试夹具（内存图 + Mock DAO）
 *
 * @author Eric
 * @date 2026/11/19
 * @description P17-BE-003 引擎单测共用夹具：以 Mockito 桩 DAO + 内存节点/连线图装配
 *              {@link ProcessFlowEngine}，断言引擎在图遍历后的节点状态、工作项、审批留痕与
 *              实例状态；不依赖真实数据库与 Spring 容器。
 */
public class FlowEngineFixture {

    static final String EXAMPLE_ID = "10000000000000000000000000000001";
    static final String TEMPLATE_ID = "10000000000000000000000000000002";
    static final String CREATOR = "10000000000000000000000000000003";
    public static final String U1 = "10000000000000000000000000000011";
    static final String U2 = "10000000000000000000000000000012";
    static final String U3 = "10000000000000000000000000000013";

    public final ObjectMapper objectMapper = new ObjectMapper();
    public final SysProcessExampleDao exampleDao = mock(SysProcessExampleDao.class);
    public final SysProcessExampleNodeDao nodeDao = mock(SysProcessExampleNodeDao.class);
    public final SysProcessExampleWorkitemDao workitemDao = mock(SysProcessExampleWorkitemDao.class);
    public final SysProcessAuditNoteDao auditNoteDao = mock(SysProcessAuditNoteDao.class);
    public final SysProcessExampleLineDao exampleLineDao = mock(SysProcessExampleLineDao.class);
    public final SysProcessTemplateLineDao templateLineDao = mock(SysProcessTemplateLineDao.class);
    public final CommonSettingService settingService = mock(CommonSettingService.class);
    public final ProcessHandlerResolver handlerResolver = mock(ProcessHandlerResolver.class);
    public final SysProcessDocumentFieldService documentFieldService = mock(SysProcessDocumentFieldService.class);
    public final FormulaEngine formulaEngine = mock(FormulaEngine.class);

    public final SysProcessExample example = new SysProcessExample();
    public final List<SysProcessExampleNode> nodes = new ArrayList<>();
    public final List<SysProcessExampleLine> lines = new ArrayList<>();
    public final List<SysProcessTemplateLine> templateLines = new ArrayList<>();
    public final List<SysProcessExampleWorkitem> workitems = new ArrayList<>();
    public final List<SysProcessAuditNote> auditNotes = new ArrayList<>();

    private ObjectProvider<ProcessTaskExecutor> taskExecutorProvider;
    private ProcessFlowEngine engine;

    public FlowEngineFixture() {
        example.setFieldId(EXAMPLE_ID);
        example.setFieldTemplateId(TEMPLATE_ID);
        example.setFieldModelName("sys_org");
        example.setFieldModelId("model-1");
        example.setFieldCreatorId(CREATOR);
        example.setFieldStatus(SysProcessConstants.EXAMPLE_STATUS_PROCESSING);

        when(exampleDao.selectById(EXAMPLE_ID)).thenReturn(example);
        when(exampleDao.update(any(SysProcessExample.class))).thenReturn(1);
        when(nodeDao.selectListByExampleId(EXAMPLE_ID)).thenAnswer(invocation -> List.copyOf(nodes));
        when(nodeDao.update(any(SysProcessExampleNode.class))).thenReturn(1);
        when(nodeDao.selectByExampleAndStatus(anyString(), anyString())).thenReturn(List.of());
        when(nodeDao.selectByExampleAndType(anyString(), anyString())).thenAnswer(invocation -> {
            String type = invocation.getArgument(1);
            for (SysProcessExampleNode node : nodes) {
                if (type.equals(node.getFieldNodeType())) {
                    return node;
                }
            }
            return null;
        });
        when(workitemDao.insert(any(SysProcessExampleWorkitem.class))).thenAnswer(invocation -> {
            workitems.add(invocation.getArgument(0));
            return 1;
        });
        when(workitemDao.update(any(SysProcessExampleWorkitem.class))).thenReturn(1);
        when(workitemDao.selectListByNodeId(anyString())).thenAnswer(invocation -> {
            String nodeId = invocation.getArgument(0);
            List<SysProcessExampleWorkitem> matched = new ArrayList<>();
            for (SysProcessExampleWorkitem workitem : workitems) {
                if (nodeId.equals(workitem.getFieldNodeId())) {
                    matched.add(workitem);
                }
            }
            return matched;
        });
        when(workitemDao.selectListByExampleId(EXAMPLE_ID)).thenAnswer(invocation -> List.copyOf(workitems));
        when(workitemDao.selectActiveByExample(EXAMPLE_ID)).thenAnswer(invocation -> {
            List<SysProcessExampleWorkitem> matched = new ArrayList<>();
            for (SysProcessExampleWorkitem workitem : workitems) {
                if (SysProcessConstants.WORKITEM_STATUS_PROCESSING.equals(workitem.getFieldStatus())
                        || SysProcessConstants.WORKITEM_STATUS_PAUSE.equals(workitem.getFieldStatus())) {
                    matched.add(workitem);
                }
            }
            return matched;
        });
        when(auditNoteDao.insert(any(SysProcessAuditNote.class))).thenAnswer(invocation -> {
            SysProcessAuditNote note = invocation.getArgument(0);
            if (null == note.getFieldCreateTime()) {
                note.setFieldCreateTime(new Date());
            }
            auditNotes.add(note);
            return 1;
        });
        when(auditNoteDao.selectListByExampleId(EXAMPLE_ID)).thenAnswer(invocation -> List.copyOf(auditNotes));
        when(exampleLineDao.selectListByExampleId(EXAMPLE_ID)).thenAnswer(invocation ->
                lines.isEmpty() ? null : List.copyOf(lines));
        when(templateLineDao.selectListByTemplateId(TEMPLATE_ID)).thenAnswer(invocation ->
                List.copyOf(templateLines));
        when(settingService.getBooleanValue(eq(SysProcessConstants.SETTING_TYPE_PROCESS), anyString()))
                .thenReturn(true);
        when(handlerResolver.resolveHandlerIds(any(), any(), any())).thenAnswer(invocation ->
                resolveFromConfig(invocation.getArgument(0)));
        when(handlerResolver.resolveCcIds(any(), any(), any())).thenAnswer(invocation -> {
            SysProcessTemplateNodeConfigDo config = invocation.getArgument(0);
            return null == config ? List.of() : splitIds(config.getCcIds());
        });
        if (Boolean.getBoolean("flow.fixture.debug")) {
            when(handlerResolver.resolveHandlerIds(any(), any(), any())).thenAnswer(invocation -> {
                SysProcessTemplateNodeConfigDo config = invocation.getArgument(0);
                List<String> ids = resolveFromConfig(config);
                System.out.println("[fixture] resolveHandlerIds config=" + config + " -> " + ids);
                return ids;
            });
        }
    }

    /** 装配引擎（taskExecutor=null 表示 P17-BE-005 未提供实现） */
    public ProcessFlowEngine engine(ProcessTaskExecutor executor) {
        ProcessFlowSupport support = new ProcessFlowSupport(exampleDao, nodeDao, workitemDao,
                auditNoteDao, objectMapper);
        ProcessFlowGraphLoader loader = new ProcessFlowGraphLoader(exampleLineDao, templateLineDao);
        this.taskExecutorProvider = null == executor ? null : providerOf(executor);
        engine = new ProcessFlowEngine(exampleDao, nodeDao, workitemDao, auditNoteDao, settingService,
                loader, support, handlerResolver, documentFieldService, formulaEngine, taskExecutorProvider);
        return engine;
    }

    /** 按节点配置解析审批人：nodeConfig 的 handlerIds 或 handlerField（文档字段） */
    private List<String> resolveFromConfig(SysProcessTemplateNodeConfigDo config) {
        if (null == config) {
            return List.of();
        }
        if (SysProcessConstants.HANDLER_SOURCE_FIELD.equals(config.getHandlerSource())) {
            String value = documentFieldService.resolveFieldValue("sys_org", "model-1", config.getHandlerField());
            return splitIds(value);
        }
        return splitIds(config.getHandlerIds());
    }

    /* ==================== 图构建 ==================== */

    /** 新增实例节点（键固定为 N{no}） */
    public SysProcessExampleNode node(int no, String name, String type) {
        return node(no, name, type, null);
    }

    public SysProcessExampleNode node(int no, String name, String type, String configJson) {
        SysProcessExampleNode node = new SysProcessExampleNode();
        node.setFieldId("node-" + no);
        node.setFieldExampleId(EXAMPLE_ID);
        node.setFieldNodeNo(no);
        node.setFieldNodeKey("N" + no);
        node.setFieldNodeName(name);
        node.setFieldNodeType(type);
        node.setFieldNodeConfig(configJson);
        node.setFieldStatus(SysProcessConstants.NODE_STATUS_WAITING);
        nodes.add(node);
        // 与 exampleDao 同口径：按 id 可取回已构建节点（引擎按 id 读取节点写入任务留痕）
        when(nodeDao.selectById(node.getFieldId())).thenReturn(node);
        return node;
    }

    /** 新增实例连线（总线型：NORMAL） */
    public SysProcessExampleLine line(String fromKey, String toKey) {
        return line(fromKey, toKey, SysProcessConstants.LINE_TYPE_NORMAL, null, null, 1);
    }

    /** 新增实例连线（完整参数） */
    public SysProcessExampleLine line(String fromKey, String toKey, String lineType, String condition,
                               Boolean isDefault, int branchOrder) {
        SysProcessExampleLine line = new SysProcessExampleLine();
        line.setFieldId("line-" + fromKey + "-" + toKey);
        line.setFieldExampleId(EXAMPLE_ID);
        line.setFieldFromKey(fromKey);
        line.setFieldToKey(toKey);
        line.setFieldLineType(lineType);
        line.setFieldCondition(condition);
        line.setFieldIsDefault(isDefault);
        line.setFieldBranchOrder(branchOrder);
        lines.add(line);
        return line;
    }

    /** 新增模板连线（实例无连线快照时的回退来源） */
    SysProcessTemplateLine templateLine(String fromKey, String toKey) {
        SysProcessTemplateLine line = new SysProcessTemplateLine();
        line.setFieldId("tline-" + fromKey + "-" + toKey);
        line.setFieldTemplateId(TEMPLATE_ID);
        line.setFieldFromKey(fromKey);
        line.setFieldToKey(toKey);
        line.setFieldLineType(SysProcessConstants.LINE_TYPE_NORMAL);
        line.setFieldBranchOrder(1);
        return line;
    }

    public SysProcessExampleNode nodeByKey(String nodeKey) {
        for (SysProcessExampleNode node : nodes) {
            if (nodeKey.equals(node.getFieldNodeKey())) {
                return node;
            }
        }
        throw new IllegalStateException("no node with key " + nodeKey);
    }

    /* ==================== 节点配置 JSON ==================== */

    /** 审批节点配置：地址本审批人 + 审批模式 */
    public static String approveConfig(String approveMode, String handlerIds) {
        return "{\"handlerIds\":\"" + handlerIds + "\",\"approveMode\":\"" + approveMode
                + "\",\"autoSkip\":\"no\",\"autoContinueSkip\":\"system\"}";
    }

    /** 审批节点配置（表单字段审批源） */
    static String fieldApproveConfig(String handlerField) {
        return "{\"handlerSource\":\"field\",\"handlerField\":\"" + handlerField
                + "\",\"approveMode\":\"single\",\"autoSkip\":\"no\"}";
    }

    /** 节点配置 JSON 构造（key/value 均按字符串写入；v1_7 键 skipMode/emptyHandlerMode 等） */
    static String configOf(String... keyValues) {
        StringBuilder json = new StringBuilder("{");
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            if (i > 0) {
                json.append(',');
            }
            json.append('"').append(keyValues[i]).append("\":\"").append(keyValues[i + 1]).append('"');
        }
        return json.append('}').toString();
    }

    /** 抄送节点配置：地址本抄送对象 */
    static String ccConfig(String ccIds) {
        return "{\"ccIds\":\"" + ccIds + "\"}";
    }

    /** 机器人节点配置：一个流程任务明细 */
    public static String robortConfig(String taskBean) {
        return "{\"tasks\":[{\"taskName\":\"流程任务\",\"taskBean\":\"" + taskBean
                + "\",\"taskType\":\"StartNode\"}]}";
    }

    /** 并行开始节点配置：汇聚模式 + 无可流转分支行为 */
    static String forkConfig(String joinMode, String noBranchMode) {
        return "{\"joinMode\":\"" + joinMode + "\",\"noBranchMode\":\"" + noBranchMode + "\"}";
    }

    /** 人工决策节点配置：默认流向 */
    static String manualConfig(String defaultFlowKey) {
        return "{\"defaultFlowKey\":\"" + defaultFlowKey + "\"}";
    }

    /* ==================== 断言辅助 ==================== */

    /** 指定节点（按节点键）的全部工作项 */
    public List<SysProcessExampleWorkitem> workitemsOf(String nodeKey) {
        String nodeId = nodeByKey(nodeKey).getFieldId();
        List<SysProcessExampleWorkitem> matched = new ArrayList<>();
        for (SysProcessExampleWorkitem workitem : workitems) {
            if (nodeId.equals(workitem.getFieldNodeId())) {
                matched.add(workitem);
            }
        }
        return matched;
    }

    /** 指定节点（按节点 id）的全部工作项 */
    List<SysProcessExampleWorkitem> workitemsOfNodeId(String nodeId) {
        List<SysProcessExampleWorkitem> matched = new ArrayList<>();
        for (SysProcessExampleWorkitem workitem : workitems) {
            if (nodeId.equals(workitem.getFieldNodeId())) {
                matched.add(workitem);
            }
        }
        return matched;
    }

    /** 指定节点状态 */
    public String statusOf(String nodeKey) {
        return nodeByKey(nodeKey).getFieldStatus();
    }

    /** 指定类型审批记录的条数 */
    public long auditCount(String handleType) {
        return auditNotes.stream().filter(note -> handleType.equals(note.getFieldHandleType())).count();
    }

    /** 指定类型审批记录的内容集合 */
    public List<String> auditContents(String handleType) {
        List<String> contents = new ArrayList<>();
        for (SysProcessAuditNote note : auditNotes) {
            if (handleType.equals(note.getFieldHandleType())) {
                contents.add(note.getFieldContent());
            }
        }
        return contents;
    }

    /** 节点运行态（JSON 解析） */
    SysProcessExampleNodeRuntimeDo runtimeOf(String nodeKey) throws Exception {
        String json = nodeByKey(nodeKey).getFieldRuntime();
        return null == json ? null : objectMapper.readValue(json, SysProcessExampleNodeRuntimeDo.class);
    }

    /** 通过指定节点（模拟审批人通过：完成该节点在途工作项后交由引擎继续流转） */
    ProcessFlowResult passNode(String nodeKey) {
        SysProcessExampleNode node = nodeByKey(nodeKey);
        Date now = new Date();
        String passedWorkitemId = null;
        for (SysProcessExampleWorkitem workitem : workitemsOf(nodeKey)) {
            if (SysProcessConstants.WORKITEM_STATUS_PROCESSING.equals(workitem.getFieldStatus())) {
                workitem.setFieldStatus(SysProcessConstants.WORKITEM_STATUS_FINISH);
                workitem.setFieldFinishTime(now);
                if (null == passedWorkitemId) {
                    passedWorkitemId = workitem.getFieldId();
                }
            }
        }
        node.setFieldStatus(SysProcessConstants.NODE_STATUS_PROCESSING);
        auditNote(SysProcessConstants.AUDIT_TYPE_HANDLER_PASS, passedWorkitemId);
        return engine(null).pass(example, nodeKey, node.getFieldNodeName(), passedWorkitemId);
    }

    /** 新增工作项（指定节点 id 与人工决策流向 flowChoice；工作项 id 由 initFieldId 生成） */
    SysProcessExampleWorkitem workitem(String nodeId, String flowChoice) {
        SysProcessExampleWorkitem workitem = new SysProcessExampleWorkitem();
        workitem.initFieldId();
        workitem.setFieldExampleId(EXAMPLE_ID);
        workitem.setFieldNodeId(nodeId);
        workitem.setFieldHandlerId(U1);
        workitem.setFieldExpectHandlerId(U1);
        workitem.setFieldStatus(SysProcessConstants.WORKITEM_STATUS_FINISH);
        workitem.setFieldCreateTime(new Date());
        setFlowChoice(workitem, flowChoice);
        workitems.add(workitem);
        return workitem;
    }

    /** 构造并登记审批记录（供人工决策流向回溯） */
    SysProcessAuditNote auditNote(String handleType, String workitemId) {
        SysProcessAuditNote note = new SysProcessAuditNote();
        note.setFieldId("audit-" + auditNotes.size());
        note.setFieldExampleId(EXAMPLE_ID);
        note.setFieldHandlerId(U1);
        note.setFieldWorkitemId(workitemId);
        note.setFieldHandleType(handleType);
        note.setFieldCreateTime(new Date(System.currentTimeMillis() + auditNotes.size()));
        auditNotes.add(note);
        return note;
    }

    /** 为工作项写入人工决策流向（模拟审批人通过时附带 flowChoice） */
    void setFlowChoice(SysProcessExampleWorkitem workitem, String flowChoice) {
        SysProcessExampleWorkitemConfigDo config = new SysProcessExampleWorkitemConfigDo();
        config.setFlowChoice(flowChoice);
        try {
            workitem.setFieldConfig(objectMapper.writeValueAsString(config));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> splitIds(String value) {
        if (null == value || value.isBlank()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (String id : value.split(";")) {
            if (!id.isBlank() && !ids.contains(id.trim())) {
                ids.add(id.trim());
            }
        }
        return ids;
    }

    private static ObjectProvider<ProcessTaskExecutor> providerOf(ProcessTaskExecutor executor) {
        return new ObjectProvider<>() {
            @Override
            public ProcessTaskExecutor getObject(Object... args) {
                return executor;
            }

            @Override
            public ProcessTaskExecutor getIfAvailable() {
                return executor;
            }

            @Override
            public ProcessTaskExecutor getIfUnique() {
                return executor;
            }

            @Override
            public ProcessTaskExecutor getObject() {
                return executor;
            }
        };
    }
}
