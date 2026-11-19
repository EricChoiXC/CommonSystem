package com.eric.base.sys.process.service.engine;

import com.eric.base.sys.process.dao.SysProcessExampleLineDao;
import com.eric.base.sys.process.model.SysProcessExample;
import com.eric.base.sys.process.model.SysProcessExampleLine;
import com.eric.base.sys.process.model.SysProcessExampleNode;
import com.eric.base.sys.process.properties.SysProcessConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 流程图加载器测试（实例连线快照为唯一来源）
 *
 * @author Eric
 * @date 2026/11/19
 * @description P17-BE-007 交付物：{@link ProcessFlowGraphLoader} 只读实例连线快照
 *              （{@code sys_process_example_line}），实例无连线快照时抛
 *              {@link ProcessFlowException#graph(String)}（「流程实例缺少连线定义: {exampleId}」），
 *              <b>不再回退模板连线</b>（P17-BE-003 的阶段性桥梁已随本任务移除）。
 */
class ProcessFlowGraphLoaderTest {

    private static final String EXAMPLE_ID = "20000000000000000000000000000001";

    private SysProcessExampleLineDao exampleLineDao;
    private ProcessFlowGraphLoader loader;
    private SysProcessExample example;

    @BeforeEach
    void setUp() {
        exampleLineDao = mock(SysProcessExampleLineDao.class);
        loader = new ProcessFlowGraphLoader(exampleLineDao);
        example = new SysProcessExample();
        example.setFieldId(EXAMPLE_ID);
        // 实例仍记录来源模板 id，但加载器不得据此回查模板连线（P17-BE-007）
        example.setFieldTemplateId("20000000000000000000000000000002");
    }

    @Test
    @DisplayName("实例连线快照为空：抛流程异常且不回退模板连线")
    void loadFailsWhenInstanceLineSnapshotAbsent() {
        when(exampleLineDao.selectListByExampleId(EXAMPLE_ID)).thenReturn(List.of());

        ProcessFlowException error = assertThrows(ProcessFlowException.class,
                () -> loader.load(example, nodes()));

        assertEquals(ProcessFlowException.CODE_GRAPH, error.getCode());
        assertEquals("流程实例缺少连线定义: " + EXAMPLE_ID, error.getMessage());
    }

    @Test
    @DisplayName("实例连线返回 null：同样按缺少连线定义处理")
    void loadFailsWhenInstanceLineSnapshotNull() {
        when(exampleLineDao.selectListByExampleId(EXAMPLE_ID)).thenReturn(null);

        ProcessFlowException error = assertThrows(ProcessFlowException.class,
                () -> loader.load(example, nodes()));

        assertEquals("流程实例缺少连线定义: " + EXAMPLE_ID, error.getMessage());
    }

    @Test
    @DisplayName("实例连线快照存在：构图并标记来源为实例快照")
    void loadBuildsGraphFromInstanceLineSnapshot() {
        when(exampleLineDao.selectListByExampleId(EXAMPLE_ID)).thenReturn(List.of(
                line("L1", "N1", "N2", SysProcessConstants.LINE_TYPE_NORMAL, 1, null),
                line("L2", "N2", "N3", SysProcessConstants.LINE_TYPE_CONDITION, 1, "amount > 100"),
                line("L3", "N2", "N4", SysProcessConstants.LINE_TYPE_CONDITION, 2, null)));

        ProcessFlowGraph graph = loader.load(example, nodes());

        assertTrue(graph.fromInstanceSnapshot(), "连线来源恒为实例快照");
        assertEquals(1, graph.outgoing("N1").size());
        assertEquals("N2", graph.outgoing("N1").get(0).toKey());
        // 出边按分支序号升序（CONDITION 分支判断顺序）
        assertEquals(List.of("N3", "N4"), graph.outgoing("N2").stream()
                .map(ProcessFlowLine::toKey).toList());
        assertEquals(1, graph.incoming("N2").size());
        assertEquals(3, graph.nodes().size(), "实例节点 N1/N2/N3 全部入图");
        assertEquals(1, graph.outgoing("N1").get(0).order(), "总线出边保留分支序号");
    }

    private static List<SysProcessExampleNode> nodes() {
        List<SysProcessExampleNode> nodes = new ArrayList<>();
        nodes.add(node("n1", 1, "N1", SysProcessConstants.NODE_TYPE_START));
        nodes.add(node("n2", 2, "N2", SysProcessConstants.NODE_TYPE_DRAFT));
        nodes.add(node("n3", 3, "N3", SysProcessConstants.NODE_TYPE_END));
        return nodes;
    }

    private static SysProcessExampleNode node(String id, int no, String key, String type) {
        SysProcessExampleNode node = new SysProcessExampleNode();
        node.setFieldId(id);
        node.setFieldExampleId(EXAMPLE_ID);
        node.setFieldNodeNo(no);
        node.setFieldNodeKey(key);
        node.setFieldNodeName(type);
        node.setFieldNodeType(type);
        node.setFieldStatus(SysProcessConstants.NODE_STATUS_WAITING);
        return node;
    }

    private static SysProcessExampleLine line(String id, String fromKey, String toKey,
                                              String lineType, int branchOrder, String condition) {
        SysProcessExampleLine line = new SysProcessExampleLine();
        line.setFieldId(id);
        line.setFieldExampleId(EXAMPLE_ID);
        line.setFieldFromKey(fromKey);
        line.setFieldToKey(toKey);
        line.setFieldLineType(lineType);
        line.setFieldBranchOrder(branchOrder);
        line.setFieldCondition(condition);
        line.setFieldIsDefault(false);
        return line;
    }
}
