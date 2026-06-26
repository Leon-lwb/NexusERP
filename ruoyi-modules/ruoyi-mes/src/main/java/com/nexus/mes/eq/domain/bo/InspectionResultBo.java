package com.nexus.mes.eq.domain.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 设备点检结果提交 BO
 * <p>
 * 对应 last_inspection JSON 结构:
 * <pre>
 * {
 *   "inspectTime": "2026-06-26T10:30:00",
 *   "inspectorId": 1,
 *   "inspectorName": "张三",
 *   "results": [
 *     {"item": "油位", "standard": "正常", "actual": "正常", "pass": true}
 *   ],
 *   "overallPass": true,
 *   "remark": "..."
 * }
 * </pre>
 *
 * @author nexus
 */
@Data
public class InspectionResultBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 点检结果明细 */
    @NotNull(message = "点检结果明细不能为空")
    private List<InspectionItemResult> results;

    /** 总体是否合格 */
    @NotNull(message = "总体合格性不能为空")
    private Boolean overallPass;

    /** 备注 */
    private String remark;

    /**
     * 单项点检结果
     */
    @Data
    public static class InspectionItemResult implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** 点检项目名称 */
        private String item;
        /** 标准值 */
        private String standard;
        /** 实际值 */
        private String actual;
        /** 是否合格 */
        private Boolean pass;
    }
}
