package com.nexus.mes.pda.domain.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * PDA 设备点检详情 (点检项模板 + 上次结果回显)
 */
@Data
public class PdaEqInspectionVo {

    private Long equipmentId;
    private String equipmentCode;
    private String equipmentName;
    private String status;
    private String workcenterCode;

    /** 点检项模板 (来源设备 inspectionItems): [{ item, standard }] */
    private List<Map<String, Object>> items;

    /** 上次点检结果 (来源设备 lastInspection): { inspectTime, inspectorName, results, overallPass, remark } */
    private Map<String, Object> lastInspection;
}
