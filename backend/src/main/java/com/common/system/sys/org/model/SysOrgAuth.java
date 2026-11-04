package com.common.system.sys.org.model;

import com.common.system.common.base.model.BaseModel;
import lombok.Data;

import java.util.List;

/**
 * @author Eric
 * @date 2026/8/5
 * @description
 */
@Data
public class SysOrgAuth extends BaseModel {

    private String fieldName;

    private String fieldCode;

    private String fieldModel;

    private String fieldDescription;

    private List<SysOrgRole> roles;
}
