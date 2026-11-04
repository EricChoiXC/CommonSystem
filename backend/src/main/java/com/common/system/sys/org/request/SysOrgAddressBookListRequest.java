package com.common.system.sys.org.request;

import com.common.system.common.base.model.BaseRequest;

/**
 * 地址本关键字列表分页查询请求
 *
 * @author Eric
 * @date 2026/8/9
 * @description 查询页签专用请求：body 为查询条件（keyword / orgTypes / limitRange），
 *              pages 为分页参数，结构与前端 api/org.js 中 queryAddressBookList 的入参一致。
 */
public class SysOrgAddressBookListRequest extends BaseRequest<SysOrgAddressBookQuery> {
}
