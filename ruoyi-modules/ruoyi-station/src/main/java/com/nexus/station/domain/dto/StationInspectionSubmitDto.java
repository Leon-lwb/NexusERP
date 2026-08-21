package com.nexus.station.domain.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 工位终端点检提交请求 (M3 契约 C15)
 *
 * @author Nexus
 */
@Data
public class StationInspectionSubmitDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 工位编码 */
    private String stationCode;

    /** 点检人工号(免登录通道, 留痕并解析档案) */
    private String operatorCode;

    /** 设备ID */
    private Long equipmentId;

    /** 逐项点检结果 */
    private List<ItemResult> results;

    /** 总判定(缺省 = 全部项 pass 与) */
    private Boolean overallPass;

    /** 备注 */
    private String remark;

    /** 单项点检结果 */
    @Data
    public static class ItemResult implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 点检项目 */
        private String item;

        /** 标准值 */
        private String standard;

        /** 实际值 */
        private String actual;

        /** 是否合格 */
        private Boolean pass;
    }
}
