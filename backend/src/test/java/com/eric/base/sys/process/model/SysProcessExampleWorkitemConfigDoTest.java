package com.eric.base.sys.process.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 流程工作项配置 Do 承载测试
 *
 * @author Eric
 * @date 2026/11/19
 * @description 覆盖工作项 field_config 反序列化契约（依据 docs/设计文档/sys/process/流程核心业务.md
 *              「驳回与重新提交（D3）」「人工决策流向（D6）」）：旧 JSON（仅 resumeMode）解析不报错、
 *              新增 flowChoice（人工决策流向）缺省为 null；新 JSON 两者完整解析与回写。
 */
class SysProcessExampleWorkitemConfigDoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void legacyResumeModeConfigParse() throws Exception {
        SysProcessExampleWorkitemConfigDo config =
                objectMapper.readValue("{\"resumeMode\":\"restart\"}", SysProcessExampleWorkitemConfigDo.class);

        assertEquals("restart", config.getResumeMode());
        assertNull(config.getFlowChoice());
    }

    @Test
    void emptyConfigParse() throws Exception {
        SysProcessExampleWorkitemConfigDo config =
                objectMapper.readValue("{}", SysProcessExampleWorkitemConfigDo.class);

        assertNull(config.getResumeMode());
        assertNull(config.getFlowChoice());
    }

    @Test
    void flowChoiceConfigParse() throws Exception {
        SysProcessExampleWorkitemConfigDo config = objectMapper.readValue(
                "{\"resumeMode\":\"restart\",\"flowChoice\":\"N6\"}", SysProcessExampleWorkitemConfigDo.class);

        assertEquals("restart", config.getResumeMode());
        assertEquals("N6", config.getFlowChoice());
    }

    @Test
    void flowChoiceRoundTripKeepsValue() throws Exception {
        SysProcessExampleWorkitemConfigDo config = new SysProcessExampleWorkitemConfigDo();
        config.setResumeMode("backToNode");
        config.setFlowChoice("N5");

        String json = new ObjectMapper().writeValueAsString(config);
        SysProcessExampleWorkitemConfigDo parsed =
                new ObjectMapper().readValue(json, SysProcessExampleWorkitemConfigDo.class);

        assertEquals("backToNode", parsed.getResumeMode());
        assertEquals("N5", parsed.getFlowChoice());
    }
}
