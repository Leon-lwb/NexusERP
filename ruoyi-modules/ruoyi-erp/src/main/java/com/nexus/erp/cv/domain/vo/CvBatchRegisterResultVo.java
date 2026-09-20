package com.nexus.erp.cv.domain.vo;

import com.nexus.common.cv.domain.FaceDtos.RegisterResult;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 人脸底库批量注册结果（ERP-CV-04）
 * <p>
 * 原实现逐条 {@code catch} 后只 log、不返回失败原因，管理员无法知道哪些人没注册进去、也无法重试。
 * 本 VO 同时给出成功明细与失败明细（subject/工号/原因），供前端对账与重试。
 * <p>
 * 兼容性：原端点 {@code POST /cv/face/batch-register} 仍返回成功明细列表（{@link #success}），
 * 新增 {@code POST /cv/face/batch-register/detail} 返回本摘要。
 *
 * @author nexus
 * @since 2026-09-20
 */
@Data
public class CvBatchRegisterResultVo {

    /** 扫描到的用户数（含跳过项） */
    private int total;

    /** 成功注册数 */
    private int successCount;

    /** 失败/跳过数 */
    private int failedCount;

    /** 成功明细（vision 返回的注册结果） */
    private List<RegisterResult> success = new ArrayList<>();

    /** 失败/跳过明细（含原因，供重试与对账） */
    private List<FailedItem> failed = new ArrayList<>();

    /**
     * 失败/跳过明细项
     */
    @Data
    public static class FailedItem {

        /** 底库 subject（未取到工号时为 null） */
        private String subject;

        /** 系统用户ID */
        private Long userId;

        /** 工号（未绑定时为 null） */
        private String employeeId;

        /** 失败/跳过原因 */
        private String reason;

        /**
         * 构造失败项
         *
         * @param subject    底库 subject（可为 null）
         * @param userId     系统用户ID
         * @param employeeId 工号（可为 null）
         * @param reason     原因
         * @return 失败项
         */
        public static FailedItem of(String subject, Long userId, String employeeId, String reason) {
            FailedItem item = new FailedItem();
            item.setSubject(subject);
            item.setUserId(userId);
            item.setEmployeeId(employeeId);
            item.setReason(reason);
            return item;
        }
    }
}
