package com.nexus.mes.fw.domain.bo;

import com.nexus.common.core.validate.AddGroup;
import com.nexus.common.core.validate.EditGroup;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.fw.domain.FwFlashRecord;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * MES固件刷写记录业务对象 fw_flash_record
 * <p>M2 刷写作业: 补录(insertByBo)宽松自动回填, 作业确认(confirmFlash)严格校验:
 * SN 须存在且非报废, 固件须已发布(RELEASED)。同一 SN 允许多条(FAIL 重刷保留历史, 判定取最新一条)。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = FwFlashRecord.class)
public class FwFlashRecordBo extends BaseEntity {

    /** 刷写记录ID */
    @Null(groups = {AddGroup.class})
    @NotNull(groups = {EditGroup.class})
    private Long flashRecordId;

    /** 刷写记录号(查询用;新增/确认自动生成 MES_FLASH_NO = FL+yyyyMMdd+日流水) */
    private String flashNo;

    /** SN ID */
    private Long snId;

    /** SN编码(确认必填, 需存在于 pro_sn) */
    @NotBlank(message = "SN编码不能为空", groups = {AddGroup.class})
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

    /** 固件ID(确认必填, 须为已发布固件) */
    @NotNull(message = "固件不能为空", groups = {AddGroup.class})
    private Long firmwareId;

    /** 固件版本(由固件ID回填) */
    private String firmwareVersion;

    /** 实际刷写文件MD5 */
    private String fileMd5;

    /** 刷写设备ID(eq_equipment) */
    private Long equipmentId;

    /** 刷写设备编码(由设备ID回填) */
    private String equipmentCode;

    /** 操作人ID(查询用;新增默认当前登录人) */
    private Long operatorId;

    /** 操作人姓名(查询用) */
    private String operatorName;

    /** 刷写时间(新增默认当前时间) */
    private Date flashTime;

    /** 刷写时间区间-起(查询用) */
    private Date beginFlashTime;

    /** 刷写时间区间-止(查询用) */
    private Date endFlashTime;

    /** 结果: PASS/FAIL(确认必填) */
    @NotBlank(message = "刷写结果不能为空", groups = {AddGroup.class})
    private String result;

    /** 失败原因 */
    private String failReason;

    /** 重试次数(默认0) */
    private Integer retryCount;

    /** 刷写模式: AUTO/MANUAL */
    private String flashMode;

    /** 备注 */
    private String remark;
}