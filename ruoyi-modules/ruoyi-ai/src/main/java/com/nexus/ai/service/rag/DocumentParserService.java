package com.nexus.ai.service.rag;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * 文档解析服务
 * <p>使用 Apache Tika 解析各种格式的文档为纯文本</p>
 *
 * @author NexusERP
 * @since 1.0.0
 */
@Slf4j
@Service
public class DocumentParserService {

    /** Apache Tika 文档解析器，负责将二进制文档流解析为纯文本。 */
    private final Tika tika = new Tika();

    /**
     * 解析文档为纯文本
     *
     * @param inputStream 文档输入流
     * @param fileName    文件名（用于日志和类型判断）
     * @return 解析后的文本内容
     * @throws RuntimeException 解析失败时抛出
     */
    public String parse(InputStream inputStream, String fileName) {
        try {
            log.info("开始解析文档: {}", fileName);
            String content = tika.parseToString(inputStream);
            log.info("文档解析完成: {}, 内容长度: {}", fileName, content.length());
            return content;
        } catch (Exception e) {
            log.error("文档解析失败: {}", fileName, e);
            throw new RuntimeException("文档解析失败: " + fileName + " - " + e.getMessage(), e);
        }
    }

    /**
     * 判断文件类型是否支持解析
     *
     * @param fileName 文件名
     * @return 是否支持
     */
    public boolean isSupported(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lowerName = fileName.toLowerCase();
        return lowerName.endsWith(".pdf")
            || lowerName.endsWith(".doc") || lowerName.endsWith(".docx")
            || lowerName.endsWith(".xls") || lowerName.endsWith(".xlsx")
            || lowerName.endsWith(".ppt") || lowerName.endsWith(".pptx")
            || lowerName.endsWith(".txt") || lowerName.endsWith(".csv")
            || lowerName.endsWith(".html") || lowerName.endsWith(".htm")
            || lowerName.endsWith(".md") || lowerName.endsWith(".markdown")
            || lowerName.endsWith(".rtf") || lowerName.endsWith(".odt");
    }
}
