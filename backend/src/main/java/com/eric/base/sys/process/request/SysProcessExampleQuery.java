package com.eric.base.sys.process.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 流程实例查询条件
 *
 * @author Eric
 * @date 2026/8/23
 * @description 流程实例列表页的查询条件：流程名称（模板名）/流程编号模糊、
 *              所属模块固定、发起人名称模糊、创建时间区间与流程状态筛选；
 *              v1_7 扩展数据范围 scope（P17-BE-015）与监控筛选 inFlightOnly/exceptionOnly
 *              （仅 monitorList 消费）。注意：本类不承载任何用户标识字段，
 *              my* 数据范围的用户标识由 Service 从会话（UserUtil）注入 Mapper。
 */
@Data
public class SysProcessExampleQuery {

    /** 流程名称（模板名称）模糊匹配 */
    private String fieldName;

    /** 流程编号模糊匹配 */
    private String fieldCode;

    /** 所属模块（process_template 二级配置项，固定条件） */
    private String fieldModelName;

    /** 发起人名称模糊匹配 */
    private String fieldCreatorName;

    /** 创建时间起（含） */
    private Date fieldCreateTimeBegin;

    /** 创建时间止（含） */
    private Date fieldCreateTimeEnd;

    /** 流程状态：ABANDON / DRAFT / PROCESSING / PAUSE / FINISH / RETURN */
    private String fieldStatus;

    /** 数据范围（v1_7 新增，P17-BE-015）：admin（默认全量）/ myCreated（我发起的）/ myJoined（我参与的）；缺省或非法值一律按 admin 处理（归一在 Service 层），my* 维度由 Mapper 强制注入本人过滤条件 */
    @Schema(description = "数据范围：admin（默认，全量，与现列表一致）/ myCreated（我发起的）/ myJoined（我参与的）；缺省或非法值一律按 admin 处理，my* 强制本人数据过滤",
            allowableValues = {"admin", "myCreated", "myJoined"}, defaultValue = "admin")
    private String scope;

    /** 监控筛选（仅 monitorList 消费）：仅看在途实例（PROCESSING/RETURN/PAUSE），默认 false */
    @Schema(description = "仅看在途实例（PROCESSING/RETURN/PAUSE），默认 false（仅流程监控列表 monitorList 消费）")
    private boolean inFlightOnly;

    /** 监控筛选（仅 monitorList 消费）：仅看异常实例（存在 SYSTEM_ERROR 审批留痕或状态 PAUSE，二者取或，Q-03 定稿口径），默认 false */
    @Schema(description = "仅看异常实例（存在 SYSTEM_ERROR 留痕或状态为 PAUSE，二者取或），默认 false（仅流程监控列表 monitorList 消费）")
    private boolean exceptionOnly;
}
