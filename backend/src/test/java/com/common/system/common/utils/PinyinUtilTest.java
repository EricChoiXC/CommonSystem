package com.common.system.common.utils;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 拼音工具测试
 *
 * @author Eric
 * @date 2026/11/10
 */
class PinyinUtilTest {

    @Test
    void nullInputReturnsNull() {
        assertNull(PinyinUtil.toPinyin(null));
        assertNull(PinyinUtil.toPinyinShort(null));
    }

    @Test
    void emptyInputReturnsNull() {
        assertNull(PinyinUtil.toPinyin(""));
        assertNull(PinyinUtil.toPinyinShort(""));
    }

    @Test
    void blankInputReturnsNull() {
        assertNull(PinyinUtil.toPinyin("   "));
        assertNull(PinyinUtil.toPinyinShort(" \t "));
    }

    @Test
    void pureChineseConvertsToFullAndShortPinyin() {
        assertEquals("ceshibu", PinyinUtil.toPinyin("测试部"));
        assertEquals("csb", PinyinUtil.toPinyinShort("测试部"));
    }

    @Test
    void planExampleConvertsAsExpected() {
        assertEquals("ceshibua1", PinyinUtil.toPinyin("测试部A1"));
        assertEquals("csba1", PinyinUtil.toPinyinShort("测试部A1"));
    }

    @Test
    void mixedLettersAndDigitsLowercaseAndKeep() {
        assertEquals("abcceshi123", PinyinUtil.toPinyin("Abc测试123"));
        assertEquals("abccs123", PinyinUtil.toPinyinShort("Abc测试123"));
    }

    @Test
    void punctuationAndSpacesAreRemoved() {
        assertEquals("ceshibumen", PinyinUtil.toPinyin("测试-部 门"));
        assertEquals("csbm", PinyinUtil.toPinyinShort("测试-部 门"));
    }

    @Test
    void polyphonicCharTakesFirstPronunciation() throws BadHanyuPinyinOutputFormatCombination {
        // 不硬编码多音字读音，与 pinyin4j 同配置的第一读音对拍，保证实现确实取数组下标 0
        HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();
        format.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        format.setVCharType(HanyuPinyinVCharType.WITH_V);
        String expected = PinyinHelper.toHanyuPinyinStringArray('重', format)[0];

        assertEquals(expected, PinyinUtil.toPinyin("重"));
        assertEquals(expected.substring(0, 1), PinyinUtil.toPinyinShort("重"));
    }

    @Test
    void inputWithoutConvertibleCharsReturnsNull() {
        // 仅含空格与标点，无可转换字符时也返回 null 而非空串
        assertNull(PinyinUtil.toPinyin(" - . "));
        assertNull(PinyinUtil.toPinyinShort("!@#"));
    }
}
