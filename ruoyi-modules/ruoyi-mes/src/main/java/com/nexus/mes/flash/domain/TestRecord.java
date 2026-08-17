package com.nexus.mes.flash.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;

/**
 * MES测试记录对象 mes_test_record (SN级全检)
 * <p>一次测试(同一 test_no)多行: 该 SN 该测试工序每个必测项一行; uk_tenant_test_no_item 幂等。</p>
 * <p>创建归属 M3 测试流程(report/manual), M1 仅落实体/Mapper(DDL-ORM 对齐)。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_test_record")
public class TestRecord extends TenantEntity {

    /** 测试记录ID(主键) */
    @TableId(value = "test_record_id")
    private Long testRecordId;

    /** 测试单号(一次SN测试一条单号, 共享多行) */
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

    /** 流转卡工序记录ID(测试工序) */
    private Long stepRecordId;

    /** 工序ID */
    private Long processId;

    /** 工序编码 */
    private String processCode;

    /** 工序名称 */
    private String processName;

    /** 测试时固件ID */
    private Long contentRefId;

    /** 测试时固件版本 */
    private String contentVersion;

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

    /** 测试人姓名 */
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

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}