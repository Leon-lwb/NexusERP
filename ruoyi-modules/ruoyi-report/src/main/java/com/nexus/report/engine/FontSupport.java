package com.nexus.report.engine;

import com.nexus.report.domain.ReportFault;

import org.apache.fontbox.ttf.TrueTypeCollection;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** 字体路径来自可信服务端配置，不能由模板或接口指定。 */
public final class FontSupport {
    /** 初始化字体支持并保存所需依赖或不可变值。 */
    private FontSupport() {}

    /**
     * 完成 {@code find} 对应的字体支持处理。
     *
     * @param configured 当前操作的 {@code configured} 输入
     * @return 当前处理返回的固定结果
     */
    public static Path find(String configured) {
        if (configured != null && !configured.isBlank()) {
            Path file = Path.of(configured).toAbsolutePath().normalize();
            if (Files.isRegularFile(file)) return file;
            throw new ReportFault("FONT_UNAVAILABLE", "配置的中文报表字体不存在", 500);
        }
        List<Path> files = new ArrayList<>();
        String windows = System.getenv().getOrDefault("WINDIR", "C:/Windows");
        files.add(Path.of(windows, "Fonts", "simhei.ttf"));
        files.add(Path.of(windows, "Fonts", "simsun.ttc"));
        files.add(Path.of("/usr/share/fonts/wqy-zenhei/wqy-zenhei.ttc"));
        files.add(Path.of("/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc"));
        files.add(Path.of("/usr/share/fonts/truetype/noto/NotoSansSC-Regular.ttf"));
        files.add(Path.of("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"));
        return files.stream()
                .filter(Files::isRegularFile)
                .findFirst()
                .orElseThrow(
                        () ->
                                new ReportFault(
                                        "FONT_UNAVAILABLE",
                                        "请配置已批准的中文报表字体 nexus.report.font-path",
                                        500));
    }

    /**
     * Handle，负责当前模块对应的契约或处理职责。
     *
     * @param font 固定私有字体引用
     * @param collection 当前契约的collection配置或状态
     */
    public record Handle(PDType0Font font, TrueTypeCollection collection) implements AutoCloseable {

        /** 释放当前对象持有的文件或扫描句柄。 */
        public void close() throws IOException {
            if (collection != null) collection.close();
        }
    }

    /**
     * 完成 {@code load} 对应的字体支持处理。
     *
     * @param document 当前操作的 {@code document} 输入
     * @param path 可信私有路径
     * @return 当前处理返回的固定结果
     */
    public static Handle load(PDDocument document, Path path) throws IOException {
        boolean collectionFile;
        try (var input = Files.newInputStream(path)) {
            collectionFile = Arrays.equals(input.readNBytes(4), new byte[] {'t', 't', 'c', 'f'});
        }
        if (collectionFile) {
            var collection = new TrueTypeCollection(path.toFile());
            var fonts = new ArrayList<TrueTypeFont>();
            collection.processAllFonts(fonts::add);
            if (fonts.isEmpty()) {
                collection.close();
                throw new IOException("Font collection is empty");
            }
            var selected =
                    fonts.stream()
                            .filter(
                                    font -> {
                                        try {
                                            return font.getName().contains("SC")
                                                    || font.getName().contains("SimSun");
                                        } catch (IOException e) {
                                            return false;
                                        }
                                    })
                            .findFirst()
                            .orElse(fonts.get(0));
            try {
                return new Handle(PDType0Font.load(document, selected, true), collection);
            } catch (Exception e) {
                collection.close();
                throw e;
            }
        }
        return new Handle(PDType0Font.load(document, path.toFile()), null);
    }
}
