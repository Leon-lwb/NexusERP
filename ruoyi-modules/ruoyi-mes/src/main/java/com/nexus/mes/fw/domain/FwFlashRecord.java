package com.nexus.mes.fw.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * MES固件刷写记录对象 fw_flash_record (SN级)
 * <p>同一 SN 允许多条(FAIL 重刷保留历史), 判定取最新一条; idx_firmware 支撑按固件召回。</p>
 * <p>创建归属 M2 刷写作业(confirm), M1 仅落实体/Mapper 供删除/归档依赖校验。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fw_flash_record")
public class FwFlashRecord extends TenantEntity {

    /** 刷写记录ID(主键) */
    @TableId(value = "flash_record_id")
    private Long flashRecordId;

    /** 刷写记录号(自动编码 MES_FLASH_NO) */
    private String flashNo;

    /** SN ID */
    private Long snId;

    /** SN编码 */
    private String snCode;

    /** MES工单ID */
    private Long mesWoId;

    /** 工单号 */
    private String woNo;

    /** 流转卡ID */
    private Long cardId;

    /** 流转卡工序记录ID(pro_flow_card_step) */
    private Long stepRecordId;

    /** 工序ID */
    private Long processId;

    /** 工序编码 */
    private String processCode;

    /** 工序名称 */
    private String processName;

    /** 固件ID */
    private Long firmwareId;

    /** 固件版本 */
    private String firmwareVersion;

    /** 实际刷写文件MD5 */
    private String fileMd5;

    /** 刷写设备ID(eq_equipment) */
    private Long equipmentId;

    /** 刷写设备编码 */
    private String equipmentCode;

    /** 操作人ID */
    private Long operatorId;

    /** 操作人姓名 */
    private String operatorName;

    /** 刷写时间 */
    private Date flashTime;

    /** 结果: PASS/FAIL */
    private String result;

    /** 失败原因 */
    private String failReason;

    /** 重试次数 */
    private Integer retryCount;

    /** 刷写模式: AUTO/MANUAL */
    private String flashMode;

    /** 备注 */
    private String remark;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}