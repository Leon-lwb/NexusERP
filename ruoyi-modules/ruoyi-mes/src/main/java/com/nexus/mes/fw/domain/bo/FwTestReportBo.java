package com.nexus.mes.fw.domain.bo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * MES 固件测试/刷写自动化上报参数 (AUTO 对接, 产线设备/上位机)
 * <p>无登录态开放接口专用: 设备按 SN + 测试项编码上报实测值,
 * 后端匹配物料测试项标准自动判定并落 fw_test_record(test_mode=AUTO)。</p>
 *
 * @author Nexus
 */
@Data
public class FwTestReportBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** SN编码(必填, 须存在于 pro_sn 且非报废) */
    @NotBlank(message = "SN编码不能为空")
    private String snCode;

    /** 测试时固件ID(可选; 提供则按固件范围过滤测试项) */
    private Long firmwareId;

    /** 测试设备编码(可选, 按 eq_equipment.equipment_code 匹配) */
    private String equipmentCode;

    /** 工序编码(可选) */
    private String processCode;

    /** 工序名称(可选) */
    private String processName;

    /** 测试项实测值列表(必填, 至少一项) */
    @Valid
    @NotEmpty(message = "测试项实测值不能为空")
    private List<FwTestItemValueBo> items;

    /**
     * 单测试项实测值
     */
    @Data
    public static class FwTestItemValueBo implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 测试项编码(fw_test_item.item_code, 如 RF-TXPWR) */
        @NotBlank(message = "测试项编码不能为空")
        private String itemCode;

        /** 实测值: NUMERIC 传数值, TEXT/ENUM 传字符串 */
        private String value;
    }
}