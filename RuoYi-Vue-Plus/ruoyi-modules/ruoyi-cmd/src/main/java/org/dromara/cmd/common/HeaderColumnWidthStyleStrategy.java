package org.dromara.cmd.common;

import org.apache.fesod.sheet.metadata.Head;
import org.apache.fesod.sheet.write.style.column.AbstractHeadColumnWidthStyleStrategy;

import java.util.List;

/**
 * 导入模板下载的列宽策略：按表头文字长度设定列宽，保证表头单行完整显示。
 * <p>
 * 背景：模板文件只有表头、没有数据行，Fesod 自带的 {@code LongestMatchColumnWidthStyleStrategy}
 * 是按「单元格内容」计算列宽的，没有数据时会退化成默认窄列，出现 {@code CustomerN / ame} 这类换行。
 * 这里改为只依据表头文字（含多级表头）计算宽度。
 *
 * @author Essilor CMD POC
 */
public class HeaderColumnWidthStyleStrategy extends AbstractHeadColumnWidthStyleStrategy {

    /** 单列最小宽度（字符数），保证短表头如 City 也不至于过窄 */
    private static final int MIN_WIDTH = 14;

    /** 表头文字右侧留白（字符数），避免贴边 */
    private static final int PADDING = 6;

    /** 单列最大宽度（字符数），POI 上限 255 */
    private static final int MAX_WIDTH = 60;

    /** 中日韩字符按 2 个字符宽估算 */
    private static final int CJK_WIDTH = 2;

    /** 中日韩字符起始码位 */
    private static final int CJK_START = 0x2E80;

    /**
     * 计算某一列的列宽
     * <p>
     * 注意单位：基类 {@code AbstractHeadColumnWidthStyleStrategy} 内部会做 {@code width * 256}
     * 再交给 POI 的 {@code Sheet#setColumnWidth}，因此这里必须返回**字符数**（不是 1/256 字符单位），
     * 返回 1/256 单位会被再乘一次 256，触发
     * {@code IllegalArgumentException: The maximum column width for an individual cell is 255 characters}。
     *
     * @param head        表头定义
     * @param columnIndex 列索引
     * @return 列宽（字符数）
     */
    @Override
    protected Integer columnWidth(Head head, Integer columnIndex) {
        int longest = 0;
        List<String> names = head == null ? null : head.getHeadNameList();
        if (names != null) {
            for (String name : names) {
                longest = Math.max(longest, displayWidth(name));
            }
        }
        return Math.min(Math.max(longest + PADDING, MIN_WIDTH), MAX_WIDTH);
    }

    /**
     * 估算字符串的显示宽度（中文等全角字符按 2 计）
     *
     * @param text 待估算文本
     * @return 显示宽度（字符数）
     */
    private int displayWidth(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            width += text.charAt(i) >= CJK_START ? CJK_WIDTH : 1;
        }
        return width;
    }
}
