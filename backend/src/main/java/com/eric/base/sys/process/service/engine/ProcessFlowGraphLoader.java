package com.eric.base.sys.process.service.engine;

import com.eric.base.sys.process.dao.SysProcessExampleLineDao;
import com.eric.base.sys.process.dao.SysProcessTemplateLineDao;
import com.eric.base.sys.process.model.SysProcessExample;
import com.eric.base.sys.process.model.SysProcessExampleLine;
import com.eric.base.sys.process.model.SysProcessExampleNode;
import com.eric.base.sys.process.model.SysProcessTemplateLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 流程图加载器（实例连线优先，模板连线回退）
 *
 * @author Eric
 * @date 2026/11/19
 * @description P17-BE-003 交付物：为 {@link ProcessFlowEngine} 装配
 *              {@link ProcessFlowGraph}。连线取值规则：
 *              <ol>
 *              <li><b>优先实例连线</b>：读 {@code sys_process_example_line}（实例创建时的连线快照，
 *                  DB-005 已为存量在途实例回填）；存量迁移的历史实例（DM-20261119-003）也走此路径；</li>
 *              <li><b>回退模板连线</b>：实例连线为空时按实例的 {@code field_template_id} 读
 *                  {@code sys_process_template_line}，并 {@code log.warn} 提示。</li>
 *              </ol>
 *              <p><b>回退为阶段性桥梁</b>：实例连线快照的复制由 <b>P17-BE-007</b> 在
 *              {@code createDraft} 落地；本任务期间新建的实例暂无连线快照，故需回退模板连线。
 *              P17-BE-007 落地后应移除回退路径（见方法内 TODO）。</p>
 *              <p>本加载器只读，不写任何表；不新增 Mapper（复用 P17-DB-001 / P17-DB-002 已交付的
 *              连线 Dao）。</p>
 */
@Component
public class ProcessFlowGraphLoader {

    private static final Logger log = LoggerFactory.getLogger(ProcessFlowGraphLoader.class);

    private final SysProcessExampleLineDao exampleLineDao;
    private final SysProcessTemplateLineDao templateLineDao;

    public ProcessFlowGraphLoader(SysProcessExampleLineDao exampleLineDao,
                                  SysProcessTemplateLineDao templateLineDao) {
        this.exampleLineDao = exampleLineDao;
        this.templateLineDao = templateLineDao;
    }

    /**
     * 加载流程图（实例连线优先，实例无连线时回退模板连线并告警）。
     *
     * @param example      流程实例（非空）
     * @param exampleNodes 实例节点列表（非空）
     * @return 流程图（只读索引）
     */
    public ProcessFlowGraph load(SysProcessExample example, List<SysProcessExampleNode> exampleNodes) {
        List<SysProcessExampleLine> instanceLines =
                exampleLineDao.selectListByExampleId(example.getFieldId());
        if (null != instanceLines && !instanceLines.isEmpty()) {
            List<ProcessFlowLine> lines = new ArrayList<>(instanceLines.size());
            for (SysProcessExampleLine line : instanceLines) {
                lines.add(ProcessFlowGraph.ofInstanceLine(line));
            }
            return new ProcessFlowGraph(exampleNodes, lines, true);
        }
        return loadFromTemplate(example, exampleNodes);
    }

    /**
     * 回退路径：按实例的模板 id 读模板连线。
     * <p>TODO(P17-BE-007)：实例创建时复制连线快照后，本回退路径应删除；
     * 在此之前它保证本任务期间新建实例（暂无连线快照）仍可按模板图流转。</p>
     */
    private ProcessFlowGraph loadFromTemplate(SysProcessExample example, List<SysProcessExampleNode> exampleNodes) {
        String templateId = example.getFieldTemplateId();
        log.warn("实例连线快照为空，回退模板连线（P17-BE-007 落地实例连线快照后移除回退）: exampleId={}, templateId={}",
                example.getFieldId(), templateId);
        List<SysProcessTemplateLine> templateLines = null == templateId
                ? List.of() : templateLineDao.selectListByTemplateId(templateId);
        List<ProcessFlowLine> lines = new ArrayList<>();
        if (null != templateLines) {
            for (SysProcessTemplateLine line : templateLines) {
                lines.add(ProcessFlowGraph.ofTemplateLine(line));
            }
        }
        return new ProcessFlowGraph(exampleNodes, lines, false);
    }
}
