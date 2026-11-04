package com.common.system.common.base.model;

import lombok.Data;

import java.util.Date;

/**
 * @author Eric
 * @date 2026/8/3
 * @description
 */
@Data
public class MainModel extends BaseModel{

    /** 标题/名称 */
    private String fieldName;

    /** 编码 */
    private String fieldCode;

    /** 创建者ID */
    private String fieldCreatorId;

    /** 创建时间 */
    private Date fieldCreateTime;

    /** 修改者ID */
    private String fieldUpdaterId;

    /** 修改时间 */
    private Date fieldUpdateTime;

    /** 是否启用 */
    private Boolean fieldEnable;
}
