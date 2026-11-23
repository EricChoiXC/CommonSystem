package com.eric.base.sys.process.service;

import com.eric.base.common.utils.UserUtil;
import com.eric.base.sys.org.model.SysOrgUser;
import com.eric.base.sys.process.dao.SysProcessStatisticDao;
import com.eric.base.sys.process.request.SysProcessStatisticAdminQuery;
import com.eric.base.sys.process.response.SysProcessStatisticAdminVo;
import com.eric.base.sys.process.response.SysProcessStatisticMyVo;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 流程统计服务
 *
 * @author Eric
 * @date 2026/11/23
 * @description 流程统计的编排层（v1_7 P17-BE-016，依据 docs/设计文档/sys/process/流程v1_7接口清单.md §7）：
 *              <ul>
 *              <li>管理员统计（{@code /statistic/admin}，准入 SYS_PROCESS_EXAMPLE_ADMIN）：
 *                  概览（总数 + 平均办结耗时）+ 状态/模块/模板数量分布 + 人员工作量 TopN，
 *                  全部为只读聚合（统计专用 Mapper，不侵入流转 SQL）；</li>
 *              <li>我的统计（{@code /statistic/my}，登录即可）：今日发起 / 未办结 / 待审 /
 *                  今日已审 / 平均耗时五项指标。</li>
 *              </ul>
 *              防越权口径（与列表查询 scope 扩展 P17-BE-015 一致）：我的统计的统计对象
 *              只取会话用户（{@link UserUtil}），服务方法不接收任何 userId 参数，
 *              请求体无用户标识字段可传；未登录注入 null，本人条件比对 NULL 恒不成立，
 *              五项指标全为 0（零泄露）。
 */
@Service
public class SysProcessStatisticService {

    /** 人员工作量 TopN 默认值（topN 缺省或小于 1 时使用，接口清单 §7.1） */
    public static final int DEFAULT_TOP_N = 10;

    private final SysProcessStatisticDao statisticDao;

    public SysProcessStatisticService(SysProcessStatisticDao statisticDao) {
        this.statisticDao = statisticDao;
    }

    /**
     * 管理员统计：概览 + 状态/模块/模板数量分布 + 人员工作量 TopN。
     * <p>口径（docs/设计文档/sys/process/流程v1_7接口清单.md §7.1）：</p>
     * <ul>
     * <li>筛选条件（所属模块 / 模板 / 流程状态 / 创建时间区间）对实例维度的全部聚合生效；</li>
     * <li>avgDurationMs 只计已 FINISH 实例（自然时长 field_finish_time - field_create_time，
     *     Q-05 定稿口径，不区分工作时长）；无 FINISH 实例时为 0；</li>
     * <li>handlerWorkloads = HANDLER_PASS 审批留痕关联工作项的实际处理人
     *     （field_expect_handler_id 优先、回退 field_handler_id，与办结待阅收件人口径一致），
     *     handledCount 为通过留痕条数，TopN 按已办工作量降序取前 N（topN 缺省或小于 1 按默认值）。</li>
     * </ul>
     *
     * @param query 统计筛选条件（body；缺省按全量统计）
     * @return 管理员统计结果
     */
    public SysProcessStatisticAdminVo adminStatistic(SysProcessStatisticAdminQuery query) {
        SysProcessStatisticAdminQuery condition = null == query ? new SysProcessStatisticAdminQuery() : query;
        SysProcessStatisticAdminVo vo = statisticDao.selectAdminOverview(condition);
        if (null == vo) {
            vo = new SysProcessStatisticAdminVo();
        }
        vo.setStatusCounts(orEmpty(statisticDao.selectStatusCounts(condition)));
        vo.setModelCounts(orEmpty(statisticDao.selectModelCounts(condition)));
        vo.setTemplateCounts(orEmpty(statisticDao.selectTemplateCounts(condition)));
        vo.setHandlerWorkloads(orEmpty(
                statisticDao.selectHandlerWorkloads(condition, normalizeTopN(condition.getTopN()))));
        return vo;
    }

    /**
     * 我的统计：今日发起 / 我的未办结 / 待审 / 今日已审 / 平均耗时（接口清单 §7.2）。
     * <p>统计对象只取会话用户：过滤身份由 {@link #currentUserIdOrNull} 从会话注入
     * （本方法无参数，请求体无用户标识字段可传）；今日窗口按应用服务器时区取
     * 今日零点（含）至明日零点（不含）。未登录注入 null，五项指标全为 0（零泄露）。</p>
     *
     * @return 我的统计结果
     */
    public SysProcessStatisticMyVo myStatistic() {
        String myUserId = currentUserIdOrNull();
        LocalDate today = LocalDate.now();
        Date dayBegin = Date.from(today.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date dayEnd = Date.from(today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        SysProcessStatisticMyVo vo = statisticDao.selectMyStatistic(myUserId, dayBegin, dayEnd);
        return null == vo ? new SysProcessStatisticMyVo() : vo;
    }

    /**
     * topN 归一：null 或小于 1 时按默认值 {@link #DEFAULT_TOP_N} 处理（不设上限，
     * 与列表 needPage=false 的全量语义一致）。
     */
    private int normalizeTopN(Integer topN) {
        return null == topN || topN < 1 ? DEFAULT_TOP_N : topN;
    }

    /**
     * 会话用户 id（未登录返回 null）：我的统计的过滤身份只取自会话；
     * Mapper 的本人条件比对 NULL 恒不成立，结果为空（零泄露）。
     */
    private String currentUserIdOrNull() {
        SysOrgUser user = UserUtil.getCurrentUser();
        return null == user ? null : user.getFieldId();
    }

    /**
     * 聚合结果空表兜底：Dao 返回 null（理论不发生，防御）时以空集合呈现，保证响应结构稳定。
     */
    private static <T> List<T> orEmpty(List<T> list) {
        return null == list ? new ArrayList<>() : list;
    }
}
