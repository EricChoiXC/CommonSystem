package com.common.system.common.utils;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

/**
 * 拼音工具类
 *
 * <p>规则：</p>
 * <ul>
 *     <li>入参为 null 或去除首尾空格后为空字符串时返回 null，不抛异常</li>
 *     <li>汉字转拼音（{@link #toPinyin} 取全拼，{@link #toPinyinShort} 取首字母）</li>
 *     <li>字母、数字转小写后保留</li>
 *     <li>空格、标点等其他字符一律剔除</li>
 * </ul>
 * <p>多音字局限：取 pinyin4j 返回的第一读音（数组下标 0），不结合词语上下文消歧，
 * 该局限已在方案层面接受。</p>
 *
 * @author Eric
 * @date 2026/11/10
 * @description 拼音工具类
 */
public class PinyinUtil {

    /**
     * 拼音输出格式：小写、无声调、ü 以 v 表示
     * （HanyuPinyinOutputFormat 非线程安全，故每次调用新建，开销可忽略）
     */
    private static HanyuPinyinOutputFormat createFormat() {
        HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();
        format.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        format.setVCharType(HanyuPinyinVCharType.WITH_V);
        return format;
    }

    /**
     * 名称拼音全称
     *
     * <p>示例：测试部A1 → ceshibua1</p>
     *
     * @param name 名称
     * @return 拼音全称；入参为 null 或空白时返回 null
     */
    public static String toPinyin(String name) {
        return convert(name, true);
    }

    /**
     * 名称拼音简称（汉字取拼音首字母）
     *
     * <p>示例：测试部A1 → csba1</p>
     *
     * @param name 名称
     * @return 拼音简称；入参为 null 或空白时返回 null
     */
    public static String toPinyinShort(String name) {
        return convert(name, false);
    }

    /**
     * 逐字符转换：汉字转拼音（全拼或首字母），字母数字转小写保留，其余剔除
     *
     * @param name  名称
     * @param full true 取拼音全称，false 取拼音首字母
     * @return 转换结果；入参为 null 或空白时返回 null
     */
    private static String convert(String name, boolean full) {
        if (StringUtil.isEmpty(name)) {
            return null;
        }
        HanyuPinyinOutputFormat format = createFormat();
        StringBuilder builder = new StringBuilder(name.length());
        for (char ch : name.toCharArray()) {
            if (isChinese(ch)) {
                String[] pinyinArray = toHanyuPinyinArray(ch, format);
                if (null == pinyinArray) {
                    continue;
                }
                // 多音字取第一读音（数组下标 0）
                String pinyin = pinyinArray[0];
                builder.append(full ? pinyin : pinyin.charAt(0));
            } else if (Character.isLetterOrDigit(ch)) {
                builder.append(Character.toLowerCase(ch));
            }
            // 空格、标点等其他字符剔除
        }
        String result = builder.toString();
        return result.isEmpty() ? null : result;
    }

    /**
     * 判断是否为汉字
     *
     * @param ch 字符
     * @return true 表示汉字
     */
    private static boolean isChinese(char ch) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
        return null != block
                && Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS.equals(block)
                || Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A.equals(block)
                || Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B.equals(block)
                || Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS.equals(block);
    }

    /**
     * 取汉字拼音数组，格式组合异常视为非汉字
     *
     * @param ch     字符
     * @param format 拼音输出格式
     * @return 拼音数组；非汉字时返回 null
     */
    private static String[] toHanyuPinyinArray(char ch, HanyuPinyinOutputFormat format) {
        try {
            return PinyinHelper.toHanyuPinyinStringArray(ch, format);
        } catch (BadHanyuPinyinOutputFormatCombination e) {
            // 当前固定格式组合下不会发生，防御性兜底：视为非汉字剔除
            return null;
        }
    }
}
