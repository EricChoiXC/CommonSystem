package com.eric.base.sys.att.model;

import com.eric.base.common.base.model.MainModel;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 附件主表模型
 *
 * @author Eric
 * @date 2026/8/7
 * @description 对应 sys_att_main；与 sys_att_file 通过 field_id 一对一关联，
 *              关联查询时额外填充 fieldFilePath / fieldFileSize / fieldIsDelete 文件信息。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysAttMain extends MainModel {

    /** 文件类型(扩展名) */
    private String fieldFileType;

    /** 模块名 */
    private String fieldModelName;

    /** 文档id */
    private String fieldModelId;

    /** 关键字 */
    private String fieldKey;

    /** 下载次数 */
    private int fieldDownload;

    /** 预览次数 */
    private int fieldView;

    /** 保存路径(相对存储根目录，来自 sys_att_file) */
    private String fieldFilePath;

    /** 文件大小(字节，来自 sys_att_file) */
    private long fieldFileSize;

    /** 文件是否删除(来自 sys_att_file) */
    private Boolean fieldIsDelete;
}
