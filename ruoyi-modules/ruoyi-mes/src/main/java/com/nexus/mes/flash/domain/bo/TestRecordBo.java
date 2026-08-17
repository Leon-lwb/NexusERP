package com.nexus.mes.flash.domain.bo;

import com.nexus.common.core.validate.AddGroup;
import com.nexus.common.core.validate.EditGroup;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.flash.domain.TestRecord;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;

/**
 * MES测试记录业务对象 mes_test_record (SN级全检)
 * <p>一次测试(同一 test_no)多行: 该 SN 该测试物料范围内每个测试项一行;
 * 开单(beginTest)自动匹配测试项并落待测行, 填报(reportTestRows)按行更新+自动判定。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = TestRecord.class)
public class TestRecordBo extends BaseEntity {

    /** 测试记录ID(填报时按行定位) */
    @Null(groups = {AddGroup.class})
    @NotNull(groups = {EditGroup.class})
    private Long testRecordId;

    /** 测试单号(一次SN测试一条单号, 共享多行) */
    private String testNo;

    /** SN ID */
    private Long snId;

    /** SN编码(开单必填, 需存在于 pro_sn 且非报废) */
    @NotBlank(message = "SN编码不能为空", groups = {AddGroup.class})
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

    /** 结果: PASS/FAIL(空=未测) */
    private String result;

    /** 测试人ID(默认当前登录人) */
    private Long testerId;

    /** 测试人姓名 */
    private String testerName;

    /** 测试设备ID */
    private Long equipmentId;

    /** 测试设备编码 */
    private String equipmentCode;

    /** 测试时间(默认当前时间) */
    private Date testTime;

    /** 测试时间区间-起(查询用) */
    private Date beginTestTime;

    /** 测试时间区间-止(查询用) */
    private Date endTestTime;

    /** 测试模式: AUTO/MANUAL */
    private String testMode;

    /** 备注 */
    private String remark;
}