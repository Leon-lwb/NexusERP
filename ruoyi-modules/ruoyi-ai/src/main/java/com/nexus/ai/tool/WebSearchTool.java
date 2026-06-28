package com.nexus.ai.tool;

import com.nexus.ai.config.AiProperties;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 联网搜索工具（LangChain4j @Tool 注解，供 WebSearchAgent 调用）
 * <p>支持 Tavily / Bing Search API 两种搜索厂商（通过 nexus.ai.web-search.provider 配置切换）</p>
 * <p>提供 webSearch（联网搜索）和 crawlWebpage（抓取网页正文）两个工具方法</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSearchTool {

    private final AiProperties aiProperties;

    @Tool("联网搜索最新信息，返回相关网页摘要和链接")
    public String webSearch(@P("搜索关键词") String query) {
        AiProperties.WebSearchConfig config = aiProperties.getWebSearch();
        if (!config.isEnabled() || config.getApiKey() == null || config.getApiKey().isBlank()) {
            return "联网搜索未配置或未启用，请在 nexus.ai.web-search 中配置搜索 API Key";
        }

        try {
            return switch (config.getProvider()) {
                case "tavily" -> searchByTavily(query, config);
                case "bing" -> searchByBing(query, config);
                default -> throw new IllegalStateException("不支持的搜索厂商: " + config.getProvider());
            };
        } catch (Exception e) {
            log.error("[联网搜索] 搜索失败: query={}", query, e);
            return "搜索失败: " + e.getMessage();
        }
    }

    @Tool("抓取指定URL的网页正文内容")
    public String crawlWebpage(@P("网页URL") String url) {
        try {
            // Jsoup 连接 + 读取均带超时，避免长连接阻塞
            String body = Jsoup.connect(url)
                .userAgent("Mozilla/5.0")
                .timeout(15000)
                .maxBodySize(300_000)
                .ignoreContentType(true)
                .get()
                .body().text();
            // 截断过长内容
            if (body.length() > 3000) {
                body = body.substring(0, 3000) + "...(已截断)";
            }
            return body;
        } catch (Exception e) {
            log.warn("[联网搜索] 网页抓取失败: url={}", url, e);
            return "网页抓取失败: " + e.getMessage();
        }
    }

    /**
     * Tavily 搜索（POST https://api.tavily.com/search）
     */
    private String searchByTavily(String query, AiProperties.WebSearchConfig config) throws Exception {
        var bodyMap = Map.of(
            "api_key", config.getApiKey(),
            "query", query,
            "max_results", config.getTopN(),
            "search_depth", "basic"
        );

        cn.hutool.http.HttpResponse resp = cn.hutool.http.HttpRequest.post("https://api.tavily.com/search")
            .header("Content-Type", "application/json")
            .body(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(bodyMap), "application/json")
            .timeout(config.getTimeoutMs())
            .execute();

        if (!resp.isOk()) {
            throw new IllegalArgumentException("Tavily API 调用失败: HTTP " + resp.getStatus());
        }

        // 解析 results[].title/url/content
        var root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(resp.body());
        var results = root.path("results");
        StringBuilder sb = new StringBuilder();
        if (results.isArray()) {
            for (var item : results) {
                sb.append("- ").append(item.path("title").asText("")).append("\n")
                  .append("  ").append(item.path("url").asText("")).append("\n")
                  .append("  ").append(item.path("content").asText("").substring(0,
                      Math.min(item.path("content").asText("").length(), 200))).append("\n\n");
            }
        }
        return sb.length() > 0 ? sb.toString() : "未找到相关结果";
    }

    /**
     * Bing 搜索（GET https://api.bing.microsoft.com/v7.0/search）
     */
    private String searchByBing(String query, AiProperties.WebSearchConfig config) throws Exception {
        cn.hutool.http.HttpResponse resp = cn.hutool.http.HttpRequest
            .get("https://api.bing.microsoft.com/v7.0/search?q=" +
                java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8))
            .header("Ocp-Apim-Subscription-Key", config.getApiKey())
            .timeout(config.getTimeoutMs())
            .execute();

        if (!resp.isOk()) {
            throw new IllegalArgumentException("Bing API 调用失败: HTTP " + resp.getStatus());
        }

        var root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(resp.body());
        var webPages = root.path("webPages").path("value");
        StringBuilder sb = new StringBuilder();
        if (webPages.isArray()) {
            for (var item : webPages) {
                sb.append("- ").append(item.path("name").asText("")).append("\n")
                  .append("  ").append(item.path("url").asText("")).append("\n")
                  .append("  ").append(item.path("snippet").asText("")).append("\n\n");
            }
        }
        return sb.length() > 0 ? sb.toString() : "未找到相关结果";
    }
}
