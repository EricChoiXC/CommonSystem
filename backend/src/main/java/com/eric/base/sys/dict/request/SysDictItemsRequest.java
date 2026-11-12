package com.eric.base.sys.dict.request;

import lombok.Data;

/**
 * 通过类型编号获取码值项请求对象
 *
 * @author Eric
 * @date 2026/9/2
 * @description 请求体 { typeCode: string }，供前端和其他模块服务快速获取某类型下的启用码值项。
 */
@Data
public class SysDictItemsRequest {

    /** 码值类型编号（sys_dict_type.field_code） */
    private String typeCode;
}
