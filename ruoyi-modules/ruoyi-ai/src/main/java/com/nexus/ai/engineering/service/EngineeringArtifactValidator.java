package com.nexus.ai.engineering.service;

import static com.nexus.common.ai.engineering.EngineeringException.require;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.nexus.ai.engineering.domain.EngineeringUploadRequest;
import com.nexus.common.ai.engineering.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import javax.imageio.ImageIO;

/** 服务端产物格式验真；不执行脚本、不解压工程文件，OBJ/MTL 仅允许同组相对引用。 */
public final class EngineeringArtifactValidator {
    private static final ObjectMapper JSON =
            new ObjectMapper(
                    JsonFactory.builder()
                            .streamReadConstraints(
                                    StreamReadConstraints.builder()
                                            .maxNestingDepth(32)
                                            .maxStringLength(4096)
                                            .maxNumberLength(32)
                                            .build())
                            .build());
    private static final Set<String> BOM_FIELDS =
            Set.of(
                    "designator",
                    "value",
                    "footprint",
                    "manufacturerPartNumber",
                    "supplierPartNumber",
                    "quantity");

    private EngineeringArtifactValidator() {}

    /** 校验真实文件并产生有界服务端解析摘要，MIME 不信任客户端自报。 */
    public static Validated validate(Path path, EngineeringUploadRequest request) {
        try {
            return switch (request.artifactType()) {
                case PCB_PREVIEW_2D -> image(path, true);
                case PCB_MODEL_TEXTURE -> image(path, false);
                case PCB_MODEL_OBJ -> model(path, request.relativePath(), false);
                case PCB_MODEL_MTL -> model(path, request.relativePath(), true);
                case PCB_BOM -> bom(path);
                case PCB_DRC_REPORT -> drc(path);
                case PCB_SOURCE -> source(path);
                case PCB_NETLIST ->
                        new Validated(
                                "application/octet-stream",
                                EngineeringJson.write(Map.of("kind", "NETLIST")));
            };
        } catch (EngineeringException e) {
            throw e;
        } catch (Exception e) {
            throw new EngineeringException(422, "ARTIFACT_FORMAT_INVALID", "文件内容不符合该产物格式");
        }
    }

    private static Validated image(Path path, boolean pngOnly) throws IOException {
        try (var input = ImageIO.createImageInputStream(path.toFile())) {
            var readers = ImageIO.getImageReaders(input);
            require(readers.hasNext(), 422, "ARTIFACT_FORMAT_INVALID", "图片无法解析");
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                require(
                        format.equals("png")
                                || (!pngOnly && (format.equals("jpeg") || format.equals("jpg"))),
                        422,
                        "ARTIFACT_FORMAT_INVALID",
                        "仅支持PNG预览及PNG/JPEG纹理");
                int width = reader.getWidth(0), height = reader.getHeight(0);
                require(
                        width > 0 && height > 0 && (long) width * height <= 32_000_000,
                        413,
                        "UPLOAD_TOO_LARGE",
                        "图片解码像素数超限");
                require(reader.read(0) != null, 422, "ARTIFACT_FORMAT_INVALID", "图片数据不完整");
                return new Validated(
                        format.equals("png") ? "image/png" : "image/jpeg",
                        EngineeringJson.write(Map.of("width", width, "height", height)));
            } finally {
                reader.dispose();
            }
        }
    }

    private static Validated source(Path path) throws IOException {
        try (var in = Files.newInputStream(path)) {
            byte[] signature = in.readNBytes(4);
            require(
                    signature.length == 4
                            && signature[0] == 'P'
                            && signature[1] == 'K'
                            && (signature[2] == 3 || signature[2] == 5 || signature[2] == 7),
                    422,
                    "ARTIFACT_FORMAT_INVALID",
                    "源工程必须是归档容器，禁止上传可执行脚本");
        }
        // 仅检查归档目录，永不解压到服务器；源工程始终以附件下载。
        int files = 0;
        long totalExpanded = 0;
        try (var zip =
                new java.util.zip.ZipInputStream(
                        Files.newInputStream(path), StandardCharsets.UTF_8)) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                require(++files <= 4096, 413, "UPLOAD_TOO_LARGE", "工程归档条目过多");
                String name = entry.getName();
                if (entry.isDirectory() && name.endsWith("/"))
                    name = name.substring(0, name.length() - 1);
                EngineeringPaths.validate(name);
                require(
                        !name.matches("(?i).*\\.(exe|dll|bat|cmd|ps1|sh|js|mjs|vbs|jar)$"),
                        422,
                        "ARTIFACT_FORMAT_INVALID",
                        "源工程归档不允许携带可执行脚本");
                // 跳过条目会解压；总展开字节数仍须受限，防止压缩炸弹耗尽 CPU/磁盘。
                long expanded = 0;
                byte[] buffer = new byte[8192];
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    expanded += read;
                    totalExpanded += read;
                    require(
                            expanded <= 100L * 1024 * 1024 && totalExpanded <= 300L * 1024 * 1024,
                            413,
                            "UPLOAD_TOO_LARGE",
                            "工程归档单条目展开体积超限");
                }
            }
        }
        require(files > 0, 422, "ARTIFACT_FORMAT_INVALID", "源工程归档没有有效条目");
        return new Validated("application/zip", EngineeringJson.write(Map.of("entries", files)));
    }

    private static Validated model(Path path, String relativePath, boolean material)
            throws IOException {
        var references = new ArrayList<Map<String, String>>();
        int vertices = 0, faces = 0;
        long lines = 0;
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                require(
                        ++lines <= 4_000_000 && line.length() <= 8192,
                        413,
                        "UPLOAD_TOO_LARGE",
                        "模型文本行数或单行长度超限");
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] fields = line.split("\\s+");
                String op = fields[0];
                if (!material) {
                    require(
                            Set.of(
                                            "v", "vt", "vn", "vp", "f", "g", "o", "s", "usemtl",
                                            "mtllib", "l", "p")
                                    .contains(op),
                            422,
                            "ARTIFACT_REFERENCE_INVALID",
                            "OBJ包含不允许的外部指令");
                    if (op.equals("v")) {
                        require(
                                ++vertices <= 2_000_000 && fields.length >= 4 && fields.length <= 8,
                                413,
                                "UPLOAD_TOO_LARGE",
                                "OBJ顶点数量或坐标格式超限");
                        for (int i = 1; i < fields.length; i++)
                            require(
                                    Double.isFinite(Double.parseDouble(fields[i])),
                                    422,
                                    "ARTIFACT_FORMAT_INVALID",
                                    "OBJ坐标必须为有限数值");
                    }
                    if (op.equals("f")) {
                        require(
                                ++faces <= 2_000_000 && fields.length >= 4,
                                413,
                                "UPLOAD_TOO_LARGE",
                                "OBJ面数量或格式超限");
                        for (int i = 1; i < fields.length; i++) {
                            int index = Integer.parseInt(fields[i].split("/", -1)[0]);
                            require(
                                    index != 0 && Math.abs((long) index) <= vertices,
                                    422,
                                    "ARTIFACT_FORMAT_INVALID",
                                    "OBJ顶点引用越界");
                        }
                    }
                    if (op.equals("mtllib"))
                        for (int i = 1; i < fields.length; i++)
                            references.add(
                                    Map.of(
                                            "path",
                                            EngineeringPaths.reference(relativePath, fields[i]),
                                            "kind",
                                            "MTL"));
                } else {
                    require(
                            !Set.of("call", "csh", "include").contains(op),
                            422,
                            "ARTIFACT_REFERENCE_INVALID",
                            "MTL包含外部指令");
                    if (op.startsWith("map_")
                            || Set.of("bump", "disp", "decal", "refl", "norm").contains(op)) {
                        require(
                                fields.length == 2,
                                422,
                                "ARTIFACT_REFERENCE_INVALID",
                                "纹理引用必须为单个组内相对文件");
                        references.add(
                                Map.of(
                                        "path",
                                        EngineeringPaths.reference(relativePath, fields[1]),
                                        "kind",
                                        "TEXTURE"));
                    }
                }
                require(references.size() <= 64, 422, "ARTIFACT_REFERENCE_INVALID", "模型引用数超限");
            }
        }
        require(material || vertices > 0, 422, "ARTIFACT_FORMAT_INVALID", "OBJ不包含有效几何");
        return new Validated(
                "text/plain",
                EngineeringJson.write(
                        Map.of(
                                "references",
                                references,
                                "vertexCount",
                                vertices,
                                "faceCount",
                                faces)));
    }

    private static Validated bom(Path path) throws IOException {
        var rows = new ArrayList<Map<String, Object>>();
        long total = 0;
        try (var parser = JSON.getFactory().createParser(path.toFile())) {
            JsonToken token = parser.nextToken();
            boolean envelope = token == JsonToken.START_OBJECT;
            if (envelope) {
                require(
                        parser.nextToken() == JsonToken.FIELD_NAME
                                && Set.of("items", "rows").contains(parser.currentName()),
                        422,
                        "ARTIFACT_FORMAT_INVALID",
                        "BOM对象必须包含items或rows数组");
                token = parser.nextToken();
            }
            require(
                    token == JsonToken.START_ARRAY,
                    422,
                    "ARTIFACT_FORMAT_INVALID",
                    "BOM必须为规范JSON数组");
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                require(parser.currentToken() != null, 422, "ARTIFACT_FORMAT_INVALID", "BOM数组不完整");
                if (total++ >= 10000) {
                    parser.skipChildren();
                    continue;
                }
                JsonNode node = JSON.readTree(parser);
                require(node.isObject(), 422, "ARTIFACT_FORMAT_INVALID", "BOM行必须是对象");
                var row = new LinkedHashMap<String, Object>();
                for (String field : BOM_FIELDS) {
                    JsonNode value = node.get(field);
                    if (field.equals("quantity")) {
                        require(
                                value != null
                                        && !value.isNull()
                                        && (value.isTextual() || value.isNumber())
                                        && value.asText().matches("[0-9]{1,9}(\\.[0-9]{1,6})?")
                                        && new java.math.BigDecimal(value.asText()).signum() > 0,
                                422,
                                "ARTIFACT_FORMAT_INVALID",
                                "BOM数量格式无效");
                        row.put(field, value.asText());
                    } else {
                        require(
                                value == null
                                        || value.isNull()
                                        || value.isTextual() && value.textValue().length() <= 512,
                                422,
                                "ARTIFACT_FORMAT_INVALID",
                                "BOM字段必须为有界文本或null");
                        row.put(field, value == null || value.isNull() ? null : value.textValue());
                    }
                }
                rows.add(row);
            }
            if (envelope)
                require(
                        parser.nextToken() == JsonToken.END_OBJECT,
                        422,
                        "ARTIFACT_FORMAT_INVALID",
                        "BOM对象包含未开放字段");
            require(parser.nextToken() == null, 422, "ARTIFACT_FORMAT_INVALID", "BOM包含尾随内容");
        }
        return new Validated(
                "application/json",
                EngineeringJson.write(
                        Map.of(
                                "bom",
                                Map.of(
                                        "items",
                                        rows,
                                        "total",
                                        total,
                                        "truncated",
                                        total > 10000))));
    }

    private static Validated drc(Path path) throws IOException {
        String status = "UNAVAILABLE";
        Integer claimedErrors = null, claimedWarnings = null;
        int errors = 0, warnings = 0, total = 0;
        boolean hasIssues = false;
        int unclassified = 0;
        var issues = new ArrayList<JsonNode>();
        var seen = new HashSet<String>();
        try (var parser = JSON.getFactory().createParser(path.toFile())) {
            require(
                    parser.nextToken() == JsonToken.START_OBJECT,
                    422,
                    "ARTIFACT_FORMAT_INVALID",
                    "DRC报告必须为JSON对象");
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                require(
                        parser.currentToken() == JsonToken.FIELD_NAME,
                        422,
                        "ARTIFACT_FORMAT_INVALID",
                        "DRC报告结构不完整");
                String name = parser.currentName();
                require(seen.add(name), 422, "ARTIFACT_FORMAT_INVALID", "DRC字段不能重复");
                parser.nextToken();
                switch (name) {
                    case "status" -> status = parser.getValueAsString();
                    case "errorCount" -> {
                        require(
                                parser.currentToken() == JsonToken.VALUE_NUMBER_INT,
                                422,
                                "ARTIFACT_FORMAT_INVALID",
                                "DRC错误计数无效");
                        claimedErrors = parser.getIntValue();
                    }
                    case "warningCount" -> {
                        require(
                                parser.currentToken() == JsonToken.VALUE_NUMBER_INT,
                                422,
                                "ARTIFACT_FORMAT_INVALID",
                                "DRC警告计数无效");
                        claimedWarnings = parser.getIntValue();
                    }
                    case "violations", "issues", "items" -> {
                        require(
                                !hasIssues && parser.currentToken() == JsonToken.START_ARRAY,
                                422,
                                "ARTIFACT_FORMAT_INVALID",
                                "DRC违规明细必须是数组");
                        hasIssues = true;
                        while (parser.nextToken() != JsonToken.END_ARRAY) {
                            require(
                                    parser.currentToken() != null,
                                    422,
                                    "ARTIFACT_FORMAT_INVALID",
                                    "DRC违规数组不完整");
                            JsonNode issue = JSON.readTree(parser);
                            require(
                                    issue.isObject(),
                                    422,
                                    "ARTIFACT_FORMAT_INVALID",
                                    "DRC违规明细必须是对象");
                            String severity =
                                    issue.path("severity").asText("").toUpperCase(Locale.ROOT);
                            require(
                                    severity.isEmpty()
                                            || Set.of("ERROR", "WARNING").contains(severity),
                                    422,
                                    "ARTIFACT_FORMAT_INVALID",
                                    "DRC明细严重度无效");
                            if (severity.isEmpty()) unclassified++;
                            else if (severity.equals("ERROR")) errors++;
                            else warnings++;
                            if (total++ < 10000) issues.add(issue);
                        }
                    }
                    default -> parser.skipChildren();
                }
            }
            require(parser.nextToken() == null, 422, "ARTIFACT_FORMAT_INVALID", "DRC包含尾随内容");
        }
        require(
                status != null
                        && Set.of("PASS", "VIOLATIONS", "UNAVAILABLE", "NOT_RUN").contains(status),
                422,
                "ARTIFACT_FORMAT_INVALID",
                "DRC状态无效");
        if (status.equals("PASS") || status.equals("VIOLATIONS")) {
            require(
                    hasIssues
                            && claimedErrors != null
                            && claimedWarnings != null
                            && claimedErrors >= 0
                            && claimedWarnings >= 0,
                    422,
                    "ARTIFACT_FORMAT_INVALID",
                    "DRC必须包含统计和真实明细");
            // CLI 某些版本只有 code/message/detail；只核对总数，不捏造逐项严重度。
            require(
                    (long) claimedErrors + claimedWarnings == total
                            && claimedErrors >= errors
                            && claimedWarnings >= warnings
                            && (unclassified > 0
                                    || claimedErrors == errors && claimedWarnings == warnings),
                    422,
                    "ARTIFACT_FORMAT_INVALID",
                    "DRC统计与明细不一致");
            errors = claimedErrors;
            warnings = claimedWarnings;
            require(
                    status.equals("PASS") ? errors + warnings == 0 : errors + warnings > 0,
                    422,
                    "ARTIFACT_FORMAT_INVALID",
                    "DRC状态与违规明细不一致");
        } else require(errors + warnings == 0, 422, "ARTIFACT_FORMAT_INVALID", "未运行的DRC不能携带违规计数");
        return new Validated(
                "application/json",
                EngineeringJson.write(
                        Map.of(
                                "drc",
                                Map.of(
                                        "status",
                                        status,
                                        "errorCount",
                                        errors,
                                        "warningCount",
                                        warnings,
                                        "issues",
                                        issues,
                                        "truncated",
                                        total > 10000))));
    }

    /**
     * 已验证 MIME 和只供服务端使用的解析摘要。
     *
     * @param mimeType 服务器格式检测所得媒体类型
     * @param validationJson 只供服务端使用的有界格式解析摘要
     */
    public record Validated(String mimeType, String validationJson) {}
}
