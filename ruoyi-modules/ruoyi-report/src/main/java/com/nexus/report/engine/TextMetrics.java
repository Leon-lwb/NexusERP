package com.nexus.report.engine;

import com.nexus.report.domain.ReportFault;

import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

/** 文本指标，负责当前模块对应的契约或处理职责。 */
public final class TextMetrics implements AutoCloseable {
    /** 保存当前 {@code document} 值。 */
    private final PDDocument document = new PDDocument();

    /** 可信私有字体路径。 */
    private final FontSupport.Handle font;

    /**
     * 初始化文本指标并保存所需依赖或不可变值。
     *
     * @param path 可信私有路径
     */
    public TextMetrics(Path path) {
        try {
            font = FontSupport.load(document, path);
        } catch (Exception e) {
            try {
                document.close();
            } catch (IOException ignored) {
            }
            throw new ReportFault("FONT_UNAVAILABLE", "报表字体无法读取或不允许嵌入", 500);
        }
    }

    /**
     * 完成 {@code fontName} 对应的文本指标处理。
     *
     * @return 当前处理返回的固定结果
     */
    public String fontName() {
        return font.font().getName();
    }

    /**
     * 完成 {@code width} 对应的文本指标处理。
     *
     * @param text 当前操作的 {@code text} 输入
     * @param size 有界页大小
     * @return 当前处理返回的固定结果
     */
    public double width(String text, double size) {
        try {
            return font.font().getStringWidth(text) / 1000 * size;
        } catch (Exception e) {
            throw new ReportFault("FONT_GLYPH_UNAVAILABLE", "报表字体缺少所需字符", 500);
        }
    }

    /**
     * 完成 {@code wrap} 对应的文本指标处理。
     *
     * @param text 当前操作的 {@code text} 输入
     * @param available 当前操作的 {@code available} 输入
     * @param size 有界页大小
     * @return 当前处理返回的固定结果
     */
    public List<String> wrap(String text, double available, double size) {
        if (!Double.isFinite(available) || available <= 0 || !Double.isFinite(size) || size <= 0)
            throw ReportFault.invalid("单元格有效宽度或字号无效");
        if (text == null) text = "";
        if (text.length() > 10_000) throw ReportFault.capacity();
        List<String> result = new ArrayList<>();
        for (String paragraph : text.replace("\t", "    ").split("\\R", -1)) {
            StringBuilder line = new StringBuilder();
            double lineWidth = 0;
            for (int i = 0; i < paragraph.length(); ) {
                int cp = paragraph.codePointAt(i);
                i += Character.charCount(cp);
                if (Character.isISOControl(cp))
                    throw new ReportFault("INVALID_DATA_TYPE", "文本含不支持的控制字符");
                String glyph = new String(Character.toChars(cp));
                double glyphWidth = width(glyph, size);
                if (glyphWidth > available + 0.01) throw ReportFault.invalid("字符宽度超过单元格，请增加列宽");
                if (!line.isEmpty() && lineWidth + glyphWidth > available) {
                    result.add(line.toString());
                    line.setLength(0);
                    lineWidth = 0;
                }
                line.append(glyph);
                lineWidth += glyphWidth;
            }
            result.add(line.toString());
        }
        return result;
    }

    /** 释放当前对象持有的文件或扫描句柄。 */
    public void close() {
        try {
            font.close();
            document.close();
        } catch (IOException e) {
            throw new ReportFault("FONT_UNAVAILABLE", "报表字体资源关闭失败", 500);
        }
    }
}
