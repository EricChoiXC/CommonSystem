package com.common.system.sys.org.model;

import com.common.system.common.base.model.MainModel;
import lombok.Data;

import java.util.List;

/**
 * @author Eric
 * @date 2026/8/3
 * @description
 */
@Data
public class SysOrgRole extends MainModel {

    private List<SysOrgUser> users;

    private List<SysOrgOrg> orgs;

    private List<SysOrgAuth> auths;
}
