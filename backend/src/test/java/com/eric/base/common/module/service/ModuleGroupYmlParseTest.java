package com.eric.base.common.module.service;

import com.eric.base.common.module.dao.CommonModuleDao;
import com.eric.base.common.module.model.CommonModule;
import com.eric.base.common.utils.YmlResourceUtil;
import com.eric.base.sys.log.service.LogService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 首批 moduleGroup.yml 资源文件解析核验
 *
 * @author Eric
 * @date 2026/11/12
 * @description 走真实的 YmlResourceUtil classpath 扫描 + CommonModuleService#parseModuleGroups
 *              解析链路，固化首批清单（修订稿 5.2）：仅 calendar、org-group 两个模块组
 *              （third-party 仅设计占位，不建 yml，决策 D5），key / name / remark 与
 *              @ModuleGroup 注解值逐字一致（MOD3-BE-002 验收标准）。
 */
class ModuleGroupYmlParseTest {

    private static final String KEY_MODULE_GROUP = "module-group";

    @Test
    void parsesFirstBatchModuleGroupYmlsFromProperties() throws IOException {
        CommonModuleService service = new CommonModuleService(mock(CommonModuleDao.class),
                mock(ModuleGroupCacheService.class), mock(RequestMappingHandlerMapping.class),
                mock(LogService.class));

        Resource[] resources = YmlResourceUtil.getResources("classpath*:properties/**/moduleGroup.yml");
        assertTrue(resources.length >= 2, "expected at least calendar and org moduleGroup.yml on classpath");

        List<CommonModule> modules = new ArrayList<>();
        for (Resource resource : resources) {
            Map<String, Object> data = YmlResourceUtil.loadYml(resource);
            Object moduleGroup = data.get(KEY_MODULE_GROUP);
            if (moduleGroup instanceof Map) {
                modules.addAll(service.parseModuleGroups((Map<?, ?>) moduleGroup, resource.getDescription()));
            }
        }

        // name / remark 必填校验已在 parseModuleGroups 内完成：被跳过的条目不会出现在结果中，
        // 因此 keySet 即「全部通过校验的首批条目」
        Map<String, CommonModule> byKey = new LinkedHashMap<>();
        for (CommonModule module : modules) {
            byKey.put(module.getFieldKey(), module);
        }
        assertEquals(Set.of("calendar", "org-group"), byKey.keySet(), () -> "parsed keys: " + byKey.keySet());
        assertEquals("日程", byKey.get("calendar").getFieldName());
        assertEquals("日程模块", byKey.get("calendar").getFieldRemark());
        assertEquals("组织架构-群组", byKey.get("org-group").getFieldName());
        assertEquals("组织架构-群组模块", byKey.get("org-group").getFieldRemark());
    }
}
