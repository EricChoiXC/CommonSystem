package com.eric.base.sys.process.controller;

import com.eric.base.common.base.model.BaseRequest;
import com.eric.base.common.base.model.BaseResponse;
import com.eric.base.common.resolver.AuthorizationContext;
import com.eric.base.common.resolver.AuthorizationHelper;
import com.eric.base.sys.process.request.SysProcessStatisticAdminQuery;
import com.eric.base.sys.process.resolver.ProcessResolver;
import com.eric.base.sys.process.response.SysProcessStatisticAdminVo;
import com.eric.base.sys.process.response.SysProcessStatisticMyVo;
import com.eric.base.sys.process.service.SysProcessStatisticService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.eric.base.sys.process.properties.SysProcessConstants.ACTION_PROCESS_STATISTIC_ADMIN;
import static com.eric.base.sys.process.properties.SysProcessConstants.ACTION_PROCESS_STATISTIC_MY;

/**
 * 流程统计接口
 *
 * @author Eric
 * @date 2026/11/23
 * @description 流程统计（管理员）与我的流程统计（v1_7 新增，P17-BE-016，依据
 *              docs/设计文档/sys/process/流程v1_7接口清单.md §7）：
 *              <ul>
 *              <li>管理员统计（admin）：仅 SYS_PROCESS_EXAMPLE_ADMIN；</li>
 *              <li>我的统计（my）：登录且启用即可（不叠加角色）。</li>
 *              </ul>
 *              统计为只读聚合（统计专用 Mapper，不侵入流转 SQL）；我的统计的统计对象
 *              只取会话用户（UserUtil），请求体不接收任何 userId 字段（防越权）。
 *              纯动作型接口（无文档 ID 维度），鉴权写法与流程监控列表（monitorList）一致；
 *              Controller 形态与本模块既有三个 Controller 一致（复杂业务 Controller 经
 *              {@link AuthorizationHelper} 统一鉴权）。
 */
@RestController
@RequestMapping("${project.request.prefix}/sys/process/statistic")
@Tag(name = "流程-流程统计", description = "流程统计（管理员）与我的流程统计；分别需 SYS_PROCESS_EXAMPLE_ADMIN / SYS_PROCESS_MY_VIEWER")
public class SysProcessStatisticController {

    private final SysProcessStatisticService baseService;
    private final ProcessResolver baseResolver;
    private final AuthorizationHelper authorizationHelper;

    public SysProcessStatisticController(SysProcessStatisticService baseService,
                                         ProcessResolver baseResolver,
                                         AuthorizationHelper authorizationHelper) {
        this.baseService = baseService;
        this.baseResolver = baseResolver;
        this.authorizationHelper = authorizationHelper;
    }

    /**
     * 管理员统计：POST /backend/sys/process/statistic/admin
     * 请求体为统一包装 { body: { fieldModelName, fieldTemplateId, fieldStatus,
     * fieldCreateTimeBegin, fieldCreateTimeEnd, topN } }（无分页维度，pages 不消费；
     * 请求体可缺省，缺省按全量统计）。
     *
     * @param request 统计请求（body 承载筛选条件，可整体缺省）
     * @return 统一响应，data 为管理员统计结果
     */
    @PostMapping("/admin")
    @Operation(summary = "管理员统计", description = "鉴权动作 sys.process.statistic.admin（仅 "
            + "SYS_PROCESS_EXAMPLE_ADMIN）；管理员统计（数量/效率/人员工作量 TopN）")
    public BaseResponse<SysProcessStatisticAdminVo> admin(
            @RequestBody(required = false) BaseRequest<SysProcessStatisticAdminQuery> request) {
        authorizationHelper.authorize(baseResolver, new AuthorizationContext(ACTION_PROCESS_STATISTIC_ADMIN));
        return BaseResponse.success(
                baseService.adminStatistic(null == request ? null : request.getBody()), null);
    }

    /**
     * 我的统计：POST /backend/sys/process/statistic/my
     * 请求体为空（或空 body，缺省即可），统计对象由会话定位——不接收任何 userId 参数（防越权）。
     *
     * @param request 兼容空 body 的统一包装（内容不消费）
     * @return 统一响应，data 为我的统计五项指标
     */
    @PostMapping("/my")
    @Operation(summary = "我的统计", description = "鉴权动作 sys.process.statistic.my（登录即可）；"
            + "我的统计（今日发起/未办结/待审/今日已审/平均耗时）")
    public BaseResponse<SysProcessStatisticMyVo> my(
            @RequestBody(required = false) BaseRequest<Void> request) {
        authorizationHelper.authorize(baseResolver, new AuthorizationContext(ACTION_PROCESS_STATISTIC_MY));
        return BaseResponse.success(baseService.myStatistic(), null);
    }
}
