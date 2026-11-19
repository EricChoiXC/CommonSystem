package com.eric.base.sys.process.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.eric.base.sys.process.dao.SysProcessTemplateDao;
import com.eric.base.sys.process.dao.SysProcessTemplateHistoryDao;
import com.eric.base.sys.process.dao.SysProcessTemplateLineDao;
import com.eric.base.sys.process.dao.SysProcessTemplateNodeDao;
import com.eric.base.sys.process.model.SysProcessTemplate;
import com.eric.base.sys.process.model.SysProcessTemplateLine;
import com.eric.base.sys.process.model.SysProcessTemplateNode;
import com.eric.base.sys.process.response.SysProcessTemplateLineVo;
import com.eric.base.sys.process.response.SysProcessTemplateNodeVo;
import com.eric.base.sys.process.response.SysProcessTemplateVo;
import com.eric.base.sys.process.task.ProcessTaskRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 流程模板服务端图校验专项测试（G1~G10）与 save/update/load 契约
 *
 * @author Eric
 * @date 2026/11/19
 * @description 覆盖 docs/设计文档/sys/process/流程v1_7接口清单.md §4.3/§4.4：
 *              <ul>
 *              <li>G1 首尾连通（N1 沿连线可达 N3）；G2 无孤立节点；</li>
 *              <li>G3 各节点出边数约束（含「有中间节点时草稿节点出边连向其后继」口径）；</li>
 *              <li>G4 条件分支默认边唯一；G5 FORK/JOIN 成对且单层；G6 MANUAL 默认流向合法；</li>
 *              <li>G7 审批 operations 必含且仅含一个 handler_pass；G8 节点 tasks 经 ProcessTaskRegistry 校验；</li>
 *              <li>G9 rejectTargets、G10 flowDecisions 候选限定；</li>
 *              <li>连线与契约：lines 引用不存在的 nodeKey 拒绝、连线全量替换、update 归档含连线快照、
 *                  load 返回 nodes（含 nodeKey）与 lines。</li>
 *              </ul>
 *              装配风格对齐 {@link SysProcessTemplateServiceTest}；本类仅新增用例，不改既有测试与主实现。
 */
class SysProcessTemplateGraphValidationTest {

    private static final String MODEL_NAME = "sys_org";
    private static final String TEMPLATE_ID = "t".repeat(32);

    private SysProcessTemplateDao templateDao;
    private SysProcessTemplateNodeDao nodeDao;
    private SysProcessTemplateLineDao lineDao;
    private SysProcessTemplateHistoryDao historyDao;
    private ProcessTaskRegistry processTaskRegistry;
    private SysProcessTemplateService service;

    @BeforeEach
    void setUp() {
        templateDao = mock(SysProcessTemplateDao.class);
        nodeDao = mock(SysProcessTemplateNodeDao.class);
        lineDao = mock(SysProcessTemplateLineDao.class);
        historyDao = mock(SysProcessTemplateHistoryDao.class);
        processTaskRegistry = mock(ProcessTaskRegistry.class);
        service = new SysProcessTemplateService(templateDao, nodeDao, lineDao, historyDao,
                processTaskRegistry, new ObjectMapper());
        when(lineDao.selectListByTemplateId(anyString())).thenReturn(List.of());
        when(nodeDao.selectListByTemplateId(anyString())).thenReturn(List.of());
        SysProcessTemplate saved = new SysProcessTemplate();
        saved.setFieldId(TEMPLATE_ID);
        saved.setFieldModelName(MODEL_NAME);
        when(templateDao.selectById(anyString())).thenReturn(saved);
    }

    /* ==================== G1 首尾连通 ==================== */

    @Test
    void saveWithNoLineRejectedByNodeOutDegreeBeforeConnectivity() {
        // 无连线：先由 G3 判定「开始节点必须有且仅有一条出边」，图校验逐节点先行
        SysProcessTemplateVo vo = vo(linearNodes(), List.of());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.save(vo));

        assertTrue(error.getMessage().contains("出边数不合法"), "无连线应先由 G3 拒绝: " + error.getMessage());
    }

    @Test
    void saveWithLoopNotReachingEndRejectedByStartEndConnectivity() {
        // 出边数全部合法（START=1、DRAFT=1、审批=1、END=0）但 N3 不可达：N4 出边指回 N2 形成无用环
        List<SysProcessTemplateNodeVo> nodes = linearNodes();
        nodes.add(1, approveNode("审批节点4"));
        SysProcessTemplateVo vo = vo(nodes, List.of(line("N1", "N2"), line("N2", "N4"), line("N4", "N2")));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.save(vo));

        assertTrue(error.getMessage().contains("首尾不连通"), "G1 未通过时应提示首尾不连通: " + error.getMessage());
    }

    @Test
    void saveWithDeadEndBeforeEndRejectedByConnectivity() {
        // 出边数全部合法（START=1、DRAFT=1、审批=1、CC=1、END=0）但 N3 不可达：中间链路进入回环 N5↔N6
        List<SysProcessTemplateNodeVo> nodes = linearNodes();
        nodes.add(1, approveNode("审批节点4"));
        nodes.add(2, ccNode("抄送节点5"));
        nodes.add(3, ccNode("抄送节点6"));
        SysProcessTemplateVo vo = vo(nodes, List.of(line("N1", "N2"), line("N2", "N4"),
                line("N4", "N5"), line("N5", "N6"), line("N6", "N5")));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.save(vo));

        assertTrue(error.getMessage().contains("首尾不连通"), "G1 不可达时应提示首尾不连通: " + error.getMessage());
    }

    @Test
    void saveWithConnectedFixedNodesAccepted() {
        SysProcessTemplateVo vo = vo(linearNodes(), List.of(line("N1", "N2"), line("N2", "N3")));

        service.save(vo);

        verify(lineDao).insertBatch(anyString(), argThat(lines -> 2 == lines.size()
                && "N1".equals(lines.get(0).getFieldFromKey()) && "N2".equals(lines.get(0).getFieldToKey())
                && "N2".equals(lines.get(1).getFieldFromKey()) && "N3".equals(lines.get(1).getFieldToKey())));
    }

    /* ==================== G2 无孤立节点 ==================== */

    @Test
    void saveWithIsolatedMiddleNodeRejected() {
        // 人工决策节点 N4 既无入边也无出边（该类型出边数仅要求 ≥1，G3 不先行拒绝，由 G2 以孤立节点拒绝）
        List<SysProcessTemplateNodeVo> nodes = linearNodes();
        nodes.add(1, manualNode("孤立人工决策4"));
        SysProcessTemplateVo vo = vo(nodes, List.of(line("N1", "N2"), line("N2", "N3")));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.save(vo));

        assertTrue(error.getMessage().contains("孤立节点"), "G2 未通过时应提示孤立节点: " + error.getMessage());
    }

    /* ==================== G3 出边数约束 ==================== */

    @Test
    void saveWithTwoStartOutLinesRejected() {
        // 开始节点出边必须唯一且指向 N2：N1 两条出边（一条指向 N4）
        List<SysProcessTemplateNodeVo> nodes = linearNodes();
        nodes.add(1, approveNode("审批节点4"));
        SysProcessTemplateVo vo = vo(nodes, List.of(line("N1", "N2"), line("N1", "N4"),
                line("N2", "N4"), line("N4", "N3")));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.save(vo));

        assertTrue(error.getMessage().contains("开始节点（START）"), "G3 开始节点出边数: " + error.getMessage());
    }

    @Test
    void saveWithStartOutLineNotPointingToDraftRejected() {
        // N1 出边唯一但目标不是草稿节点 N2
        List<SysProcessTemplateNodeVo> nodes = linearNodes();
        nodes.add(1, approveNode("审批节点4"));
        SysProcessTemplateVo vo = vo(nodes, List.of(line("N1", "N4"), line("N2", "N4"), line("N4", "N3")));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.save(vo));

        assertTrue(error.getMessage().contains("指向草稿节点 N2"), "G3 开始节点出边目标: " + error.getMessage());
    }

    @Test
    void saveWithDraftWithoutOutLineRejected() {
        // 草稿节点必须有且仅有一条出边：N2 无出边（存在中间节点，跳过「默认指向 N3」分支）
        List<SysProcessTemplateNodeVo> nodes = linearNodes();
        nodes.add(1, approveNode("审批节点4"));
        SysProcessTemplateVo vo = vo(nodes, List.of(line("N1", "N2"), line("N4", "N3")));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.save(vo));

        assertTrue(error.getMessage().contains("草稿节点（DRAFT）"), "G3 草稿节点出边数: " + error.getMessage());
    }

    @Test
    void saveWithDraftTwoOutLinesRejected() {
        // 草稿节点出边必须唯一：N2 两条出边
        List<SysProcessTemplateNodeVo> nodes = linearNodes();
        nodes.add(1, approveNode("审批节点4"));
        nodes.add(2, approveNode("审批节点5"));
        SysProcessTemplateVo vo = vo(nodes, List.of(line("N1", "N2"), line("N2", "N4"), line("N2", "N5"),
                line("N4", "N5"), line("N5", "N3")));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.save(vo));

        assertTrue(error.getMessage().contains("草稿节点（DRAFT）"), "G3 草稿节点出边数: " + error.getMessage());
    }

    @Test
    void saveWithDraftOutLineToMiddleNodeAccepted() {
        // 回归：含中间节点时草稿节点出边连向后续链路（不强制直达 N3），由 G1/G2 保证无断链、无孤立
        List<SysProcessTemplateNodeVo> nodes = linearNodes();
        nodes.add(1, approveNode("审批节点4"));
        SysProcessTemplateVo vo = vo(nodes, List.of(line("N1", "N2"), line("N2", "N4"), line("N4", "N3")));

        service.save(vo);

        verify(lineDao).insertBatch(anyString(), argThat(lines -> 3 == lines.size()
                && "N2".equals(lines.get(1).getFieldFromKey()) && "N4".equals(lines.get(1).getFieldToKey())));
    }

    /* ==================== G3 出边数约束（其余节点类型） ==================== */

    @Test
    void saveWithApproveNodeWithoutOutLineRejected() {
        // APPROVE 出边必须唯一：N4 无出边、N5 出边指向 N3（保证 N3 有入边）
        List<SysProcessTemplateNodeVo> nodes = linearNodes();
        nodes.add(1, approveNode("审批节点4"));
        nodes.add(2, approveNode("审批节点5"));
        SysProcessTemplateVo vo = vo(nodes, List.of(line("N1", "N2"), line("N2", "N4"), line("N5", "N3")));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.save(vo));

        assertTrue(error.getMessage().contains("出边数不合法"), "G3 审批节点出边数: " + error.getMessage());
    }

    @Test
    void saveWithTwoApproveOutLinesRejected() {
        // APPROVE 出边必须唯一：N4 两条出边
        List<SysProcessTemplateNodeVo> nodes = linearNodes();
        nodes.add(1, approveNode("审批节点4"));
        nodes.add(2, approveNode("审批节点5"));
        SysProcessTemplateVo vo = vo(nodes, List.of(line("N1", "N2"), line("N2", "N4"), line("N4", "N5"),
                line("N4", "N3"), line("N5", "N3")));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.save(vo));

        assertTrue(error.getMessage().contains("出边数不合法"), "G3 审批节点出边数: " + error.getMessage());
    }

    @Test
    void saveWithTwoCcOutLinesRejected() {
        // CC 出边必须唯一：N4（抄送）两条出边
        List<SysProcessTemplateNodeVo> nodes = linearNodes();
        nodes.add(1, ccNode("抄送节点4"));
        SysProcessTemplateVo vo = vo(nodes, List.of(line("N1", "N2"), line("N2", "N4"), line("N4", "N3"),
                line("N4", "N2")));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.save(vo));

        assertTrue(error.getMessage().contains("出边数不合法"), "G3 抄送节点出边数: " + error.getMessage());
    }

    @Test
    void saveWithRobotNodeWithoutOutLineRejected() {
        // ROBORT 出边必须唯一：N4（机器人）无出边
        List<SysProcessTemplateNodeVo> nodes = linearNodes();
        nodes.add(1, robotNode("机器人节点4"));
        nodes.add(2, approveNode("审批节点5"));
        SysProcessTemplateVo vo = vo(nodes, List.of(line("N1", "N2"), line("N2", "N4"), line("N5", "N3")));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.save(vo));

        assertTrue(error.getMessage().contains("出边数不合法"), "G3 机器人节点出边数: " + error.getMessage());
    }

    /* ==================== G3 出边数约束（非成对节点：END / CONDITION / FORK / MANUAL） ==================== */

    @Test
    void saveWithEndOutLineRejected() {
        // END 不允许有出边：N3→N2
        SysProcessTemplateVo vo = vo(linearNodes(), List.of(line("N1", "N2"), line("N2", "N3"), line("N3", "N2")));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.save(vo));

        assertTrue(error.getMessage().contains("结束节点（END）不允许有出边"), "G3 结束节点出边: " + error.getMessage());
    }

    @Test
    void saveWithConditionNoOutLineRejectedByIsolation() {
        // CONDITION 零出边且零入边：最简图形下由 G2 孤立节点提示（G2 先于 G3 出边数判定）
        List<SysProcessTemplateNodeVo> nodes = linearNodes();
        nodes.add(1, plainNode("CONDITION", "条件分支4"));
        SysProcessTemplateVo vo = vo(nodes, List.of(line("N1", "N2"), line("N2", "N3")));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.save(vo));

        assertTrue(error.getMessage().contains("孤立节点"), "G2 孤立节点判定: " + error.getMessage());
    }

    @Test
    void saveWithConditionOutLineMissingDefaultBranchRejected() {
        // G4：CONDITION 出边无一条默认分支
        List<SysProcessTemplateNodeVo> nodes = linearNodes();
        nodes.add(1, plainNode("CONDITION", "条件分支4"));
        nodes.add(2, approveNode("审批节点5"));
        SysProcessTemplateVo vo = vo(nodes, List.of(line("N1", "N2"), line("N2", "N4"), line("N4", "N5"),
                line("N4", "N3"), line("N5", "N3")));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.save(vo));

        assertTrue(error.getMessage().contains("默认分支"), "G4 默认分支唯一: " + error.getMessage());
    }

    @Test
    void saveWithConditionTwoDefaultBranchesRejected() {
        // G4：CONDITION 出边有两条默认分支
        List<SysProcessTemplateNodeVo> nodes = linearNodes();
        nodes.add(1, plainNode("CONDITION", "条件分支4"));
        nodes.add(2, approveNode("审批节点5"));
        SysProcessTemplateVo vo = vo(nodes, List.of(line("N1", "N2"), line("N2", "N4"),
                defaultLine("N4", "N5"), defaultLine("N4", "N3"), line("N5", "N3")));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.save(vo));

        assertTrue(error.getMessage().contains("默认分支"), "G4 默认分支唯一: " + error.getMessage());
    }

    /* ==================== G5 FORK/JOIN 成对与单层 ==================== */

    @Test
    void saveWithSingleDefaultBranchAccepted() {
        // G4 合法对照：CONDITION 两条出边、恰一条默认分支，两条分支均可达 N3
        List<SysProcessTemplateNodeVo> nodes = linearNodes();
        nodes.add(1, plainNode("CONDITION", "条件分支4"));
        nodes.add(2, approveNode("审批节点5"));
        SysProcessTemplateVo vo = vo(nodes, List.of(line("N1", "N2"), line("N2", "N4"),
                defaultLine("N4", "N3"), line("N4", "N5"), line("N5", "N3")));

        service.save(vo);

        verify(lineDao).insertBatch(anyString(), argThat(lines -> 5 == lines.size()
                && Boolean.TRUE.equals(lines.get(2).getFieldIsDefault())));
    }

    @Test
    void saveWithSingleLevelForkJoinAccepted() {
        // 单层并行：FORK 分支分别经审批与抄送汇聚到同一 JOIN
        List<SysProcessTemplateNodeVo> nodes = linearNodes();
        nodes.add(1, plainNode("FORK", "并行开始4"));
        nodes.add(2, approveNode("审批节点5"));
        nodes.add(3, ccNode("抄送节点6"));
        nodes.add(4, plainNode("JOIN", "并行结束7"));
        SysProcessTemplateVo vo = vo(nodes, List.of(line("N1", "N2"), line("N2", "N4"),
                line("N4", "N5"), line("N4", "N6"), line("N5", "N7"), line("N6", "N7"), line("N7", "N3")));

        service.save(vo);

        verify(lineDao).insertBatch(anyString(), argThat(lines -> 7 == lines.size()
                && "PARALLEL".equals(lines.get(2).getFieldLineType())));
    }

    @Test
    void saveWithUnpairedJoinRejected() {
        // JOIN 无对应 FORK
        List<SysProcessTemplateNodeVo> nodes = linearNodes();
        nodes.add(1, plainNode("JOIN", "并行结束4"));
        SysProcessTemplateVo vo = vo(nodes, List.of(line("N1", "N2"), line("N2", "N4"), line("N4", "N3")));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.save(vo));

        assertTrue(error.getMessage().contains("并行结束节点（N4）没有对应的并行开始节点"),
                "G5 未成对: " + error.getMessage());
    }

    @Test
    void saveWithUnpairedForkRejected() {
        // FORK 分支未汇聚到 JOIN
        List<SysProcessTemplateNodeVo> nodes = linearNodes();
        nodes.add(1, plainNode("FORK", "并行开始4"));
        nodes.add(2, approveNode("审批节点5"));
        SysProcessTemplateVo vo = vo(nodes, List.of(line("N1", "N2"), line("N2", "N4"),
                line("N4", "N5"), line("N4", "N3"), line("N5", "N3")));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.save(vo));

        assertTrue(error.getMessage().contains("未汇聚到并行结束节点（JOIN）"), "G5 未汇聚: " + error.getMessage());
    }

    @Test
    void saveWithNestedForkRejected() {
        // 嵌套并行：外层 FORK（N4）分支可达内层 FORK（N8），但两处并行都汇聚到同一 JOIN（N6）
        List<SysProcessTemplateNodeVo> nodes = linearNodes();
        nodes.add(1, plainNode("FORK", "并行开始4"));
        nodes.add(2, approveNode("审批节点5"));
        nodes.add(3, plainNode("JOIN", "并行结束6"));
        nodes.add(4, plainNode("FORK", "并行开始7"));
        nodes.add(5, approveNode("审批节点8"));
        SysProcessTemplateVo vo = vo(nodes, List.of(line("N1", "N2"), line("N2", "N4"),
                line("N4", "N5"), line("N4", "N3"), line("N4", "N6"), line("N5", "N6"), line("N6", "N7"),
                line("N7", "N8"), line("N7", "N6"), line("N8", "N6")));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.save(vo));

        assertTrue(error.getMessage().contains("嵌套"), "G5 嵌套并行: " + error.getMessage());
    }

    @Test
    void saveWithSecondForkInsideFirstBranchRejected() {
        // 第二个 FORK 位于第一个 FORK 的分支上：按嵌套并行拒绝（嵌套判定先于「多 FORK 认领同一 JOIN」判定）
        // 出边数全部合法（START=1、DRAFT=1、FORK=2、JOIN=1、APPROVE=1、END=0），JOIN 出边经审批节点回到 N5
        List<SysProcessTemplateNodeVo> nodes = linearNodes();
        nodes.add(1, plainNode("FORK", "并行开始4"));
        nodes.add(2, plainNode("FORK", "并行开始5"));
        nodes.add(3, plainNode("JOIN", "并行结束6"));
        nodes.add(4, approveNode("审批节点7"));
        SysProcessTemplateVo vo = vo(nodes, List.of(line("N1", "N2"),
                line("N2", "N4"), line("N4", "N5"), line("N4", "N6"), line("N5", "N4"),
                line("N6", "N7"), line("N7", "N5"), line("N5", "N3")));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.save(vo));

        assertTrue(error.getMessage().contains("嵌套了另一个并行开始节点（FORK）"), "G5 嵌套并行: " + error.getMessage());
    }

    @Test
    void saveWithJoinTwoOutLinesRejected() {
        // JOIN 出边必须唯一
        List<SysProcessTemplateNodeVo> nodes = linearNodes();
        nodes.add(1, plainNode("FORK", "并行开始4"));
        nodes.add(2, approveNode("审批节点5"));
        nodes.add(3, approveNode("审批节点6"));
        nodes.add(4, plainNode("JOIN", "并行结束7"));
        SysProcessTemplateVo vo = vo(nodes, List.of(line("N1", "N2"),
                line("N2", "N4"), line("N4", "N5"), line("N4", "N6"), line("N5", "N7"), line("N6", "N7"),
                line("N7", "N3"), line("N7", "N5")));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.save(vo));

        assertTrue(error.getMessage().contains("并行结束节点（JOIN）必须有且仅有一条出边"),
                "G3 JOIN 出边数: " + error.getMessage());
    }

    /* ==================== G6 MANUAL 默认流向 ==================== */

    @Test
    void saveWithManualDefaultFlowNotAnOutTargetRejected() {
        // G6：MANUAL 的 defaultFlowKey 必须是其实际出边目标之一（出边指向 N3，默认流向却写 N4）
        List<SysProcessTemplateNodeVo> nodes = linearNodes();
        SysProcessTemplateNodeVo manual = manualNode("人工决策4");
        manual.setDefaultFlowKey("N4");
        nodes.add(1, manual);
        SysProcessTemplateVo vo = vo(nodes, List.of(line("N1", "N2"), line("N2", "N4"), line("N4", "N3")));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.save(vo));

        assertTrue(error.getMessage().contains("不是其实际出边目标之一"), "G6 默认流向: " + error.getMessage());
    }

    @Test
    void saveWithManualDefaultFlowEmptyRejected() {
        // G6：MANUAL 的 defaultFlowKey 不能为空
        List<SysProcessTemplateNodeVo> nodes = linearNodes();
        SysProcessTemplateNodeVo manual = manualNode("人工决策4");
        manual.setDefaultFlowKey(null);
        nodes.add(1, manual);
        SysProcessTemplateVo vo = vo(nodes, List.of(line("N1", "N2"), line("N2", "N4"), line("N4", "N3")));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.save(vo));

        assertTrue(error.getMessage().contains("默认流向不能为空"), "G6 默认流向必填: " + error.getMessage());
    }

    @Test
    void saveWithManualDefaultFlowOnOutTargetAccepted() {
        // G6 合法对照：默认流向即其唯一出边目标 N3
        List<SysProcessTemplateNodeVo> nodes = linearNodes();
        nodes.add(1, manualNode("人工决策4"));
        SysProcessTemplateVo vo = vo(nodes, List.of(line("N1", "N2"), line("N2", "N4"), line("N4", "N3")));

        service.save(vo);

        verify(nodeDao).insertBatch(anyString(), argThat(saved -> saved.get(2).getFieldNodeConfig()
                .contains("\"defaultFlowKey\":\"N3\"")));
    }

    /* ==================== G7 审批节点 operations ==================== */

    @Test
    void saveWithTwoHandlerPassOperationsRejected() {
        // G7：审批节点必须包含且仅包含一个 handler_pass，此处两个
        List<SysProcessTemplateNodeVo> nodes = linearNodes();
        SysProcessTemplateNodeVo approve = approveNode("审批节点4");
        approve.setOperations(List.of(operation("handler_pass", "通过"), operation("handler_pass", "同意")));
        nodes.add(1, approve);
        SysProcessTemplateVo vo = vo(nodes, List.of(line("N1", "N2"), line("N2", "N4"), line("N4", "N3")));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.save(vo));

        assertTrue(error.getMessage().contains("handler_pass"), "G7 通过操作唯一: " + error.getMessage());
    }

    @Test
    void saveWithOperationWithoutHandlerPassRejected() {
        // G7：审批节点 operations 缺少 handler_pass（显式给出非空 operations，服务端不再补默认通过操作）
        List<SysProcessTemplateNodeVo> nodes = linearNodes();
        SysProcessTemplateNodeVo approve = approveNode("审批节点4");
        approve.setOperations(List.of(operation("handler_reject", "驳回")));
        nodes.add(1, approve);
        SysProcessTemplateVo vo = vo(nodes, List.of(line("N1", "N2"), line("N2", "N4"), line("N4", "N3")));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.save(vo));

        assertTrue(error.getMessage().contains("通过操作（handler_pass）"), "G7 缺通过操作: " + error.getMessage());
    }

    @Test
    void saveWithSingleHandlerPassOperationAccepted() {
        // G7 合法对照：operations 恰含一个 handler_pass
        List<SysProcessTemplateNodeVo> nodes = linearNodes();
        SysProcessTemplateNodeVo approve = approveNode("审批节点4");
        approve.setOperations(List.of(operation("handler_reject", "驳回"), operation("handler_pass", "通过")));
        nodes.add(1, approve);
        SysProcessTemplateVo vo = vo(nodes, List.of(line("N1", "N2"), line("N2", "N4"), line("N4", "N3")));

        service.save(vo);

        verify(nodeDao).insertBatch(anyString(), argThat(saved -> saved.get(2).getFieldNodeConfig()
                .contains("\"operateType\":\"handler_pass\"")));
    }

    /* ==================== 工具方法 ==================== */

    /** 请求体：模板基本信息 + 指定节点 + 指定连线（不自动补连线，图校验用例自行构造）。 */
    private static SysProcessTemplateVo vo(List<SysProcessTemplateNodeVo> nodes, List<SysProcessTemplateLineVo> lines) {
        SysProcessTemplateVo vo = new SysProcessTemplateVo();
        vo.setFieldName("采购订单审批模板");
        vo.setFieldModelName(MODEL_NAME);
        vo.setFieldCode("PO-APPROVE-001");
        vo.setNodes(nodes);
        vo.setLines(lines);
        return vo;
    }

    /** 固定节点：开始 START（归一 N1）、草稿 DRAFT（归一 N2）、结束 END（归一 N3）。 */
    private static List<SysProcessTemplateNodeVo> linearNodes() {
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

    /** 审批节点：审批源/审批人/审批模式等按默认值填写，operations 缺省由服务端补默认通过操作（满足 G7）。 */
    private static SysProcessTemplateNodeVo approveNode(String name) {
        SysProcessTemplateNodeVo vo = new SysProcessTemplateNodeVo();
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

    /** 抄送节点：抄送源默认地址本，抄送对象必填（否则节点配置校验先行拒绝）。 */
    private static SysProcessTemplateNodeVo ccNode(String name) {
        SysProcessTemplateNodeVo vo = new SysProcessTemplateNodeVo();
        vo.setFieldNodeName(name);
        vo.setFieldNodeType("CC");
        vo.setCcSource("org");
        vo.setCcIds("user-9");
        vo.setCcNames("王五");
        return vo;
    }

    /** 无节点配置的中间节点（条件/并行开始/并行结束/机器人），供图校验用例构造分支结构。 */
    private static SysProcessTemplateNodeVo plainNode(String type, String name) {
        SysProcessTemplateNodeVo vo = new SysProcessTemplateNodeVo();
        vo.setFieldNodeType(type);
        vo.setFieldNodeName(name);
        return vo;
    }

    /** 机器人节点（ROBORT 沿用历史拼写，无节点级配置）。 */
    private static SysProcessTemplateNodeVo robotNode(String name) {
        return plainNode("ROBORT", name);
    }

    /** 人工决策节点：默认流向 + 流向决策来源，供 G6/G10 用例按需覆写。 */
    private static SysProcessTemplateNodeVo manualNode(String name) {
        SysProcessTemplateNodeVo vo = plainNode("MANUAL", name);
        vo.setDefaultFlowKey("N3");
        return vo;
    }

    private static SysProcessTemplateLineVo line(String fromKey, String toKey) {
        SysProcessTemplateLineVo vo = new SysProcessTemplateLineVo();
        vo.setFieldFromKey(fromKey);
        vo.setFieldToKey(toKey);
        return vo;
    }

    /** 默认分支线（条件分支节点出边，G4 判定用）。 */
    private static SysProcessTemplateLineVo defaultLine(String fromKey, String toKey) {
        SysProcessTemplateLineVo vo = line(fromKey, toKey);
        vo.setFieldIsDefault(Boolean.TRUE);
        return vo;
    }

    /** 审批操作明细行（G7 用例构造 operations）。 */
    private static SysProcessTemplateNodeVo.OperationDetailVo operation(String operateType, String operateName) {
        SysProcessTemplateNodeVo.OperationDetailVo vo = new SysProcessTemplateNodeVo.OperationDetailVo();
        vo.setOperateType(operateType);
        vo.setOperateName(operateName);
        return vo;
    }

    /** 流程任务明细行（G8 用例构造 tasks）。 */
    private static SysProcessTemplateNodeVo.TaskDetailVo task(String taskBean, String taskType) {
        SysProcessTemplateNodeVo.TaskDetailVo vo = new SysProcessTemplateNodeVo.TaskDetailVo();
        vo.setTaskName("流程任务");
        vo.setTaskBean(taskBean);
        vo.setTaskType(taskType);
        return vo;
    }
}
