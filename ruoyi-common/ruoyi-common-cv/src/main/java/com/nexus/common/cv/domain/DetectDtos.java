package com.nexus.common.cv.domain;

import lombok.Data;

import java.util.List;

/**
 * 检测/OCR 响应 DTO
 * <p>与 nexus-vision-service /api/detect/material、/api/detect/product JSON 契约对应</p>
 *
 * @author nexus
 * @since 1.0.0
 */
public class DetectDtos {

    private DetectDtos() {
    }

    /**
     * YOLO 检测项
     */
    @Data
    public static class DetectionItem {
        private String className;
        private double confidence;
        private List<Double> bbox; // [x1, y1, x2, y2]
    }

    /**
     * OCR 文本项
     */
    @Data
    public static class OcrItem {
        private String text;
        private double confidence;
        private Object bbox;
    }

    /**
     * 物料/通用检测结果
     * <p>POST /api/detect/material → {success, detections: [DetectionItem], count, process_time_ms}</p>
     */
    @Data
    public static class MaterialDetectResult {
        private boolean success;
        private List<DetectionItem> detections;
        private int count;
        private double processTimeMs;
    }

    /** 工业缺陷检测结果；场景显式回传，bbox 保留原图像素坐标，不返回 OCR 数据。 */
    @Data
    public static class DefectDetectResult {
        private String scenario;
        private boolean success;
        private List<DetectionItem> detections;
        private int count;
        private double processTimeMs;
    }

    /**
     * 成品检测 + OCR 结果
     * <p>POST /api/detect/product → {success, detections, ocr_results: [OcrItem], count, process_time_ms}</p>
     */
    @Data
    public static class ProductDetectResult {
        /** 实际执行的模型场景；旧 OCR 响应可为空，工业成功响应必须匹配所选场景。 */
        private String effectiveScenario;
        private boolean success;
        private List<DetectionItem> detections;
        private List<OcrItem> ocrResults;
        private int count;
        private double processTimeMs;
    }
}
