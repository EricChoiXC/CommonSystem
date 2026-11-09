package com.common.system.common.base.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 公共导入结果
 *
 * @author Eric
 * @date 2026/11/9
 * @description 公共导入方案（ImportExportService#importExcel）的统一返回结构：
 *              文件数据行总数 / 成功导入条数 / 失败（跳过）条数 / 行错误明细。
 *              设计为可继承的普通类，模块可派生专属导入结果并追加自有字段
 *              （Q6 评审结论，如 i18n 的 SysI18nTranslationImportResultVo extends ImportResultVo）。
 */
@Schema(description = "公共导入结果：总数 / 成功 / 失败 / 行错误明细")
public class ImportResultVo {

    /** 文件数据行总数（不含标题行） */
    @Schema(description = "文件数据行总数（不含标题行）", example = "100")
    private int total;

    /** 成功导入条数 */
    @Schema(description = "成功导入条数", example = "98")
    private int success;

    /** 失败/跳过条数 */
    @Schema(description = "失败/跳过条数", example = "2")
    private int failed;

    /** 行错误明细：row 为文件行号，从 2 起（1 为标题行） */
    @Schema(description = "行错误明细：row 为文件行号，从 2 起（1 为标题行）")
    private List<RowError> errors;

    /**
     * 行错误
     */
    @Schema(description = "行错误：一行一条，row 为文件行号，从 2 起（1 为标题行）")
    public static class RowError {

        /** 文件行号：从 2 起（1 为标题行） */
        @Schema(description = "文件行号：从 2 起（1 为标题行）", example = "2")
        private int row;

        /** 错误字段名：导入 VO 的 Java 字段名；行级（非字段）错误时为空串 */
        @Schema(description = "错误字段名：导入 VO 的 Java 字段名；行级（非字段）错误时为空串",
                example = "fieldCode")
        private String field;

        /** 错误消息：可直接展示给用户的错误描述 */
        @Schema(description = "错误消息：可直接展示给用户的错误描述", example = "字段不能为空")
        private String message;

        public RowError() {
        }

        public RowError(int row, String field, String message) {
            this.row = row;
            this.field = field;
            this.message = message;
        }

        /**
         * 获取文件行号
         */
        public int getRow() {
            return row;
        }

        /**
         * 设置文件行号
         */
        public void setRow(int row) {
            this.row = row;
        }

        /**
         * 获取错误字段名
         */
        public String getField() {
            return field;
        }

        /**
         * 设置错误字段名
         */
        public void setField(String field) {
            this.field = field;
        }

        /**
         * 获取错误消息
         */
        public String getMessage() {
            return message;
        }

        /**
         * 设置错误消息
         */
        public void setMessage(String message) {
            this.message = message;
        }
    }

    /**
     * 获取文件数据行总数
     */
    public int getTotal() {
        return total;
    }

    /**
     * 设置文件数据行总数
     */
    public void setTotal(int total) {
        this.total = total;
    }

    /**
     * 获取成功导入条数
     */
    public int getSuccess() {
        return success;
    }

    /**
     * 设置成功导入条数
     */
    public void setSuccess(int success) {
        this.success = success;
    }

    /**
     * 获取失败/跳过条数
     */
    public int getFailed() {
        return failed;
    }

    /**
     * 设置失败/跳过条数
     */
    public void setFailed(int failed) {
        this.failed = failed;
    }

    /**
     * 获取行错误明细
     */
    public List<RowError> getErrors() {
        return errors;
    }

    /**
     * 设置行错误明细
     */
    public void setErrors(List<RowError> errors) {
        this.errors = errors;
    }
}
