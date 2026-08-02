package com.nexus.common.cv.domain;

import lombok.Data;

import java.util.List;

/**
 * 人脸识别相关响应 DTO
 * <p>与 nexus-vision-service /api/v1/faces* 端点 JSON 契约对应</p>
 *
 * @author nexus
 * @since 1.0.0
 */
public class FaceDtos {

    private FaceDtos() {
    }

    /**
     * 人脸框
     */
    @Data
    public static class FaceBox {
        private double x;
        private double y;
        private double width;
        private double height;
    }

    /**
     * 1:1 验证结果
     * <p>POST /api/v1/faces/verify → {success, subject, similarity, matched, reason, process_time_ms}</p>
     */
    @Data
    public static class VerifyResult {
        private boolean success;
        private String subject;
        private double similarity;
        private boolean matched;
        private String reason;
        private double processTimeMs;
    }

    /**
     * 1:N 识别匹配项
     */
    @Data
    public static class MatchItem {
        private String subject;
        private double similarity;
        private boolean matched;
        private FaceBox box;
    }

    /**
     * 1:N 识别结果
     * <p>POST /api/v1/faces/recognize → {success, result: [MatchItem], face_count, process_time_ms}</p>
     */
    @Data
    public static class RecognizeResult {
        private boolean success;
        private List<MatchItem> result;
        private int faceCount;
        private double processTimeMs;
    }

    /**
     * 注册结果
     * <p>POST /api/v1/faces → {success, subject, face_count, ...}</p>
     */
    @Data
    public static class RegisterResult {
        private boolean success;
        private String subject;
        private int faceCount;
        private double processTimeMs;
    }

    /**
     * 底库条目(列表/详情)
     */
    @Data
    public static class SubjectItem {
        private String subject;
        private String displayName;
        private int faceCount;
        private String remark;
        private String photo; // 详情含照片 base64
        private String createTime;
        private String updateTime;
    }

    /**
     * 底库列表
     * <p>GET /api/v1/faces?page=&page_size= → {success, total, items}</p>
     */
    @Data
    public static class SubjectList {
        private boolean success;
        private long total;
        private List<SubjectItem> items;
    }
}
