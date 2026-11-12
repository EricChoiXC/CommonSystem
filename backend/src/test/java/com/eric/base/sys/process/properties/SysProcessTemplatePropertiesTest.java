package com.eric.base.sys.process.properties;

import com.eric.base.sys.process.response.SysProcessTemplateModelVo;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流程模板分类配置合并测试
 *
 * @author Eric
 * @date 2026/8/22
 * @description 覆盖 process_template 配置段的多模块聚合规则（依据设计文档 sys/process/
 *              流程核心业务.md「process_template 配置项 和 field_model_name 取值」）：
 *              多 yml 段合并保持顺序、重复分类 key fail-fast、name/class/desc/ops 解析。
 *              迁移说明：原工程使用两个业务模块（项目/合同）作为测试数据，
 *              本工程改为 sys 侧模型（sys_org/sys_notify），聚合规则不变。
 */
class SysProcessTemplatePropertiesTest {

    @Test
    void mergeTwoSectionsKeepsOrderAndFields() {
        Map<String, SysProcessTemplateModelVo> models = new LinkedHashMap<>();

        SysProcessTemplateProperties.mergeModels(models, Map.of(
                "sys_org", Map.of(
                        "name", "组织",
                        "class", "com.eric.base.sys.org.model.SysOrgUser",
                        "desc", "组织主流程",
                        "ops", Map.of("attachment", "允许修改附件"))),
                "classpath:properties/sys/org/application.yml");
        SysProcessTemplateProperties.mergeModels(models, Map.of(
                "sys_notify", Map.of(
                        "name", "通知",
                        "class", "com.eric.base.sys.notify.model.SysNotifyTodo")),
                "classpath:properties/sys/notify/application.yml");

        assertEquals(2, models.size());
        SysProcessTemplateModelVo org = models.get("sys_org");
        assertEquals("组织", org.getName());
        assertEquals("com.eric.base.sys.org.model.SysOrgUser", org.getClassName());
        assertEquals("组织主流程", org.getDesc());
        assertEquals(Map.of("attachment", "允许修改附件"), org.getOps());
        SysProcessTemplateModelVo notify = models.get("sys_notify");
        assertEquals("通知", notify.getName());
        assertTrue(notify.getOps().isEmpty());
    }

    @Test
    void duplicateModelKeyFailsFast() {
        Map<String, SysProcessTemplateModelVo> models = new LinkedHashMap<>();
        SysProcessTemplateProperties.mergeModels(models, Map.of("sys_org", Map.of("name", "组织")),
                "classpath:properties/sys/org/application.yml");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> SysProcessTemplateProperties.mergeModels(models, Map.of("sys_org", Map.of("name", "组织2")),
                        "classpath:properties/sys/notify/application.yml"));
        assertTrue(ex.getMessage().contains("sys_org"));
        assertTrue(ex.getMessage().contains("sys/notify/application.yml"));
    }

    @Test
    void nonMapValueSkipped() {
        Map<String, SysProcessTemplateModelVo> models = new LinkedHashMap<>();

        SysProcessTemplateProperties.mergeModels(models, Map.of(
                "sys_org", Map.of("name", "组织"),
                "sys_bad", "not-a-map"),
                "classpath:properties/sys/org/application.yml");

        assertEquals(1, models.size());
        assertTrue(models.containsKey("sys_org"));
    }
}
