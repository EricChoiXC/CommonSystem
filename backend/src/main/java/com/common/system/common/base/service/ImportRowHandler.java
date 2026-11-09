package com.common.system.common.base.service;

/**
 * 模块行级业务校验扩展点：标准导入逻辑第 4 步「验证数据合法性和正确性」的模块承接。
 * <p>由模块在接入 ImportExportService#importExcel 时传入：
 * 公共导入完成文件解析、标题映射、字典转换与 @Export(required) 非空校验后，
 * 逐行回调本接口做模块自定义的行级业务校验（如唯一性、外键存在性等）。</p>
 * <p>错误处理口径：ignoreError = false 时，任一行校验失败即整体抛出回滚；
 * ignoreError = true 时，失败行跳过并计入 ImportResultVo#errors（row 为文件行号，从 2 起）。</p>
 *
 * @param <T> 当前行解析后的导入 VO 类型
 * @author Eric
 * @date 2026/11/9
 */
@FunctionalInterface
public interface ImportRowHandler<T> {

    /**
     * 模块行级业务校验扩展点（标准导入逻辑第 4 步「验证数据合法性和正确性」的模块承接）。
     *
     * @param row   当前行解析后的导入 VO
     * @param rowNo 文件行号，从 2 起（1 为标题行）
     * @return 校验不通过时返回错误消息；通过返回 null 或空串
     */
    String validate(T row, int rowNo);
}
