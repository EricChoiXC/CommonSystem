package com.eric.base.sys.process.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流程模板节点配置 Do v2 扩展与旧值兼容归一测试
 *
 * @author Eric
 * @date 2026/11/19
 * @description 覆盖节点配置 JSON 反序列化契约（依据 docs/设计文档/sys/process/流程核心业务.md
 *              「节点配置弹窗」与 docs/设计文档/sys/process/流程v1_7接口清单.md「nodes[] 元素」）：
 *              旧 JSON（autoSkip 以 yes/no 表达）反序列化归一为 skipMode 的 prev/none 且语义等价、
 *              不报错；新 JSON（v2 全字段）完整解析；缺省字段带默认值；
 *              嵌套于历史版本内容（SysProcessTemplateHistoryContentDo）时同样归一。
 */
class SysProcessTemplateNodeConfigDoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /* ==================== 旧 JSON 兼容归一（autoSkip yes/no → skipMode prev/none） ==================== */

    @Test
    void legacyAutoSkipYesReadsAsPrev() throws Exception {
        String json = "{\"handlerSource\":\"org\",\"handlerIds\":\"user-1;user-2\","
                + "\"handlerNames\":\"张三;李四\",\"approveMode\":\"single\","
                + "\"triggerType\":\"fixed\",\"autoSkip\":\"yes\",\"autoContinueSkip\":\"no\","
                + "\"ops\":{\"attachment\":true}}";

        SysProcessTemplateNodeConfigDo config = objectMapper.readValue(json, SysProcessTemplateNodeConfigDo.class);

        // 读取归一：yes → prev
        assertEquals("prev", config.getSkipMode());
        // 存量字段原值保留（存量 JSON 不改写）
        assertEquals("yes", config.getAutoSkip());
        assertEquals("org", config.getHandlerSource());
        assertEquals("user-1;user-2", config.getHandlerIds());
        assertEquals("single", config.getApproveMode());
        assertEquals("fixed", config.getTriggerType());
        assertEquals("no", config.getAutoContinueSkip());
        assertEquals(Boolean.TRUE, config.getOps().get("attachment"));
    }

    @Test
    void legacyAutoSkipNoReadsAsNone() throws Exception {
        String json = "{\"handlerSource\":\"org\",\"handlerIds\":\"user-1\",\"autoSkip\":\"no\"}";

        SysProcessTemplateNodeConfigDo config = objectMapper.readValue(json, SysProcessTemplateNodeConfigDo.class);

        assertEquals("none", config.getSkipMode());
        assertEquals("no", config.getAutoSkip());
    }

    @Test
    void legacyAutoSkipSystemReadsAsSystem() throws Exception {
        String json = "{\"handlerSource\":\"org\",\"handlerIds\":\"user-1\",\"autoSkip\":\"system\"}";

        SysProcessTemplateNodeConfigDo config = objectMapper.readValue(json, SysProcessTemplateNodeConfigDo.class);

        assertEquals("system", config.getSkipMode());
    }

    @Test
    void legacyJsonWithoutAutoSkipReadsAsDefaultSystem() throws Exception {
        String json = "{\"handlerSource\":\"field\",\"handlerField\":\"fieldOwner\"}";

        SysProcessTemplateNodeConfigDo config = objectMapper.readValue(json, SysProcessTemplateNodeConfigDo.class);

        assertEquals("system", config.getSkipMode());
        assertNull(config.getAutoSkip());
    }

    /* ==================== 缺省值 ==================== */

    @Test
    void emptyConfigUsesDefaults() throws Exception {
        SysProcessTemplateNodeConfigDo config =
                objectMapper.readValue("{}", SysProcessTemplateNodeConfigDo.class);

        assertEquals("system", config.getSkipMode());
        assertEquals("system", config.getEmptyHandlerMode());
        assertEquals("leader", config.getHandlerResolveMode());
        assertTrue(config.getRejectTargets().isEmpty());
        assertTrue(config.getOperations().isEmpty());
        assertTrue(config.getTasks().isEmpty());
        assertTrue(config.getFlowDecisions().isEmpty());
        assertNull(config.getHandlerFormula());
        assertNull(config.getApproveMode());
        assertNull(config.getAutoSkip());
    }

    /* ==================== 新 JSON 全字段解析 ==================== */

    @Test
    void newJsonFullFieldsParse() throws Exception {
        String json = "{"
                + "\"handlerSource\":\"formula\",\"handlerFormula\":\"${ownerLeader}\","
                + "\"handlerResolveMode\":\"member\","
                + "\"handlerIds\":\"dept-1;user-1\",\"handlerNames\":\"研发部;张三\","
                + "\"approveMode\":\"serial\",\"skipMode\":\"any\",\"autoContinueSkip\":\"yes\","
                + "\"emptyHandlerMode\":\"error\","
                + "\"rejectTargets\":[\"N2\",\"N4\"],"
                + "\"operations\":["
                + "{\"operateType\":\"handler_pass\",\"operateName\":\"通过\",\"defaultContent\":\"同意\"},"
                + "{\"operateType\":\"handler_reject\",\"operateName\":\"拒绝\",\"defaultContent\":\"不同意\"}"
                + "],"
                + "\"tasks\":["
                + "{\"taskName\":\"归档\",\"taskBean\":\"documentArchive\",\"taskType\":\"EndNode\",\"params\":{\"keep\":true}}"
                + "],"
                + "\"ops\":{\"attachment\":true}"
                + "}";

        SysProcessTemplateNodeConfigDo config = objectMapper.readValue(json, SysProcessTemplateNodeConfigDo.class);

        assertEquals("formula", config.getHandlerSource());
        assertEquals("${ownerLeader}", config.getHandlerFormula());
        assertEquals("member", config.getHandlerResolveMode());
        assertEquals("dept-1;user-1", config.getHandlerIds());
        assertEquals("研发部;张三", config.getHandlerNames());
        assertEquals("serial", config.getApproveMode());
        assertEquals("any", config.getSkipMode());
        assertEquals("yes", config.getAutoContinueSkip());
        assertEquals("error", config.getEmptyHandlerMode());
        assertEquals(List.of("N2", "N4"), config.getRejectTargets());

        assertEquals(2, config.getOperations().size());
        SysProcessTemplateNodeConfigDo.OperationDetail pass = config.getOperations().get(0);
        assertEquals("handler_pass", pass.getOperateType());
        assertEquals("通过", pass.getOperateName());
        assertEquals("同意", pass.getDefaultContent());
        SysProcessTemplateNodeConfigDo.OperationDetail reject = config.getOperations().get(1);
        assertEquals("handler_reject", reject.getOperateType());
        assertEquals("拒绝", reject.getOperateName());
        assertEquals("不同意", reject.getDefaultContent());

        assertEquals(1, config.getTasks().size());
        SysProcessTemplateNodeConfigDo.TaskDetail task = config.getTasks().get(0);
        assertEquals("归档", task.getTaskName());
        assertEquals("documentArchive", task.getTaskBean());
        assertEquals("EndNode", task.getTaskType());
        assertEquals(Boolean.TRUE, task.getParams().get("keep"));

        assertEquals(Boolean.TRUE, config.getOps().get("attachment"));
    }

    @Test
    void ccNodeConfigParse() throws Exception {
        String json = "{\"ccSource\":\"org\",\"ccIds\":\"group-1;role-1\",\"ccNames\":\"项目群组;项目管理员\"}";

        SysProcessTemplateNodeConfigDo config = objectMapper.readValue(json, SysProcessTemplateNodeConfigDo.class);

        assertEquals("org", config.getCcSource());
        assertEquals("group-1;role-1", config.getCcIds());
        assertEquals("项目群组;项目管理员", config.getCcNames());
    }

    @Test
    void ccFormulaSourceParse() throws Exception {
        String json = "{\"ccSource\":\"formula\",\"ccFormula\":\"${ccList}\"}";

        SysProcessTemplateNodeConfigDo config = objectMapper.readValue(json, SysProcessTemplateNodeConfigDo.class);

        assertEquals("formula", config.getCcSource());
        assertEquals("${ccList}", config.getCcFormula());
    }

    @Test
    void manualNodeConfigParse() throws Exception {
        String json = "{\"flowDecisions\":[\"N2\",\"N4\"],\"defaultFlowKey\":\"N6\"}";

        SysProcessTemplateNodeConfigDo config = objectMapper.readValue(json, SysProcessTemplateNodeConfigDo.class);

        assertEquals(List.of("N2", "N4"), config.getFlowDecisions());
        assertEquals("N6", config.getDefaultFlowKey());
    }

    /* ==================== skipMode 显式值与旧键共存、旧值归一 ==================== */

    @Test
    void explicitSkipModeWinsOverLegacyAutoSkip() throws Exception {
        SysProcessTemplateNodeConfigDo config = objectMapper.readValue(
                "{\"skipMode\":\"any\",\"autoSkip\":\"no\"}", SysProcessTemplateNodeConfigDo.class);

        assertEquals("any", config.getSkipMode());
    }

    @Test
    void legacyAutoSkipWinsWhenListedAfterExplicitSkipMode() throws Exception {
        // 显式 skipMode 优先与 JSON 键顺序无关
        SysProcessTemplateNodeConfigDo config = objectMapper.readValue(
                "{\"autoSkip\":\"yes\",\"skipMode\":\"none\"}", SysProcessTemplateNodeConfigDo.class);

        assertEquals("none", config.getSkipMode());
    }

    @Test
    void skipModeLegacyValueNormalizes() throws Exception {
        assertEquals("prev", objectMapper
                .readValue("{\"skipMode\":\"yes\"}", SysProcessTemplateNodeConfigDo.class).getSkipMode());
        assertEquals("none", objectMapper
                .readValue("{\"skipMode\":\"no\"}", SysProcessTemplateNodeConfigDo.class).getSkipMode());
    }

    /* ==================== 序列化语义等价（归一幂等） ==================== */

    @Test
    void legacyConfigRoundTripKeepsSemantics() throws Exception {
        String legacyJson = "{\"handlerSource\":\"org\",\"handlerIds\":\"user-1\",\"autoSkip\":\"yes\"}";

        SysProcessTemplateNodeConfigDo first = objectMapper.readValue(legacyJson, SysProcessTemplateNodeConfigDo.class);
        String serialized = objectMapper.writeValueAsString(first);
        SysProcessTemplateNodeConfigDo second = objectMapper.readValue(serialized, SysProcessTemplateNodeConfigDo.class);

        assertEquals("prev", second.getSkipMode());
        assertEquals("yes", second.getAutoSkip());
    }

    /* ==================== 嵌套结构（历史版本内容）同样归一 ==================== */

    @Test
    void historyContentNestedNodeConfigNormalizes() throws Exception {
        String json = "{"
                + "\"template\":{\"fieldId\":\"t-1\",\"fieldName\":\"模板\",\"fieldModelName\":\"sys_org\"},"
                + "\"nodes\":["
                + "{\"fieldNo\":3,\"fieldNodeName\":\"审批\",\"fieldNodeType\":\"APPROVE\","
                + "\"fieldNodeConfig\":{\"handlerSource\":\"org\",\"handlerIds\":\"user-1\",\"autoSkip\":\"no\"}}"
                + "]"
                + "}";

        SysProcessTemplateHistoryContentDo content =
                objectMapper.readValue(json, SysProcessTemplateHistoryContentDo.class);

        assertEquals(1, content.getNodes().size());
        SysProcessTemplateNodeConfigDo config = content.getNodes().get(0).getFieldNodeConfig();
        assertEquals("none", config.getSkipMode());
        assertEquals("no", config.getAutoSkip());
    }
}
