package com.nexus.common.cv.client;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.nexus.common.cv.config.CvProperties;
import com.nexus.common.cv.domain.DetectDtos.DetectionItem;
import com.nexus.common.cv.domain.DetectDtos.MaterialDetectResult;
import com.nexus.common.cv.domain.DetectDtos.OcrItem;
import com.nexus.common.cv.domain.DetectDtos.ProductDetectResult;
import com.nexus.common.cv.domain.FaceDtos.FaceBox;
import com.nexus.common.cv.domain.FaceDtos.MatchItem;
import com.nexus.common.cv.domain.FaceDtos.RecognizeResult;
import com.nexus.common.cv.domain.FaceDtos.RegisterResult;
import com.nexus.common.cv.domain.FaceDtos.SubjectItem;
import com.nexus.common.cv.domain.FaceDtos.SubjectList;
import com.nexus.common.cv.domain.FaceDtos.VerifyResult;
import com.nexus.common.cv.domain.HealthResult;
import com.nexus.common.cv.exception.VisionException;
import com.nexus.common.json.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * VisionClient 默认实现
 * <p>基于 Hutool HttpUtil,multipart file 优先,image_base64 兜底;统一携带 x-api-key 头。</p>
 * <p>disabled 或未配置 api-key 时,业务方法直接抛 {@link VisionException}(业务方捕获降级)。</p>
 *
 * @author nexus
 * @since 1.0.0
 */
@Slf4j
public class VisionClientImpl implements VisionClient {

    private final CvProperties properties;

    /** 连续健康检查失败计数 */
    private volatile int healthFailCount = 0;

    public VisionClientImpl(CvProperties properties) {
        this.properties = properties;
    }

    @Override
    public VerifyResult verifyFace(byte[] image, String subject) {
        JsonNode json = multipart("/api/v1/faces/verify",
            List.of(new Part("subject", subject)), image, "image");
        VerifyResult result = new VerifyResult();
        result.setSuccess(json.path("success").asBoolean(false));
        result.setSubject(subject);
        result.setSimilarity(json.path("similarity").asDouble(0.0));
        result.setMatched(json.path("matched").asBoolean(false));
        result.setReason(json.path("reason").asText("ok"));
        result.setProcessTimeMs(json.path("process_time_ms").asDouble(0.0));
        return result;
    }

    @Override
    public RecognizeResult recognizeFace(byte[] image, int limit) {
        JsonNode json = multipart("/api/v1/faces/recognize",
            List.of(new Part("limit", String.valueOf(Math.max(1, Math.min(limit, 10))))),
            image, "image");
        RecognizeResult result = new RecognizeResult();
        result.setSuccess(json.path("success").asBoolean(false));
        result.setFaceCount(json.path("face_count").asInt(0));
        result.setProcessTimeMs(json.path("process_time_ms").asDouble(0.0));
        List<MatchItem> items = new ArrayList<>();
        for (JsonNode node : json.path("result")) {
            MatchItem item = new MatchItem();
            item.setSubject(node.path("subject").asText());
            item.setSimilarity(node.path("similarity").asDouble(0.0));
            item.setMatched(node.path("matched").asBoolean(false));
            JsonNode box = node.path("box");
            if (box.isObject() && !box.isEmpty()) {
                FaceBox faceBox = new FaceBox();
                faceBox.setX(box.path("x").asDouble(0.0));
                faceBox.setY(box.path("y").asDouble(0.0));
                faceBox.setWidth(box.path("width").asDouble(0.0));
                faceBox.setHeight(box.path("height").asDouble(0.0));
                item.setBox(faceBox);
            }
            items.add(item);
        }
        result.setResult(items);
        return result;
    }

    @Override
    public RegisterResult registerFace(byte[] image, String subject, String displayName, String remark) {
        List<Part> parts = new ArrayList<>();
        parts.add(new Part("subject", subject));
        if (StrUtil.isNotBlank(displayName)) {
            parts.add(new Part("display_name", displayName));
        }
        if (StrUtil.isNotBlank(remark)) {
            parts.add(new Part("remark", remark));
        }
        JsonNode json = multipart("/api/v1/faces", parts, image, "image");
        RegisterResult result = new RegisterResult();
        result.setSuccess(json.path("success").asBoolean(false));
        result.setSubject(subject);
        result.setFaceCount(json.path("face_count").asInt(1));
        result.setProcessTimeMs(json.path("process_time_ms").asDouble(0.0));
        return result;
    }

    @Override
    public SubjectList listFaces(int page, int pageSize) {
        String url = url("/api/v1/faces") + "?page=" + page + "&page_size=" + pageSize;
        JsonNode json = getJson(url);
        SubjectList result = new SubjectList();
        result.setSuccess(json.path("success").asBoolean(false));
        result.setTotal(json.path("total").asLong(0L));
        List<SubjectItem> items = new ArrayList<>();
        for (JsonNode node : json.path("items")) {
            items.add(toSubjectItem(node));
        }
        result.setItems(items);
        return result;
    }

    @Override
    public SubjectItem getFace(String subject) {
        JsonNode json = getJson(url("/api/v1/faces/" + encodePath(subject)));
        return toSubjectItem(json);
    }

    @Override
    public boolean deleteFace(String subject) {
        String url = url("/api/v1/faces/" + encodePath(subject));
        try (HttpResponse response = HttpRequest.delete(url)
            .header("x-api-key", apiKey())
            .timeout(properties.getTimeoutMs())
            .execute()) {
            if (response.getStatus() != 200) {
                throw new VisionException("删除底库失败 HTTP " + response.getStatus(), response.getStatus(), "DELETE_FAILED");
            }
            return parseJson(response.body()).path("success").asBoolean(false);
        }
    }

    @Override
    public MaterialDetectResult detectMaterial(byte[] image) {
        JsonNode json = multipart("/api/detect/material", List.of(), image, "file");
        MaterialDetectResult result = new MaterialDetectResult();
        result.setSuccess(json.path("success").asBoolean(false));
        result.setCount(json.path("count").asInt(0));
        result.setProcessTimeMs(json.path("process_time_ms").asDouble(0.0));
        result.setDetections(parseDetections(json));
        return result;
    }

    @Override
    public ProductDetectResult detectProduct(byte[] image, boolean enableOcr) {
        List<Part> parts = List.of(new Part("enable_ocr", String.valueOf(enableOcr)));
        JsonNode json = multipart("/api/detect/product", parts, image, "file");
        ProductDetectResult result = new ProductDetectResult();
        result.setSuccess(json.path("success").asBoolean(false));
        result.setCount(json.path("count").asInt(0));
        result.setProcessTimeMs(json.path("process_time_ms").asDouble(0.0));
        result.setDetections(parseDetections(json));
        List<OcrItem> ocr = new ArrayList<>();
        for (JsonNode node : json.path("ocr_results")) {
            OcrItem item = new OcrItem();
            item.setText(node.path("text").asText());
            item.setConfidence(node.path("confidence").asDouble(0.0));
            item.setBbox(node.get("bbox"));
            ocr.add(item);
        }
        result.setOcrResults(ocr);
        return result;
    }

    @Override
    public HealthResult health() {
        return getHealth("/health");
    }

    @Override
    public HealthResult ready() {
        return getHealth("/health/ready");
    }

    @Override
    public boolean isAvailable() {
        try {
            HealthResult result = ready();
            if ("ready".equals(result.getStatus()) || "ok".equals(result.getStatus())) {
                healthFailCount = 0;
                return true;
            }
            return false;
        } catch (VisionException e) {
            healthFailCount++;
            if (healthFailCount == properties.getHealthFailThreshold()) {
                log.error("[cv] vision 连续 {} 次健康检查失败,判定服务不可用,请检查 nexus-vision-service 状态",
                    properties.getHealthFailThreshold());
            }
            return false;
        }
    }

    @Override
    public void resetHealth() {
        healthFailCount = 0;
    }

    // ===== 内部工具 =====

    /**
     * 健康检查(存活/就绪共用)
     *
     * @param path 探针路径(/health 或 /health/ready)
     */
    private HealthResult getHealth(String path) {
        JsonNode json = getJson(path);
        HealthResult result = new HealthResult();
        result.setStatus(json.path("status").asText());
        result.setService(json.path("service").asText());
        List<String> models = new ArrayList<>();
        for (JsonNode node : json.path("models")) {
            models.add(node.asText());
        }
        result.setModels(models);
        return result;
    }

    /**
     * 解析 YOLO 检测项列表
     *
     * @param json 响应 JSON(detections 数组)
     */
    private List<DetectionItem> parseDetections(JsonNode json) {
        List<DetectionItem> items = new ArrayList<>();
        for (JsonNode node : json.path("detections")) {
            DetectionItem item = new DetectionItem();
            item.setClassName(node.path("class_name").asText());
            item.setConfidence(node.path("confidence").asDouble(0.0));
            List<Double> bbox = new ArrayList<>();
            for (JsonNode n : node.path("bbox")) {
                bbox.add(n.asDouble());
            }
            item.setBbox(bbox);
            items.add(item);
        }
        return items;
    }

    /**
     * 解析底库条目
     *
     * @param node 响应 JSON 节点
     */
    private SubjectItem toSubjectItem(JsonNode node) {
        SubjectItem item = new SubjectItem();
        item.setSubject(node.path("subject").asText());
        item.setDisplayName(node.path("display_name").asText());
        item.setFaceCount(node.path("face_count").asInt(0));
        item.setRemark(node.path("remark").asText());
        item.setPhoto(node.path("photo").asText());
        item.setCreateTime(node.path("create_time").asText());
        item.setUpdateTime(node.path("update_time").asText());
        return item;
    }

    /**
     * multipart 表单请求(图片 + 附加表单字段)
     *
     * @param path      请求路径
     * @param formParts 附加表单字段(subject/limit/enable_ocr 等)
     * @param image     图片字节
     * @param fileField 文件字段名(vision 端点用 image 或 file)
     */
    private JsonNode multipart(String path, List<Part> formParts, byte[] image, String fileField) {
        ensureEnabled();
        String url = url(path);
        HttpRequest request = HttpRequest.post(url)
            .header("x-api-key", apiKey())
            .timeout(properties.getTimeoutMs());
        for (Part part : formParts) {
            request.form(part.name(), part.value());
        }
        request.form(fileField, image, "image.jpg");
        return execute(request);
    }

    /**
     * GET 请求(携带 x-api-key)
     *
     * @param url 完整 URL
     */
    private JsonNode getJson(String url) {
        ensureEnabled();
        HttpRequest request = HttpRequest.get(url)
            .header("x-api-key", apiKey())
            .timeout(properties.getTimeoutMs());
        return execute(request);
    }

    /**
     * 执行请求并解析响应
     * <p>非 200 → 抛 {@link VisionException}(含 HTTP 状态与响应摘要);网络/解析异常统一包装。</p>
     *
     * @param request 已构建的 Hutool 请求
     */
    private JsonNode execute(HttpRequest request) {
        try (HttpResponse response = request.execute()) {
            if (response.getStatus() != 200) {
                String detail = response.body() != null ? StrUtil.maxLength(response.body(), 300) : "";
                throw new VisionException("视觉服务调用失败 HTTP " + response.getStatus() + " " + detail,
                    response.getStatus(), "HTTP_" + response.getStatus());
            }
            return parseJson(response.body());
        } catch (VisionException e) {
            throw e;
        } catch (Exception e) {
            throw new VisionException("视觉服务调用异常: " + e.getMessage(), e);
        }
    }

    /**
     * 解析响应 JSON(项目统一 Jackson ObjectMapper)
     *
     * @param body 响应体
     */
    private JsonNode parseJson(String body) {
        try {
            return JsonUtils.getObjectMapper().readTree(body);
        } catch (Exception e) {
            throw new VisionException("视觉服务响应解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 调用前校验:服务已启用且已配置 api-key,否则抛 {@link VisionException}
     */
    private void ensureEnabled() {
        if (!properties.isEnabled()) {
            throw new VisionException("视觉服务已禁用(nexus.vision.enabled=false)");
        }
        if (StrUtil.isBlank(properties.getApiKey())) {
            throw new VisionException("未配置 nexus.vision.api-key");
        }
    }

    private String apiKey() {
        return properties.getApiKey();
    }

    /**
     * 拼接完整 URL(base-url 去尾部斜杠 + 路径)
     */
    private String url(String path) {
        String base = properties.getBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + path;
    }

    /**
     * URL 编码路径段(subject 可能含特殊字符)
     */
    private String encodePath(String segment) {
        return HttpUtil.encodeParams(segment, StandardCharsets.UTF_8);
    }

    /** 表单字段 */
    private record Part(String name, String value) {
    }
}
