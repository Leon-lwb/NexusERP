package com.nexus.ai.context;

import com.nexus.ai.service.sse.AiSseMessageHelper.SourceCard;

import java.util.ArrayList;
import java.util.List;

/**
 * 联网搜索来源收集器
 * <p>
 * 在 AI 请求线程内收集 {@link WebSearchTool} 搜索到的来源卡片，供请求结束时
 * 通过 SSE（AI_WEB_SEARCH 事件）推送到前端。与 {@link AiRequestContext} 同生命周期：
 * 异步任务开始时随上下文创建（InheritableThreadLocal 传播），结束时调用 {@link #clear()} 清理。
 * </p>
 *
 * @author NexusERP
 * @since 2.1.0
 */
public final class SearchSourceHolder {

    /** 本请求内已收集的搜索来源（按出现顺序，由调用方去重） */
    private static final ThreadLocal<List<SourceCard>> SOURCES = new InheritableThreadLocal<>();

    private SearchSourceHolder() {
    }

    /**
     * 添加一个搜索来源
     *
     * @param title 标题
     * @param url   链接
     */
    public static void add(String title, String url) {
        if (title == null || title.isBlank() || url == null || url.isBlank()) {
            return;
        }
        List<SourceCard> sources = SOURCES.get();
        if (sources == null) {
            sources = new ArrayList<>();
            SOURCES.set(sources);
        }
        sources.add(new SourceCard(title.trim(), url.trim()));
    }

    /**
     * 取出并清空本请求已收集的来源（按 url 去重，保留首次出现顺序）
     *
     * @return 来源列表，可能为空列表
     */
    public static List<SourceCard> drain() {
        List<SourceCard> sources = SOURCES.get();
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }
        SOURCES.remove();
        List<SourceCard> deduped = new ArrayList<>();
        var seen = new java.util.HashSet<String>();
        for (SourceCard source : sources) {
            if (source.url() != null && seen.add(source.url())) {
                deduped.add(source);
            }
        }
        return deduped;
    }

    /**
     * 清理当前线程的收集器（必须在异步任务结束时调用，避免线程复用导致来源串扰）
     */
    public static void clear() {
        SOURCES.remove();
    }
}
