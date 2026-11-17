package com.eric.base.sys.att.model;

import com.eric.base.common.base.model.BaseModel;
import com.eric.base.common.source.annotation.ColumnField;
import com.eric.base.common.source.annotation.TableIndex;
import com.eric.base.common.source.annotation.TableSource;
import com.eric.base.common.source.enums.ColumnType;
import com.eric.base.common.source.enums.DefaultFunction;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 附件主表模型
 *
 * @author Eric
 * @date 2026/8/7
 * @description 对应 sys_att_main；与 sys_att_file 通过 field_id 一对一关联，
 *              关联查询时额外填充 fieldFilePath / fieldFileSize / fieldIsDelete 文件信息。
 *              <p>2026/11/17 F19-DB-001 存量标注批一继承对齐（裁决 A：继承对齐表结构）：
 *              原继承 MainModel 与表结构不符（本表仅含部分审计列 field_enable /
 *              field_creator_id / field_create_time，无 field_name 列定义、无修改人与
 *              修改时间列），改继承 BaseModel 对齐公共列模板；表中存在的部分审计列与
 *              field_name 按业务列 @ColumnField 标注（重声明字段 Java 类型与 MainModel
 *              原声明一致），列定义与 dev 库 information_schema 逐列一致；
 *              fieldFilePath / fieldFileSize / fieldIsDelete 来自 sys_att_file 关联查询，
 *              fieldCode / fieldUpdaterId / fieldUpdateTime 为主表继承兼容字段，均不入
 *              本表列清单；公共列（field_id 主键）由引擎公共列模板内建，无需标注。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableSource(name = "sys_att_main", comment = "附件主表",
        indexes = {
                @TableIndex(name = "idx_att_main_creator", columns = "field_creator_id"),
                @TableIndex(name = "idx_att_main_key", columns = "field_key"),
                @TableIndex(name = "idx_att_main_model", columns = {"field_model_name", "field_model_id"})})
public class SysAttMain extends BaseModel {

    /** 文件名 */
    @ColumnField(name = "field_name", comment = "文件名", type = ColumnType.STRING, length = 200)
    private String fieldName;

    /** 兼容字段：本表无 field_code 列，保留属性供响应序列化与业务代码使用 */
    private String fieldCode;

    /** 上传者id（表中存在的部分审计列，按业务列标注） */
    @ColumnField(name = "field_creator_id", comment = "上传者id", type = ColumnType.STRING, length = 32, nullable = true)
    private String fieldCreatorId;

    /** 上传时间（表中存在的部分审计列，按业务列标注；类型与 MainModel 原声明一致） */
    @ColumnField(name = "field_create_time", comment = "上传时间",
            type = ColumnType.TIMESTAMP, defaultFunction = DefaultFunction.CURRENT_TIMESTAMP)
    private java.util.Date fieldCreateTime;

    /** 兼容字段：本表无 field_updater_id 列，保留属性供响应序列化与业务代码使用 */
    private String fieldUpdaterId;

    /** 兼容字段：本表无 field_update_time 列，保留属性供响应序列化与业务代码使用 */
    private java.util.Date fieldUpdateTime;

    /** 是否有效（表中存在的部分审计列，按业务列标注） */
    @ColumnField(name = "field_enable", comment = "是否有效", type = ColumnType.BOOLEAN, defaultValue = "b'1'")
    private Boolean fieldEnable;

    /** 文件类型(扩展名) */
    @ColumnField(name = "field_file_type", comment = "文件类型(扩展名)", type = ColumnType.STRING, length = 50, nullable = true)
    private String fieldFileType;

    /** 模块名 */
    @ColumnField(name = "field_model_name", comment = "模块名", type = ColumnType.STRING, length = 200, nullable = true)
    private String fieldModelName;

    /** 文档id */
    @ColumnField(name = "field_model_id", comment = "文档id", type = ColumnType.STRING, length = 200, nullable = true)
    private String fieldModelId;

    /** 关键字 */
    @ColumnField(name = "field_key", comment = "关键字", type = ColumnType.STRING, length = 200, nullable = true)
    private String fieldKey;

    /** 下载次数 */
    @ColumnField(name = "field_download", comment = "下载次数", type = ColumnType.INT, defaultValue = "0")
    private int fieldDownload;

    /** 预览次数 */
    @ColumnField(name = "field_view", comment = "预览次数", type = ColumnType.INT, defaultValue = "0")
    private int fieldView;

    /** 保存路径(相对存储根目录，来自 sys_att_file，展示字段不入库) */
    private String fieldFilePath;

    /** 文件大小(字节，来自 sys_att_file，展示字段不入库) */
    private long fieldFileSize;

    /** 文件是否删除(来自 sys_att_file，展示字段不入库) */
    private Boolean fieldIsDelete;
}
