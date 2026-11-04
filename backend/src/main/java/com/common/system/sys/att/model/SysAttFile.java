package com.common.system.sys.att.model;

import com.common.system.common.base.model.BaseModel;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 附件文件表模型
 *
 * @author Eric
 * @date 2026/8/7
 * @description 对应 sys_att_file；与 sys_att_main 通过 field_id 一对一关联。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysAttFile extends BaseModel {

    /** 文件名 */
    private String fieldName;

    /** 文件类型(扩展名) */
    private String fieldFileType;

    /** 保存路径(相对存储根目录) */
    private String fieldFilePath;

    /** 上传时间 */
    private Date fieldCreateTime;

    /** 文件大小(字节) */
    private long fieldFileSize;

    /** 是否删除 */
    private Boolean fieldIsDelete;
}
