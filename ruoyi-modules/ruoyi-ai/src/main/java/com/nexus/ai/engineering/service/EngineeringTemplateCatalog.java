package com.nexus.ai.engineering.service;

import static com.nexus.common.ai.engineering.EngineeringException.require;

import com.nexus.common.ai.engineering.*;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.*;
import java.math.BigDecimal;
import java.util.*;

/** 仅加载已审 classpath 模板目录；默认空目录，不能从模型提供的 URL 动态下载模板。 */
@Component
@ConditionalOnProperty(prefix = "nexus.ai.engineering", name = "enabled", havingValue = "true")
public class EngineeringTemplateCatalog {
    private final List<Template> templates;

    /** 加载由连接器已审模板清单导出的元数据；无文件时安全地返回空目录。 */
    public EngineeringTemplateCatalog() {
        try (var in = getClass().getResourceAsStream("/engineering/templates.json")) {
            templates =
                    in == null
                            ? List.of()
                            : List.of(
                                    EngineeringJson.read(
                                                    new String(
                                                            in.readAllBytes(),
                                                            java.nio.charset.StandardCharsets
                                                                    .UTF_8),
                                                    Catalog.class)
                                            .templates());
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("工程模板目录无效，已拒绝启用", e);
        }
    }

    /** 列举已审核版本和固定参数约束，不包含本地模板路径。 */
    public List<Template> list() {
        return templates;
    }

    /** 从已审 manifest 读取默认值，不由模型补造工程参数。 */
    public Map<String, Object> parameters(EngineeringTaskCommand command) {
        var template =
                templates.stream()
                        .filter(
                                t ->
                                        Objects.equals(t.templateId(), command.templateId())
                                                && Objects.equals(
                                                        t.templateVersion(),
                                                        command.templateVersion()))
                        .findFirst()
                        .orElse(null);
        require(template != null, 422, "TEMPLATE_UNAVAILABLE", "模板尚未审核或该版本不可用");
        var result = new java.util.TreeMap<String, Object>();
        if (command.parameters() != null) result.putAll(command.parameters());
        template.parameters()
                .forEach(
                        (key, rule) -> {
                            if (result.get(key) == null && rule.defaultValue() != null)
                                result.put(key, rule.defaultValue());
                        });
        return result;
    }

    /** 核对受控模板版本、完整摘要与参数约束，空目录明确返回不支持。 */
    public void validate(EngineeringTaskCommand command) {
        var template =
                templates.stream()
                        .filter(
                                t ->
                                        Objects.equals(t.templateId(), command.templateId())
                                                && Objects.equals(
                                                        t.templateVersion(),
                                                        command.templateVersion()))
                        .findFirst()
                        .orElse(null);
        require(template != null, 422, "TEMPLATE_UNAVAILABLE", "模板尚未审核或该版本不可用");
        require(
                Objects.equals(template.templateSha256(), command.templateSha256()),
                422,
                "TEMPLATE_HASH_MISMATCH",
                "模板摘要不匹配");
        Map<String, Object> params = command.parameters() == null ? Map.of() : command.parameters();
        for (var entry : template.parameters().entrySet()) {
            var rule = entry.getValue();
            Object value = params.get(entry.getKey());
            require(
                    !rule.required() || value != null,
                    422,
                    "NEEDS_INPUT",
                    "缺少模板必填参数：" + entry.getKey());
            if (value != null && !rule.enumValues().isEmpty())
                require(
                        value instanceof Number number
                                && number.doubleValue() == number.intValue()
                                && rule.enumValues().contains(number.intValue()),
                        422,
                        "INVALID_REQUEST",
                        "参数不在模板允许枚举内");
            if (value != null && rule.min() != null) {
                try {
                    var number = new BigDecimal(value.toString());
                    require(
                            number.compareTo(rule.min()) >= 0
                                    && (rule.max() == null || number.compareTo(rule.max()) <= 0),
                            422,
                            "INVALID_REQUEST",
                            "模板参数不在允许范围内");
                } catch (NumberFormatException e) {
                    throw new EngineeringException(422, "INVALID_REQUEST", "模板参数不是有效十进制数");
                }
            }
        }
        Object modules = params.get("optionalModules");
        if (modules != null)
            require(
                    modules instanceof List<?> list
                            && list.size() <= 32
                            && list.stream()
                                    .allMatch(
                                            m ->
                                                    m instanceof String
                                                            && template.optionalModules()
                                                                    .contains(m)),
                    422,
                    "INVALID_REQUEST",
                    "可选模块不在已审模板范围内");
    }

    /**
     * 元数据根节点，由已审 manifest 生成。
     *
     * @param protocolVersion 固定协议主版本 nexus-engineering/1
     * @param templates 已审模板元数据集合，缺失目录时为空
     */
    public record Catalog(String protocolVersion, Template[] templates) {}

    /**
     * 模板公开元数据，不含脚本和文件系统路径。
     *
     * @param templateId 允许目录中的模板标识
     * @param templateVersion 已审模板固定版本
     * @param templateSha256 已审模板归档完整 SHA-256
     * @param title 已审模板中文显示名
     * @param parameters 仅允许目录声明的有界结构化板参数
     * @param optionalModules 模板声明的可选模块标识集合
     * @param reviewStatus 模板实际审查级别，演示不代表工业验收
     * @param description 模板真实能力与限制说明
     * @param validatedEditorVersion 模板核对时的编辑器版本
     * @param canManufacture 首期固定为 false，不能据此自动投产
     */
    public record Template(
            String templateId,
            String templateVersion,
            String templateSha256,
            String title,
            Map<String, ParameterRule> parameters,
            List<String> optionalModules,
            String reviewStatus,
            String description,
            String validatedEditorVersion,
            boolean canManufacture) {
        public Template {
            canManufacture = false;
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
            optionalModules = optionalModules == null ? List.of() : List.copyOf(optionalModules);
        }
    }

    /**
     * 十进制参数的必填和范围约束。
     *
     * @param required 模板参数是否必填
     * @param min 允许的最小十进制值
     * @param max 允许的最大十进制值
     * @param defaultValue 已审 manifest 中的可信默认值
     * @param enumValues 已审 manifest 允许的整数枚举
     */
    public record ParameterRule(
            boolean required,
            BigDecimal min,
            BigDecimal max,
            Object defaultValue,
            List<Integer> enumValues) {
        public ParameterRule {
            enumValues = enumValues == null ? List.of() : List.copyOf(enumValues);
        }
    }
}
