package com.nexus.ai.service.rag;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 文本分段服务
 * <p>将长文本按指定大小和重叠量切分为多个片段，用于向量嵌入</p>
 *
 * @author NexusERP
 * @since 1.0.0
 */
@Slf4j
@Service
public class ChunkingService {

    /** 默认分段大小（字符数） */
    private static final int DEFAULT_CHUNK_SIZE = 500;

    /** 默认重叠大小（字符数） */
    private static final int DEFAULT_OVERLAP_SIZE = 30;

    /** 默认分隔符 */
    private static final String DEFAULT_SEPARATOR = "#";

    /**
     * 将文本切分为多个片段（使用默认参数）
     *
     * @param content 原始文本内容
     * @return 片段列表（含序号）
     */
    public List<ChunkResult> split(String content) {
        return split(content, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP_SIZE, DEFAULT_SEPARATOR);
    }

    /**
     * 将文本切分为多个片段（使用知识库配置参数）
     *
     * @param content      原始文本内容
     * @param textBlockSize 文本块大小
     * @param overlapChar  重叠字符数
     * @param separator    分隔符
     * @return 片段列表（含序号）
     */
    public List<ChunkResult> split(String content, Integer textBlockSize, Integer overlapChar, String separator) {
        int chunkSize = textBlockSize != null ? textBlockSize : DEFAULT_CHUNK_SIZE;
        int overlap = overlapChar != null ? overlapChar : DEFAULT_OVERLAP_SIZE;
        String sep = (separator != null && !separator.isEmpty()) ? separator : DEFAULT_SEPARATOR;

        // 安全/健壮性：overlap >= chunkSize 时游标回退不前进（死循环/CPU 空转），
        // chunkSize <= 0 或 overlap < 0 同样非法——统一钳制到安全区间
        if (chunkSize <= 0) {
            chunkSize = DEFAULT_CHUNK_SIZE;
        }
        if (overlap < 0 || overlap >= chunkSize) {
            overlap = Math.min(DEFAULT_OVERLAP_SIZE, chunkSize / 2);
        }

        if (content == null || content.isEmpty()) {
            return List.of();
        }

        // 如果内容包含分隔符，优先按分隔符切分
        if (content.contains(sep)) {
            return splitBySeparator(content, sep, chunkSize, overlap);
        }

        // 否则按固定大小+重叠字符滑动窗口切分
        return splitByFixedSize(content, chunkSize, overlap);
    }

    /**
     * 按分隔符切分
     *
     * @param content   原始文本内容
     * @param separator 分隔符
     * @param chunkSize 文本块大小上限
     * @param overlap   重叠字符数
     * @return 片段列表
     */
    private List<ChunkResult> splitBySeparator(String content, String separator, int chunkSize, int overlap) {
        String[] parts = content.split(Pattern.quote(separator));
        List<ChunkResult> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();
        int index = 0;

        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;

            if (currentChunk.length() + trimmed.length() > chunkSize && currentChunk.length() > 0) {
                // 当前片段已满，保存并开始新片段
                ChunkResult result = new ChunkResult();
                result.setContent(currentChunk.toString().trim());
                result.setIndex(index++);
                chunks.add(result);
                currentChunk = new StringBuilder();
            }

            if (currentChunk.length() > 0) {
                currentChunk.append("\n");
            }
            currentChunk.append(trimmed);
        }

        // 处理最后一个片段
        if (currentChunk.length() > 0) {
            ChunkResult result = new ChunkResult();
            result.setContent(currentChunk.toString().trim());
            result.setIndex(index++);
            chunks.add(result);
        }

        log.debug("按分隔符切分完成: 分隔符={}, 片段数={}", separator, chunks.size());
        return chunks;
    }

    /**
     * 按固定大小切分（滑动窗口 + 句子边界识别）
     *
     * @param content   原始文本内容
     * @param chunkSize 文本块大小
     * @param overlap   重叠字符数
     * @return 片段列表
     */
    private List<ChunkResult> splitByFixedSize(String content, int chunkSize, int overlap) {
        content = content.replaceAll("\\s+", " ").trim();

        if (content.length() <= chunkSize) {
            ChunkResult result = new ChunkResult();
            result.setContent(content);
            result.setIndex(0);
            return List.of(result);
        }

        List<ChunkResult> chunks = new ArrayList<>();
        int start = 0;
        int index = 0;

        while (start < content.length()) {
            int end = Math.min(start + chunkSize, content.length());

            if (end < content.length()) {
                int sentenceEnd = findSentenceBoundary(content, start, end);
                if (sentenceEnd > start) {
                    end = sentenceEnd;
                }
            }

            String chunk = content.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                ChunkResult result = new ChunkResult();
                result.setContent(chunk);
                result.setIndex(index++);
                chunks.add(result);
            }

            // 前进性保障：next 必须 > start，否则死循环。
            // 尾块长度 <= overlap（如 970 字符 / 500 块 / 30 重叠的尾块恰好 30）或
            // 句子边界提前结束 + 大 overlap 时，end-overlap 会回退到 start 及以前——
            // 回退时退化为"至少前进剩余长度的一半"，保证游标严格单调前进。
            int next = end - overlap;
            if (next <= start) {
                next = start + Math.max(1, (end - start) / 2);
            }
            start = next;
        }

        log.debug("按固定大小切分完成: chunkSize={}, overlap={}, 片段数={}", chunkSize, overlap, chunks.size());
        return chunks;
    }

    /**
     * 在指定范围内查找最近的句子边界
     *
     * @param content 文本内容
     * @param start   搜索起始位置
     * @param end     搜索结束位置
     * @return 句子边界位置（如果未找到则返回 end）
     */
    private int findSentenceBoundary(String content, int start, int end) {
        for (int i = end; i > start + end / 2; i--) {
            char c = content.charAt(i);
            if (c == '。' || c == '？' || c == '！' || c == '.' || c == '?' || c == '!'
                || c == '\n' || c == '；' || c == ';') {
                return i + 1;
            }
        }
        return end;
    }

    /**
     * 分段结果
     */
    public static class ChunkResult {
        /** 片段内容 */
        private String content;
        /** 片段序号 */
        private int index;

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public int getIndex() { return index; }
        public void setIndex(int index) { this.index = index; }
    }
}
