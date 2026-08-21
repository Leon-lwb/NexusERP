package com.nexus.station.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 工位终端报工记录对象 mes_station_report
 * <p>
 * 设备通道过站报工(C3 单件/C4 批量)的幂等承载表: 唯一索引
 * uk_tenant_client_key(tenant_id, client_key) 兜底离线队列重放去重,
 * client_key = SINGLE|snCode|stationCode|clientTs 或 BATCH|stationCode|clientTs。
 * </p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_station_report")
public class MesStationReport extends TenantEntity {

    /** 报工记录ID(主键) */
    @TableId(value = "report_id")
    private Long reportId;

    /** 报工类型: SINGLE(单件过站)/BATCH(批量计数) */
    private String reportType;

    /** SN编码(SINGLE) */
    private String snCode;

    /** 工位编码 */
    private String stationCode;

    /** 流转卡ID */
    private Long cardId;

    /** 流转卡工序记录ID */
    private Long stepRecordId;

    /** MES工单ID */
    private Long mesWoId;

    /** 工单编号(冗余) */
    private String woNo;

    /** 操作员用户ID */
    private Long operatorId;

    /** 操作员工号 */
    private String operatorCode;

    /** 操作员姓名 */
    private String operatorName;

    /** 结果: PASS/FAIL(SINGLE) */
    private String result;

    /** 不良代码(逗号分隔) */
    private String defectCodes;

    /** 良品数量 */
    private BigDecimal goodQty;

    /** 不良数量 */
    private BigDecimal defectQty;

    /** 客户端时间戳(毫秒) */
    private Long clientTs;

    /** 幂等键: SINGLE|sn|station|ts 或 BATCH|station|ts */
    private String clientKey;
}
