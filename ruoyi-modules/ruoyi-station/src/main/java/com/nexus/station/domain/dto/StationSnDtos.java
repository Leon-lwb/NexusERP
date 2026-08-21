package com.nexus.station.domain.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 关键件绑定/解绑请求 (M2 契约 C5/C6)
 *
 * @author Nexus
 */
public final class StationSnDtos {

    private StationSnDtos() {
    }

    /**
     * 关键件批量绑定请求(C5)
     */
    @Data
    public static class BindDto implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 父SN编码(整机/主板) */
        private String parentSnCode;

        /** 组件SN编码列表(关键件) */
        private List<String> componentSnCodes;

        /** 操作员工号(可空) */
        private String operatorCode;
    }

    /**
     * 关键件解绑/换板请求(C6): 传 newComponentSnCode 即为换板(解绑+重绑一步完成)
     */
    @Data
    public static class UnbindDto implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 被解绑组件SN编码 */
        private String componentSnCode;

        /** 换板新组件SN编码(可空; 传则解绑后立即重绑到原父SN) */
        private String newComponentSnCode;

        /** 操作原因(必填) */
        private String reason;

        /** 操作员工号(可空) */
        private String operatorCode;
    }
}
