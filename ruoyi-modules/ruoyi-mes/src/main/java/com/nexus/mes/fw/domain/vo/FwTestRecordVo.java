package com.nexus.mes.fw.domain.vo;

import com.nexus.common.translation.annotation.Translation;
import com.nexus.common.translation.constant.TransConstant;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.fw.domain.FwTestRecord;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * MES测试记录视图对象 fw_test_record
 * <p>M3 测试流程启用, M1 随 DDL-ORM 对齐一并建立。</p>
 *
 * @author Nexus
 */
@Data
@AutoMapper(target = FwTestRecord.class)
public class FwTestRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 测试记录ID */
    private Long testRecordId;

    /** 测试单号 */
    private String testNo;

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

    /** 测试时固件ID */
    private Long firmwareId;

    /** 测试时固件版本 */
    private String firmwareVersion;

    /** 测试项ID */
    private Long testItemId;

    /** 测试项编码(快照) */
    private String itemCode;

    /** 测试项名称(快照) */
    private String itemName;

    /** 数据类型(快照) */
    private String dataType;

    /** 单位 */
    private String unit;

    /** 实测数值 */
    private BigDecimal numericValue;

    /** 实测文本 */
    private String textValue;

    /** 上限(快照) */
    private BigDecimal upperLimit;

    /** 下限(快照) */
    private BigDecimal lowerLimit;

    /** 目标值(快照) */
    private BigDecimal targetValue;

    /** ENUM合格值(快照) */
    private String passEnum;

    /** 结果: PASS/FAIL */
    private String result;

    /** 测试人ID */
    private Long testerId;

    /** 测试人姓名(由 testerId 翻译) */
    @Translation(type = TransConstant.USER_ID_TO_NAME, mapper = "testerId")
    private String testerName;

    /** 测试设备ID */
    private Long equipmentId;

    /** 测试设备编码 */
    private String equipmentCode;

    /** 测试时间 */
    private Date testTime;

    /** 测试模式: AUTO/MANUAL */
    private String testMode;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private Date createTime;
}