package com.nexus.mes.fw.domain.vo;

import com.nexus.common.translation.annotation.Translation;
import com.nexus.common.translation.constant.TransConstant;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.fw.domain.FwFlashRecord;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * MES固件刷写记录视图对象 fw_flash_record
 * <p>M2 刷写作业启用, M1 随 DDL-ORM 对齐一并建立。</p>
 *
 * @author Nexus
 */
@Data
@AutoMapper(target = FwFlashRecord.class)
public class FwFlashRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 刷写记录ID */
    private Long flashRecordId;

    /** 刷写记录号 */
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

    /** 流转卡工序记录ID */
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

    /** 刷写设备ID */
    private Long equipmentId;

    /** 刷写设备编码 */
    private String equipmentCode;

    /** 操作人ID */
    private Long operatorId;

    /** 操作人姓名(由 operatorId 翻译) */
    @Translation(type = TransConstant.USER_ID_TO_NAME, mapper = "operatorId")
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

    /** 创建时间 */
    private Date createTime;
}