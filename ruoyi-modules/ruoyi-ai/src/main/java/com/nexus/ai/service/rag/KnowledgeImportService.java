package com.nexus.ai.service.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库文档导入服务
 * <p>支持从 URL 批量下载文档并导入到知识库，复用已有的文档处理流程</p>
 * <p>
 * 导入流程：下载 URL → 保存为临时文件 → 构造 MultipartFile →
 * 调用 KnowledgeProcessService.uploadAndProcess() → 清理临时文件
 * </p>
 *
 * @author NexusERP
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeImportService {

    /** 知识文档处理服务，复用上传→解析→分段→向量化流程。 */
    private final KnowledgeProcessService knowledgeProcessService;
    /** 文档解析服务，用于校验下载文件的格式是否支持。 */
    private final DocumentParserService documentParserService;

    /** HTTP 连接超时（秒） */
    private static final int CONNECT_TIMEOUT_SECONDS = 30;

    /** HTTP 读取超时（秒） */
    private static final int READ_TIMEOUT_SECONDS = 60;

    /** 单个文件最大大小（50MB） */
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024L;

    /** URL 导入最大数量 */
    private static final int MAX_URL_COUNT = 20;

    /**
     * 从 URL 列表批量导入文档到知识库
     * <p>
     * 对每个 URL：下载文件 → 校验格式 → 上传到 OSS → 创建文档记录 → 异步解析→分段→向量化
     * </p>
     *
     * @param knowledgeId 知识库ID
     * @param urls        文档 URL 列表（最多 {@value #MAX_URL_COUNT} 个）
     * @return 导入结果，包含 success（成功数）、failed（失败数）、errors（失败详情）
     */
    public Map<String, Object> importFromUrls(Long knowledgeId, List<String> urls) {
        int success = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        if (urls == null || urls.isEmpty()) {
            return buildResult(success, failed, errors, "URL 列表不能为空");
        }

        if (urls.size() > MAX_URL_COUNT) {
            return buildResult(success, failed, errors, "URL 数量超过限制（最多" + MAX_URL_COUNT + "个）");
        }

        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

        for (String url : urls) {
            try {
                importSingleUrl(httpClient, knowledgeId, url.trim());
                success++;
                log.info("[KnowledgeImport] URL 导入成功: {}", url);
            } catch (Exception e) {
                failed++;
                String errorMsg = url + ": " + e.getMessage();
                errors.add(errorMsg);
                log.warn("[KnowledgeImport] URL 导入失败: {} - {}", url, e.getMessage());
            }
        }

        return buildResult(success, failed, errors, null);
    }

    /**
     * 导入单个 URL 文档
     * <p>下载文件 → 校验格式 → 构造 MultipartFile → 调用已有的上传处理流程</p>
     *
     * @param httpClient  HTTP 客户端
     * @param knowledgeId 知识库ID
     * @param url         文档 URL
     * @throws IOException      下载失败
     * @throws InterruptedException 下载被中断
     */
    private void importSingleUrl(HttpClient httpClient, Long knowledgeId, String url)
        throws IOException, InterruptedException {

        // 1. 下载文件
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS))
            .GET()
            .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            throw new IOException("HTTP 请求失败，状态码: " + response.statusCode());
        }

        byte[] fileBytes = response.body();
        if (fileBytes.length == 0) {
            throw new IOException("下载的文件内容为空");
        }
        if (fileBytes.length > MAX_FILE_SIZE) {
            throw new IOException("文件大小超过限制（最大50MB）");
        }

        // 2. 从 URL 提取文件名
        String fileName = extractFileName(url, response);

        // 3. 校验文件格式
        if (!documentParserService.isSupported(fileName)) {
            throw new IllegalArgumentException("不支持的文件类型: " + fileName);
        }

        // 4. 构造 MultipartFile 并调用已有的上传处理流程
        MultipartFile multipartFile = new ByteArrayMultipartFile(fileName, fileBytes);
        knowledgeProcessService.uploadAndProcess(knowledgeId, multipartFile);
    }

    /**
     * 从 URL 或 Content-Disposition 头中提取文件名
     *
     * @param url      原始 URL
     * @param response HTTP 响应
     * @return 文件名
     */
    private String extractFileName(String url, HttpResponse<byte[]> response) {
        // 优先从 Content-Disposition 头提取
        String contentDisposition = response.headers()
            .firstValue("Content-Disposition").orElse(null);
        if (contentDisposition != null) {
            // 解析 filename="xxx" 或 filename*=UTF-8''xxx
            for (String part : contentDisposition.split(";")) {
                part = part.trim();
                if (part.startsWith("filename=")) {
                    String name = part.substring("filename=".length()).replace("\"", "");
                    if (!name.isEmpty()) {
                        return name;
                    }
                }
            }
        }

        // 从 URL 路径提取
        String path = URI.create(url).getPath();
        if (path != null && !path.isEmpty()) {
            int lastSlash = path.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash < path.length() - 1) {
                String name = path.substring(lastSlash + 1);
                if (name.contains(".")) {
                    return name;
                }
            }
        }

        // 默认文件名
        return "imported_document.txt";
    }

    /**
     * 构建导入结果
     *
     * @param success   成功数
     * @param failed    失败数
     * @param errors    失败详情
     * @param globalError 全局错误信息（非 null 时表示整体失败）
     * @return 结果映射
     */
    private Map<String, Object> buildResult(int success, int failed, List<String> errors, String globalError) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("failed", failed);
        result.put("errors", errors);
        if (globalError != null) {
            result.put("globalError", globalError);
        }
        return result;
    }

    // ==================== 内部类：ByteArrayMultipartFile ====================

    /**
     * 基于 byte[] 的 MultipartFile 实现
     * <p>用于将下载的文件内容包装为 MultipartFile，以便复用已有的上传处理流程</p>
     */
    private static class ByteArrayMultipartFile implements MultipartFile {

        private final String fileName;
        private final byte[] content;

        ByteArrayMultipartFile(String fileName, byte[] content) {
            this.fileName = fileName;
            this.content = content;
        }

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return fileName;
        }

        @Override
        public String getContentType() {
            return "application/octet-stream";
        }

        @Override
        public boolean isEmpty() {
            return content == null || content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() throws IOException {
            return content;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(Path dest) throws IOException, IllegalStateException {
            Files.write(dest, content);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            Files.write(dest.toPath(), content);
        }
    }
}
