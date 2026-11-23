package com.eric.base.sys.process.dao;

import com.eric.base.sys.process.mapper.SysProcessStatisticMapper;
import com.eric.base.sys.process.model.SysProcessStatisticHandlerWorkloadDo;
import com.eric.base.sys.process.model.SysProcessStatisticModelCountDo;
import com.eric.base.sys.process.model.SysProcessStatisticStatusCountDo;
import com.eric.base.sys.process.model.SysProcessStatisticTemplateCountDo;
import com.eric.base.sys.process.request.SysProcessStatisticAdminQuery;
import com.eric.base.sys.process.response.SysProcessStatisticAdminVo;
import com.eric.base.sys.process.response.SysProcessStatisticMyVo;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

/**
 * 流程统计数据访问对象
 *
 * @author Eric
 * @date 2026/11/23
 * @description 与 {@link SysProcessStatisticMapper} 一对一对接，不承载业务逻辑；
 *              仅暴露流程统计的只读聚合入口（P17-BE-016），供
 *              {@code SysProcessStatisticService} 编排管理员统计与我的统计使用。
 *              不继承 BaseDao：统计无单一实体、无增删改（与 SysProcessOrgUserDao
 *              专用只读先例一致）。
 */
@Repository
public class SysProcessStatisticDao {

    private final SysProcessStatisticMapper statisticMapper;

    public SysProcessStatisticDao(SysProcessStatisticMapper statisticMapper) {
        this.statisticMapper = statisticMapper;
    }

    /**
     * 管理员统计概览（总数 + 平均办结耗时，只计 FINISH 实例）。
     */
    public SysProcessStatisticAdminVo selectAdminOverview(SysProcessStatisticAdminQuery query) {
        return statisticMapper.selectAdminOverview(query);
    }

    /**
     * 按流程状态的数量分布（与管理员概览共用筛选条件）。
     */
    public List<SysProcessStatisticStatusCountDo> selectStatusCounts(SysProcessStatisticAdminQuery query) {
        return statisticMapper.selectStatusCounts(query);
    }

    /**
     * 按所属模块的数量分布（与管理员概览共用筛选条件）。
     */
    public List<SysProcessStatisticModelCountDo> selectModelCounts(SysProcessStatisticAdminQuery query) {
        return statisticMapper.selectModelCounts(query);
    }

    /**
     * 按流程模板的数量分布（只读关联模板补全名称）。
     */
    public List<SysProcessStatisticTemplateCountDo> selectTemplateCounts(SysProcessStatisticAdminQuery query) {
        return statisticMapper.selectTemplateCounts(query);
    }

    /**
     * 人员工作量 TopN（HANDLER_PASS 留痕关联工作项实际处理人口径；topN 由 Service 归一）。
     */
    public List<SysProcessStatisticHandlerWorkloadDo> selectHandlerWorkloads(
            SysProcessStatisticAdminQuery query, int topN) {
        return statisticMapper.selectHandlerWorkloads(query, topN);
    }

    /**
     * 我的统计五项指标（myUserId 由 Service 从会话注入，未登录为 null；dayBegin/dayEnd 为今日窗口）。
     */
    public SysProcessStatisticMyVo selectMyStatistic(String myUserId, Date dayBegin, Date dayEnd) {
        return statisticMapper.selectMyStatistic(myUserId, dayBegin, dayEnd);
    }
}
