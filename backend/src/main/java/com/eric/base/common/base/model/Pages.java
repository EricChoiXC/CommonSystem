package com.eric.base.common.base.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 分页参数
 *
 * @author Eric
 * @date 2026/8/3
 * @description 列表类接口统一分页参数。列表请求按「页码从 1 起」的语义使用：
 *              needPage 为 false 时不分页返回全部（用于下拉、树形等场景）；
 *              前端切换每页条数时须回到第 1 页重新查询。
 */
@Data
@Schema(description = "分页参数")
public class Pages {

    /** 页码，从 1 开始；小于 1 时由服务端修正为 1 */
    private int pageNo;

    /** 每页条数；前端可选 10 / 20 / 50 / 100，默认 10 */
    private int pageSize;

    /** 是否分页：true 按 pageNo + pageSize 取页，false 返回全部匹配结果 */
    private boolean needPage;
}
