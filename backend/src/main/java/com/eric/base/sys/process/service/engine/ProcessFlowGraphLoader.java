package com.eric.base.sys.process.service.engine;

import com.eric.base.sys.process.dao.SysProcessExampleLineDao;
import com.eric.base.sys.process.model.SysProcessExample;
import com.eric.base.sys.process.model.SysProcessExampleLine;
import com.eric.base.sys.process.model.SysProcessExampleNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 流程图加载器（实例连线快照）
 *
 * @author Eric
 * @date 2026/11/19
 * @description P17-BE-003 交付物、P17-BE-007 收敛：为 {@link ProcessFlowEngine} 装配
 *              {@link ProcessFlowGraph}。连线唯一取值来源为 <b>实例连线快照</b>：
 *              <ol>
 *              <li>读 {@code sys_process_example_line}（实例创建时的连线快照，由
 *                  {@code SysProcessExampleService#createDraft} 与节点快照同事务全量复制，
 *                  P17-DB-005 已为存量在途实例回填）；</li>
 *              <li>实例连线为空时抛 {@link ProcessFlowException#graph(String)}
 *                  （「流程实例缺少连线定义: {exampleId}」），与引擎「流程实例缺少节点定义」同风格：
 *                  图结构缺失属不可恢复错误，由引擎统一出口转流程异常（实例 PAUSE + SYSTEM_ERROR 留痕），
 *                  不静默回退模板连线、不按节点编号兜底流转。</li>
 *              </ol>
 *              <p><b>P17-BE-007 已移除模板连线回退桥梁</b>（原 P17-BE-003 的阶段性实现）：
 *              实例创建即复制连线快照，流转只读实例快照，模板后续变更不影响在途实例。</p>
 *              <p>本加载器只读，不写任何表；不新增 Mapper（复用 P17-DB-002 已交付的连线 Dao）。</p>
 */
@Component
public class ProcessFlowGraphLoader {

    private final SysProcessExampleLineDao exampleLineDao;

    public ProcessFlowGraphLoader(SysProcessExampleLineDao exampleLineDao) {
        this.exampleLineDao = exampleLineDao;
    }

    /**
     * 加载流程图（连线取自实例快照；实例无连线快照时抛流程异常）。
     *
     * @param example      流程实例（非空）
     * @param exampleNodes 实例节点列表（非空）
     * @return 流程图（只读索引，连线来源恒为实例快照）
     * @throws ProcessFlowException 实例连线快照缺失（图结构不可用）
     */
    public ProcessFlowGraph load(SysProcessExample example, List<SysProcessExampleNode> exampleNodes) {
        List<SysProcessExampleLine> instanceLines =
                exampleLineDao.selectListByExampleId(example.getFieldId());
        if (null == instanceLines || instanceLines.isEmpty()) {
            throw ProcessFlowException.graph("流程实例缺少连线定义: " + example.getFieldId());
        }
        List<ProcessFlowLine> lines = new ArrayList<>(instanceLines.size());
        for (SysProcessExampleLine line : instanceLines) {
            lines.add(ProcessFlowGraph.ofInstanceLine(line));
        }
        return new ProcessFlowGraph(exampleNodes, lines, true);
    }
}
