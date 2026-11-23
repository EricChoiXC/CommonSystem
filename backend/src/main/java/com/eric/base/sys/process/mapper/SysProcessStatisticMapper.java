package com.eric.base.sys.process.mapper;

import com.eric.base.sys.process.model.SysProcessStatisticHandlerWorkloadDo;
import com.eric.base.sys.process.model.SysProcessStatisticModelCountDo;
import com.eric.base.sys.process.model.SysProcessStatisticStatusCountDo;
import com.eric.base.sys.process.model.SysProcessStatisticTemplateCountDo;
import com.eric.base.sys.process.request.SysProcessStatisticAdminQuery;
import com.eric.base.sys.process.response.SysProcessStatisticAdminVo;
import com.eric.base.sys.process.response.SysProcessStatisticMyVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 流程统计专用只读 Mapper
 *
 * @author Eric
 * @date 2026/11/23
 * @description v1_7 流程统计（P17-BE-016）专用聚合语句（依据 docs/设计文档/sys/process/流程v1_7接口清单.md §7）：
 *              <ul>
 *              <li>只读聚合：仅 SELECT，不写任何流转表，不复用/不改动流转 SQL
 *                 （流转语句归 SysProcessExampleMapper 等既有 Mapper）；</li>
 *              <li>管理员统计：概览（总数 + 平均办结耗时）与按状态/模块/模板的数量分布
 *                  共用统计筛选条件（模块/模板/状态/创建时间区间），人员工作量 TopN 独立语句；</li>
 *              <li>我的统计：统计对象由 myUserId 承载——该值由 Service 层从会话（UserUtil）
 *                  注入，Mapper 不读会话、不信任请求体传入的用户标识。</li>
 *              </ul>
 */
@Mapper
public interface SysProcessStatisticMapper {

    /**
     * 管理员统计概览：筛选范围内实例总数 + 平均办结耗时
     * （只计 field_status = 'FINISH' 且 field_finish_time 非空的实例，自然时长毫秒，无办结实例为 0）。
     *
     * @param query 统计筛选条件（模块/模板/状态/创建时间区间）
     * @return 概览行（totalCount / avgDurationMs 两列；COUNT 恒有一行）
     */
    SysProcessStatisticAdminVo selectAdminOverview(@Param("query") SysProcessStatisticAdminQuery query);

    /**
     * 按流程状态的数量分布（随统计筛选条件生效，按数量降序、状态升序稳定排序）。
     */
    List<SysProcessStatisticStatusCountDo> selectStatusCounts(@Param("query") SysProcessStatisticAdminQuery query);

    /**
     * 按所属模块的数量分布（随统计筛选条件生效，按数量降序、模块升序稳定排序）。
     */
    List<SysProcessStatisticModelCountDo> selectModelCounts(@Param("query") SysProcessStatisticAdminQuery query);

    /**
     * 按流程模板的数量分布（只读关联 sys_process_template 补全模板名称，
     * 按数量降序、模板 id 升序稳定排序）。
     */
    List<SysProcessStatisticTemplateCountDo> selectTemplateCounts(@Param("query") SysProcessStatisticAdminQuery query);

    /**
     * 人员工作量 TopN：HANDLER_PASS 审批留痕关联工作项的实际处理人
     * （field_expect_handler_id 优先、回退 field_handler_id），handledCount = 通过留痕条数，
     * avgDurationMs = 该处理人经办的 FINISH 实例平均办结耗时（毫秒）；
     * 按已办工作量降序、处理人 id 升序稳定排序，LIMIT #{topN} 截取。
     *
     * @param query 统计筛选条件（作用于实例维度）
     * @param topN  取前 N（由 Service 层归一，恒 &gt;= 1）
     */
    List<SysProcessStatisticHandlerWorkloadDo> selectHandlerWorkloads(
            @Param("query") SysProcessStatisticAdminQuery query, @Param("topN") int topN);

    /**
     * 我的统计：五项指标一次聚合（今日发起 / 我的未办结 / 待审 / 今日已审 / 平均耗时）。
     * myUserId 为过滤身份（Service 层从会话注入，未登录为 null，条件比对 NULL 恒不成立）；
     * dayBegin / dayEnd 为今日窗口（Service 层按应用服务器时区计算的今日零点（含）与明日零点（不含））。
     */
    SysProcessStatisticMyVo selectMyStatistic(@Param("myUserId") String myUserId,
                                              @Param("dayBegin") Date dayBegin,
                                              @Param("dayEnd") Date dayEnd);
}
