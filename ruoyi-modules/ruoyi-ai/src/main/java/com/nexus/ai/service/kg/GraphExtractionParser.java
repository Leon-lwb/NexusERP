package com.nexus.ai.service.kg;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.service.kg.dto.GraphExtractionResult;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 图谱抽取结果解析工具
 * <p>{@code GraphExtractionAgent} 输出严格 JSON，但 LLM 偶尔会包裹代码块或附加说明，
 * 本工具负责：剥离代码块 → Jackson 解析 → 子串兜底 → 正则兜底。</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Slf4j
public final class GraphExtractionParser {

    /** 复用的 Jackson 对象映射器，忽略未知字段以容忍 LLM 多余字段。 */
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** 实体对象正则（匹配 {"name":"...","type":"..."...}，name/type 顺序可互换） */
    private static final Pattern ENTITY_PATTERN = Pattern.compile(
        "\\{[^{}]*\"name\"\\s*:\\s*\"([^\"]+)\"[^{}]*\"type\"\\s*:\\s*\"([^\"]+)\"[^{}]*\\}");

    /** 关系对象正则（匹配 source/target/type 顺序可互换） */
    private static final Pattern RELATION_PATTERN = Pattern.compile(
        "\\{[^{}]*\"source\"\\s*:\\s*\"([^\"]+)\"[^{}]*\"target\"\\s*:\\s*\"([^\"]+)\"[^{}]*\"type\"\\s*:\\s*\"([^\"]+)\"[^{}]*\\}");

    private GraphExtractionParser() {
    }

    /**
     * 剥离 ```json ... ``` 代码块包裹
     *
     * @param raw 原始输出
     * @return 去除代码块后的字符串
     */
    public static String stripCodeFence(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.startsWith("```")) {
            // 去掉首行 ```json 或 ```
            int firstNewline = s.indexOf('\n');
            if (firstNewline > 0) {
                s = s.substring(firstNewline + 1);
            }
            // 去掉结尾 ```
            int fenceEnd = s.lastIndexOf("```");
            if (fenceEnd >= 0) {
                s = s.substring(0, fenceEnd);
            }
            s = s.trim();
        }
        return s;
    }

    /**
     * 尝试解析 JSON（剥离代码块 + 子串兜底）
     *
     * @param json 原始输出
     * @return 解析成功返回结果，失败返回 null
     */
    public static GraphExtractionResult parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        // 1. 剥离代码块后直接 Jackson 解析
        String stripped = stripCodeFence(json);
        GraphExtractionResult result = tryRead(stripped);
        if (result != null) {
            return result;
        }
        // 2. 取首个 { 到末尾 } 的子串再解析（去除前后说明文字）
        int first = stripped.indexOf('{');
        int last = stripped.lastIndexOf('}');
        if (first >= 0 && last > first) {
            result = tryRead(stripped.substring(first, last + 1));
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    /**
     * 正则兜底：尽力从输出中恢复实体与关系（最后手段）
     *
     * @param json 原始输出
     * @return 恢复的结果（可能为空列表，但不会返回 null）
     */
    public static GraphExtractionResult regexFallback(String json) {
        GraphExtractionResult result = new GraphExtractionResult();
        if (json == null || json.isBlank()) {
            return result;
        }
        List<GraphExtractionResult.EntityDto> entities = new ArrayList<>();
        Matcher em = ENTITY_PATTERN.matcher(json);
        while (em.find()) {
            GraphExtractionResult.EntityDto e = new GraphExtractionResult.EntityDto();
            e.setName(em.group(1));
            e.setType(em.group(2));
            e.setConfidence(0.5);
            entities.add(e);
        }
        List<GraphExtractionResult.RelationDto> relations = new ArrayList<>();
        Matcher rm = RELATION_PATTERN.matcher(json);
        while (rm.find()) {
            GraphExtractionResult.RelationDto r = new GraphExtractionResult.RelationDto();
            r.setSource(rm.group(1));
            r.setTarget(rm.group(2));
            r.setType(rm.group(3));
            r.setStrength(5);
            r.setConfidence(0.5);
            relations.add(r);
        }
        result.setEntities(entities);
        result.setRelations(relations);
        return result;
    }

    private static GraphExtractionResult tryRead(String json) {
        try {
            GraphExtractionResult r = MAPPER.readValue(json, GraphExtractionResult.class);
            return r.getEntities() == null && r.getRelations() == null ? null : r;
        } catch (Exception e) {
            log.debug("[KG] JSON 解析失败: {}", e.getMessage());
            return null;
        }
    }
}
