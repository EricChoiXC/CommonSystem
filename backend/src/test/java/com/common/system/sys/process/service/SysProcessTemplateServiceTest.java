package com.common.system.sys.process.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.common.system.sys.process.dao.SysProcessTemplateDao;
import com.common.system.sys.process.dao.SysProcessTemplateHistoryDao;
import com.common.system.sys.process.dao.SysProcessTemplateNodeDao;
import com.common.system.sys.process.model.SysProcessTemplate;
import com.common.system.sys.process.model.SysProcessTemplateHistory;
import com.common.system.sys.process.model.SysProcessTemplateNode;
import com.common.system.sys.process.response.SysProcessTemplateNodeVo;
import com.common.system.sys.process.response.SysProcessTemplateVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 流程模板业务服务测试
 *
 * @author Eric
 * @date 2026/8/22
 * @description 覆盖模板维护核心业务规则（依据 artificial/docs/设计文档/sys/process/流程核心业务.md「流程模板」）：
 *              固定节点规则与编号归一（结束节点编号随节点创建往后顺延）、审批节点配置校验
 *              （审批人必填、审批模式/节点触发/自动跳过取值、触发条件判断公式）、
 *              编辑时历史版本归档与版本号递增。
 */
class SysProcessTemplateServiceTest {

    private static final String MODEL_NAME = "sys_org";

    private SysProcessTemplateDao templateDao;
    private SysProcessTemplateNodeDao nodeDao;
    private SysProcessTemplateHistoryDao historyDao;
    private SysProcessTemplateService service;

    @BeforeEach
    void setUp() {
        templateDao = mock(SysProcessTemplateDao.class);
        nodeDao = mock(SysProcessTemplateNodeDao.class);
        historyDao = mock(SysProcessTemplateHistoryDao.class);
        service = new SysProcessTemplateService(templateDao, nodeDao, historyDao, new ObjectMapper());
    }

    /* ==================== 新建：固定节点与编号归一 ==================== */

    @Test
    void saveWithOnlyFixedNodesKeepsNo123() {
        SysProcessTemplateVo vo = templateVo(defaultFixedNodes());

        service.save(vo);

        verify(templateDao).insert(argThat(t -> MODEL_NAME.equals(t.getFieldModelName())
                && Integer.valueOf(1).equals(t.getFieldVersion())));
        verify(nodeDao).insertBatch(anyString(), argThat(nodes -> {
            if (nodes.size() != 3) {
                return false;
            }
            return nodes.get(0).getFieldNo() == 1
                    && "START".equals(nodes.get(0).getFieldNodeType())
                    && nodes.get(1).getFieldNo() == 2
                    && "DRAFT".equals(nodes.get(1).getFieldNodeType())
                    && nodes.get(2).getFieldNo() == 3
                    && "END".equals(nodes.get(2).getFieldNodeType())
                    && nodes.stream().allMatch(n -> null == n.getFieldNodeConfig());
        }));
    }

    @Test
    void saveWithApproveNodesShiftsEndNoBackward() {
        // 添加 2 个审批节点后：开始 1、草稿 2、审批 3、审批 4、结束 5（结束节点编号随创建顺延）
        List<SysProcessTemplateNodeVo> nodes = defaultFixedNodes();
        nodes.add(1, approveNode(null, "审批节点3"));
        nodes.add(2, approveNode(null, "审批节点4"));
        SysProcessTemplateVo vo = templateVo(nodes);

        service.save(vo);

        verify(nodeDao).insertBatch(anyString(), argThat(saved -> {
            if (saved.size() != 5) {
                return false;
            }
            List<Integer> nos = saved.stream().map(SysProcessTemplateNode::getFieldNo).toList();
            return List.of(1, 2, 3, 4, 5).equals(nos)
                    && "APPROVE".equals(saved.get(2).getFieldNodeType())
                    && "APPROVE".equals(saved.get(3).getFieldNodeType())
                    && "END".equals(saved.get(4).getFieldNodeType())
                    && saved.get(2).getFieldNodeConfig().contains("\"approveMode\":\"single\"");
        }));
    }

    @Test
    void saveWithoutFixedNodesRejected() {
        SysProcessTemplateVo vo = templateVo(List.of(approveNode(null, "审批节点")));
        assertThrows(IllegalArgumentException.class, () -> service.save(vo));
    }

    @Test
    void saveWithDuplicateStartNodeRejected() {
        List<SysProcessTemplateNodeVo> nodes = defaultFixedNodes();
        nodes.add(fixedNode("START"));
        SysProcessTemplateVo vo = templateVo(nodes);
        assertThrows(IllegalArgumentException.class, () -> service.save(vo));
    }

    /* ==================== 新建：审批节点配置校验 ==================== */

    @Test
    void saveApproveNodeWithoutHandlersRejected() {
        List<SysProcessTemplateNodeVo> nodes = defaultFixedNodes();
        nodes.add(1, approveNode(null, "审批节点3"));
        nodes.get(1).setHandlerIds("");
        SysProcessTemplateVo vo = templateVo(nodes);
        assertThrows(IllegalArgumentException.class, () -> service.save(vo));
    }

    @Test
    void saveWithInvalidApproveModeRejected() {
        List<SysProcessTemplateNodeVo> nodes = defaultFixedNodes();
        nodes.add(1, approveNode(null, "审批节点3"));
        nodes.get(1).setApproveMode("anyone");
        SysProcessTemplateVo vo = templateVo(nodes);
        assertThrows(IllegalArgumentException.class, () -> service.save(vo));
    }

    @Test
    void saveWithInvalidAutoSkipRejected() {
        List<SysProcessTemplateNodeVo> nodes = defaultFixedNodes();
        nodes.add(1, approveNode(null, "审批节点3"));
        nodes.get(1).setAutoSkip("sometimes");
        SysProcessTemplateVo vo = templateVo(nodes);
        assertThrows(IllegalArgumentException.class, () -> service.save(vo));
    }

    @Test
    void saveWithInvalidAutoContinueSkipRejected() {
        List<SysProcessTemplateNodeVo> nodes = defaultFixedNodes();
        nodes.add(1, approveNode(null, "审批节点3"));
        nodes.get(1).setAutoContinueSkip("always");
        SysProcessTemplateVo vo = templateVo(nodes);
        assertThrows(IllegalArgumentException.class, () -> service.save(vo));
    }

    @Test
    void saveWithConditionTriggerButEmptyFormulaRejected() {
        List<SysProcessTemplateNodeVo> nodes = defaultFixedNodes();
        SysProcessTemplateNodeVo approve = approveNode(null, "审批节点3");
        approve.setTriggerType("condition");
        approve.setCondition("");
        nodes.add(1, approve);
        SysProcessTemplateVo vo = templateVo(nodes);
        assertThrows(IllegalArgumentException.class, () -> service.save(vo));
    }

    @Test
    void saveWithNonFormulaConditionRejected() {
        List<SysProcessTemplateNodeVo> nodes = defaultFixedNodes();
        SysProcessTemplateNodeVo approve = approveNode(null, "审批节点3");
        approve.setTriggerType("condition");
        approve.setCondition("随便填写的文本");
        nodes.add(1, approve);
        SysProcessTemplateVo vo = templateVo(nodes);
        assertThrows(IllegalArgumentException.class, () -> service.save(vo));
    }

    @Test
    void saveWithFormulaConditionAccepted() {
        List<SysProcessTemplateNodeVo> nodes = defaultFixedNodes();
        SysProcessTemplateNodeVo approve = approveNode(null, "审批节点3");
        approve.setTriggerType("condition");
        approve.setCondition("amount > 1000");
        nodes.add(1, approve);
        SysProcessTemplateVo vo = templateVo(nodes);

        service.save(vo);

        verify(nodeDao).insertBatch(anyString(), argThat(saved ->
                saved.get(2).getFieldNodeConfig().contains("\"triggerType\":\"condition\"")
                        && saved.get(2).getFieldNodeConfig().contains("\"condition\":\"amount > 1000\"")));
    }

    @Test
    void saveWithFieldSourceAcceptsConfiguredField() {
        List<SysProcessTemplateNodeVo> nodes = defaultFixedNodes();
        SysProcessTemplateNodeVo approve = approveNode(null, "审批节点3");
        approve.setHandlerSource("field");
        approve.setHandlerField("fieldManagerId");
        approve.setHandlerIds(null);
        approve.setHandlerNames(null);
        nodes.add(1, approve);
        SysProcessTemplateVo vo = templateVo(nodes);

        service.save(vo);

        verify(nodeDao).insertBatch(anyString(), argThat(saved -> {
            String config = saved.get(2).getFieldNodeConfig();
            return config.contains("\"handlerSource\":\"field\"")
                    && config.contains("\"handlerField\":\"fieldManagerId\"");
        }));
    }

    @Test
    void saveWithFieldSourceWithoutFieldRejected() {
        List<SysProcessTemplateNodeVo> nodes = defaultFixedNodes();
        SysProcessTemplateNodeVo approve = approveNode(null, "审批节点3");
        approve.setHandlerSource("field");
        approve.setHandlerField(null);
        approve.setHandlerIds(null);
        nodes.add(1, approve);
        SysProcessTemplateVo vo = templateVo(nodes);

        assertThrows(IllegalArgumentException.class, () -> service.save(vo));
    }

    @Test
    void saveWithFieldSourceUnknownFieldRejected() {
        List<SysProcessTemplateNodeVo> nodes = defaultFixedNodes();
        SysProcessTemplateNodeVo approve = approveNode(null, "审批节点3");
        approve.setHandlerSource("field");
        approve.setHandlerField("notConfiguredField");
        approve.setHandlerIds(null);
        nodes.add(1, approve);
        SysProcessTemplateVo vo = templateVo(nodes);

        assertThrows(IllegalArgumentException.class, () -> service.save(vo));
    }

    @Test
    void saveWithExistingFieldIdRejected() {
        String templateId = "t".repeat(32);
        SysProcessTemplate exist = new SysProcessTemplate();
        exist.setFieldId(templateId);
        when(templateDao.selectById(templateId)).thenReturn(exist);
        SysProcessTemplateVo vo = templateVo(defaultFixedNodes());
        vo.setFieldId(templateId);

        assertThrows(IllegalArgumentException.class, () -> service.save(vo));
    }

    /* ==================== 编辑：历史归档与版本号 ==================== */

    @Test
    void updateArchivesHistoryAndBumpsVersion() {
        String templateId = "t".repeat(32);
        SysProcessTemplate exist = new SysProcessTemplate();
        exist.setFieldId(templateId);
        exist.setFieldName("旧模板");
        exist.setFieldModelName(MODEL_NAME);
        exist.setFieldVersion(1);
        exist.setFieldEnable(true);
        when(templateDao.selectById(templateId)).thenReturn(exist);
        when(nodeDao.selectListByTemplateId(templateId)).thenReturn(List.of());
        SysProcessTemplateVo vo = templateVo(defaultFixedNodes());
        vo.setFieldId(templateId);
        vo.setFieldName("新模板");

        service.update(vo);

        verify(historyDao).insert(argThat(h -> templateId.equals(h.getFieldTemplateId())
                && Integer.valueOf(1).equals(h.getFieldVersion())
                && h.getFieldContent().contains("\"fieldName\":\"旧模板\"")));
        verify(templateDao).update(argThat(t -> Integer.valueOf(2).equals(t.getFieldVersion())
                && "新模板".equals(t.getFieldName())));
        verify(nodeDao).deleteByTemplateId(templateId);
    }

    @Test
    void updateWithChangedModelNameRejected() {
        String templateId = "t".repeat(32);
        SysProcessTemplate exist = new SysProcessTemplate();
        exist.setFieldId(templateId);
        exist.setFieldName("旧模板");
        exist.setFieldModelName(MODEL_NAME);
        exist.setFieldVersion(1);
        exist.setFieldEnable(true);
        when(templateDao.selectById(templateId)).thenReturn(exist);
        SysProcessTemplateVo vo = templateVo(defaultFixedNodes());
        vo.setFieldId(templateId);
        vo.setFieldModelName("another_model");

        assertThrows(IllegalArgumentException.class, () -> service.update(vo));
    }

    /* ==================== 详情 ==================== */

    @Test
    void loadReturnsNullWhenTemplateAbsent() {
        when(templateDao.selectById(anyString())).thenReturn(null);
        assertNull(service.load("not-exist"));
    }

    @Test
    void loadParsesNodeConfigFromJson() {
        String templateId = "t".repeat(32);
        SysProcessTemplate template = new SysProcessTemplate();
        template.setFieldId(templateId);
        template.setFieldName("模板A");
        template.setFieldModelName(MODEL_NAME);
        template.setFieldVersion(3);
        template.setFieldEnable(true);
        when(templateDao.selectById(templateId)).thenReturn(template);

        SysProcessTemplateNode node = new SysProcessTemplateNode();
        node.setFieldId("n".repeat(32));
        node.setFieldTemplateId(templateId);
        node.setFieldNo(3);
        node.setFieldNodeName("审批节点3");
        node.setFieldNodeType("APPROVE");
        node.setFieldNodeConfig("{\"handlerIds\":\"user-1;user-2\",\"handlerNames\":\"张三;李四\","
                + "\"approveMode\":\"single\",\"triggerType\":\"condition\","
                + "\"condition\":\"amount > 1000\",\"autoSkip\":\"system\",\"autoContinueSkip\":\"no\"}");
        when(nodeDao.selectListByTemplateId(templateId)).thenReturn(List.of(node));

        SysProcessTemplateVo vo = service.load(templateId);

        assertEquals(1, vo.getNodes().size());
        SysProcessTemplateNodeVo nodeVo = vo.getNodes().get(0);
        assertEquals(3, nodeVo.getFieldNo());
        assertEquals("user-1;user-2", nodeVo.getHandlerIds());
        assertEquals("张三;李四", nodeVo.getHandlerNames());
        assertEquals("single", nodeVo.getApproveMode());
        assertEquals("condition", nodeVo.getTriggerType());
        assertEquals("amount > 1000", nodeVo.getCondition());
        assertEquals("system", nodeVo.getAutoSkip());
        assertEquals("no", nodeVo.getAutoContinueSkip());
    }

    /* ==================== 置为默认模板 ==================== */

    @Test
    void setDefaultReplacesExistingDefault() {
        String templateId = "t".repeat(32);
        SysProcessTemplate exist = new SysProcessTemplate();
        exist.setFieldId(templateId);
        exist.setFieldName("审批模板A");
        exist.setFieldModelName(MODEL_NAME);
        exist.setFieldEnable(true);
        exist.setFieldDefault(false);
        when(templateDao.selectById(templateId)).thenReturn(exist);

        service.setDefault(templateId);

        // 置为默认=替换：先清同分类旧默认，再置目标为默认
        verify(templateDao).clearDefaultByModelName(MODEL_NAME);
        verify(templateDao).update(argThat(t -> templateId.equals(t.getFieldId())
                && Boolean.TRUE.equals(t.getFieldDefault())));
    }

    @Test
    void setDefaultAlreadyDefaultNoOp() {
        String templateId = "t".repeat(32);
        SysProcessTemplate exist = new SysProcessTemplate();
        exist.setFieldId(templateId);
        exist.setFieldModelName(MODEL_NAME);
        exist.setFieldEnable(true);
        exist.setFieldDefault(true);
        when(templateDao.selectById(templateId)).thenReturn(exist);

        service.setDefault(templateId);

        verify(templateDao, never()).clearDefaultByModelName(anyString());
        verify(templateDao, never()).update(any());
    }

    @Test
    void setDefaultDisabledTemplateRejected() {
        String templateId = "t".repeat(32);
        SysProcessTemplate exist = new SysProcessTemplate();
        exist.setFieldId(templateId);
        exist.setFieldEnable(false);
        when(templateDao.selectById(templateId)).thenReturn(exist);

        assertThrows(IllegalArgumentException.class, () -> service.setDefault(templateId));
    }

    /* ==================== 工具方法 ==================== */

    private static SysProcessTemplateVo templateVo(List<SysProcessTemplateNodeVo> nodes) {
        SysProcessTemplateVo vo = new SysProcessTemplateVo();
        vo.setFieldName("采购订单审批模板");
        vo.setFieldModelName(MODEL_NAME);
        vo.setFieldCode("PO-APPROVE-001");
        vo.setNodes(nodes);
        return vo;
    }

    private static List<SysProcessTemplateNodeVo> defaultFixedNodes() {
        List<SysProcessTemplateNodeVo> nodes = new ArrayList<>();
        nodes.add(fixedNode("START"));
        nodes.add(fixedNode("DRAFT"));
        nodes.add(fixedNode("END"));
        return nodes;
    }

    private static SysProcessTemplateNodeVo fixedNode(String type) {
        SysProcessTemplateNodeVo vo = new SysProcessTemplateNodeVo();
        vo.setFieldNodeType(type);
        return vo;
    }

    private static SysProcessTemplateNodeVo approveNode(String id, String name) {
        SysProcessTemplateNodeVo vo = new SysProcessTemplateNodeVo();
        vo.setFieldId(id);
        vo.setFieldNodeName(name);
        vo.setFieldNodeType("APPROVE");
        vo.setHandlerIds("user-1;user-2");
        vo.setHandlerNames("张三;李四");
        vo.setApproveMode("single");
        vo.setTriggerType("fixed");
        vo.setAutoSkip("system");
        vo.setAutoContinueSkip("system");
        return vo;
    }
}
