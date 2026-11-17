package com.eric.base.sys.att.model;

import com.eric.base.common.base.model.BaseModel;
import com.eric.base.common.source.annotation.ColumnField;
import com.eric.base.common.source.annotation.TableSource;
import com.eric.base.common.source.enums.ColumnType;
import com.eric.base.common.source.enums.DefaultFunction;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 附件文件表模型
 *
 * @author Eric
 * @date 2026/8/7
 * @description 对应 sys_att_file；与 sys_att_main 通过 field_id 一对一关联。
 *              <p>2026/11/17 F19-DB-001 存量标注批一：类级 @TableSource + 业务列
 *              @ColumnField（field_create_time 为本表自有列，按业务列标注），列定义与
 *              dev 库 information_schema 逐列一致；公共列（field_id 主键）由引擎公共列
 *              模板内建，无需标注。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableSource(name = "sys_att_file", comment = "附件文件表")
public class SysAttFile extends BaseModel {

    /** 文件名 */
    @ColumnField(name = "field_name", comment = "文件名", type = ColumnType.STRING, length = 200)
    private String fieldName;

    /** 文件类型(扩展名) */
    @ColumnField(name = "field_file_type", comment = "文件类型(扩展名)", type = ColumnType.STRING, length = 50, nullable = true)
    private String fieldFileType;

    /** 保存路径(相对存储根目录) */
    @ColumnField(name = "field_file_path", comment = "保存路径(相对存储根目录)", type = ColumnType.STRING, length = 200, nullable = true)
    private String fieldFilePath;

    /** 上传时间（本表自有列，按业务列标注） */
    @ColumnField(name = "field_create_time", comment = "上传时间",
            type = ColumnType.TIMESTAMP, defaultFunction = DefaultFunction.CURRENT_TIMESTAMP)
    private Date fieldCreateTime;

    /** 文件大小(字节) */
    @ColumnField(name = "field_file_size", comment = "文件大小(字节)", type = ColumnType.BIGINT, defaultValue = "0")
    private long fieldFileSize;

    /** 是否删除 */
    @ColumnField(name = "field_is_delete", comment = "是否删除", type = ColumnType.BOOLEAN, defaultValue = "b'0'")
    private Boolean fieldIsDelete;
}
